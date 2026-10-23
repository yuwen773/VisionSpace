package com.yuwen.visionspace.agent.tools;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.yuwen.visionspace.agent.model.ImageResource;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实生成工具的错误契约。
 *
 * <p>评审发现：{@code generateImages} 把 {@code ImageSynthesis.call} 的异常吞掉后返回空列表，
 * 上层台账据此把「超时/失败」记成 {@code SUCCEEDED}，同一个提示词再也不会被重试。
 * 这里断言的是工具本身必须把失败交出去，而不是断言某个 Mock 被调用。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageGeneratorToolTest {

    @Mock
    private ImageSynthesis imageSynthesis;

    private ImageGeneratorTool tool;

    @BeforeEach
    void setUp() {
        tool = new ImageGeneratorTool();
        ReflectionTestUtils.setField(tool, "imageSynthesis", imageSynthesis);
        ReflectionTestUtils.setField(tool, "dashScopeApiKey", "test-key");
        ReflectionTestUtils.setField(tool, "imageModel", "test-model");
    }

    @Test
    void successfulGenerationReturnsTheUrls() throws Exception {
        ImageSynthesisResult generated = result(Map.of("url", "https://cdn.example/a.png"));
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(generated);

        List<ImageResource> images = tool.generateImages("一只猫", "CONTENT");

        assertEquals(1, images.size());
        assertEquals("https://cdn.example/a.png", images.get(0).getUrl());
    }

    @Test
    void providerExceptionIsNotSwallowedAsAnEmptySuccess() throws Exception {
        // 服务端已受理但客户端超时：这正是「结果未知」的典型窗口
        when(imageSynthesis.call(any(ImageSynthesisParam.class)))
                .thenThrow(new ApiException(new RuntimeException("Read timed out")));

        ImageGenerationException error = assertThrows(ImageGenerationException.class,
                () -> tool.generateImages("一只猫", "CONTENT"));

        assertTrue(error.getMessage().contains("ApiException"),
                "the failure type must reach the caller so it can be classified: " + error.getMessage());
    }

    @Test
    void emptyResultIsReportedAsFailureInsteadOfSuccess() throws Exception {
        ImageSynthesisResult empty = result(Map.of());
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(empty);

        assertThrows(ImageGenerationException.class,
                () -> tool.generateImages("一只猫", "CONTENT"),
                "a call that produced no image must not be recorded as a success");
    }

    @Test
    void nullResultIsReportedAsFailure() throws Exception {
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(null);

        assertThrows(ImageGenerationException.class,
                () -> tool.generateImages("一只猫", "CONTENT"));
    }

    @Test
    void generatedCallIsMadeWithTheConfiguredModelAndPrompt() throws Exception {
        ImageSynthesisResult generated = result(Map.of("url", "https://cdn.example/a.png"));
        when(imageSynthesis.call(any(ImageSynthesisParam.class))).thenReturn(generated);

        tool.generateImages("品牌 Logo", "LOGO");

        var captor = org.mockito.ArgumentCaptor.forClass(ImageSynthesisParam.class);
        verify(imageSynthesis).call(captor.capture());
        assertEquals("test-model", captor.getValue().getModel());
        assertTrue(captor.getValue().getPrompt().contains("品牌 Logo"));
    }

    private ImageSynthesisResult result(Map<String, String> image) {
        ImageSynthesisOutput output = new ImageSynthesisOutput();
        output.setResults(image.isEmpty() ? List.of() : List.of(image));
        // SDK 的结果对象构造器是私有的，这里只替身它的输出
        ImageSynthesisResult result = org.mockito.Mockito.mock(ImageSynthesisResult.class);
        when(result.getOutput()).thenReturn(output);
        return result;
    }
}
