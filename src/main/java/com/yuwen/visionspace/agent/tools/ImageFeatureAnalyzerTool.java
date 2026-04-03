package com.yuwen.visionspace.agent.tools;

import cn.hutool.json.JSONUtil;
import com.yuwen.visionspace.agent.model.ImageAnalysisResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;

/**
 * 图片特征分析工具
 * <p>
 * 使用多模态大模型对图片进行结构化分析，返回内容描述、色调、风格、主体、氛围及衍生搜索关键词。
 * <p>
 * 使用场景：
 * 1. 独立使用 — 用户上传图片，想了解图片内容或风格
 * 2. 探索入口 — 根据分析结果中的 searchKeywords 继续搜索相似图片
 * 3. 降级方案 — 当以图搜图 API 失败时，通过分析提取关键词后走关键词搜索
 */
@Slf4j
@Component
public class ImageFeatureAnalyzerTool {

    @Value("${vision-space.vision-recognition.base-url}")
    private String baseUrl;

    @Value("${vision-space.vision-recognition.api-key}")
    private String apiKey;

    @Value("${vision-space.vision-recognition.model}")
    private String model;


    private ChatClient visionChatClient;

    private static final String ANALYSIS_PROMPT = """
            请对这张图片进行全面的视觉分析，严格按以下 JSON 格式返回结果，不要输出任何其他文字：

            {
              "description": "图片内容的一句话概括（中文）",
              "colors": ["主色调1", "主色调2", "主色调3"],
              "style": "视觉风格描述（中文），如：现代简约、赛博朋克、水彩手绘等",
              "subjects": ["主体对象1", "主体对象2"],
              "mood": "场景氛围/情绪（中文），如：温馨舒适、科技专业、活泼明快等",
              "searchKeywords": "keyword1, keyword2, keyword3（英文，逗号分隔，适合图片搜索）"
            }
            """;


    @PostConstruct
    private void init() {
        OpenAiApi visionApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .build();
        OpenAiChatModel visionModel = OpenAiChatModel.builder()
                .openAiApi(visionApi)
                .defaultOptions(options)
                .build();
        this.visionChatClient = ChatClient.builder(visionModel)
                .defaultSystem(ANALYSIS_PROMPT)
                .build();
    }

    @Tool(name = "analyzeImageFeatures", description = "使用 AI 多模态模型对图片进行结构化分析。" +
            "返回图片的内容描述、主色调、视觉风格、主体对象、场景氛围以及衍生搜索关键词。" +
            "适用场景：用户上传参考图片想了解其内容；根据图片特征提取关键词后搜索相似图片；以图搜图失败时的降级方案。")
    public ImageAnalysisResult analyzeImageFeatures(
            @ToolParam(description = "要分析的图片 URL，必须是可公开访问的地址，例如：'https://example.com/photo.jpg'") String imageUrl) {
        try {
            Media media = new Media(resolveMimeType(imageUrl), URI.create(imageUrl));
            UserMessage userMessage = UserMessage.builder()
                    .text("请分析这张图片")
                    .media(media)
                    .build();
            ChatResponse response = visionChatClient.prompt()
                    .messages(userMessage)
                    .call()
                    .chatResponse();
            String rawText = response.getResult().getOutput().getText();
            return parseAnalysisResult(rawText);
        } catch (Exception e) {
            log.error("图片特征分析失败: {}", e.getMessage(), e);
            return ImageAnalysisResult.builder()
                    .description("图片分析失败")
                    .searchKeywords("")
                    .build();
        }
    }

    private ImageAnalysisResult parseAnalysisResult(String rawText) {
        try {
            String json = rawText;
            // 处理模型可能返回的 markdown 代码块包裹
            if (rawText.contains("```")) {
                json = rawText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            return JSONUtil.toBean(json, ImageAnalysisResult.class);
        } catch (Exception e) {
            log.warn("解析图片分析结果失败，原始内容: {}", rawText, e);
            return ImageAnalysisResult.builder()
                    .description(rawText)
                    .searchKeywords("")
                    .build();
        }
    }

    /**
     * 根据 URL 后缀推断 MIME 类型，无法识别时默认 image/jpeg
     */
    private MimeType resolveMimeType(String imageUrl) {
        String path = imageUrl.contains("?") ? imageUrl.substring(0, imageUrl.indexOf("?")) : imageUrl;
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return MimeTypeUtils.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MimeTypeUtils.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MimeType.valueOf("image/webp");
        if (lower.endsWith(".svg")) return MimeType.valueOf("image/svg+xml");
        return MimeTypeUtils.IMAGE_JPEG;
    }
}
