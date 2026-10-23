package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.tools.ImageGeneratorTool;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实生成工具 × 真实台账：生成超时必须进入待核对，不能被记成成功。
 *
 * <p>评审的缺陷是两个组件各自"看起来没问题"拼出来的：工具把异常吞成空列表，
 * 拦截器把非 error 返回一律记成功。只测其中一边都发现不了，所以这里走完整调用链。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentGenerationFailureTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-gen";
    private static final String CALL_ID = "call-gen";
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
    void timedOutGenerationIsPausedForReviewInsteadOfRecordedAsSucceeded() throws Exception {
        when(imageSynthesis.call(any(ImageSynthesisParam.class)))
                .thenThrow(new ApiException(new RuntimeException("Read timed out")));

        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request(), req -> {
                    List<ImageResource> images = tool.generateImages("一只猫", "CONTENT");
                    return ToolCallResponse.of(CALL_ID, "generateImages", String.valueOf(images));
                }));

        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status(),
                "a generation that may have happened remotely must wait for review");
    }

    @Test
    void generationThatReturnsNoImageIsNotRecordedAsSucceeded() throws Exception {
        ImageSynthesisResult empty = resultWith(List.of());
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(empty);

        assertThrows(PendingReviewException.class,
                () -> interceptor.interceptToolCall(request(), req -> {
                    List<ImageResource> images = tool.generateImages("一只猫", "CONTENT");
                    return ToolCallResponse.of(CALL_ID, "generateImages", String.valueOf(images));
                }));

        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status());
    }

    @Test
    void successfulGenerationIsRecordedAsSucceeded() throws Exception {
        ImageSynthesisResult ok = resultWith(List.of(Map.of("url", "https://cdn.example/a.png")));
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(ok);

        ToolCallResponse response = interceptor.interceptToolCall(request(), req -> {
            List<ImageResource> images = tool.generateImages("一只猫", "CONTENT");
            return ToolCallResponse.of(CALL_ID, "generateImages", String.valueOf(images));
        });

        assertTrue(!response.isError());
        assertEquals(AgentActionRecord.STATUS_SUCCEEDED, status());
    }

    private String status() {
        return store.status(THREAD, OWNER, ACTION_ID);
    }

    private ImageSynthesisResult resultWith(List<Map<String, String>> results) {
        ImageSynthesisOutput output = new ImageSynthesisOutput();
        output.setResults(results);
        ImageSynthesisResult result = org.mockito.Mockito.mock(ImageSynthesisResult.class);
        when(result.getOutput()).thenReturn(output);
        return result;
    }

    private ToolCallRequest request() {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
        return ToolCallRequest.builder()
                .toolName("generateImages")
                .arguments("{\"description\":\"一只猫\",\"category\":\"CONTENT\"}")
                .toolCallId(CALL_ID)
                .context(Map.of())
                .executionContext(new ToolCallExecutionContext(config, new OverAllState(Map.of())))
                .build();
    }
}
