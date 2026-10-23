package com.yuwen.visionspace.agent.recovery;

/**
 * 执行状态的格式版本。
 *
 * <p>格式写在每一份状态里，恢复时按它判断能不能安全续跑。不能识别的格式
 * 一律不重建、不猜测：宁可明确暂停，也不能用消息历史伪造完整恢复。
 */
public enum AgentStateFormat {

    /**
     * 本工单引入的完整状态：包含 messages、迭代计数、阶段、待确认信息与恢复点。
     */
    V2_FULL("v2-full", true),

    /**
     * 旧轻量归档（仅消息）。可以查看历史，但不足以证明能安全续跑。
     */
    V1_LIGHTWEIGHT("v1-lightweight", false);

    private final String code;
    private final boolean resumable;

    AgentStateFormat(String code, boolean resumable) {
        this.code = code;
        this.resumable = resumable;
    }

    public String code() {
        return code;
    }

    /** 只有完整状态才允许从它继续执行 */
    public boolean resumable() {
        return resumable;
    }

    /**
     * 解析存储中的格式标识。未知格式返回空，调用方必须按「不可恢复」处理。
     */
    public static AgentStateFormat fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AgentStateFormat format : values()) {
            if (format.code.equals(code)) {
                return format;
            }
        }
        return null;
    }
}
