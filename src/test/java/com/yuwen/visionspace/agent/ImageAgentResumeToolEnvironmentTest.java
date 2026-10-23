package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.yuwen.visionspace.agent.recovery.AgentRecoveryService;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.recovery.ResumeToolEnvironment;
import com.yuwen.visionspace.agent.recovery.ResumeToolUnavailableException;
import com.yuwen.visionspace.agent.recovery.RunStatus;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.entity.AgentRunState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 续跑前的工具环境判定：无法确认上次用了什么工具时，不能猜着继续。
 *
 * <p>评审缺陷是「一律用内置 agent」，MCP 工具凭空消失。修好之后还有一个更隐蔽的
 * 缺口：状态里没有记录（NULL）被当成「没有用 MCP」，于是同样会在少了工具的情况下
 * 继续跑。这里把「没有记录」与「记录了空环境」分开验证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageAgentResumeToolEnvironmentTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-resume";

    @Mock
    private AgentSessionGuard sessionGuard;
    @Mock
    private AgentRecoveryService recoveryService;
    @Mock
    private AgentRunStateStore runStateStore;
    @Mock
    private ResumeToolEnvironment resumeToolEnvironment;
    @Mock
    private BaseCheckpointSaver checkpointSaver;

    private ImageAgent imageAgent;
    private AgentRunState state;

    @BeforeEach
    void setUp() throws Exception {
        imageAgent = new ImageAgent();
        ReflectionTestUtils.setField(imageAgent, "sessionGuard", sessionGuard);
        ReflectionTestUtils.setField(imageAgent, "recoveryService", recoveryService);
        ReflectionTestUtils.setField(imageAgent, "runStateStore", runStateStore);
        ReflectionTestUtils.setField(imageAgent, "resumeToolEnvironment", resumeToolEnvironment);
        ReflectionTestUtils.setField(imageAgent, "checkpointSaver", checkpointSaver);

        state = new AgentRunState();
        state.setThreadId(THREAD);
        state.setUserId(OWNER);

        when(runStateStore.loadResumable(THREAD, OWNER)).thenReturn(Optional.of(state));
        when(runStateStore.toCheckpoint(state)).thenReturn(Optional.of(
                Checkpoint.builder().id("cp").nodeId("model").nextNodeId("tool_node")
                        .state(Map.of()).build()));
        when(runStateStore.resumeConfig(eq(THREAD), eq(OWNER), eq(state), anyString()))
                .thenReturn(RunnableConfig.builder().threadId(THREAD).build());
    }

    @Test
    void missingToolEnvironmentRefusesToResumeInsteadOfSilentlyDroppingMcpTools() throws Exception {
        when(runStateStore.recordedMcpServerCodes(state)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> imageAgent.resume(THREAD, OWNER));

        verify(runStateStore).updateStatus(eq(THREAD), eq(OWNER),
                eq(RunStatus.PAUSED_UNRECOVERABLE), anyString());
        verify(resumeToolEnvironment, never()).resolve(any(), anyLong());
        verify(checkpointSaver, never()).put(any(), any());
    }

    @Test
    void revokedMcpServerRefusesToResumeInsteadOfRunningWithoutIt() throws Exception {
        when(runStateStore.recordedMcpServerCodes(state))
                .thenReturn(Optional.of(List.of("remote-a")));
        when(resumeToolEnvironment.resolve(List.of("remote-a"), OWNER))
                .thenThrow(new ResumeToolUnavailableException("上次执行使用的 MCP 服务已停用或撤销"));

        assertThrows(BusinessException.class, () -> imageAgent.resume(THREAD, OWNER));

        verify(runStateStore).updateStatus(eq(THREAD), eq(OWNER),
                eq(RunStatus.PAUSED_UNRECOVERABLE), anyString());
        verify(checkpointSaver, never()).put(any(), any());
    }

    @Test
    void explicitlyEmptyEnvironmentProceedsWithTheBuiltInAgent() throws Exception {
        // 记录了「没有用 MCP」：这是可确认的状态，可以继续
        when(runStateStore.recordedMcpServerCodes(state)).thenReturn(Optional.of(List.of()));
        when(resumeToolEnvironment.resolve(List.of(), OWNER))
                .thenReturn(new ResumeToolEnvironment.Environment(new org.springframework.ai.tool.ToolCallback[0], false));
        when(checkpointSaver.put(any(), any())).thenThrow(new IllegalStateException("redis down"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> imageAgent.resume(THREAD, OWNER));

        // 走到了写回检查点这一步，说明工具环境判定已放行
        assertEquals("执行状态无法写回，已暂停自动执行", error.getMessage());
        verify(resumeToolEnvironment).resolve(List.of(), OWNER);
    }
}
