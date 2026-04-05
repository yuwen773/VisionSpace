package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 调用 MCP 服务器工具请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerCallToolRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MCP 服务器代码
     */
    private String mcpServerCode;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具参数
     */
    private Map<String, Object> toolParams;
}
