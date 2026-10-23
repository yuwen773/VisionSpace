package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.agent.RecoveryStateVO;
import com.yuwen.visionspace.model.dto.agent.RecoveryActionVO;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import com.yuwen.visionspace.model.entity.AgentRunState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 受限恢复流程。
 *
 * <p>所有入口先过 {@link AgentSessionGuard}：会话归属只认服务端身份，
 * 其他用户和没有成员关系的平台管理员都不能代为续跑私人会话。
 *
 * <p>恢复本身也受限：
 * <ul>
 *   <li>没有可续跑的完整状态时明确报告「不可恢复」，不回退到消息历史；</li>
 *   <li>存在待核对动作时拒绝自动续跑，必须先由有权用户核对；</li>
 *   <li>带 {@code expectedVersion} 的请求在版本已推进时被拒绝，重复或过期的
 *       恢复请求不会产生第二次工具执行。</li>
 * </ul>
 */
@Slf4j
@Service
public class AgentRecoveryService {

    /**
     * 「执行中」动作的陈旧宽限：超过这个时长没有更新才认为是上次中断遗留。
     * 单次工具调用远短于它，正常执行中的动作不会被误判。
     */
    private static final Duration STALE_ACTION_GRACE = Duration.ofMinutes(10);

    /**
     * RUNNING 状态的失活判定阈值：状态行超过这个时长没有任何推进，且没有
     * 有效租约，就认为执行进程已消失（硬崩溃不会经过 catch 改写状态，
     * 持久行会永远停在 RUNNING）。阈值与 {@link #STALE_ACTION_GRACE} 一致：
     * 检查点保存节奏远快于它，正常运行不会被误判为失活。
     */
    private static final Duration RUN_LIVENESS_THRESHOLD = Duration.ofMinutes(10);

    private final AgentSessionGuard sessionGuard;
    private final AgentRunStateStore runStateStore;
    private final AgentActionLedger actionLedger;

    public AgentRecoveryService(AgentSessionGuard sessionGuard,
                                AgentRunStateStore runStateStore,
                                AgentActionLedger actionLedger) {
        this.sessionGuard = sessionGuard;
        this.runStateStore = runStateStore;
        this.actionLedger = actionLedger;
    }

    /**
     * 查询恢复状态：明确区分「可续跑」「等待确认」「待核对」「不可恢复」。
     */
    public RecoveryStateVO describe(String threadId, Long userId) {
        sessionGuard.requireOwned(threadId, userId);
        Optional<AgentRunState> display = runStateStore.loadForDisplay(threadId, userId);
        List<AgentActionRecord> actions = actionLedger.list(threadId, userId);

        RecoveryStateVO vo = new RecoveryStateVO();
        vo.setThreadId(threadId);
        vo.setActions(actions.stream().map(this::toActionVO).toList());
        vo.setPendingReviewCount(actionLedger.countPendingReview(threadId, userId));

        if (display.isEmpty()) {
            // 没有执行状态记录：只有历史消息可看，不能续跑
            vo.setRunStatus(RunStatus.PAUSED_UNRECOVERABLE);
            vo.setResumable(false);
            vo.setReason("没有可续跑的执行状态，只能查看历史消息");
            return vo;
        }

        AgentRunState state = display.get();
        vo.setRunStatus(state.getRunStatus());
        vo.setStateVersion(state.getStateVersion());
        vo.setLegacyArchiveOnly(Integer.valueOf(1).equals(state.getLegacyArchiveOnly()));
        vo.setBackupError(state.getBackupError());
        vo.setBackupAttempts(state.getBackupAttempts());
        vo.setPendingConfirmation(state.getPendingConfirmation());
        // RUNNING 必须区分「仍有执行在推进」与「进程失活遗留」：硬崩溃不经过
        // catch，持久行会停在 RUNNING。前者不打扰用户，后者必须给出恢复入口。
        vo.setActiveExecution(isActivelyExecuting(state));

        boolean resumable = runStateStore.isResumable(state) && vo.getPendingReviewCount() == 0;
        vo.setResumable(resumable);
        if (!resumable) {
            vo.setReason(reasonFor(state, vo.getPendingReviewCount()));
        }
        return vo;
    }

    /**
     * 判断 RUNNING 状态是否仍有活跃执行。
     *
     * <p>两个信号，任一成立即视为活跃：
     * <ul>
     *   <li>有效租约未过期——有执行方正在跑（续跑认领会持有租约）；</li>
     *   <li>状态行近期有推进——检查点/状态写入会刷新 updateTime。</li>
     * </ul>
     * 非 RUNNING 状态没有「活跃」概念，一律返回 false。
     */
    private boolean isActivelyExecuting(AgentRunState state) {
        if (!RunStatus.RUNNING.equals(state.getRunStatus())) {
            return false;
        }
        Instant now = Instant.now();
        if (state.getResumeLeaseUntil() != null
                && state.getResumeLeaseUntil().toInstant().isAfter(now)) {
            return true;
        }
        return state.getUpdateTime() != null
                && state.getUpdateTime().toInstant().isAfter(now.minus(RUN_LIVENESS_THRESHOLD));
    }

