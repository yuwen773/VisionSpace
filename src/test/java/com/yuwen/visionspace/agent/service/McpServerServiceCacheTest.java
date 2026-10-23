package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.agent.service.impl.McpServerServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.manager.outbound.OutboundPolicyProperties;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.McpServerDetail;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MCP 工具缓存失效与保存期校验。
 *
 * <p>缓存有 5 分钟 TTL；update/delete 若不主动失效，用户改完配置后仍会调用旧工具。
 * 保存期还要拒绝会被服务端禁用或无法安全访问的配置。
 */
@ExtendWith(MockitoExtension.class)
class McpServerServiceCacheTest {

    @Mock
    private McpServerMapper mcpServerMapper;

    @Mock
    private MCPManager mcpManager;

    @Mock
    private McpServerAccess serverAccess;

    private McpServerServiceImpl service;

    /** 本机 DNS 会把公共域名解析到基准测试网段，服务层用例改用受控主机名。 */
    private static final String REMOTE_HOST = "mcp.internal.example";

    @BeforeEach
    void setUp() {
        OutboundPolicy policy = new OutboundPolicy(policyProperties());
        service = new McpServerServiceImpl(mcpServerMapper, mcpManager, serverAccess,
                guard("local"), policy);
    }

    private OutboundPolicyProperties policyProperties() {
        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        properties.setTrustedHosts(java.util.List.of(REMOTE_HOST));
        return properties;
    }

    private McpCapabilityGuard guard(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new McpCapabilityGuard(environment);
    }

    private void stubExisting(String code, long userId) {
        McpServer entity = new McpServer();
        entity.setMcpServerCode(code);
        entity.setUserId(userId);
        entity.setStatus(1);
        entity.setInstallType("SSE");
        when(serverAccess.findOwned(code, userId)).thenReturn(entity);
    }

    private McpServerDetail detail(String code) {
        McpServerDetail d = new McpServerDetail();
        d.setMcpServerCode(code);
        d.setName("demo");
        d.setDeployConfig("{\"mcpServers\":{\"demo\":{\"type\":\"sse\",\"url\":\"https://" + REMOTE_HOST + "/sse\"}}}");
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

    @Test
    void updateOfAnotherUsersServerIsNotFound() {
        when(serverAccess.findOwned("srv-1", 7L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateMcp(detail("srv-1"), 7L));

        assertEquals(40400, error.getCode());
        verify(mcpServerMapper, never()).updateById(any(McpServer.class));
        verify(mcpManager, never()).invalidateToolsCache(any(), any());
    }

    @Test
    void statusToggleKeepsOtherFieldsAndRejectsDeletedStatus() {
        stubExisting("srv-1", 7L);
        McpServerDetail detail = detail("srv-1");
        detail.setStatus(0);

        service.updateMcp(detail, 7L);

        verify(mcpServerMapper).updateById(argThat((McpServer entity) -> entity.getStatus() == 0));

        McpServerDetail invalid = detail("srv-1");
        invalid.setStatus(3);
        assertThrows(BusinessException.class, () -> service.updateMcp(invalid, 7L));
    }

    @Test
    void productionRejectsStdioConfigurationBeforePersisting() {
        McpServerServiceImpl prodService = new McpServerServiceImpl(mcpServerMapper, mcpManager,
                serverAccess, guard("prod"), new OutboundPolicy(policyProperties()));
        McpServerDetail stdio = new McpServerDetail();
        stdio.setName("local");
        stdio.setDeployConfig("{\"mcpServers\":{\"x\":{\"type\":\"stdio\",\"command\":\"npx\"}}}");

        BusinessException error = assertThrows(BusinessException.class,
                () -> prodService.createMcp(stdio, 7L));
        assertEquals(40300, error.getCode());
        verify(mcpServerMapper, never()).insert(any(McpServer.class));
    }

    @Test
    void productionCanStillDisableALegacyStdioServer() {
        // 历史遗留的 STDIO 配置在保存时会被拒绝；若停用也走同一校验，
        // 它就永远无法被关闭，反而一直保持启用
        McpServer legacy = new McpServer();
        legacy.setMcpServerCode("legacy");
        legacy.setUserId(7L);
        legacy.setStatus(1);
        legacy.setInstallType("STDIO");
        legacy.setDeployConfig("{\"mcpServers\":{\"x\":{\"type\":\"stdio\",\"command\":\"npx\"}}}");
        when(serverAccess.findOwned("legacy", 7L)).thenReturn(legacy);

        McpServerServiceImpl prodService = new McpServerServiceImpl(mcpServerMapper, mcpManager,
                serverAccess, guard("prod"), new OutboundPolicy(policyProperties()));
        McpServerDetail disable = new McpServerDetail();
        disable.setMcpServerCode("legacy");
        disable.setName("legacy");
        disable.setDeployConfig(legacy.getDeployConfig());
        disable.setInstallType("STDIO");
        disable.setStatus(0);

        prodService.updateMcp(disable, 7L);

        verify(mcpServerMapper).updateById(argThat((McpServer entity) -> entity.getStatus() == 0));
    }

    @Test
    void restrictedRemoteTargetIsRejectedBeforePersisting() {
        McpServerDetail restricted = new McpServerDetail();
        restricted.setName("meta");
        restricted.setDeployConfig(
                "{\"mcpServers\":{\"x\":{\"type\":\"sse\",\"url\":\"http://169.254.169.254/sse\"}}}");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createMcp(restricted, 7L));
        assertEquals(40000, error.getCode());
        verify(mcpServerMapper, never()).insert(any(McpServer.class));
    }

    @Test
    void remoteServerIsSavedWithDerivedHost() {
        String code = service.createMcp(detail(null), 7L);

        assertEquals(32, code.length());
        verify(mcpServerMapper).insert(argThat((McpServer entity) ->
                ("https://" + REMOTE_HOST).equals(entity.getHost())
                        && "SSE".equals(entity.getInstallType())
                        && entity.getStatus() == 1
                        && entity.getUserId() == 7L));
    }
}
