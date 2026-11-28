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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    private static final int DISCOVERY_BATCH_SIZE = 4;

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
        // 分批发起授权范围内的发现请求，再按原顺序汇总，避免冷缓存把等待时间逐个相加。
        for (int start = 0; start < servers.size(); start += DISCOVERY_BATCH_SIZE) {
            int end = Math.min(start + DISCOVERY_BATCH_SIZE, servers.size());
            var discoveries = servers.subList(start, end).stream()
                    .map(this::discoverTools)
                    .toList();
            for (int index = start; index < end; index++) {
                McpServer server = servers.get(index);
                try {
                    List<McpTool> tools = discoveries.get(index - start).get();
                    if (tools != null) {
                        for (McpTool tool : tools) {
                            callbacks.add(new McpToolCallback(mcpManager, serverAccess,
                                    server.getMcpServerCode(), userId, tool));
                        }
                    }
                } catch (ExecutionException | CancellationException e) {
                    log.warn("MCP 工具解析失败: serverCode={}", server.getMcpServerCode());
                } catch (InterruptedException e) {
                    discoveries.forEach(discovery -> discovery.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new CancellationException("MCP tool discovery interrupted");
                }
            }
        }

        return callbacks.toArray(new ToolCallback[0]);
    }

    private CompletableFuture<List<McpTool>> discoverTools(McpServer server) {
        if (!capabilityGuard.isInstallTypeAllowed(server.getInstallType())) {
            log.info("跳过被禁用的 MCP 能力: serverCode={}, installType={}",
                    server.getMcpServerCode(), server.getInstallType());
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            return mcpManager.getToolsAsync(server);
        } catch (RuntimeException e) {
            log.warn("MCP 工具解析失败: serverCode={}", server.getMcpServerCode());
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
