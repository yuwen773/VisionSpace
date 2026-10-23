package com.yuwen.visionspace.agent.mcp;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 把当前用户已启用的 MCP 服务解析成工具回调。
 *
 * <p>归属判定在这里完成一次（批量回读），之后每个回调在执行前还会再回读一次，
 * 因此「解析时有权」不等于「执行时有权」。解析不到的服务直接跳过，
 * 不会因为某个 code 属于他人而建立任何连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolCallbackProvider {

    private final MCPManager mcpManager;
    private final McpServerAccess serverAccess;
    private final McpCapabilityGuard capabilityGuard;

    public ToolCallback[] getCallbacks(List<String> mcpServerCodes, Long userId) {
        if (mcpServerCodes == null || mcpServerCodes.isEmpty() || userId == null) {
            return new ToolCallback[0];
        }

        List<McpServer> servers = serverAccess.findEnabled(mcpServerCodes, userId);
        if (servers.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        for (McpServer entity : servers) {
            // 被禁用的能力不进入工具列表：UI 与服务端用同一份判定
            if (!capabilityGuard.isInstallTypeAllowed(entity.getInstallType())) {
                log.info("跳过被禁用的 MCP 能力: serverCode={}, installType={}",
                        entity.getMcpServerCode(), entity.getInstallType());
                continue;
            }
            try {
                List<McpTool> tools = mcpManager.getTools(entity);
                if (tools != null) {
                    for (McpTool tool : tools) {
                        callbacks.add(new McpToolCallback(mcpManager, serverAccess,
                                entity.getMcpServerCode(), userId, tool));
                    }
                }
            } catch (Exception e) {
                // 单个服务失败不影响其他服务
                log.warn("MCP 工具解析失败: serverCode={}", entity.getMcpServerCode());
            }
        }

        return callbacks.toArray(new ToolCallback[0]);
    }
}
