package com.yuwen.visionspace.agent.recovery;

/**
 * 外部动作结果未知且不能安全重试：本次执行必须暂停，等待有权用户核对。
 *
 * <p>与「明确失败」区分：明确失败可以安全重试，这里不能。
 */
public class PendingReviewException extends RuntimeException {

    private final String toolName;

    public PendingReviewException(String toolName) {
        super("工具 " + toolName + " 的执行结果未知，已暂停并等待核对");
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
