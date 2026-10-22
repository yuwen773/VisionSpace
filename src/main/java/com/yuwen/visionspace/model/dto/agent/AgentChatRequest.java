package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

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

    // 身份只取服务端登录态：请求体里的 userId 不再参与归属或偏好判定，
    // 字段已移除，旧客户端多传会被忽略。

    /**
     * 用户上传的图片 URL 列表（后端拼接为 <image-analysis> 标签）
     */
    private List<String> imageUrls;

    /**
     * 会话级别的 MCP 服务器覆盖列表（优先级最高）
     */
    private List<String> enabledMcpServers;

    /**
     * 禁用用户默认 MCP 偏好，仅使用 enabledMcpServers
     */
    private Boolean disableMcpDefault;
}
