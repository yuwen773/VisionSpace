package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
                    .action("regenerate")
                    .build();
        }

        int count = images.size();

        // 简单规则判断：数量 < 3 时建议生成
        if (count < 3) {
            return QualityResult.builder()
                    .matchScore(0.3)
                    .reason("搜索结果数量较少")
                    .suggestions(List.of("搜索结果不足，建议使用 AIGC 补充生成"))
                    .action("regenerate")
                    .build();
        }

        // 数量 >= 3 时，返回中等评分，让 Agent 决定
        return QualityResult.builder()
                .matchScore(0.6)
                .reason("搜索结果数量充足，可以展示给用户")
                .suggestions(List.of("可以先展示搜索结果，询问用户满意度"))
                .action("return")
                .build();
    }
}
