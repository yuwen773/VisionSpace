package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 对话请求
 */
@Data
public class AgentChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 会话ID（可选，不传则自动生成）
     */
    private String threadId;
}
