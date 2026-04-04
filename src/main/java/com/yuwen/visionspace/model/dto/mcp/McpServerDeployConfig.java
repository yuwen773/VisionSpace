package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器部署配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerDeployConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 启动命令
     */
    private List<String> startCmd;

    /**
     * 安装配置
     */
    private String installConfig;

    /**
     * 环境变量名称列表
     */
    private List<String> envs;

    /**
     * 环境变量键值对
     */
    private Map<String, String> envValue;

    /**
     * 授权信息
     */
    private String authorization;

    /**
     * 远程地址
     */
    private String remoteAddress;

    /**
     * 远程端点
     */
    private String remoteEndpoint;

    /**
     * 远程请求头
     */
    private Map<String, String> remoteHeader;
}
