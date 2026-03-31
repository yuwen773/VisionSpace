package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 图片质量评估工具
 * 评估搜索结果与用户意图的匹配度
 */
@Slf4j
@Component
public class QualityEvaluatorTool {

    /**
     * 评估图片列表与用户意图的匹配度
     *
     * @param images 图片列表
     * @param userIntent 用户原始意图描述
     * @return 评估结果
     */
    @Tool(description = "评估图片列表与用户意图的匹配度，返回匹配分数和改进建议")
    public QualityResult evaluateImageQuality(
            @ToolParam(description = "要评估的图片列表") List<ImageResource> images,
            @ToolParam(description = "用户的原始意图描述") String userIntent) {

        // 数量检查
        if (images == null || images.isEmpty()) {
            return QualityResult.builder()
                    .matchScore(0.0)
                    .reason("没有搜索结果")
                    .suggestions(List.of("建议使用 AIGC 生成图片"))
                    .action(ActionType.REGENERATE)
                    .build();
        }

        int count = images.size();

        // 简单规则判断：数量 < 3 时建议生成
        if (count < 3) {
            return QualityResult.builder()
                    .matchScore(0.3)
                    .reason("搜索结果数量较少")
                    .suggestions(List.of("搜索结果不足，建议使用 AIGC 补充生成"))
                    .action(ActionType.REGENERATE)
                    .build();
        }

        // 语义匹配度评估
        double semanticScore = calculateSemanticMatch(userIntent, images);
        if (semanticScore < 0.3) {
            return QualityResult.builder()
                    .matchScore(semanticScore)
                    .reason("图片与用户意图不匹配")
                    .suggestions(List.of("图片类型不符合需求，建议使用 AIGC 生成符合意图的图片"))
                    .action(ActionType.REGENERATE)
                    .build();
        }

        // 数量 >= 3 且语义匹配度尚可，返回中等偏上评分
        return QualityResult.builder()
                .matchScore(semanticScore)
                .reason("搜索结果数量充足，图片与意图基本匹配")
                .suggestions(List.of("可以先展示搜索结果，询问用户满意度"))
                .action(ActionType.RETURN)
                .build();
    }

    /**
     * 计算图片列表与用户意图的语义匹配度
     *
     * @param userIntent 用户意图描述
     * @param images 图片列表
     * @return 匹配度分数 (0.0 - 1.0)
     */
    private double calculateSemanticMatch(String userIntent, List<ImageResource> images) {
        if (userIntent == null || userIntent.isEmpty()) {
            return 0.6; // 无意图信息时返回中等分数
        }

        String normalizedIntent = userIntent.toLowerCase(Locale.ROOT);

        int matchCount = 0;
        for (ImageResource image : images) {
            if (isIntentMatchImage(normalizedIntent, image)) {
                matchCount++;
            }
        }

        // 计算匹配比例
        double matchRatio = (double) matchCount / images.size();

        // 结合数量因素：数量越多，匹配要求越高
        // 如果有3张图，至少需要50%匹配才算良好
        // 如果有5张图，至少需要60%匹配才算良好
        double quantityFactor = Math.min(1.0, (double) images.size() / 5.0);
        double threshold = 0.5 - (1.0 - quantityFactor) * 0.2;

        if (matchRatio >= threshold) {
            return Math.min(0.9, 0.6 + matchRatio * 0.3);
        } else {
            return Math.max(0.3, matchRatio * 0.6);
        }
    }

    /**
     * 判断用户意图是否与单张图片匹配
     *
     * @param normalizedIntent 规范化的用户意图（小写）
     * @param image 图片资源
     * @return 是否匹配
     */
    private boolean isIntentMatchImage(String normalizedIntent, ImageResource image) {
        // 检查类别关键词
        ImageCategoryEnum category = image.getCategory();
        if (category != null) {
            String categoryText = category.getText().toLowerCase(Locale.ROOT);
            String categoryValue = category.getValue().toLowerCase(Locale.ROOT);

            if (normalizedIntent.contains(categoryText) || normalizedIntent.contains(categoryValue)) {
                return true;
            }

            // 额外的中文关键词映射
            if (category == ImageCategoryEnum.LOGO && normalizedIntent.contains("标志")) {
                return true;
            }
            if (category == ImageCategoryEnum.ILLUSTRATION && (normalizedIntent.contains("插画") || normalizedIntent.contains("插图"))) {
                return true;
            }
            if (category == ImageCategoryEnum.ARCHITECTURE && (normalizedIntent.contains("架构") || normalizedIntent.contains("结构图"))) {
                return true;
            }
        }

        // 检查描述关键词
        String description = image.getDescription();
        if (description != null && !description.isEmpty()) {
            String normalizedDesc = description.toLowerCase(Locale.ROOT);
            // 简单的关键词重叠检查
            String[] intentWords = normalizedIntent.split("[\\s,，.。;；]+");
            for (String word : intentWords) {
                if (word.length() >= 2 && normalizedDesc.contains(word)) {
                    return true;
                }
            }
        }

        return false;
    }
}
