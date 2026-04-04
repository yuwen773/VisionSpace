package com.yuwen.visionspace.agent.interceptor;

import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.agent.tool.McpToolCallbackProvider;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextMcpModelInterceptor {

    private final UserMcpPreferenceService userMcpPreferenceService;
    private final McpToolCallbackProvider mcpToolCallbackProvider;

    /**
     * 优先级规则（从高到低）：
     * 1. AgentChatRequest.enabledMcpServers（会话覆盖）
     * 2. userMcpPreference.defaultEnabledServers（用户默认偏好）
     * 3. disableMcpDefault=true 时，全部忽略，仅使用 enabledMcpServers
     */
    public ToolCallback[] resolveMcpCallbacks(AgentChatRequest chatRequest, Long userId) {
        List<String> enabledMcpServers = chatRequest.getEnabledMcpServers();

        // 优先级1: 会话覆盖
        if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
            return mcpToolCallbackProvider.getCallbacks(enabledMcpServers);
        }

        // 优先级2: 用户默认偏好
        if (!Boolean.TRUE.equals(chatRequest.getDisableMcpDefault())) {
            List<String> defaultServers = userMcpPreferenceService.getDefaultEnabledServers(userId);
            if (defaultServers != null && !defaultServers.isEmpty()) {
                return mcpToolCallbackProvider.getCallbacks(defaultServers);
            }
        }

        return new ToolCallback[0];
    }
}
