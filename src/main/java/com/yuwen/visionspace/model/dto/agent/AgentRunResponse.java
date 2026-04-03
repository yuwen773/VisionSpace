package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 流式响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点名称 (agent/heartbeat)
     */
    private String node;

    /**
     * Agent 名称
     */
    private String agent;

    /**
     * 流式文本片段
     */
    private String chunk;

    /**
     * 完整消息 (MessageDTO will be created in Task 3)
     */
    private Object message;

    /**
     * Token 使用统计
     */
    private Object tokenUsage;

    /**
     * 错误标志
     */
    private Boolean error;

    /**
     * 错误类型
     */
    private String errorType;

    /**
     * 错误信息
     */
    private String errorMessage;
}
