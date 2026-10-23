package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话恢复状态。
 *
 * <p>UI 据此区分「可以继续」「等待人工确认」「结果未知待核对」「明确不可恢复」，
 * 而不是把所有异常都显示成同一种失败。
 */
@Data
public class RecoveryStateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String threadId;

    /** RUNNING / AWAITING_CONFIRMATION / COMPLETED / FAILED / PAUSED_UNRECOVERABLE */
    private String runStatus;

    /** 是否可以从完整状态继续执行 */
    private boolean resumable;

    /** 不可续跑的原因（面向用户的固定文案，不含正文） */
    private String reason;

    /** 状态版本，续跑请求带回来可拒绝过期请求 */
    private Long stateVersion;

    /** 1 = 只有旧轻量归档，可查看历史但不可续跑 */
    private boolean legacyArchiveOnly;

    /** 最近一次备份失败原因（不含正文/秘密） */
    private String backupError;

    private Integer backupAttempts;

    /** 待人工确认信息（已脱敏） */
    private String pendingConfirmation;

    /** 待核对动作数 */
    private int pendingReviewCount;

    /** RUNNING 状态下是否仍有活跃执行在推进；false 表示进程失活遗留，可安全恢复 */
    private boolean activeExecution;

    /** 动作台账 */
    private List<RecoveryActionVO> actions;
}
