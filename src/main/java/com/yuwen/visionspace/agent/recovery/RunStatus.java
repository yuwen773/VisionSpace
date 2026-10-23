package com.yuwen.visionspace.agent.recovery;

/**
 * 执行状态。区分「可以继续」「等待人工确认」「明确失败」「无法安全恢复」，
 * 不用一个笼统的失败态掩盖不同处置方式。
 */
public final class RunStatus {

    /** 正常推进中 */
    public static final String RUNNING = "RUNNING";

    /** 停在人工确认点，确认信息已持久化 */
    public static final String AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";

    /** 已正常完成 */
    public static final String COMPLETED = "COMPLETED";

    /** 明确失败，可安全重试 */
    public static final String FAILED = "FAILED";

    /** 结果未知或状态不可识别：明确暂停，等待有权用户核对，不自动续跑 */
    public static final String PAUSED_UNRECOVERABLE = "PAUSED_UNRECOVERABLE";

    private RunStatus() {
    }
}
