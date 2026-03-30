package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

/**
 * Agent 用户反馈请求
 */
@Data
public class FeedbackRequest {

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
    private String action;

    /**
     * 用户自定义消息
     */
    private String message;
}
