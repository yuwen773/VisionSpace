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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一动作的互斥：真实 {@link AgentActionLedger} + 真实拦截器 + 有状态 Mapper 替身。
 *
 * <p>评审的受控双线程探针证明同一 action 的外部 handler 被调用两次。这里的断言
 * 落在<b>外部调用次数</b>与<b>最终持久状态</b>上，不看返回值。
 */
class AgentActionConcurrencyTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-concurrent";
    private static final String CALL_ID = "call-1";
    private static final String TOOL = "generateImages";

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
    void twoConcurrentCallsForTheSameActionRunTheExternalToolOnce() throws Exception {
        AtomicInteger externalCalls = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            firstEntered.countDown();
            try {
                releaseFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ToolCallResponse.of(CALL_ID, TOOL, "[\"url\"]");
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> interceptor.interceptToolCall(request(), handler));
            assertTrue(firstEntered.await(10, TimeUnit.SECONDS), "first call should reach the tool");

            // 第一个调用还在外部执行中：第二个必须被挡住，而不是也进入 handler
            Future<?> second = pool.submit(() -> interceptor.interceptToolCall(request(), handler));

            // 第二个在被挡住时立刻结束（不会进入 handler，因此不必等第一个释放）。
            // 顺序很重要：先等第二个落败，再放行第一个，否则第一个可能已经结算成
            // SUCCEEDED，第二次就会合理地拿到「已确认完成」而不是暂停。
            ExecutionException blocked = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);

            // 第二个不是「拿一条错误文本继续跑」：模型会换个 toolCallId 再来一次，
            // 同一个外部副作用就产生两次。没有完成证据就必须暂停本轮。
            assertTrue(blocked.getCause() instanceof PendingReviewException,
                    "an in-flight action must pause the run: " + blocked.getCause());

            assertEquals(1, externalCalls.get(),
                    "the same action must not reach the external tool twice");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crashAfterExternalSideEffectDoesNotAllowImmediateReplay() {
        // 模拟「外部已执行、本地未提交就崩溃」：行停在 EXECUTING
        ToolCallHandler crash = request -> {
            throw new IllegalStateException("process died after the upload");
        };
        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request(), crash));
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW,
                store.status(THREAD, OWNER, actionId()));

        // 立刻重来（远早于任何宽限期）：不能再次调用外部，也不能让运行继续
        AtomicInteger replayCalls = new AtomicInteger();
        ToolCallHandler replay = request -> {
            replayCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, TOOL, "[\"url\"]");
        };
        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request(), replay),
                "an unknown-result action must pause the run, not report a fake success");

        assertEquals(0, replayCalls.get(),
                "an unknown-result action must never be re-executed automatically");
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW,
                store.status(THREAD, OWNER, actionId()));
    }

    @Test
    void inFlightExecutingRowIsNotReclaimableEvenWithoutAnyGracePeriod() {
        // 直接制造一条刚写入的 EXECUTING：没有时间可以依赖
        AgentActionClaim first = ledger.claim(THREAD, OWNER, actionId(), TOOL, "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        assertTrue(first.acquired(), "the first claim owns the action");

        AgentActionClaim second = ledger.claim(THREAD, OWNER, actionId(), TOOL, "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.IN_FLIGHT, second.outcome(),
                "an in-flight action is claimed by someone else, regardless of elapsed time");
        assertNull(second.token(), "a loser must not receive a claim token");
    }

    @Test
    void onlyTheClaimOwnerCanSettleTheAction() {
        AgentActionClaim claim = ledger.claim(THREAD, OWNER, actionId(), TOOL, "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        // 没有 token 或 token 不对：不能把动作推进到终态
        boolean settledWithoutToken = ledger.markSucceeded(null, "x");
        boolean settledWithWrongToken = ledger.markSucceeded(
                AgentActionClaim.acquired(THREAD, OWNER, actionId(), "forged"), "x");

        assertEquals(false, settledWithoutToken);
        assertEquals(false, settledWithWrongToken);
        assertEquals(AgentActionRecord.STATUS_EXECUTING, store.status(THREAD, OWNER, actionId()));

        assertTrue(ledger.markSucceeded(claim, "x"));
        assertEquals(AgentActionRecord.STATUS_SUCCEEDED, store.status(THREAD, OWNER, actionId()));
    }

    @Test
    void completedActionIsReportedAsCompletedRatherThanExecutedAgain() {
        AgentActionClaim claim = ledger.claim(THREAD, OWNER, actionId(), TOOL, "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        ledger.markSucceeded(claim, "ok");

        AgentActionClaim again = ledger.claim(THREAD, OWNER, actionId(), TOOL, "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.ALREADY_SUCCEEDED, again.outcome(),
                "only a proven completion may be reported as completed");
    }

    private ToolCallRequest request() {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
        return ToolCallRequest.builder()
                .toolName(TOOL)
                .arguments("{\"prompt\":\"x\"}")
                .toolCallId(CALL_ID)
                .context(Map.of())
                .executionContext(new ToolCallExecutionContext(config, new OverAllState(Map.of())))
                .build();
    }

    private String actionId() {
        return AgentActionLedger.actionId(THREAD, CALL_ID);
    }
}
