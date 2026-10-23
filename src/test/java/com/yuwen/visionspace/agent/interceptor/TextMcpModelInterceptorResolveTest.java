package com.yuwen.visionspace.agent.interceptor;

import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.agent.mcp.McpToolCallbackProvider;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP 解析：返回的 code 列表只能包含<b>当前用户已启用</b>的服务。
 *
 * <p>这些 code 会被写进执行状态供续跑使用，因此解析阶段就必须按归属过滤，
 * 不能把请求里声明的 code 原样记下来。
 */
@ExtendWith(MockitoExtension.class)
class TextMcpModelInterceptorResolveTest {

    private static final Long OWNER = 7L;

    @Mock
    private McpToolCallbackProvider provider;

    @Mock
    private McpServerAccess serverAccess;

    @Test
    void explicitServersAreFilteredByCurrentOwnershipBeforeBeingRecorded() {
        when(serverAccess.findEnabled(List.of("mine", "foreign"), OWNER))
                .thenReturn(List.of(server("mine")));
        when(provider.getCallbacks(List.of("mine"), OWNER)).thenReturn(new ToolCallback[0]);
        TextMcpModelInterceptor interceptor = new TextMcpModelInterceptor(provider, serverAccess);

        AgentChatRequest request = new AgentChatRequest();
        request.setEnabledMcpServers(List.of("mine", "foreign"));
        TextMcpModelInterceptor.McpResolution resolution = interceptor.resolve(request, OWNER);

        assertEquals(List.of("mine"), resolution.serverCodes(),
                "another user's code must never be persisted as part of the run");
    }

    @Test
    void nothingIsRecordedWhenNoServerIsUsable() {
        when(serverAccess.findEnabled(List.of("foreign"), OWNER)).thenReturn(List.of());
        TextMcpModelInterceptor interceptor = new TextMcpModelInterceptor(provider, serverAccess);

        AgentChatRequest request = new AgentChatRequest();
        request.setEnabledMcpServers(List.of("foreign"));
        TextMcpModelInterceptor.McpResolution resolution = interceptor.resolve(request, OWNER);

        assertTrue(resolution.isEmpty());
        assertEquals(List.of(), resolution.serverCodes());
        verify(provider, never()).getCallbacks(any(), any());
    }

    @Test
    void disabledDefaultLeavesOnlyExplicitServers() {
        when(serverAccess.findEnabled(List.of("explicit"), OWNER))
                .thenReturn(List.of(server("explicit")));
        when(provider.getCallbacks(List.of("explicit"), OWNER)).thenReturn(new ToolCallback[0]);
        TextMcpModelInterceptor interceptor = new TextMcpModelInterceptor(provider, serverAccess);

        AgentChatRequest request = new AgentChatRequest();
        request.setEnabledMcpServers(List.of("explicit"));
        request.setDisableMcpDefault(true);
        TextMcpModelInterceptor.McpResolution resolution = interceptor.resolve(request, OWNER);

        assertEquals(List.of("explicit"), resolution.serverCodes());
        // 显式列表优先，不会再读用户默认启用列表
        verify(serverAccess, never()).findEnabled(null, OWNER);
    }

    @Test
    void missingUserRecordsNothing() {
        TextMcpModelInterceptor interceptor = new TextMcpModelInterceptor(provider, serverAccess);

        AgentChatRequest request = new AgentChatRequest();
        request.setEnabledMcpServers(List.of("mine"));
        TextMcpModelInterceptor.McpResolution resolution = interceptor.resolve(request, null);

        assertTrue(resolution.isEmpty());
        assertEquals(List.of(), resolution.serverCodes());
    }

    private McpServer server(String code) {
        McpServer server = new McpServer();
        server.setMcpServerCode(code);
        return server;
    }
}
