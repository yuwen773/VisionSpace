package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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
     * 数量充足、但命中意图的图片不足半数时的中性分数。
     */
    private static final double NEUTRAL_SCORE = 0.3;

    /**
     * 评估图片列表与用户意图的匹配度
     *
     * @param images 图片列表
     * @param userIntent 用户原始意图描述
     * @return 评估结果
     */
    @Tool(name = "evaluateImageQuality", description = "评估一组图片与用户原始意图的匹配程度。在图片搜索完成后调用，用于判断搜索结果是否满足需求、是否需要重新搜索或改用 AIGC 生成。返回匹配度分数（0-1）、原因分析和下一步建议。")
    public QualityResult evaluateImageQuality(
            @ToolParam(description = "待评估的图片资源列表，来自搜索或生成的结果") List<ImageResource> images,
            @ToolParam(description = "用户的原始需求描述，例如：'需要一个科技感的网站首页 banner'、'为公司 logo 找参考'") String userIntent) {

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
                    .matchScore(NEUTRAL_SCORE)
                    .reason("搜索结果数量较少")
                    .suggestions(List.of("搜索结果不足，建议使用 AIGC 补充生成"))
                    .action(ActionType.REGENERATE)
                    .build();
        }

        // 数量 >= 3 时，数量本身已足以展示；语义匹配只作为加分项，不作为否决条件。
        // 注意：搜索结果（Pexels/unDraw/以图搜图）的 category 固定为 CONTENT，且描述多为英文，
        // 无法据此可靠判断中文意图是否匹配，因此这里只给出信号，由模型结合 reason 决定下一步。
        String normalizedIntent = userIntent == null ? "" : userIntent.toLowerCase(Locale.ROOT);
        String[] intentWords = normalizedIntent.split("[\\s,，.。;；]+");
        int matchCount = (int) images.stream()
                .filter(image -> isIntentMatchImage(normalizedIntent, intentWords, image))
                .count();
        boolean hasSignal = matchCount > 0;

        return QualityResult.builder()
                .matchScore(scoreFromMatchRatio(matchCount, images.size()))
                .reason(hasSignal
                        ? "搜索结果数量充足，且存在与意图相符的图片描述"
                        : "搜索结果数量充足，但未能从描述中确认与意图的匹配度")
                .suggestions(hasSignal
                        ? List.of("可以先展示搜索结果，询问用户满意度")
                        : List.of("建议先展示搜索结果，由用户判断是否符合意图；如明显不符再考虑 AIGC 生成"))
                .action(ActionType.RETURN)
                .build();
    }

    /**
     * 由匹配比例换算匹配度分数。
     * 半数以上命中时给 0.6~0.9，否则统一给中性分。
     */
    private double scoreFromMatchRatio(int matchCount, int total) {
        double matchRatio = (double) matchCount / total;
        return matchRatio >= 0.5
                ? Math.min(0.9, 0.6 + matchRatio * 0.3)
                : NEUTRAL_SCORE;
    }

    /**
     * 判断用户意图是否与单张图片匹配
     *
     * @param normalizedIntent 规范化的用户意图（小写）
     * @param intentWords      预切分好的意图关键词
     * @param image 图片资源
     * @return 是否匹配
     */
    private boolean isIntentMatchImage(String normalizedIntent, String[] intentWords, ImageResource image) {
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
            // 简单的关键词重叠检查（intentWords 在调用方预先切分，避免每张图重复 split）
            for (String word : intentWords) {
                if (word.length() >= 2 && normalizedDesc.contains(word)) {
                    return true;
                }
            }
        }

        return false;
    }
}
