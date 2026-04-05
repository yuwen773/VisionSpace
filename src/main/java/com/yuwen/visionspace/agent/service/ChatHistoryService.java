package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.mapper.AgentMessageMapper;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.dto.agent.ChatHistoryVO;
import com.yuwen.visionspace.model.dto.agent.MessageVO;
import com.yuwen.visionspace.model.dto.agent.SessionVO;
import com.yuwen.visionspace.model.entity.AgentMessage;
import com.yuwen.visionspace.model.entity.AgentSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatHistoryService {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private AgentConstants agentConstants;

    @Autowired
    public ChatHistoryService(AgentSessionMapper sessionMapper, AgentMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Resource
    public void setAgentConstants(AgentConstants agentConstants) {
        this.agentConstants = agentConstants;
    }

    // ==================== 异步写入 ====================

    /**
     * 异步保存用户消息
     */
    @Async("agentAsyncExecutor")
    public void saveUserMessage(String sessionId, Long userId, String content) {
        ensureSessionExists(sessionId, userId, content);
        AgentMessage message = buildMessage(sessionId, "USER", null, content);
        messageMapper.insert(message);
        sessionMapper.updateMessageCount(sessionId, truncate(content));
    }

    /**
     * 异步保存助手消息
     */
    @Async("agentAsyncExecutor")
    public void saveAssistantMessage(String sessionId, String content, String subType) {
        AgentMessage message = buildMessage(sessionId, "ASSISTANT", subType, content);
        messageMapper.insert(message);
        sessionMapper.updateMessageCount(sessionId, truncate(content));
    }

    /**
     * 保存 reasoning 消息
     */
    @Async("agentAsyncExecutor")
    public void saveReasoningMessage(String sessionId, String content) {
        AgentMessage message = buildMessage(sessionId, "ASSISTANT", "reasoning", content);
        messageMapper.insert(message);
    }

    /**
     * 保存工具调用请求
     */
    @Async("agentAsyncExecutor")
    public void saveToolCallMessage(String sessionId, String content) {
        AgentMessage message = buildMessage(sessionId, "ASSISTANT", "tool-call", content);
        messageMapper.insert(message);
    }

    /**
     * 保存工具返回结果
     */
    @Async("agentAsyncExecutor")
    public void saveToolResultMessage(String sessionId, String content) {
        AgentMessage message = buildMessage(sessionId, "ASSISTANT", "tool-result", content);
        messageMapper.insert(message);
    }

    /**
     * 保存工具确认消息
     */
    @Async("agentAsyncExecutor")
    public void saveToolConfirmMessage(String sessionId, String content) {
        AgentMessage message = buildMessage(sessionId, "ASSISTANT", "mcp-confirm", content);
        messageMapper.insert(message);
    }

    /**
     * 异步保存摘要
     */
    @Async("agentAsyncExecutor")
    public void saveSummary(String sessionId, String summaryContent, Integer tokenCountBefore) {
        AgentSession session = sessionMapper.selectBySessionId(sessionId);
        if (session != null) {
            session.setSummaryContent(summaryContent);
            session.setSummaryAt(new Date());
            session.setLastMessage(truncate(summaryContent));
            sessionMapper.updateById(session);
        }
        messageMapper.markAsSummary(sessionId, new Date());
    }

    // ==================== 查询加载 ====================

    /**
     * 加载最近的对话消息（用于 Agent 恢复上下文）
     */
    public List<Message> loadRecentMessages(String sessionId, int limit) {
        List<AgentMessage> entities =
                messageMapper.selectBySessionIdOrderByTimeASC(sessionId, limit);

        return entities.stream()
                .filter(e -> "USER".equals(e.getRole())
                        || ("ASSISTANT".equals(e.getRole()) && "text".equals(e.getSubType())))
                .map(this::toSpringAIMessage)
                .collect(Collectors.toList());
    }

    /**
     * 分页加载聊天历史
     */
    public ChatHistoryVO loadHistory(String sessionId, Long beforeId, int limit) {
        List<AgentMessage> entities;
        if (beforeId == null) {
            // 首次加载：正序（最老的消息在前）
            entities = messageMapper.selectBySessionIdOrderByTimeASC(sessionId, limit);
        } else {
            // 向上滚动加载更多：倒序分页
            entities = messageMapper.selectByCursor(sessionId, beforeId, limit);
        }

        boolean hasMore = entities.size() == limit;
        Long nextCursor = hasMore ? entities.get(entities.size() - 1).getId() : null;

        List<MessageVO> vos = entities.stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());

        return new ChatHistoryVO(vos, hasMore, nextCursor);
    }

    /**
     * 获取用户的会话列表（游标分页）
     */
    public List<SessionVO> getUserSessions(Long userId, Long beforeId, int limit) {
        List<AgentSession> sessions =
                sessionMapper.selectByUserIdCursor(userId, beforeId, limit);

        return sessions.stream()
                .map(this::toSessionVO)
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 确保 Session 记录存在，不存在则自动创建（幂等）
     */
    private void ensureSessionExists(String sessionId, Long userId, String firstMessage) {
        AgentSession existing = sessionMapper.selectBySessionId(sessionId);
        if (existing != null) {
            return;
        }
        try {
            AgentSession session = new AgentSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setTitle(truncate(firstMessage));
            session.setStatus(1);
            session.setMessageCount(0);
            session.setLastMessage("");
            session.setCreatedTime(new Date());
            session.setUpdatedTime(new Date());
            sessionMapper.insert(session);
            log.info("自动创建 AgentSession, sessionId={}, userId={}", sessionId, userId);
        } catch (DuplicateKeyException e) {
            log.debug("Session 已存在（并发创建）, sessionId={}", sessionId);
        }
    }

    private String truncate(String content) {
        if (content == null) return "";
        int maxLength = agentConstants.getHistoryTruncateLength();
        int reserveLength = agentConstants.getHistoryTruncateReserve();
        return content.length() > maxLength ? content.substring(0, reserveLength) + "..." : content;
    }

    private AgentMessage buildMessage(String sessionId, String role,
                                       String subType, String content) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSubType(subType);
        message.setContent(content);
        message.setCreatedTime(new Date());
        return message;
    }

    private Message toSpringAIMessage(AgentMessage entity) {
        return switch (entity.getRole()) {
            case "USER" -> new UserMessage(entity.getContent());
            case "ASSISTANT" -> new AssistantMessage(entity.getContent());
            case "SYSTEM" -> new SystemMessage(entity.getContent());
            default -> new AssistantMessage(entity.getContent());
        };
    }

    private MessageVO toMessageVO(AgentMessage entity) {
        MessageVO messageVO = new MessageVO();
        BeanUtils.copyProperties(entity, messageVO);
        return messageVO;
    }

    private SessionVO toSessionVO(AgentSession entity) {
        SessionVO sessionVO = new SessionVO();
        BeanUtils.copyProperties(entity, sessionVO);
        return sessionVO;
    }
}
