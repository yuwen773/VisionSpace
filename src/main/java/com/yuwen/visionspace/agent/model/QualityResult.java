package com.yuwen.visionspace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片质量评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 匹配度评分 (0.0-1.0)
     */
    private Double matchScore;

    /**
     * 评分理由
     */
    private String reason;

    /**
     * 改进建议
     */
    private List<String> suggestions;

    /**
     * 推荐动作: return (返回结果) | regenerate (重新生成) | research (重新搜索)
     */
    private ActionType action;
}
