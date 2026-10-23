package com.yuwen.visionspace.agent.recovery;

/**
 * 一次动作认领的结果。
 *
 * <p>调用方必须按 {@link Outcome} 分别处理，不能把「没抢到」一律当成「已完成」：
 * 那正是评审发现的缺陷——待核对的动作被当成已完成，模型据此继续产生副作用。
 *
 * <p>认领同时带上动作身份（会话、归属、动作标识）与 {@code token}：结算时整份
 * 交回台账，不需要调用方再拼一遍四个参数，也就不会出现「拿 A 的 token 去结算 B」
 * 这种靠肉眼才能发现的错配。
 */
public record AgentActionClaim(Outcome outcome, String threadId, Long userId,
                               String actionId, String token) {

    public enum Outcome {
        /** 本次认领成功，可以调用外部 */
        ACQUIRED,
        /** 有可靠完成证据：已确认完成，不得重放 */
        ALREADY_SUCCEEDED,
        /** 另一个执行方正在跑这个动作，不得并发执行 */
        IN_FLIGHT,
        /** 结果未知，必须由有权用户核对后才能继续 */
        NEEDS_REVIEW,
        /** 用户已核对为「未执行」并接受不再执行：终态，不得重新认领 */
        DISMISSED,
        /** 台账不可用：不能证明任何事，必须暂停 */
        UNAVAILABLE
    }

    public static AgentActionClaim acquired(String threadId, Long userId,
                                            String actionId, String token) {
        return new AgentActionClaim(Outcome.ACQUIRED, threadId, userId, actionId, token);
    }

    /** 没有抢到时的结果：没有 token，因此也不允许结算 */
    public static AgentActionClaim notAcquired(Outcome outcome, String threadId,
                                               Long userId, String actionId) {
        return new AgentActionClaim(outcome, threadId, userId, actionId, null);
    }

    public boolean acquired() {
        return outcome == Outcome.ACQUIRED;
    }
}
