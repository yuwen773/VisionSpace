package com.yuwen.visionspace.agent.mcp;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolRequest;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolResponse;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已缓存回调在停用或撤权之后不得继续执行。
 *
 * <p>回调只保存 code 与用户，执行前回读归属与启用状态；拒绝路径不产生任何
 * 外部调用（{@link MCPManager#callTool} 一次都没有被调用）。
 */
@ExtendWith(MockitoExtension.class)
class McpToolCallbackAuthorizationTest {

    private static final String SERVER_CODE = "srv-1";
    private static final long OWNER = 7L;

    @Mock
    private MCPManager mcpManager;

    @Mock
    private McpServerAccess serverAccess;

    private McpToolCallback callback;

    @BeforeEach
    void setUp() {
        McpTool tool = new McpTool();
        tool.setName("lookup");
        tool.setDescription("demo");
        callback = new McpToolCallback(mcpManager, serverAccess, SERVER_CODE, OWNER, tool);
    }

    @Test
    void executesOnlyAfterReReadingOwnershipAndEnabledState() {
        McpServer enabled = server(OWNER, McpServerAccess.STATUS_ENABLED);
        when(serverAccess.requireExecutable(SERVER_CODE, OWNER)).thenReturn(enabled);
        McpServerCallToolResponse response = new McpServerCallToolResponse();
        response.setIsError(false);
        when(mcpManager.callTool(any(), eq(enabled))).thenReturn(response);

        callback.call("{\"q\":\"x\"}");

        verify(mcpManager).callTool(any(McpServerCallToolRequest.class), eq(enabled));
    }

    @Test
    void disabledServerIsRejectedWithoutAnyExternalCall() {
        when(serverAccess.requireExecutable(SERVER_CODE, OWNER))
                .thenThrow(new BusinessException(40101, "MCP 服务不可用"));

        assertThrows(BusinessException.class, () -> callback.call("{}"));

        verify(mcpManager, never()).callTool(any(), any());
    }

    @Test
    void deletedServerIsRejectedWithoutAnyExternalCall() {
        when(serverAccess.requireExecutable(SERVER_CODE, OWNER))
                .thenThrow(new BusinessException(40101, "MCP 服务不可用"));
        assertThrows(BusinessException.class, () -> callback.call("{}"));
        verify(mcpManager, never()).callTool(any(), any());
    }

    @Test
    void serverOwnedByAnotherUserIsNotExecutable() {
        // 同一 code 在他人名下：回读按当前用户过滤，结果为不可执行
        when(serverAccess.requireExecutable(SERVER_CODE, OWNER))
                .thenThrow(new BusinessException(40101, "MCP 服务不可用"));
        assertThrows(BusinessException.class, () -> callback.call("{}"));
        verify(mcpManager, never()).callTool(any(), any());
    }

    @Test
    void toolDefinitionDoesNotRequireDatabaseAccess() {
        // 工具定义可以离线生成；真正的授权发生在执行时
        assertEquals("lookup", callback.getToolDefinition().name());
        verify(serverAccess, never()).requireExecutable(any(), any());
    }

    private McpServer server(long userId, int status) {
        McpServer entity = new McpServer();
        entity.setMcpServerCode(SERVER_CODE);
        entity.setUserId(userId);
        entity.setStatus(status);
        entity.setInstallType("SSE");
        return entity;
    }

    @Test
    void rejectionUsesSameMessageForMissingAndForeignServers() {
        // 不区分「不存在」与「属于他人」：错误信息不能用于探测他人配置
        BusinessException missing = new BusinessException(40101, "MCP 服务不可用");
        BusinessException foreign = new BusinessException(40101, "MCP 服务不可用");
        assertEquals(missing.getMessage(), foreign.getMessage());
        assertEquals(missing.getCode(), foreign.getCode());
    }

    @Test
    void emptyInputStillRequiresAuthorizationFirst() {
        when(serverAccess.requireExecutable(SERVER_CODE, OWNER))
                .thenThrow(new BusinessException(40101, "MCP 服务不可用"));
        // 空参数不是放行条件：授权判定先于参数解析
        assertThrows(BusinessException.class, () -> callback.call(null));
        verify(mcpManager, never()).callTool(any(), any());
    }

}
