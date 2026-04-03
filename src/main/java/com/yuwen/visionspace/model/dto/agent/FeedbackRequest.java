package com.yuwen.visionspace.model.dto.agent;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.AgentPhase;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent 用户反馈请求
 */
@Data
public class FeedbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private String threadId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 是否满意
     */
    private Boolean satisfied;

    /**
     * 不满意原因 (可选)
     */
    private String reason;

    /**
     * 建议动作: regenerate, research, return (可选)
     */
    private ActionType action;

    /**
     * 当前迭代阶段 (可选)
     */
    private AgentPhase currentPhase;

    /**
     * 用户自定义消息
     */
    private String message;
}
