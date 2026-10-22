package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ImageFeatureAnalyzerTool 单元测试。
 * <p>
 * 原实现会真实调用多模态大模型（外部付费接口），在无网络/无额度时会挂起或失败，
 * 这里改为 mock ChatClient，保证测试离线、可重复。
 */
class ImageFeatureAnalyzerToolTest {

    private ImageFeatureAnalyzerTool tool;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        tool = new ImageFeatureAnalyzerTool();
        // ChatClient.builder(...).defaultSystem(...).build() 是链式 mock
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(tool, "visionChatClient", chatClient);
    }

    @Test
    void analyzeImageFeatures_shouldParseModelJson() {
        String json = """
                {"description":"一只橘猫","colors":["橙色"],"style":"写实",
                 "subjects":["猫"],"mood":"温馨","searchKeywords":"orange cat, pet"}
                """;
        mockModelReply(json);

        ImageAnalysisResult result = tool.analyzeImageFeatures("https://example.com/cat.png");

        assertNotNull(result);
        assertEquals("一只橘猫", result.getDescription());
        assertEquals("orange cat, pet", result.getSearchKeywords());
        assertEquals("写实", result.getStyle());
    }

    @Test
    void analyzeImageFeatures_shouldStripMarkdownCodeFence() {
        mockModelReply("```json\n{\"description\":\"架构图\",\"searchKeywords\":\"architecture\"}\n```");

        ImageAnalysisResult result = tool.analyzeImageFeatures("https://example.com/a.jpg");

        assertEquals("架构图", result.getDescription());
        assertEquals("architecture", result.getSearchKeywords());
    }

    @Test
    void analyzeImageFeatures_shouldFallBackWhenModelFails() {
        when(chatClient.prompt().messages(any(Message.class)).call().chatResponse())
                .thenThrow(new IllegalStateException("upstream 502"));

        ImageAnalysisResult result = tool.analyzeImageFeatures("https://example.com/a.jpg");

        assertNotNull(result);
        assertEquals("图片分析失败", result.getDescription());
        assertEquals("", result.getSearchKeywords());
    }

    @Test
    void analyzeImageFeatures_shouldKeepRawTextWhenJsonIsInvalid() {
        mockModelReply("这不是 JSON");

        ImageAnalysisResult result = tool.analyzeImageFeatures("https://example.com/a.jpg");

        assertEquals("这不是 JSON", result.getDescription());
        assertEquals("", result.getSearchKeywords());
    }

    @Test
    void resolveMimeType_shouldInferFromExtensionIgnoringQuery() {
        assertEquals(MimeTypeUtils.IMAGE_PNG, resolve("https://example.com/a.png"));
        assertEquals(MimeTypeUtils.IMAGE_GIF, resolve("https://example.com/a.gif?v=1"));
        assertEquals(MimeType.valueOf("image/webp"), resolve("https://example.com/a.webp"));
        assertEquals(MimeTypeUtils.IMAGE_JPEG, resolve("https://example.com/a"));
        assertEquals(MimeTypeUtils.IMAGE_JPEG, resolve("https://example.com/a.bmp"));
    }

    private MimeType resolve(String url) {
        return ReflectionTestUtils.invokeMethod(tool, "resolveMimeType", url);
    }

    private void mockModelReply(String text) {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
        when(chatClient.prompt().messages(any(Message.class)).call().chatResponse())
                .thenReturn(response);
    }
}
