package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.exception.ApiException;
import com.yuwen.visionspace.agent.config.AgentToolExceptionPolicy;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.agent.tools.ImageGeneratorTool;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实框架调用链：模型发起工具调用 → 框架执行工具 → 拦截器落台账。
 *
 * <p>评审指出的漏洞只在链路上存在：{@code AgentToolNode.executeSyncTool} 用
 * {@code DefaultToolExecutionExceptionProcessor(alwaysThrow=false)} 把工具异常
 * 折成一条**非 error** 的响应，拦截器因此把超时记成成功。只测工具本身或只测
 * 拦截器都发现不了——两边各自「看起来都对」。
 *
 * <p>这里用真实 {@link ReactAgent} + 真实 {@link AgentToolExceptionPolicy} +
 * 真实拦截器与台账，断言最终持久状态与外部调用次数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentToolChainFailureTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-chain";
    private static final String CALL_ID = "call-chain";
    private static final String ACTION_ID = AgentActionLedger.actionId(THREAD, CALL_ID);

    @Mock
    private ImageSynthesis imageSynthesis;

    private InMemoryActionRecordMapper store;
    private AgentActionInterceptor interceptor;
    private ImageGeneratorTool tool;

    @BeforeEach
    void setUp() {
        store = new InMemoryActionRecordMapper();
        interceptor = new AgentActionInterceptor(new AgentActionLedger(store.proxy()));
        tool = new ImageGeneratorTool();
        ReflectionTestUtils.setField(tool, "imageSynthesis", imageSynthesis);
        ReflectionTestUtils.setField(tool, "dashScopeApiKey", "test-key");
        ReflectionTestUtils.setField(tool, "imageModel", "test-model");
    }

    @Test
    void frameworkConvertedToolFailureStillReachesTheLedgerAsUnknown() throws Exception {
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenThrow(new ApiException(new RuntimeException("Read timed out")));

        ReactAgent agent = agentThatCallsGenerateImages();

        // 框架把工具异常包装成 ToolExecutionException 重抛；拦截器据此判成结果未知
        assertThrows(PendingReviewException.class,
                () -> agent.call("生成一只猫", config()));

        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status(),
                "a timed-out paid generation must wait for review, not be recorded as done");
    }

    @Test
    void unReviewedActionIsNotReExecutedWhenTheModelAsksAgain() throws Exception {
        when(imageSynthesis.call(any(ImageSynthesisParam.class)))
                .thenThrow(new ApiException(new RuntimeException("boom")));

        assertThrows(PendingReviewException.class,
                () -> agentThatCallsGenerateImages().call("生成一只猫", config()));

        // 同一 actionId 再来一次（新的一轮执行，模型重新发起同一个工具调用）：
        // 已经处于待核对，不得再次触达外部服务
        AtomicInteger calls = new AtomicInteger();
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return null;
        });
        assertThrows(PendingReviewException.class,
                () -> agentThatCallsGenerateImages().call("生成一只猫", config()));

        assertEquals(0, calls.get(), "an un-reviewed action must not reach the provider again");
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status());
    }

    private String status() {
        return store.status(THREAD, OWNER, ACTION_ID);
    }

    private RunnableConfig config() {
        return RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
    }

    /**
     * 模型第一次返回一个 generateImages 工具调用，之后返回纯文本。
     *
     * <p>用真实 {@code ReactAgent} 是为了让工具真的经过 {@code AgentToolNode}：
     * 异常转换、拦截器链、结算时机都在框架里，不是我们手写的调用顺序。
     */
    private ReactAgent agentThatCallsGenerateImages() {
        ChatModel model = new ScriptedChatModel();
        return ReactAgent.builder()
                .name("test_agent")
                .model(model)
                .instruction("test")
                .methodTools(tool)
                .interceptors(List.of(interceptor))
                .toolExecutionExceptionProcessor(AgentToolExceptionPolicy.processor())
                .build();
    }

    /** 只发一次工具调用，之后一律返回文本，避免测试陷入循环 */
    private static final class ScriptedChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            if (calls.getAndIncrement() == 0) {
                AssistantMessage message = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                CALL_ID, "function", "generateImages",
                                "{\"description\":\"一只猫\",\"category\":\"CONTENT\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(message)));
            }
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("已经处理完了"))));
        }
    }
}
