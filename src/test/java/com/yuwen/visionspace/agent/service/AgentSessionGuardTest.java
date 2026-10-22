package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSessionGuardTest {

    private static final Long OWNER = 101L;
    private static final Long STRANGER = 202L;

    @Mock
    private AgentSessionMapper sessionMapper;

    private AgentSessionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AgentSessionGuard(sessionMapper);
        AgentConstants constants = new AgentConstants();
        constants.setHistoryTruncateLength(10);
        constants.setHistoryTruncateReserve(7);
        // setter 注入在单测里直接赋值
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "agentConstants", constants);
    }

    @Test
    void bindOwner_refusesSessionOwnedByAnotherUser() {
        when(sessionMapper.selectBySessionId("thread-1")).thenReturn(session(OWNER));

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.bindOwner("thread-1", STRANGER, "hi"));

        assertEquals(40101, error.getCode());
        // 插入是 ON DUPLICATE KEY 空操作，但仍必须执行一次以覆盖「会话不存在」的首次创建
        verify(sessionMapper).insertIfAbsent(eq("thread-1"), eq(STRANGER), anyString());
        // 不产生任何写入
        verify(sessionMapper, never()).updateMessageCount(anyString(), anyLong(), anyString());
    }

    @Test
    void bindOwner_returnsSessionWhenOwnerMatches() {
        when(sessionMapper.selectBySessionId("thread-1")).thenReturn(session(OWNER));

        AgentSession result = guard.bindOwner("thread-1", OWNER, "hi");

        assertEquals(OWNER, result.getUserId());
    }

    @Test
    void bindOwner_rejectsMissingUserIdWithoutTouchingDatabase() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.bindOwner("thread-1", null, "hi"));

        assertEquals(40100, error.getCode());
        verify(sessionMapper, never()).insertIfAbsent(anyString(), any(), anyString());
    }

    @Test
    void requireOwned_refusesUnknownSessionWithoutLeakingExistence() {
        when(sessionMapper.selectBySessionId("ghost")).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.requireOwned("ghost", OWNER));

        // 与「属于他人」返回同一个错误码，避免探测他人会话标识
        assertEquals(40101, error.getCode());
    }

    @Test
    void requireOwned_refusesSessionOwnedByAnotherUser() {
        when(sessionMapper.selectBySessionId("thread-1")).thenReturn(session(OWNER));

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.requireOwned("thread-1", STRANGER));

        assertEquals(40101, error.getCode());
    }

    @Test
    void requireOwned_rejectsMalformedThreadIdsBeforeQuerying() {
        for (String invalid : new String[]{"", "   ", "a b", "line\nbreak", "x".repeat(65)}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> guard.requireOwned(invalid, OWNER), "should reject: " + invalid);
            assertEquals(40000, error.getCode(), "should reject: " + invalid);
        }
        verify(sessionMapper, never()).selectBySessionId(anyString());
    }

    private AgentSession session(Long userId) {
        AgentSession session = new AgentSession();
        session.setSessionId("thread-1");
        session.setUserId(userId);
        return session;
    }
}
