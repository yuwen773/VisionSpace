package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.recovery.AgentRecoveryService;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.agent.service.ChatHistoryService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 待核对暂停对所有执行入口一致生效。
 *
 * <p>评审缺陷：只在续跑入口判断，用户再发一条消息就能让模型重新调用同一个工具。
 * 这里逐个入口验证「有待核对动作时不会进入模型」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageAgentPendingReviewGateTest {

    private static final Long OWNER = 42L;
    private static final String THREAD = "thread-gate";

    @Mock
    private AgentSessionGuard sessionGuard;
    @Mock
    private AgentRecoveryService recoveryService;
    @Mock
    private AgentRunStateStore runStateStore;
    @Mock
    private ChatHistoryService chatHistoryService;

    private ImageAgent imageAgent;

    @BeforeEach
    void setUp() {
        imageAgent = new ImageAgent();
        ReflectionTestUtils.setField(imageAgent, "sessionGuard", sessionGuard);
        ReflectionTestUtils.setField(imageAgent, "recoveryService", recoveryService);
        ReflectionTestUtils.setField(imageAgent, "runStateStore", runStateStore);
        ReflectionTestUtils.setField(imageAgent, "chatHistoryService", chatHistoryService);

        // 存在结果未知的动作：所有入口都必须停住
        doThrow(new BusinessException(com.yuwen.visionspace.exception.ErrorCode.OPERATION_ERROR,
                "存在结果未知的外部动作，请先核对后再继续"))
                .when(recoveryService).requireNoPendingReview(anyString(), anyLong());
    }

    @Test
    void syncChatIsBlockedWhileAnActionNeedsReview() {
        assertThrows(BusinessException.class,
                () -> imageAgent.chat("再生成一张", List.of(), THREAD, OWNER));

        verify(chatHistoryService, never()).saveUserMessage(anyString(), anyLong(), anyString());
    }

    @Test
    void streamingChatIsBlockedBeforeTheStreamIsBuilt() {
        assertThrows(BusinessException.class,
                () -> imageAgent.stream("再生成一张", List.of(), THREAD, OWNER));

        verify(chatHistoryService, never()).saveUserMessage(anyString(), anyLong(), anyString());
    }

    @Test
    void feedbackIsBlockedWhileAnActionNeedsReview() {
        assertThrows(BusinessException.class,
                () -> imageAgent.handleFeedback(THREAD, OWNER, false, "不好看", null, null));
    }

    @Test
    void resumeIsBlockedWhileAnActionNeedsReview() {
        AgentRunState state = new AgentRunState();
        state.setThreadId(THREAD);
        state.setUserId(OWNER);
        when(runStateStore.loadResumable(THREAD, OWNER)).thenReturn(Optional.of(state));

        assertThrows(BusinessException.class, () -> imageAgent.resume(THREAD, OWNER));

        // 闸门在重建 Agent / 回填检查点之前就拦住了
        verify(runStateStore, never()).updateStatus(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void interruptibleEntryIsBlockedAsWell() {
        assertThrows(BusinessException.class,
                () -> imageAgent.invokeAndGetOutput("再生成一张", THREAD, OWNER));
    }
}
