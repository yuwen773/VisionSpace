package com.yuwen.visionspace.agent.tools;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 图片生成工具，基于阿里云百炼文生图模型
 */
@Slf4j
@Component
public class ImageGeneratorTool {

    @Resource
    private ImageSynthesis imageSynthesis;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:qwen-image-plus}")
    private String imageModel;

    @Tool(name = "generateImages", description = "使用 AI 文生图模型根据描述生成图片。适用于搜索结果不理想时、或用户需要原创图片的场景（如品牌 Logo、插画、背景图、概念图等）。生成前会暂停等待用户确认。返回生成图片的 URL。")
    public List<ImageResource> generateImages(
            @ToolParam(description = "图片生成描述，需尽量详细，包含主题、风格、色调、用途等。例如：'一个简约现代的科技公司 Logo，蓝色主色调，几何图形风格' 或 '一个温馨的咖啡厅插画，暖色调，扁平风格'") String description,
            @ToolParam(description = "图片类别，可选值：LOGO（品牌标识）、ILLUSTRATION（插画装饰）、CONTENT（内容配图）、ARCHITECTURE（架构图）等类型，根据用户需求选择") String category) {
        List<ImageResource> imageList = new ArrayList<>();
        try {
            ImageCategoryEnum categoryEnum = ImageCategoryEnum.getEnumByValue(category);
            if (categoryEnum == null) {
                categoryEnum = ImageCategoryEnum.CONTENT;
            }

            String prompt = buildPrompt(description, categoryEnum);

            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(prompt)
                    .size(resolveSize(categoryEnum))
                    .n(1)
                    .build();
            ImageSynthesisResult result = imageSynthesis.call(param);
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(imageUrl)) {
                        imageList.add(ImageResource.builder()
                                .category(categoryEnum)
                                .description(description)
                                .url(imageUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI 生成图片失败: {}", e.getMessage(), e);
        }
        return imageList;
    }

    /**
     * 根据类别构建不同的 prompt 模板
     */
    private String buildPrompt(String description, ImageCategoryEnum category) {
        return switch (category) {
            case LOGO -> String.format("生成一个 Logo 图标，图中禁止包含任何文字！设计要求：%s", description);
            case ILLUSTRATION -> String.format("生成一张装饰性插画，风格简洁现代，内容：%s", description);
            case ARCHITECTURE -> String.format("生成一张技术架构示意图，风格清晰专业，内容：%s", description);
            default -> description;
        };
    }

    /**
     * 根据类别选择合适的图片尺寸
     */
    private String resolveSize(ImageCategoryEnum category) {
        return switch (category) {
            case LOGO -> "512*512";
            case ILLUSTRATION -> "1024*1024";
            default -> "1024*1024";
        };
    }
}
