package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.agent.interceptor.TextMcpModelInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 续跑时的工具环境重建：既不能丢 MCP，也不能沿用旧授权。
 */
@ExtendWith(MockitoExtension.class)
class ResumeToolEnvironmentTest {

    private static final Long OWNER = 7L;

    @Mock
    private TextMcpModelInterceptor mcpInterceptor;

    @Test
    void noRecordedMcpMeansTheBuiltInAgent() {
        ResumeToolEnvironment env = new ResumeToolEnvironment(mcpInterceptor);

        ResumeToolEnvironment.Environment resolved = env.resolve(List.of(), OWNER);

        assertFalse(resolved.usesMcp());
        assertEquals(0, resolved.callbacks().length);
    }

    @Test
    void recordedServersAreRebuiltForTheCurrentUser() {
        ToolCallback callback = tool("echo");
        when(mcpInterceptor.resolveForCodes(List.of("remote"), OWNER))
                .thenReturn(new TextMcpModelInterceptor.McpResolution(
                        new ToolCallback[]{callback}, List.of("remote")));
        ResumeToolEnvironment env = new ResumeToolEnvironment(mcpInterceptor);

        ResumeToolEnvironment.Environment resolved = env.resolve(List.of("remote"), OWNER);

        assertTrue(resolved.usesMcp(), "a run that used MCP must not silently drop it on resume");
        assertEquals(1, resolved.callbacks().length);
        assertEquals("echo", resolved.callbacks()[0].getToolDefinition().name());
    }

    @Test
    void revokedServerBlocksTheResumeInsteadOfContinuingWithoutTheTool() {
        // 服务已停用/撤销：解析不到，续跑必须拒绝
        when(mcpInterceptor.resolveForCodes(List.of("remote"), OWNER))
                .thenReturn(new TextMcpModelInterceptor.McpResolution(new ToolCallback[0], List.of()));
        ResumeToolEnvironment env = new ResumeToolEnvironment(mcpInterceptor);

        ResumeToolUnavailableException error = assertThrows(ResumeToolUnavailableException.class,
                () -> env.resolve(List.of("remote"), OWNER));

        assertTrue(error.getMessage().contains("MCP"));
    }

    @Test
    void partiallyRevokedServersAlsoBlockTheResume() {
        // 两个服务里只有一个还可用：少一个工具继续跑不是等价恢复
        when(mcpInterceptor.resolveForCodes(List.of("a", "b"), OWNER))
                .thenReturn(new TextMcpModelInterceptor.McpResolution(
                        new ToolCallback[]{tool("echo")}, List.of("a")));
        ResumeToolEnvironment env = new ResumeToolEnvironment(mcpInterceptor);

        assertThrows(ResumeToolUnavailableException.class,
                () -> env.resolve(List.of("a", "b"), OWNER));
    }

    private ToolCallback tool(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }
        };
    }
}
