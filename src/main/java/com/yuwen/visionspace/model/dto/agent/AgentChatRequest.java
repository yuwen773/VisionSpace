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
     * 用户上传的图片资源标识列表。
     *
     * <p>绑定到会话的是资源标识而不是地址：服务端按记录解析可信地址，
     * 客户端声明的地址不参与授权判定。重复提交同一批标识是幂等的。
     *
     * <p>按十进制字符串传输：资源标识是雪花值，超出 JavaScript 的安全整数范围，
     * 客户端转成 number 会丢精度，服务端收到后按字符串解析。
     */
    private List<String> attachmentIds;

    /**
     * 用户上传的图片 URL 列表（后端拼接为 &lt;image-analysis&gt; 标签）。
     *
     * <p>旧客户端兼容字段：地址必须与本人已有资源记录精确匹配才会被接受，
     * 未登记或属于他人的地址一律拒绝。
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
