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

        boolean resumable = runStateStore.isResumable(state) && vo.getPendingReviewCount() == 0;
        vo.setResumable(resumable);
        if (!resumable) {
            vo.setReason(reasonFor(state, vo.getPendingReviewCount()));
        }
        return vo;
    }

    /**
     * 校验一次恢复请求是否真的可以执行。
     *
     * <p>不在这里直接执行，是因为执行要交给 {@code ImageAgent}；
     * 这里只做「能不能恢复」的判定，避免恢复入口和模型入口各自判断。
     */
    public AgentRunState requireResumable(String threadId, Long userId, Long expectedVersion) {
        sessionGuard.requireOwned(threadId, userId);

        AgentRunState state = runStateStore.loadResumable(threadId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "该会话没有可安全续跑的执行状态，已暂停自动执行"));

        // 过期或重复的恢复请求：版本已经推进，不能再用旧版本续跑
        if (expectedVersion != null && !expectedVersion.equals(state.getStateVersion())) {
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR,
                    "恢复请求已过期，请刷新会话状态后重试");
        }

        int pending = actionLedger.countPendingReview(threadId, userId);
        ThrowUtils.throwIf(pending > 0, ErrorCode.OPERATION_ERROR,
                "存在结果未知的外部动作，请先核对后再继续");

        return state;
    }

    /**
     * 收敛上次执行遗留的「执行中」动作。可证明幂等的退回可重试，其余进入待核对。
     */
    public int reconcileBeforeRun(String threadId, Long userId) {
        return actionLedger.reconcileStaleExecuting(threadId, userId);
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

    public AgentRunStateStore store() {
        return runStateStore;
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
