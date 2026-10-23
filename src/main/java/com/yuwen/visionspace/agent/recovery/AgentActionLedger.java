package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * 外部动作台账：让「这个动作到底做没做」可以核对，而不是靠重放去猜。
 *
 * <p>状态机（只允许这些推进）：
 * <pre>
 *   (无) --register--> EXECUTING
 *   EXECUTING --成功--> SUCCEEDED
 *   EXECUTING --明确失败--> FAILED
 *   EXECUTING --结果未知且不能安全重试--> UNKNOWN_NEEDS_REVIEW
 *   EXECUTING --结果未知但可证明幂等--> FAILED（允许安全重试）
 *   UNKNOWN_NEEDS_REVIEW --人工确认已发生--> SUCCEEDED
 *   UNKNOWN_NEEDS_REVIEW --人工确认未发生--> FAILED
 * </pre>
 *
 * <p>{@code SUCCEEDED} 是终态，任何后续写入都不会把它打回可执行状态，
 * 因此已确认完成的动作不会被重放。
 */
@Slf4j
@Component
public class AgentActionLedger {

    private static final int SUMMARY_LIMIT = 200;

    private final AgentActionRecordMapper mapper;

    public AgentActionLedger(AgentActionRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 动作标识：会话 + 工具调用 id 派生，重试同一工具调用得到同一个标识。
     */
    public static String actionId(String threadId, String toolCallId) {
        return digest((threadId == null ? "" : threadId) + "|" + (toolCallId == null ? "" : toolCallId));
    }

    /**
     * 参数摘要：只存 sha-256，审计里不会出现正文。
     */
    public static String argumentsDigest(String arguments) {
        return arguments == null ? null : digest(arguments);
    }

    private static String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 登记动作开始。已存在的动作不会被重置，因此「已确认完成」不会被打回执行中。
     *
     * @return true 表示可以执行；false 表示该动作已经是终态，必须跳过
     */
    public boolean begin(String threadId, Long userId, String actionId, String toolName,
                         String arguments, String idempotency) {
        mapper.insertIfAbsent(threadId, userId, actionId, toolName, argumentsDigest(arguments),
                AgentActionRecord.STATUS_EXECUTING,
                idempotency == null ? AgentActionRecord.IDEMPOTENCY_NOT_PROVEN : idempotency);
        AgentActionRecord record = mapper.selectOne(threadId, userId, actionId);
        if (record == null) {
            return true;
        }
        // 只有「未完成」的动作可以继续执行；已确认完成的不重放
        return AgentActionRecord.STATUS_EXECUTING.equals(record.getStatus())
                || AgentActionRecord.STATUS_FAILED.equals(record.getStatus());
    }

    public void markSucceeded(String threadId, Long userId, String actionId, String resultSummary) {
        mapper.settle(threadId, userId, actionId, AgentActionRecord.STATUS_SUCCEEDED,
                truncate(resultSummary));
    }

    /** 明确失败：外部没有成功，可以安全重试 */
    public void markFailed(String threadId, Long userId, String actionId, String resultSummary) {
        mapper.settle(threadId, userId, actionId, AgentActionRecord.STATUS_FAILED,
                truncate(resultSummary));
    }

    /**
     * 结果未知：调用可能已经发生，本地没能提交结果。
     *
     * <p>只有可证明幂等的动作才允许退回 {@code FAILED} 走安全重试；
     * 否则进入待核对，暂停自动执行。这是本工单对「恰好一次」的替代方案：
     * 不做通用承诺，只保证不会盲目重放。
     */
    public void markUnknown(String threadId, Long userId, String actionId,
                            boolean idempotencyProven, String resultSummary) {
        String status = idempotencyProven
                ? AgentActionRecord.STATUS_FAILED
                : AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW;
        mapper.settle(threadId, userId, actionId, status, truncate(resultSummary));
        if (!idempotencyProven) {
            log.warn("外部动作结果未知且不能安全重试，进入待核对, actionId={}", actionId);
        }
    }

    /** 人工核对：确认外部已发生 */
    public boolean confirmCompleted(String threadId, Long userId, String actionId, String note) {
        return mapper.confirmCompleted(threadId, userId, actionId, truncate(note)) == 1;
    }

    /** 人工核对：确认外部未发生（或接受不再执行） */
    public boolean dismissUnknown(String threadId, Long userId, String actionId, String note) {
        return mapper.dismissUnknown(threadId, userId, actionId, truncate(note)) == 1;
    }

    /**
     * 新一次执行开始前收敛上次遗留的 {@code EXECUTING}。
     *
     * <p>上一次进程在执行中途消失时，动作可能已经发到外部而本地没来得及记结果。
     * 这正是「外部完成、本地结果未提交」的故障窗口：不能把它当成没发生而重放。
     * 可证明幂等的动作退回 {@code FAILED} 走安全重试，其余进入待核对。
     *
     * <p><b>只收敛陈旧的记录。</b>同一会话被另一个实例或并发请求正常执行时，
     * 它的 {@code EXECUTING} 是「正在进行」，不是「上次遗留」。用更新时间的
     * 宽限期区分两者：宽限期内的记录一律不动，否则会把正在执行的动作
     * 判成中断，进而让另一次执行重放它。
     *
     * @param staleBefore 只有最后更新早于此时刻的 {@code EXECUTING} 才视为遗留
     * @return 收敛的动作数
     */
    public int reconcileStaleExecuting(String threadId, Long userId, Instant staleBefore) {
        int reconciled = 0;
        for (AgentActionRecord record : mapper.selectByThread(threadId, userId)) {
            if (!AgentActionRecord.STATUS_EXECUTING.equals(record.getStatus())) {
                continue;
            }
            if (!isStale(record, staleBefore)) {
                continue;
            }
            boolean proven = AgentActionRecord.IDEMPOTENCY_PROVEN.equals(record.getIdempotency());
            String status = proven
                    ? AgentActionRecord.STATUS_FAILED
                    : AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW;
            if (mapper.settle(threadId, userId, record.getActionId(), status,
                    "上次执行中断，结果未确认") == 1) {
                reconciled++;
            }
        }
        return reconciled;
    }

    /** 更新时间为空时按陈旧处理：宁可让用户核对，也不当成「正在执行」而卡死 */
    private boolean isStale(AgentActionRecord record, Instant staleBefore) {
        Date updated = record.getUpdateTime();
        return updated == null || !updated.toInstant().isAfter(staleBefore);
    }

    public List<AgentActionRecord> list(String threadId, Long userId) {
        return mapper.selectByThread(threadId, userId);
    }

    public int countPendingReview(String threadId, Long userId) {
        return mapper.countPendingReview(threadId, userId);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > SUMMARY_LIMIT ? value.substring(0, SUMMARY_LIMIT) : value;
    }
}
