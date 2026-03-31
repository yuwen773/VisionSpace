package com.yuwen.visionspace.model.dto.agent;

/**
 * 消息类型枚举
 */
public enum MessageType {
    /**
     * 用户消息
     */
    USER("user"),

    /**
     * AI 助手消息
     */
    ASSISTANT("assistant"),

    /**
     * 工具调用请求
     */
    TOOL_REQUEST("tool-request"),

    /**
     * 需要用户确认
     */
    TOOL_CONFIRM("tool-confirm"),

    /**
     * 工具响应
     */
    TOOL_RESPONSE("tool");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}