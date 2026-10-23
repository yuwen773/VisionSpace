package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具执行边界：走真实台账 + 有状态 Mapper 替身，断言落在外部调用次数与持久状态上。
 */
class AgentActionInterceptorTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-1";
    private static final String CALL_ID = "call-1";

    private InMemoryActionRecordMapper store;
    private AgentActionLedger ledger;
    private AgentActionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        store = new InMemoryActionRecordMapper();
        ledger = new AgentActionLedger(store.proxy());
        interceptor = new AgentActionInterceptor(ledger);
    }

    @Test
    void successfulCallIsRecordedAndExternalActionRunsOnce() {
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "[\"url\"]");
        };

        interceptor.interceptToolCall(request("generateImages"), handler);

        assertEquals(1, externalCalls.get());
        assertEquals(AgentActionRecord.STATUS_SUCCEEDED, status("generateImages"));
    }

    @Test
    void alreadyCompletedActionProducesNoExternalCall() {
        ledger.markSucceeded(ledger.claim(THREAD, OWNER, actionId(), "generateImages",
                "{\"prompt\":\"x\"}", AgentActionRecord.IDEMPOTENCY_NOT_PROVEN), "ok");
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "x");
        };

        ToolCallResponse response = interceptor.interceptToolCall(request("generateImages"), handler);

        assertEquals(0, externalCalls.get(), "confirmed action must not be re-executed");
        assertTrue(response.getResult().contains("已确认完成"),
                "only a proven completion may say 已完成: " + response.getResult());
        assertFalse(response.isError());
    }

    @Test
    void toolLevelErrorIsExplicitFailureAndRetryable() {
        ToolCallHandler handler = request ->
                ToolCallResponse.error(CALL_ID, "generateImages", "quota exceeded");

        interceptor.interceptToolCall(request("generateImages"), handler);

        assertEquals(AgentActionRecord.STATUS_FAILED, status("generateImages"));
    }

    @Test
    void exceptionOnNonIdempotentToolPausesForReviewInsteadOfRetrying() {
        ToolCallHandler handler = request -> {
            throw new IllegalStateException("connection reset after upload");
        };

        PendingReviewException error = assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request("generateImages"), handler));

        assertEquals("generateImages", error.getToolName());
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status("generateImages"));
    }

    @Test
    void exceptionOnIdempotentToolPropagatesAsRetryableFailure() {
        ToolCallHandler handler = request -> {
            throw new IllegalStateException("timeout");
        };

        assertThrows(IllegalStateException.class,
                () -> interceptor.interceptToolCall(request("searchContentImages"), handler));

        // 可证明幂等：退回 FAILED 走安全重试，而不是待核对
        assertEquals(AgentActionRecord.STATUS_FAILED, status("searchContentImages"));
    }

    @Test
    void inFlightActionPausesTheRunInsteadOfLettingTheModelRetry() {
        ledger.claim(THREAD, OWNER, actionId(), "generateImages", "{\"prompt\":\"x\"}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "x");
        };

        // 只回一句「正在执行中」是不够的：模型会换个 toolCallId 再来一次，
        // 同一个外部副作用就会产生两次。没有完成证据就不能继续。
        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request("generateImages"), handler));

        assertEquals(0, externalCalls.get());
        assertEquals(AgentActionRecord.STATUS_EXECUTING, status("generateImages"),
                "the original claim must stay untouched");
    }

    @Test
    void inFlightIdempotentActionReportsExecutingWithoutPausing() {
        ledger.claim(THREAD, OWNER, actionId(), "searchContentImages", "{\"prompt\":\"x\"}",
                AgentActionRecord.IDEMPOTENCY_PROVEN);
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "searchContentImages", "x");
        };

        ToolCallResponse response =
                interceptor.interceptToolCall(request("searchContentImages"), handler);

        // 只读工具没有副作用，回一条错误信息即可，不必中断整轮
        assertEquals(0, externalCalls.get());
        assertTrue(response.isError(), "an in-flight action is not a completion");
        assertFalse(response.getResult().contains("已确认完成"));
        assertTrue(response.getResult().contains("正在执行"));
    }

    @Test
    void actionWaitingForReviewPausesTheRunInsteadOfFakingCompletion() {
        ToolCallHandler crash = request -> {
            throw new IllegalStateException("uploaded then lost the response");
        };
        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request("generateImages"), crash));

        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler retry = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "x");
        };

        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request("generateImages"), retry),
                "an un-reviewed action must pause the run, not be described as done");

        assertEquals(0, externalCalls.get());
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status("generateImages"));
    }

    @Test
    void ledgerFailureIsReportedAsUnavailableNotAsCompleted() {
        // 台账完全不可用：认领与读回都抛错
        AgentActionRecordMapperFailer failing = new AgentActionRecordMapperFailer();
        AgentActionInterceptor broken = new AgentActionInterceptor(new AgentActionLedger(failing.proxy()));
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "x");
        };

        ToolCallResponse response = broken.interceptToolCall(request("generateImages"), handler);

        assertEquals(0, externalCalls.get(), "a broken ledger must not let the side effect through");
        assertTrue(response.isError(), "ledger failure is not a completion");
        assertFalse(response.getResult().contains("已确认完成"));
        assertTrue(response.getResult().contains("台账"));
    }

    @Test
    void missingOwnerSkipsLedgerButStillExecutes() {
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "searchContentImages", "ok");
        };
        RunnableConfig anonymous = RunnableConfig.builder().threadId(THREAD).build();

        interceptor.interceptToolCall(request(anonymous, "searchContentImages"), handler);

        assertEquals(1, externalCalls.get());
        assertNull(status("searchContentImages"), "no trusted owner means no ledger row");
    }

    @Test
    void idempotencyClassificationIsExplicit() {
        assertTrue(AgentActionInterceptor.isIdempotent("searchContentImages"));
        assertTrue(AgentActionInterceptor.isIdempotent("evaluateImageQuality"));
        assertFalse(AgentActionInterceptor.isIdempotent("generateImages"),
                "generation creates external resources and cannot be proven idempotent");
        assertFalse(AgentActionInterceptor.isIdempotent(null));
    }

    private String status(String toolName) {
        return store.status(THREAD, OWNER, AgentActionLedger.actionId(THREAD, CALL_ID));
    }

    private String actionId() {
        return AgentActionLedger.actionId(THREAD, CALL_ID);
    }

    private ToolCallRequest request(String toolName) {
        return request(config(), toolName);
    }

    private ToolCallRequest request(RunnableConfig config, String toolName) {
        ToolCallExecutionContext context = new ToolCallExecutionContext(config, new OverAllState(Map.of()));
        return ToolCallRequest.builder()
                .toolName(toolName)
                .arguments("{\"prompt\":\"x\"}")
                .toolCallId(CALL_ID)
                .context(Map.of())
                .executionContext(context)
                .build();
    }

    private RunnableConfig config() {
        return RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
    }
}
