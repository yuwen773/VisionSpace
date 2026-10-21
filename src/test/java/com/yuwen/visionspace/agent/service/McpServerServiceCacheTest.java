package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.service.impl.McpServerServiceImpl;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.McpServerDetail;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MCP 工具缓存失效回归测试。
 * <p>
 * MCPManager 的工具列表有 5 分钟 Redis 缓存；update/delete 若不主动失效，
 * 用户改完配置后最多 5 分钟内仍会调用到旧的工具定义。
 */
@ExtendWith(MockitoExtension.class)
class McpServerServiceCacheTest {

    @Mock
    private McpServerMapper mcpServerMapper;

    @Mock
    private MCPManager mcpManager;

    @InjectMocks
    private McpServerServiceImpl service;

    private void stubExisting(String code, long userId) {
        McpServer entity = new McpServer();
        entity.setMcpServerCode(code);
        entity.setUserId(userId);
        entity.setStatus(1);
        entity.setInstallType("SSE");
        when(mcpServerMapper.selectOne(any())).thenReturn(entity);
    }

    private McpServerDetail detail(String code) {
        McpServerDetail d = new McpServerDetail();
        d.setMcpServerCode(code);
        d.setName("demo");
        d.setDeployConfig("{\"mcpServers\":{\"demo\":{\"type\":\"sse\",\"url\":\"https://example.com/sse\"}}}");
        return d;
    }

    @Test
    void updateShouldInvalidateCachedTools() {
        stubExisting("srv-1", 7L);

        service.updateMcp(detail("srv-1"), 7L);

        verify(mcpManager).invalidateToolsCache(7L, "srv-1");
    }

    @Test
    void deleteShouldInvalidateCachedTools() {
        stubExisting("srv-1", 7L);

        service.deleteMcp("srv-1", 7L);

        verify(mcpManager).invalidateToolsCache(7L, "srv-1");
    }
}
