package com.yuwen.visionspace.agent.recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.Mockito.verify;

/**
 * 运行生命周期：状态与措辞只由这一个模块写入。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunLifecycleTest {

    private static final String THREAD = "thread-1";
    private static final long USER = 7L;

    @Mock
    private AgentRunStateStore runStateStore;

    @InjectMocks
    private AgentRunLifecycle lifecycle;

    @Test
    void startedAndFailedCarryNoPendingText() {
        lifecycle.started(THREAD, USER);
        lifecycle.failed(THREAD, USER);

        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.RUNNING, null);
        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.FAILED, null);
    }

    @Test
    void unknownActionResultUsesTheSinglePauseWording() {
        lifecycle.pausedForUnknownAction(THREAD, USER, "generateImage");

        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.PAUSED_UNRECOVERABLE,
                AgentRunLifecycle.UNKNOWN_ACTION_PREFIX + "generateImage");
    }

    @Test
    void otherPausesKeepTheirReason() {
        lifecycle.paused(THREAD, USER, "无法确认上次执行使用的工具环境，请重新发起请求");

        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.PAUSED_UNRECOVERABLE,
                "无法确认上次执行使用的工具环境，请重新发起请求");
    }

    @Test
    void awaitingConfirmationStoresDistinctToolNamesOnly() {
        lifecycle.awaitingConfirmation(THREAD, USER,
                Arrays.asList("generateImage", null, "generateImage", "searchImages"));

        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.AWAITING_CONFIRMATION,
                "generateImage\nsearchImages");
    }

    @Test
    void awaitingConfirmationWithoutToolsStoresEmptySummary() {
        lifecycle.awaitingConfirmation(THREAD, USER, null);

        verify(runStateStore).updateStatus(THREAD, USER, RunStatus.AWAITING_CONFIRMATION, "");
    }
}
