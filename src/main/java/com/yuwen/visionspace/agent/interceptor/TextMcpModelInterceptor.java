package com.yuwen.visionspace.agent.interceptor;

import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.agent.mcp.McpToolCallbackProvider;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import com.yuwen.visionspace.model.entity.McpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 决定本次会话可以使用哪些 MCP 工具。
 *
 * <p>三条来源（会话显式 code、用户默认启用、禁用默认后的显式列表）都必须按
 * 当前登录用户回读：请求里的 code 只是选择意图，不是授权依据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextMcpModelInterceptor {

    private final McpToolCallbackProvider mcpToolCallbackProvider;
    private final McpServerAccess serverAccess;

    /**
     * 优先级规则（从高到低）：
     * 1. AgentChatRequest.enabledMcpServers（会话覆盖）
     * 2. 该用户 status=1 的服务作为默认
     * 3. disableMcpDefault=true 时，全部忽略，仅使用 enabledMcpServers
     *
     * @param userId 服务端登录用户，所有 code 都按它回读
     */
    public ToolCallback[] resolveMcpCallbacks(AgentChatRequest chatRequest, Long userId) {
        if (chatRequest == null || userId == null) {
            return new ToolCallback[0];
        }
        List<String> enabledMcpServers = chatRequest.getEnabledMcpServers();

        // 优先级1: 会话覆盖；他人 code 与已停用服务在这里被过滤掉
        if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
            return mcpToolCallbackProvider.getCallbacks(enabledMcpServers, userId);
        }

        // 优先级2: 该用户默认启用的服务（status=1）
        if (!Boolean.TRUE.equals(chatRequest.getDisableMcpDefault())) {
            List<McpServer> enabledServers = serverAccess.findEnabled(null, userId);
            if (!enabledServers.isEmpty()) {
                List<String> serverCodes = enabledServers.stream()
                        .map(McpServer::getMcpServerCode)
                        .toList();
                return mcpToolCallbackProvider.getCallbacks(serverCodes, userId);
            }
        }

        return new ToolCallback[0];
    }
}
