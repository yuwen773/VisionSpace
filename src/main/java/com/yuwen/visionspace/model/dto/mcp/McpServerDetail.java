package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * MCP 服务器详细信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MCP 服务器代码
     */
    private String mcpServerCode;

    /**
     * 服务器名称
     */
    private String name;

    /**
     * 服务器描述
     */
    private String description;

    /**
     * 部署配置
     */
    private String deployConfig;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 安装类型
     */
    private String installType;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 工具列表 (needTools=true 时返回)
     */
    private List<McpTool> tools;
}
