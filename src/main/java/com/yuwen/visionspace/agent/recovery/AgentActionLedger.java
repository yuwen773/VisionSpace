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
import java.util.UUID;

/**
 * 外部动作台账：让「这个动作到底做没做」可以核对，而不是靠重放去猜。
 *
 * <p>状态机（只允许这些推进）：
 * <pre>
 *   (无) --认领--> EXECUTING
 *   EXECUTING --成功--> SUCCEEDED
 *   EXECUTING --明确失败--> FAILED
 *   EXECUTING --结果未知且不能安全重试--> UNKNOWN_NEEDS_REVIEW
 *   EXECUTING --结果未知但可证明幂等--> FAILED（允许安全重试）
 *   UNKNOWN_NEEDS_REVIEW --人工确认已发生--> SUCCEEDED
 *   UNKNOWN_NEEDS_REVIEW --人工确认未发生--> DISMISSED
 *   FAILED --重新认领--> EXECUTING
 * </pre>
 *
 * <p><b>执行互斥靠原子认领，不靠等待时间。</b>{@link #claim} 用一条
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 完成「判定 + 占用」，只有尚不存在
 * 或已明确失败的动作能被认领；执行中、待核对、已完成一律影响 0 行。
 * 认领成功会拿到 {@code claimToken}，后续推进状态必须带同一个 token。
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
     * 原子认领一个动作。
     *
     * <p>返回的 {@link AgentActionClaim.Outcome} 必须被分别处理：
     * 只有 {@code ALREADY_SUCCEEDED} 能报告「已完成」，其余都要暂停或拒绝。
     *
     * <p>台账本身不可用时返回 {@code UNAVAILABLE}：这时什么都证明不了，
     * 既不能放行外部执行，也不能声称动作已完成。
     */
    public AgentActionClaim claim(String threadId, Long userId, String actionId, String toolName,
                                  String arguments, String idempotency) {
        String token = UUID.randomUUID().toString();
        String normalizedIdempotency = idempotency == null
                ? AgentActionRecord.IDEMPOTENCY_NOT_PROVEN : idempotency;
        try {
            mapper.claim(threadId, userId, actionId, toolName,
                    argumentsDigest(arguments), normalizedIdempotency, token);
        } catch (Exception e) {
            log.warn("动作认领失败，台账不可用, tool={}", toolName);
            return AgentActionClaim.notAcquired(AgentActionClaim.Outcome.UNAVAILABLE,
                    threadId, userId, actionId);
        }

        // 不能用「影响行数」判断是否抢到：MySQL Connector/J 默认按匹配行数返回，
        // 「命中但值没变」也会是非零，四个并发认领会全部以为自己是赢家
        // （真实 MySQL 验收用例 concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn 复现过）。
        // 唯一可靠的证据是读回的 claimToken 是不是本次生成的。
        AgentActionRecord record;
        try {
            record = mapper.selectForClaim(threadId, userId, actionId);
        } catch (Exception e) {
            log.warn("动作状态读取失败，台账不可用, tool={}", toolName);
            return AgentActionClaim.notAcquired(AgentActionClaim.Outcome.UNAVAILABLE,
                    threadId, userId, actionId);
        }
        if (record == null) {
            // 认领没生效却读不到行：无法判断，按不可用处理
            return AgentActionClaim.notAcquired(AgentActionClaim.Outcome.UNAVAILABLE,
                    threadId, userId, actionId);
        }
        if (AgentActionRecord.STATUS_EXECUTING.equals(record.getStatus())
                && token.equals(record.getClaimToken())) {
            return AgentActionClaim.acquired(threadId, userId, actionId, token);
        }
        // 没抢到：按真实状态区分「已完成 / 执行中 / 待核对 / 已放弃」，不一律当成已完成
        return AgentActionClaim.notAcquired(outcomeOf(record.getStatus()),
                threadId, userId, actionId);
    }

    private AgentActionClaim.Outcome outcomeOf(String status) {
        if (AgentActionRecord.STATUS_SUCCEEDED.equals(status)) {
            return AgentActionClaim.Outcome.ALREADY_SUCCEEDED;
        }
        if (AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW.equals(status)) {
            return AgentActionClaim.Outcome.NEEDS_REVIEW;
        }
        if (AgentActionRecord.STATUS_DISMISSED.equals(status)) {
            return AgentActionClaim.Outcome.DISMISSED;
        }
        if (AgentActionRecord.STATUS_EXECUTING.equals(status)) {
            return AgentActionClaim.Outcome.IN_FLIGHT;
        }
        // FAILED 本应被上面的 claim 重新认领成功；走到这里说明存在并发，
        // 另一次调用刚刚改了状态，按「执行中」处理更保守
        return AgentActionClaim.Outcome.IN_FLIGHT;
    }

    /** 由认领者标记成功；token 不符时返回 false，不会把别人的结果覆盖掉 */
    public boolean markSucceeded(AgentActionClaim claim, String resultSummary) {
        return settle(claim, AgentActionRecord.STATUS_SUCCEEDED, resultSummary);
    }

    /** 明确失败：外部没有成功，可以安全重试 */
    public boolean markFailed(AgentActionClaim claim, String resultSummary) {
        return settle(claim, AgentActionRecord.STATUS_FAILED, resultSummary);
    }

    /**
     * 结果未知：调用可能已经发生，本地没能提交结果。
     *
     * <p>只有可证明幂等的动作才允许退回 {@code FAILED} 走安全重试；
     * 否则进入待核对，暂停自动执行。这是本工单对「恰好一次」的替代方案：
     * 不做通用承诺，只保证不会盲目重放。
     */
    public boolean markUnknown(AgentActionClaim claim, boolean idempotencyProven,
                               String resultSummary) {
        String status = idempotencyProven
                ? AgentActionRecord.STATUS_FAILED
                : AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW;
        boolean settled = settle(claim, status, resultSummary);
        if (!idempotencyProven) {
            log.warn("外部动作结果未知且不能安全重试，进入待核对, actionId={}",
                    claim == null ? null : claim.actionId());
        }
        return settled;
    }

    private boolean settle(AgentActionClaim claim, String status, String resultSummary) {
        if (claim == null || claim.token() == null) {
            // 没有认领凭据就不能推进状态：否则任何调用方都能伪造完成
            log.warn("缺少认领凭据，拒绝推进动作状态, actionId={}",
                    claim == null ? null : claim.actionId());
            return false;
        }
        return mapper.settleClaimed(claim.threadId(), claim.userId(), claim.actionId(),
                claim.token(), status, truncate(resultSummary)) == 1;
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
     * <p><b>宽限期只用于判断「还活着吗」，不用于判断「安不安全」。</b>执行互斥由
     * {@link #claim} 保证：即便这里不收敛，执行中的动作也不会被再次认领。宽限期
     * 只决定何时把一条没人再推进的记录改判，避免把另一个实例正在跑的动作提前
     * 标成待核对。
     *
     * @param staleBefore 只有最后更新早于此时刻的 {@code EXECUTING} 才视为遗留
     * @return 收敛的动作数
     */
    public int reconcileStaleExecuting(String threadId, Long userId, Instant staleBefore) {
        if (staleBefore == null) {
            throw new IllegalArgumentException("缺少陈旧判定时间，无法收敛中断的动作");
        }
        int reconciled = mapper.reconcileStaleExecuting(threadId, userId,
                Date.from(staleBefore), "上次执行中断，结果未确认");
        if (reconciled > 0) {
            log.warn("收敛了 {} 个中断的外部动作，需核对是否已产生副作用", reconciled);
        }
        return reconciled;
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
