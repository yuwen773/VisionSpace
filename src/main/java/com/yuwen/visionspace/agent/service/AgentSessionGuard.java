package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.entity.AgentSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 会话归属的唯一判定入口。
 *
 * <p>会话归属只认服务端登录身份，请求体里的 userId 一律不参与判定。首次使用某个
 * threadId 时用唯一索引原子绑定所属用户；之后每次读写与执行前都必须回读核对，
 * 不能把「插入成功」当作归属成立的证据。
 *
 * <p>拒绝时统一返回「无权访问该会话」，不区分「不存在」与「属于他人」，
 * 避免用错误信息探测他人的会话标识。平台管理员没有旁路：这里只看归属。
 */
@Slf4j
@Service
public class AgentSessionGuard {

    /** 与 agent_session.sessionId 的 varchar(64) 对齐，超长会在写入时变成系统错误 */
    private static final int MAX_THREAD_ID_LENGTH = 64;

    private final AgentSessionMapper sessionMapper;

    @Resource
    private AgentConstants agentConstants;

    public AgentSessionGuard(AgentSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    /**
     * 首次使用原子绑定归属，随后回读核对。
     *
     * <p>已存在的会话不会被改写归属：唯一索引上的插入是空操作，
     * 回读后发现属于他人就拒绝。
     */
    public AgentSession bindOwner(String threadId, Long userId, String firstMessage) {
        Long ownerId = requireUserId(userId);
        String id = requireThreadId(threadId);
        sessionMapper.insertIfAbsent(id, ownerId, truncate(firstMessage));
        return requireOwned(id, ownerId);
    }

    /**
     * 只读核对：会话必须已存在且属于当前用户。
     */
    public AgentSession requireOwned(String threadId, Long userId) {
        Long ownerId = requireUserId(userId);
        String id = requireThreadId(threadId);
        AgentSession session = sessionMapper.selectBySessionId(id);
        ThrowUtils.throwIf(session == null || !ownerId.equals(session.getUserId()),
                ErrorCode.NO_AUTH_ERROR, "无权访问该会话");
        return session;
    }

    private Long requireUserId(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return userId;
    }

    private String requireThreadId(String threadId) {
        ThrowUtils.throwIf(threadId == null || threadId.isBlank(),
                ErrorCode.PARAMS_ERROR, "会话标识不能为空");
        String id = threadId.trim();
        // 控制字符与空白会让会话标识在日志、Redis 键与 SQL 里表现不一致
        ThrowUtils.throwIf(id.length() > MAX_THREAD_ID_LENGTH
                        || id.chars().anyMatch(Character::isWhitespace)
                        || id.chars().anyMatch(Character::isISOControl),
                ErrorCode.PARAMS_ERROR, "会话标识格式不合法");
        return id;
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int maxLength = agentConstants.getHistoryTruncateLength();
        int reserveLength = agentConstants.getHistoryTruncateReserve();
        return content.length() > maxLength ? content.substring(0, reserveLength) + "..." : content;
    }
}
