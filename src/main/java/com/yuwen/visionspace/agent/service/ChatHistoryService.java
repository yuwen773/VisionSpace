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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatHistoryService {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentSessionGuard sessionGuard;
    private AgentConstants agentConstants;

    @Autowired
    public ChatHistoryService(AgentSessionMapper sessionMapper, AgentMessageMapper messageMapper,
                              AgentSessionGuard sessionGuard) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.sessionGuard = sessionGuard;
    }

    @Resource
    public void setAgentConstants(AgentConstants agentConstants) {
        this.agentConstants = agentConstants;
    }

    // ==================== 异步写入 ====================

    // 写入一律带 userId：归属条件写进 SQL，不在应用层「先查后写」。
    // 被拒绝时只记日志、不抛异常——这些方法跑在异步线程里，异常不会回到请求方，
    // 而且拒绝的语义是「没有副作用」，不是「请求失败」。

    /**
     * 异步保存用户消息。会话不存在时按当前用户原子创建。
     */
    @Async("agentAsyncExecutor")
    public void saveUserMessage(String sessionId, Long userId, String content) {
        saveUserMessage(sessionId, userId, content, null);
    }

    /**
     * 异步保存用户消息，并登记这条消息携带的图片资源。
     *
     * <p>消息正文只保存用户输入的文字：图片地址不进正文，浏览器按
     * {@code attachmentIds} 走受控入口读取。
     *
     * @param attachmentIds 这条消息携带的图片资源标识，可为空
     */
    @Async("agentAsyncExecutor")
    public void saveUserMessage(String sessionId, Long userId, String content, List<Long> attachmentIds) {
        sessionGuard.bindOwner(sessionId, userId, content);
        insertOwned(sessionId, userId, "USER", null, content, joinIds(attachmentIds));
        sessionMapper.updateMessageCount(sessionId, userId, truncate(content));
    }

    /**
     * 异步保存助手消息
     */
    @Async("agentAsyncExecutor")
    public void saveAssistantMessage(String sessionId, Long userId, String content, String subType) {
        if (insertOwned(sessionId, userId, "ASSISTANT", subType, content, null)) {
            sessionMapper.updateMessageCount(sessionId, userId, truncate(content));
        }
    }

    /**
     * 保存 reasoning 消息
     */
    @Async("agentAsyncExecutor")
    public void saveReasoningMessage(String sessionId, Long userId, String content) {
        insertOwned(sessionId, userId, "ASSISTANT", "reasoning", content, null);
    }

    /**
     * 保存工具调用请求
     */
    @Async("agentAsyncExecutor")
    public void saveToolCallMessage(String sessionId, Long userId, String content) {
        insertOwned(sessionId, userId, "ASSISTANT", "tool-call", content, null);
    }

    /**
     * 保存工具返回结果
     */
    @Async("agentAsyncExecutor")
    public void saveToolResultMessage(String sessionId, Long userId, String content) {
        insertOwned(sessionId, userId, "ASSISTANT", "tool-result", content, null);
    }

    /**
     * 保存工具确认消息
     */
    @Async("agentAsyncExecutor")
    public void saveToolConfirmMessage(String sessionId, Long userId, String content) {
        insertOwned(sessionId, userId, "ASSISTANT", "mcp-confirm", content, null);
    }

    /**
     * 异步保存摘要。会话必须属于该用户，否则整段写入跳过。
     */
    @Async("agentAsyncExecutor")
    public void saveSummary(String sessionId, Long userId, String summaryContent, Integer tokenCountBefore) {
        if (sessionMapper.updateSummaryOwned(sessionId, userId, summaryContent, truncate(summaryContent)) == 0) {
            log.warn("拒绝写入他人会话的摘要, sessionId={}, userId={}", sessionId, userId);
            return;
        }
        messageMapper.markAsSummaryOwned(sessionId, userId, new Date());
    }

    // ==================== 查询加载 ====================

    /**
     * 分页加载聊天历史。
     * <p>
     * 每次取「比游标更早的最近 limit 条」（SQL 层倒序），再翻转为正序返回，
     * 保证 {@code messages} 始终按时间从旧到新排列，同时 {@code hasMore}/{@code nextCursor}
     * 在各页之间语义一致：nextCursor 是本页最旧一条消息的 id，下一页从它继续往前取。
     */
    public ChatHistoryVO loadHistory(String sessionId, Long beforeId, int limit) {
        // 倒序取最近的一页，再翻转成正序用于展示（reversed() 是视图，不复制）
        List<AgentMessage> entities =
                messageMapper.selectByCursor(sessionId, beforeId, limit).reversed();

        boolean hasMore = entities.size() == limit;
        // 翻转后首元素即本页最旧的消息
        Long nextCursor = hasMore ? entities.get(0).getId() : null;

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
     * 验证会话是否属于指定用户
     */
    public boolean isSessionOwner(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        AgentSession session = sessionMapper.selectBySessionId(sessionId);
        return session != null && userId.equals(session.getUserId());
    }

    /**
     * 带归属的消息写入。会话不属于该用户时 SQL 不产生任何行。
     */
    private boolean insertOwned(String sessionId, Long userId, String role, String subType, String content,
                                String attachmentIds) {
        int inserted = messageMapper.insertOwned(sessionId, userId, role, subType, content, attachmentIds);
        if (inserted == 0) {
            log.warn("拒绝写入他人会话的消息, sessionId={}, userId={}, role={}", sessionId, userId, role);
            return false;
        }
        return true;
    }

    /**
     * 资源标识列表拼成十进制逗号分隔的字符串。
     */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 解析消息携带的资源标识。存的是十进制字符串，读出来仍是标识而不是地址。
     *
     * <p>按字符串返回：资源标识是雪花值，超出 JavaScript 的安全整数范围，
     * 转成 number 会丢精度。
     */
    private List<String> parseIds(String attachmentIds) {
        if (attachmentIds == null || attachmentIds.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String part : attachmentIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Long.valueOf(trimmed);
                ids.add(trimmed);
            } catch (NumberFormatException e) {
                log.warn("消息附件标识无法解析，已跳过: value={}", trimmed);
            }
        }
        return ids;
    }

    private String truncate(String content) {
        if (content == null) return "";
        int maxLength = agentConstants.getHistoryTruncateLength();
        int reserveLength = agentConstants.getHistoryTruncateReserve();
        return content.length() > maxLength ? content.substring(0, reserveLength) + "..." : content;
    }

    private MessageVO toMessageVO(AgentMessage entity) {
        MessageVO messageVO = new MessageVO();
        BeanUtils.copyProperties(entity, messageVO);
        messageVO.setAttachmentIds(parseIds(entity.getAttachmentIds()));
        return messageVO;
    }

    private SessionVO toSessionVO(AgentSession entity) {
        SessionVO sessionVO = new SessionVO();
        BeanUtils.copyProperties(entity, sessionVO);
        return sessionVO;
    }
}
