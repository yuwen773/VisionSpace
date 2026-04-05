package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.mapper.AgentMessageMapper;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.dto.agent.ChatHistoryVO;
import com.yuwen.visionspace.model.dto.agent.SessionVO;
import com.yuwen.visionspace.model.entity.AgentMessage;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private AgentSessionMapper sessionMapper;

    @Mock
    private AgentMessageMapper messageMapper;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    @Test
    void loadHistory_shouldReturnPagedResults() {
        AgentMessage msg1 = createMessage(1L, "USER", null, "Hello");
        AgentMessage msg2 = createMessage(2L, "ASSISTANT", "text", "Hi there");
        when(messageMapper.selectByCursor("thread-123", null, 40))
                .thenReturn(List.of(msg1, msg2));

        ChatHistoryVO result = chatHistoryService.loadHistory("thread-123", null, 40);

        assertNotNull(result);
        assertEquals(2, result.getMessages().size());
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
    }

    @Test
    void loadHistory_whenHasMore_shouldReturnNextCursor() {
        List<AgentMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            messages.add(createMessage((long) i, "USER", null, "Message " + i));
        }
        when(messageMapper.selectByCursor("thread-123", null, 40)).thenReturn(messages);

        ChatHistoryVO result = chatHistoryService.loadHistory("thread-123", null, 40);

        assertTrue(result.isHasMore());
        assertEquals(39L, result.getNextCursor());
    }

    @Test
    void getUserSessions_shouldReturnSessionList() {
        AgentSession session = new AgentSession();
        session.setSessionId("thread-123");
        session.setTitle("Test Session");
        session.setLastMessage("Last message");
        session.setMessageCount(5);
        session.setSummaryContent("Summary");
        session.setCreatedTime(new Date());
        session.setUpdatedTime(new Date());

        when(sessionMapper.selectByUserIdCursor(1L, null, 20))
                .thenReturn(List.of(session));

        List<SessionVO> result = chatHistoryService.getUserSessions(1L, null, 20);

        assertEquals(1, result.size());
        assertEquals("thread-123", result.get(0).getSessionId());
        assertEquals("Test Session", result.get(0).getTitle());
    }

    private AgentMessage createMessage(Long id, String role, String subType, String content) {
        AgentMessage msg = new AgentMessage();
        msg.setId(id);
        msg.setSessionId("thread-123");
        msg.setRole(role);
        msg.setSubType(subType);
        msg.setContent(content);
        msg.setCreatedTime(new Date());
        return msg;
    }
}
