package com.yuwen.visionspace.agent.manager;

import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.manager.outbound.OutboundPolicyProperties;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 生产封禁 STDIO 的「零进程启动」证明。
 *
 * <p>被拒绝的配置指向一个会留下痕迹的启动命令：只要进程真的被执行，
 * 标记文件就会出现。断言标记文件不存在、子进程集合没有新增，
 * 并且异常发生在任何进程创建之前。
 */
class MCPManagerStdioBanTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectedStdioLeavesNoProcessAndNoMarkerFile() throws Exception {
        Path marker = tempDir.resolve("stdio-started.txt");
        // 一旦被执行，这个命令会写出标记文件
        String command = "cmd.exe";
        List<String> args = List.of("/c", "echo started> \"" + marker + "\"");

        MCPManager manager = manager("prod");
        McpServer entity = stdioServer(command, args);

        Set<Long> childrenBefore = childProcesses();
        BusinessException error = assertThrows(BusinessException.class, () -> manager.getTools(entity));
        Set<Long> childrenAfter = childProcesses();

        assertEquals(40300, error.getCode());
        assertFalse(Files.exists(marker), "no process may be started for a rejected STDIO config");
        assertEquals(childrenBefore, childrenAfter, "no child process may be spawned");
    }

    @Test
    void rejectionHappensForCallToolToo() {
        MCPManager manager = manager("prod");
        McpServer entity = stdioServer("cmd.exe", List.of("/c", "exit 0"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> manager.callTool(new com.yuwen.visionspace.model.dto.mcp.McpServerCallToolRequest(
                        entity.getMcpServerCode(), "anything", java.util.Map.of()), entity));
        assertEquals(40300, error.getCode());
    }

    private MCPManager manager(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        return new MCPManager(redis, new McpCapabilityGuard(environment),
                new OutboundPolicy(new OutboundPolicyProperties()));
    }

    private McpServer stdioServer(String command, List<String> args) {
        StringBuilder argsJson = new StringBuilder("[");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                argsJson.append(',');
            }
            argsJson.append('"').append(args.get(i).replace("\\", "\\\\")).append('"');
        }
        argsJson.append(']');

        McpServer entity = new McpServer();
        entity.setMcpServerCode("stdio-controlled");
        entity.setUserId(7L);
        entity.setStatus(1);
        entity.setInstallType("STDIO");
        entity.setHost("");
        entity.setDeployConfig("{\"mcpServers\":{\"x\":{\"type\":\"stdio\",\"command\":\""
                + command + "\",\"args\":" + argsJson + "}}}");
        return entity;
    }

    private Set<Long> childProcesses() {
        return ProcessHandle.current().children().map(ProcessHandle::pid).collect(Collectors.toSet());
    }
}
