package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MCP 工具定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpTool implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入模式
     */
    private InputSchema inputSchema;
}
