package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用真实的状态机语义模拟 {@link AgentActionRecordMapper}。
 *
 * <p>Mockito 无法表达「同一行只允许一个调用方认领成功」，而这条原子性正是
 * 本工单要证明的东西。这里按 MySQL 的语义实现：
 * <ul>
 *   <li>{@code claim} 是条件更新：只有不存在或已是 {@code FAILED} 时才生效；</li>
 *   <li>返回受影响行数，0 表示没抢到；</li>
 *   <li>{@code settleClaimed} 只在 token 与状态都匹配时生效；</li>
 *   <li>所有方法在同一个监视器下执行，等价于单行行锁。</li>
 * </ul>
 */
final class InMemoryActionRecordMapper {

    private final Map<String, AgentActionRecord> rows = new ConcurrentHashMap<>();

    AgentActionRecordMapper proxy() {
        InvocationHandler handler = (proxy, method, args) -> dispatch(method, args);
        return (AgentActionRecordMapper) Proxy.newProxyInstance(
                AgentActionRecordMapper.class.getClassLoader(),
                new Class<?>[]{AgentActionRecordMapper.class}, handler);
    }

    private synchronized Object dispatch(Method method, Object[] args) {
        return switch (method.getName()) {
            case "claim" -> claim(args);
            case "selectForClaim" -> copy(rows.get(key(args[0], args[1], args[2])));
            case "selectByThread" -> list(args);
            case "settleClaimed" -> settleClaimed(args);
            case "reconcileStaleExecuting" -> reconcile(args);
            case "confirmCompleted" -> confirm(args);
            case "dismissUnknown" -> dismiss(args);
            case "countPendingReview" -> countPending(args);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    /** 原子认领：不存在则创建，已是 FAILED 则重新置为 EXECUTING；其余一律 0 行 */
    private int claim(Object[] args) {
        String threadId = (String) args[0];
        Long userId = (Long) args[1];
        String actionId = (String) args[2];
        String key = key(threadId, userId, actionId);

        AgentActionRecord existing = rows.get(key);
        if (existing == null) {
            AgentActionRecord record = new AgentActionRecord();
            record.setThreadId(threadId);
            record.setUserId(userId);
            record.setActionId(actionId);
            record.setToolName((String) args[3]);
            record.setArgumentsDigest((String) args[4]);
            record.setStatus(AgentActionRecord.STATUS_EXECUTING);
            record.setIdempotency((String) args[5]);
            record.setClaimToken((String) args[6]);
            record.setAttempt(1);
            record.setUpdateTime(new Date());
            rows.put(key, record);
            return 1;
        }
        if (!AgentActionRecord.STATUS_FAILED.equals(existing.getStatus())) {
            return 0;
        }
        existing.setStatus(AgentActionRecord.STATUS_EXECUTING);
        existing.setIdempotency((String) args[5]);
        existing.setClaimToken((String) args[6]);
        existing.setAttempt(existing.getAttempt() == null ? 1 : existing.getAttempt() + 1);
        existing.setUpdateTime(new Date());
        return 2;
    }

    /** 只有当前认领者能落结果；token 不符或已不是 EXECUTING 都影响 0 行 */
    private int settleClaimed(Object[] args) {
        String key = key(args[0], args[1], args[2]);
        AgentActionRecord record = rows.get(key);
        if (record == null
                || !AgentActionRecord.STATUS_EXECUTING.equals(record.getStatus())
                || !java.util.Objects.equals(record.getClaimToken(), args[3])) {
            return 0;
        }
        record.setStatus((String) args[4]);
        record.setResultSummary((String) args[5]);
        record.setUpdateTime(new Date());
        return 1;
    }

    /** 收敛遗留 EXECUTING：只改「最后更新早于 staleBefore」的行，幂等与否决定落点 */
    private int reconcile(Object[] args) {
        Date staleBefore = (Date) args[2];
        int changed = 0;
        for (AgentActionRecord record : rows.values()) {
            if (!record.getThreadId().equals(args[0]) || !record.getUserId().equals(args[1])) {
                continue;
            }
            if (!AgentActionRecord.STATUS_EXECUTING.equals(record.getStatus())) {
                continue;
            }
            Date updated = record.getUpdateTime();
            if (updated != null && updated.after(staleBefore)) {
                continue;
            }
            record.setStatus(AgentActionRecord.IDEMPOTENCY_PROVEN.equals(record.getIdempotency())
                    ? AgentActionRecord.STATUS_FAILED
                    : AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW);
            record.setResultSummary((String) args[3]);
            record.setUpdateTime(new Date());
            changed++;
        }
        return changed;
    }

    private int confirm(Object[] args) {
        String key = key(args[0], args[1], args[2]);
        AgentActionRecord record = rows.get(key);
        if (record == null || !List.of(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW,
                AgentActionRecord.STATUS_FAILED).contains(record.getStatus())) {
            return 0;
        }
        record.setStatus(AgentActionRecord.STATUS_SUCCEEDED);
        record.setResultSummary((String) args[3]);
        return 1;
    }

    private int dismiss(Object[] args) {
        String key = key(args[0], args[1], args[2]);
        AgentActionRecord record = rows.get(key);
        if (record == null
                || !AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW.equals(record.getStatus())) {
            return 0;
        }
        record.setStatus(AgentActionRecord.STATUS_DISMISSED);
        record.setResultSummary((String) args[3]);
        return 1;
    }

    private int countPending(Object[] args) {
        return (int) rows.values().stream()
                .filter(r -> r.getThreadId().equals(args[0]) && r.getUserId().equals(args[1]))
                .filter(r -> AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW.equals(r.getStatus()))
                .count();
    }

    private List<AgentActionRecord> list(Object[] args) {
        List<AgentActionRecord> result = new ArrayList<>();
        for (AgentActionRecord record : rows.values()) {
            if (record.getThreadId().equals(args[0]) && record.getUserId().equals(args[1])) {
                result.add(copy(record));
            }
        }
        return result;
    }

    /** 直接读当前状态，供断言使用 */
    synchronized String status(String threadId, Long userId, String actionId) {
        AgentActionRecord record = rows.get(key(threadId, userId, actionId));
        return record == null ? null : record.getStatus();
    }

    /** 让某条动作看起来是「很久以前中断的」，用于收敛测试 */
    synchronized void ageRow(String threadId, Long userId, String actionId, Date updateTime) {
        AgentActionRecord record = rows.get(key(threadId, userId, actionId));
        if (record != null) {
            record.setUpdateTime(updateTime);
        }
    }

    private String key(Object threadId, Object userId, Object actionId) {
        return threadId + "|" + userId + "|" + actionId;
    }

    private AgentActionRecord copy(AgentActionRecord source) {
        if (source == null) {
            return null;
        }
        AgentActionRecord record = new AgentActionRecord();
        record.setThreadId(source.getThreadId());
        record.setUserId(source.getUserId());
        record.setActionId(source.getActionId());
        record.setToolName(source.getToolName());
        record.setArgumentsDigest(source.getArgumentsDigest());
        record.setStatus(source.getStatus());
        record.setIdempotency(source.getIdempotency());
        record.setClaimToken(source.getClaimToken());
        record.setAttempt(source.getAttempt());
        record.setResultSummary(source.getResultSummary());
        record.setUpdateTime(source.getUpdateTime());
        return record;
    }
}
