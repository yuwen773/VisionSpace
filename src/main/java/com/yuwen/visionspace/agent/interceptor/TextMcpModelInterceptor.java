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
     * 一次 MCP 解析的结果。
     *
     * @param callbacks  可用工具回调
     * @param serverCodes 实际解析到的服务 code（用于持久化「这次用了哪些服务」，
     *                    续跑时按它重新授权；只存 code，不含凭据与地址）
     */
    public record McpResolution(ToolCallback[] callbacks, List<String> serverCodes) {

        public boolean isEmpty() {
            return callbacks == null || callbacks.length == 0;
        }
    }

    /**
     * 优先级规则（从高到低）：
     * 1. AgentChatRequest.enabledMcpServers（会话覆盖）
     * 2. 该用户 status=1 的服务作为默认
     * 3. disableMcpDefault=true 时，全部忽略，仅使用 enabledMcpServers
     *
     * @param userId 服务端登录用户，所有 code 都按它回读
     */
    public McpResolution resolve(AgentChatRequest chatRequest, Long userId) {
        if (chatRequest == null || userId == null) {
            return new McpResolution(new ToolCallback[0], List.of());
        }
        List<String> enabledMcpServers = chatRequest.getEnabledMcpServers();

        // 优先级1: 会话覆盖；他人 code 与已停用服务在这里被过滤掉
        if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
            return resolveForCodes(enabledMcpServers, userId);
        }

        // 优先级2: 该用户默认启用的服务（status=1）
        if (!Boolean.TRUE.equals(chatRequest.getDisableMcpDefault())) {
            List<McpServer> enabledServers = serverAccess.findEnabled(null, userId);
            if (!enabledServers.isEmpty()) {
                List<String> serverCodes = enabledServers.stream()
                        .map(McpServer::getMcpServerCode)
                        .toList();
                return resolveForCodes(serverCodes, userId);
            }
        }

        return new McpResolution(new ToolCallback[0], List.of());
    }

    /**
     * 按指定 code 解析，用于续跑时重建上一次的 MCP 环境。
     *
     * <p>每个 code 都按<b>当前</b>用户与启用状态回读：已停用、已删除或换了归属的
     * 服务不会出现在结果里。返回的 code 列表只含真正解析成功的服务。
     */
    public McpResolution resolveForCodes(List<String> mcpServerCodes, Long userId) {
        if (mcpServerCodes == null || mcpServerCodes.isEmpty() || userId == null) {
            return new McpResolution(new ToolCallback[0], List.of());
        }
        // 先按当前归属与启用状态过滤，避免把不可用的 code 记进状态
        List<String> executable = serverAccess.findEnabled(mcpServerCodes, userId).stream()
                .map(McpServer::getMcpServerCode)
                .toList();
        if (executable.isEmpty()) {
            return new McpResolution(new ToolCallback[0], List.of());
        }
        return new McpResolution(mcpToolCallbackProvider.getCallbacks(executable, userId), executable);
    }
}
