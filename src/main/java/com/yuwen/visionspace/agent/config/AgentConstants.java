package com.yuwen.visionspace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 相关常量配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.agent")
public class AgentConstants {

    /**
     * 触发摘要的消息数量阈值
     */
    private int summaryThreshold = 20;

    /**
     * 摘要后保留的消息数量
     */
    private int messagesToKeep = 10;

    /**
     * 探索阶段最大迭代次数
     */
    private int maxExploreCount = 3;

    /**
     * 确认阶段最大迭代次数
     */
    private int maxConfirmCount = 2;

    /**
     * 总体最大迭代次数
     */
    private int maxIterationCount = 15;

    /**
     * 历史消息截断长度
     */
    private int historyTruncateLength = 100;

    /**
     * 历史消息截断预留长度
     */
    private int historyTruncateReserve = 97;

    /**
     * 最大保留消息数
     */
    private int maxMessagesToTrim = 10;

    /**
     * 反馈历史最大保存条数
     */
    private int maxFeedbackHistorySize = 100;

    /**
     * 摘要最大长度
     */
    private int summaryMaxLength = 500;

    /**
     * 摘要截断长度
     */
    private int summaryTruncateLength = 497;

    /**
     * 最大历史分页大小
     */
    private int maxHistoryPageSize = 100;
}
