package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具执行边界：外部调用次数与最终状态是判定依据，不靠代码自查。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentActionInterceptorTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-1";
    private static final String CALL_ID = "call-1";

    @Mock
    private AgentActionLedger ledger;

    private AgentActionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AgentActionInterceptor(ledger);
    }

    @Test
    void successfulCallIsRecordedAndExternalActionRunsOnce() {
        when(ledger.begin(anyString(), eq(OWNER), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "[\"url\"]");
        };

        interceptor.interceptToolCall(request("generateImages", false), handler);

        assertEquals(1, externalCalls.get());
        verify(ledger).markSucceeded(eq(THREAD), eq(OWNER), anyString(), anyString());
    }

    @Test
    void alreadyCompletedActionProducesNoExternalCall() {
        // 已确认完成的动作：begin 返回 false，直接跳过
        when(ledger.begin(anyString(), eq(OWNER), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "generateImages", "x");
        };

        ToolCallResponse response = interceptor.interceptToolCall(request("generateImages", false), handler);

        assertEquals(0, externalCalls.get(), "confirmed action must not be re-executed");
        assertTrue(response.getResult().contains("已确认完成"));
    }

    @Test
    void toolLevelErrorIsExplicitFailureAndRetryable() {
        when(ledger.begin(anyString(), eq(OWNER), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        ToolCallHandler handler = request -> ToolCallResponse.error(CALL_ID, "generateImages", "quota exceeded");

        interceptor.interceptToolCall(request("generateImages", false), handler);

        verify(ledger).markFailed(eq(THREAD), eq(OWNER), anyString(), anyString());
        verify(ledger, never()).markUnknown(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyBoolean(), anyString());
    }

    @Test
    void exceptionOnNonIdempotentToolPausesForReviewInsteadOfRetrying() {
        when(ledger.begin(anyString(), eq(OWNER), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        ToolCallHandler handler = request -> {
            throw new IllegalStateException("connection reset after upload");
        };

        PendingReviewException error = assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request("generateImages", false), handler));

        assertEquals("generateImages", error.getToolName());
        // 结果未知 + 不可证明幂等 => UNKNOWN_NEEDS_REVIEW，不放行重试
        verify(ledger).markUnknown(eq(THREAD), eq(OWNER), anyString(), eq(false), anyString());
    }

    @Test
    void exceptionOnIdempotentToolPropagatesAsRetryableFailure() {
        when(ledger.begin(anyString(), eq(OWNER), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        ToolCallHandler handler = request -> {
            throw new IllegalStateException("timeout");
        };

        assertThrows(IllegalStateException.class,
                () -> interceptor.interceptToolCall(request("searchContentImages", true), handler));

        // 可证明幂等：退回 FAILED 走安全重试，而不是待核对
        verify(ledger).markUnknown(eq(THREAD), eq(OWNER), anyString(), eq(true), anyString());
    }

    @Test
    void missingOwnerSkipsLedgerButStillExecutes() {
        AtomicInteger externalCalls = new AtomicInteger();
        ToolCallHandler handler = request -> {
            externalCalls.incrementAndGet();
            return ToolCallResponse.of(CALL_ID, "searchContentImages", "ok");
        };
        RunnableConfig anonymous = RunnableConfig.builder().threadId(THREAD).build();

        interceptor.interceptToolCall(request(anonymous, "searchContentImages", true), handler);

        assertEquals(1, externalCalls.get());
        verify(ledger, never()).begin(anyString(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void idempotencyClassificationIsExplicit() {
        assertTrue(AgentActionInterceptor.isIdempotent("searchContentImages"));
        assertTrue(AgentActionInterceptor.isIdempotent("evaluateImageQuality"));
        assertFalse(AgentActionInterceptor.isIdempotent("generateImages"),
                "generation creates external resources and cannot be proven idempotent");
        assertFalse(AgentActionInterceptor.isIdempotent(null));
    }

    private ToolCallRequest request(String toolName, boolean idempotent) {
        return request(config(), toolName, idempotent);
    }

    private ToolCallRequest request(RunnableConfig config, String toolName, boolean idempotent) {
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