    /**
     * 校验一次恢复请求是否真的可以执行。
     *
     * <p>不在这里直接执行，是因为执行要交给 {@code ImageAgent}；
     * 这里只做「能不能恢复」的判定，避免恢复入口和模型入口各自判断。
     */
    public ResumeClaim requireResumable(String threadId, Long userId, Long expectedVersion) {
        sessionGuard.requireOwned(threadId, userId);

        AgentRunState state = runStateStore.loadResumable(threadId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "该会话没有可安全续跑的执行状态，已暂停自动执行"));

        // 过期或重复的恢复请求：版本已经推进，不能再用旧版本续跑
        ThrowUtils.throwIf(expectedVersion != null
                        && !expectedVersion.equals(state.getStateVersion()),
                ErrorCode.OPERATION_ERROR, "恢复请求已过期，请刷新会话状态后重试");

        // 与其它入口同一道闸门，不在这里重复一遍判断逻辑
        requireNoPendingReview(threadId, userId);

        // 认领这次续跑：版本 + 租约，两个条件都满足才执行，并发的第二个请求在这里落败
        String leaseToken = runStateStore.claimForResume(threadId, userId, state.getStateVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "该会话正在被另一个请求继续执行，请刷新状态后重试"));

        return new ResumeClaim(state, leaseToken);
    }

    /**
     * 一次成功的续跑认领：状态快照 + 本次租约凭据。
     *
     * <p>租约必须在执行结束后释放，否则用户要等到租约到期才能再次续跑。
     */
    public record ResumeClaim(AgentRunState state, String leaseToken) {
    }

    /**
     * 所有执行入口共用的暂停闸门：存在结果未知的动作时，任何入口都不得继续跑。
     *
     * <p>普通对话、流式对话、用户反馈、续跑都必须走这里。只在续跑入口判断是不够的：
     * 用户完全可以再发一条消息让模型重新调用同一个工具，绕开暂停。
     */
    public void requireNoPendingReview(String threadId, Long userId) {
        if (threadId == null || userId == null) {
            return;
        }
        int pending = actionLedger.countPendingReview(threadId, userId);
        ThrowUtils.throwIf(pending > 0, ErrorCode.OPERATION_ERROR,
                "存在结果未知的外部动作，请先核对后再继续");
    }

    /** 释放续跑租约：执行结束后调用，失败也不影响本次结果 */
    public void releaseResumeLease(String threadId, Long userId, String leaseToken) {
        runStateStore.releaseResumeLease(threadId, userId, leaseToken);
    }

    /**
     * 收敛上次执行遗留的「执行中」动作。可证明幂等的退回可重试，其余进入待核对。
     *
     * <p>只处理超过 {@link #STALE_ACTION_GRACE} 没有更新的记录：正在被另一个
     * 实例执行的动作会持续刷新更新时间，不能被这里判成中断而放行重放。
     */
    public int reconcileBeforeRun(String threadId, Long userId) {
        Instant staleBefore = Instant.now().minus(STALE_ACTION_GRACE);
        return actionLedger.reconcileStaleExecuting(threadId, userId, staleBefore);
    }

    /**
     * 人工核对：确认外部动作已经发生，不再重放。
     */
    public RecoveryStateVO confirmAction(String threadId, Long userId, String actionId, String note) {
        sessionGuard.requireOwned(threadId, userId);
        boolean changed = actionLedger.confirmCompleted(threadId, userId, actionId, note);
        ThrowUtils.throwIf(!changed, ErrorCode.OPERATION_ERROR,
                "该动作不在待核对状态，或已被处理");
        return describe(threadId, userId);
    }

    /**
     * 人工核对：确认外部动作没有发生（或接受不再执行）。
     */
    public RecoveryStateVO dismissAction(String threadId, Long userId, String actionId, String note) {
        sessionGuard.requireOwned(threadId, userId);
        boolean changed = actionLedger.dismissUnknown(threadId, userId, actionId, note);
        ThrowUtils.throwIf(!changed, ErrorCode.OPERATION_ERROR,
                "该动作不在待核对状态，或已被处理");
        return describe(threadId, userId);
    }

    private String reasonFor(AgentRunState state, int pendingReview) {
        if (pendingReview > 0) {
            return "存在结果未知的外部动作，需要先核对";
        }
        if (Integer.valueOf(1).equals(state.getLegacyArchiveOnly())) {
            return "只有旧消息归档，不能证明可以安全续跑";
        }
        if (AgentStateFormat.fromCode(state.getStateFormat()) == null) {
            return "执行状态格式无法识别，已暂停自动执行";
        }
        if (state.getNextNodeId() == null) {
            return "执行状态缺少恢复点，已暂停自动执行";
        }
        return "当前状态不可续跑";
    }

    private RecoveryActionVO toActionVO(AgentActionRecord record) {
        RecoveryActionVO vo = new RecoveryActionVO();
        vo.setActionId(record.getActionId());
        vo.setToolName(record.getToolName());
        vo.setStatus(record.getStatus());
        vo.setAttempt(record.getAttempt());
        vo.setResultSummary(record.getResultSummary());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }
}
