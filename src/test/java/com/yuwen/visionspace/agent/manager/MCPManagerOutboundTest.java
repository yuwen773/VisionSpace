package com.yuwen.visionspace.agent.manager;

import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.acceptance.ControlledMcpServer;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.manager.outbound.OutboundPolicyProperties;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolRequest;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolResponse;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 远程接入与出站策略、危险能力封禁的联动。
 *
 * <p>远程目标使用本机受控 MCP 替身（只监听回环），测试显式放行回环；
 * 拒绝场景断言替身收到的请求数为 0，即"零连接、零工具执行"。
 */
class MCPManagerOutboundTest {

    private ControlledMcpServer mcpServer;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() throws Exception {
        mcpServer = new ControlledMcpServer();
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }

    @AfterEach
    void tearDown() {
        mcpServer.close();
    }

    @Test
    void authorizedRemoteToolsAreDiscoveredAndExecuted() {
        MCPManager manager = manager("acceptance");
        McpServer entity = server("SSE", mcpServer.url());

        List<McpTool> tools = manager.getTools(entity);
        assertEquals(1, tools.size(), "controlled server exposes one tool");
        assertEquals("echo", tools.get(0).getName());

        McpServerCallToolRequest request = new McpServerCallToolRequest();
        request.setMcpServerCode(entity.getMcpServerCode());
        request.setToolName("echo");
        request.setToolParams(Map.of("q", "x"));
        McpServerCallToolResponse response = manager.callTool(request, entity);

        assertFalse(Boolean.TRUE.equals(response.getIsError()), response.toString());
        assertTrue(response.getContent().stream()
                .anyMatch(c -> ((McpServerCallToolResponse.TextContent) c).getText().contains("controlled-mcp-result")));
        assertEquals(1, mcpServer.toolCalls());
    }

    @Test
    void productionRejectsStdioBeforeAnyProcessOrConnection() {
        MCPManager manager = manager("prod");
        // 命令名故意不存在：一旦真的尝试启动进程就会留下可观测痕迹
        McpServer entity = serverWithConfig("STDIO", "",
                "{\"mcpServers\":{\"x\":{\"type\":\"stdio\",\"command\":\"definitely-not-a-real-command\"}}}");

        BusinessException error = assertThrows(BusinessException.class, () -> manager.getTools(entity));
        assertEquals(40300, error.getCode());
        assertEquals(0, mcpServer.requestCount());
        assertEquals(0, mcpServer.toolCalls());
    }

    @Test
    void stdioDeclaredAsRemoteIsStillRejectedInProduction() {
        // installType 声明成 SSE，但配置里带 command：仍按 STDIO 处理
        MCPManager manager = manager("prod");
        McpServer entity = serverWithConfig("SSE", "",
                "{\"mcpServers\":{\"x\":{\"type\":\"stdio\",\"command\":\"definitely-not-a-real-command\"}}}");

        BusinessException error = assertThrows(BusinessException.class, () -> manager.getTools(entity));
        assertEquals(40300, error.getCode());
        assertEquals(0, mcpServer.requestCount());
    }

    @Test
    void restrictedRemoteTargetIsRejectedWithoutConnecting() {
        MCPManager manager = manager("acceptance");
        // 回环替身本身是受控目标；这里用元数据地址验证策略，替身不会收到任何请求
        McpServer entity = server("SSE", "http://169.254.169.254/sse");

        BusinessException error = assertThrows(BusinessException.class, () -> manager.getTools(entity));
        assertEquals(40000, error.getCode());
        assertEquals(0, mcpServer.requestCount());
    }

    @Test
    void toolsCacheIsKeyedByOwnerSoUsersDoNotShareDefinitions() {
        MCPManager manager = manager("acceptance");
        McpServer first = server("SSE", mcpServer.url());
        first.setUserId(1L);
        McpServer second = server("SSE", mcpServer.url());
        second.setUserId(2L);
        second.setMcpServerCode(first.getMcpServerCode());

        manager.getTools(first);
        manager.getTools(second);

        // 两次读取必须使用不同的缓存键（按归属隔离），因此替身被查询两次
        assertTrue(mcpServer.requestCount() >= 2,
                "each owner must resolve tools independently: " + mcpServer.requests());
    }

    private MCPManager manager(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        properties.setAllowLoopback(true);
        return new MCPManager(redis, new McpCapabilityGuard(environment),
                new OutboundPolicy(properties));
    }

    private McpServer server(String installType, String url) {
        return serverWithConfig(installType, url,
                "{\"mcpServers\":{\"x\":{\"type\":\"sse\",\"url\":\"" + url + "\"}}}");
    }

    private McpServer serverWithConfig(String installType, String host, String deployConfig) {
        McpServer entity = new McpServer();
        entity.setMcpServerCode("controlled");
        entity.setUserId(7L);
        entity.setStatus(1);
        entity.setInstallType(installType);
        entity.setHost(host);
        entity.setDeployConfig(deployConfig);
        return entity;
    }
}
