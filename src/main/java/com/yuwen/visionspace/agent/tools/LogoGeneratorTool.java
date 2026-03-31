package com.yuwen.visionspace.agent.tools;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
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
 * Logo 设计 生成工具  基于阿里云百炼的 文生图模型
 */
@Slf4j
@Component
public class LogoGeneratorTool {
    @Resource
    private DashScopeImageModel dashScopeImageModel;

    @Resource
    private ImageSynthesis imageSynthesis;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:qwen-image-plus}")
    private String imageModel;

    /**
     * 生成 Logo 设计图片 Spring AI Alibaba 暂时buzhic
     * @param description
     * @return
     */
    @Tool(description = "根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@ToolParam(description = "Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            ImageSynthesisResult result = imageSynthesis.call(param);
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(imageUrl)) {
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(imageUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }
//    @Tool(description = "根据描述生成 Logo 设计图片，用于网站品牌标识")
//    public List<ImageResource> generateLogos(@ToolParam(description = "Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
//        List<ImageResource> logoList = new ArrayList<>();
//        try {
//            // 构建 Logo 设计提示词
//            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
//
//            var imageOptions = DashScopeImageOptions.builder()
//                    .model(imageModel)
//                    .n(1)
//                    .width(1024)
//                    .height(1024)
//                    .style("photography")
//                    .build();
//
//            ImagePrompt prompt = new ImagePrompt(logoPrompt, imageOptions);
//            ImageResponse response = dashScopeImageModel.call(prompt);
//            for (ImageGeneration result : response.getResults()) {
//                System.err.println(result.getMetadata());
//                logoList.add(ImageResource.builder().
//                        category(ImageCategoryEnum.LOGO).
//                        description(description).
//                        url(result.getOutput().getUrl()).
//                        build());
//            }
//
//
//        } catch (Exception e) {
//            log.error("生成 Logo 失败: {}", e.getMessage(), e);
//        }
//        return logoList;
//    }
}
