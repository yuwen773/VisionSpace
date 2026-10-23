package com.yuwen.visionspace.agent.mcp;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具解析阶段的归属与能力过滤。
 *
 * <p>解析不到的服务不建立连接；被禁用的能力不进入工具列表。
 */
@ExtendWith(MockitoExtension.class)
class McpToolCallbackProviderTest {

    @Mock
    private MCPManager mcpManager;

    @Mock
    private McpServerAccess serverAccess;

    @Test
    void anotherUsersCodeProducesNoCallbacksAndNoConnection() {
        when(serverAccess.findEnabled(List.of("foreign"), 7L)).thenReturn(List.of());
        McpToolCallbackProvider provider = provider("local");

        ToolCallback[] callbacks = provider.getCallbacks(List.of("foreign"), 7L);

        assertEquals(0, callbacks.length);
        verify(mcpManager, never()).getTools(any());
    }

    @Test
    void disabledCapabilityIsSkippedWithoutConnecting() {
        when(serverAccess.findEnabled(List.of("local-stdio"), 7L))
                .thenReturn(List.of(server("local-stdio", "STDIO")));
        McpToolCallbackProvider provider = provider("prod");

        ToolCallback[] callbacks = provider.getCallbacks(List.of("local-stdio"), 7L);

        assertEquals(0, callbacks.length);
        verify(mcpManager, never()).getTools(any());
    }

    @Test
    void enabledRemoteServerYieldsCallbacksBoundToOwner() {
        McpServer entity = server("remote", "SSE");
        when(serverAccess.findEnabled(List.of("remote"), 7L)).thenReturn(List.of(entity));
        McpTool tool = new McpTool();
        tool.setName("echo");
        when(mcpManager.getTools(entity)).thenReturn(List.of(tool));
        McpToolCallbackProvider provider = provider("local");

        ToolCallback[] callbacks = provider.getCallbacks(List.of("remote"), 7L);

        assertEquals(1, callbacks.length);
        assertEquals("echo", callbacks[0].getToolDefinition().name());
    }

    @Test
    void missingUserIdYieldsNoCallbacks() {
        McpToolCallbackProvider provider = provider("local");
        assertEquals(0, provider.getCallbacks(List.of("remote"), null).length);
        assertEquals(0, provider.getCallbacks(List.of(), 7L).length);
        verify(serverAccess, never()).findEnabled(any(), any());
    }

    private McpToolCallbackProvider provider(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new McpToolCallbackProvider(mcpManager, serverAccess,
                new McpCapabilityGuard(environment));
    }

    private McpServer server(String code, String installType) {
        McpServer entity = new McpServer();
        entity.setMcpServerCode(code);
        entity.setUserId(7L);
        entity.setStatus(1);
        entity.setInstallType(installType);
        return entity;
    }
}
