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

    /**
     * 意图匹配判定。
     * <p>
     * 与 {@link #matchScore} 互补：分数是估计值，判定是结论。调用方应优先读本字段，
     * 因为「无法判断」和「判断为不匹配」可能落在同一个分数上，但处理方式相反。
     */
    private MatchVerdict verdict;

    /**
     * 当前迭代阶段
     */
    private AgentPhase phase;

    /**
     * 当前探索次数
     */
    private Integer exploreCount;
}
