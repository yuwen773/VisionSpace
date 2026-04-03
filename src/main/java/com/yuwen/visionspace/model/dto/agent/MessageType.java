package com.yuwen.visionspace.model.dto.agent;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 消息类型枚举
 */
@Getter
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
     * AI 思考内容
     */
    REASONING("reasoning"),

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

    @JsonValue
    public String getValue() {
        return value;
    }

}