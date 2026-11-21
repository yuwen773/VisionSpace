package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;

import java.util.List;
import java.util.Set;

/**
 * 外部动作政策：一个工具的结果属于哪种情况，以及每种情况对用户怎么说。
 *
 * <p>ADR-0002 的核心规则只有一句：外部动作结果不明时不得重放。此前它被拆成
 * 五份各自维护的判断——拦截器里的幂等集合、异常策略里的同一份集合、
 * 台账的 {@code markUnknown} 分支、停顿时各处手写的文案、以及两个各自构造的
 * 确认钩子。集合一旦漂移，同一个工具就会在两条路径上得到不同结论。
 *
 * <p>现在所有入口都问这里：
 * <ul>
 *   <li>{@link AgentActionInterceptor} 用它判断结果未知时能否安全重试；</li>
 *   <li>{@code AgentToolExceptionPolicy} 用它决定异常是否重抛；</li>
 *   <li>{@link AgentActionLedger} 按它返回的 {@link Outcome} 落状态；</li>
 *   <li>暂停与人工确认的措辞由这里统一给出。</li>
 * </ul>
 *
 * <p>做成静态工具是刻意的：异常策略在构建 Agent 时以方法引用传入，无法注入 Bean；
 * 政策本身没有状态，也不需要 Spring 生命周期。
 */
public final class ExternalActionPolicy {

    /** 一次外部动作的结果，从未知出发的三种去向。 */
    public enum Outcome {
        /** 已确认完成：不重放。 */
        CONFIRMED,
        /** 结果未知但可证明幂等：退回可重试。 */
        SAFE_TO_RETRY,
        /** 结果未知且不能证明幂等：进入待核对，暂停自动执行。 */
        NEEDS_REVIEW
    }

    /**
     * 可证明幂等的工具：同样的参数重复执行不会产生额外外部副作用。
     *
     * <p>搜索、分析、评估都是只读调用；生成类工具会产生新的外部资源，不能证明幂等，
     * 因此默认不在这个集合里——结果未知时一律进入待核对。
     *
     * <p>{@code searchInternalImages} 曾因登记遗漏被当成非幂等工具：结果是同样的
     * 只读检索，却被判成待核对并中断整轮执行。新工具登记到这里即可，不需要改别的判断。
     */
    private static final Set<String> IDEMPOTENT_TOOLS = Set.of(
            "searchContentImages",
            "searchSimilarImages",
            "searchIllustrations",
            "searchInternalImages",
            "analyzeImageFeatures",
            "evaluateImageQuality"
    );

    /** 结果未知、已暂停时给用户看的统一说明。 */
    private static final String PAUSED_MESSAGE = "该步骤的外部执行结果未知，已暂停，请核对后再继续";

    private ExternalActionPolicy() {
    }

    /** 结果未知时，这个动作能不能安全重试。 */
    public static Outcome unknownOutcome(String toolName) {
        return canSafelyRetry(toolName) ? Outcome.SAFE_TO_RETRY : Outcome.NEEDS_REVIEW;
    }

    public static boolean canSafelyRetry(String toolName) {
        return toolName != null && IDEMPOTENT_TOOLS.contains(toolName);
    }

    /**
     * 结果未知且不能安全重试：中断本次执行，交由入口暂停。
     *
     * <p>抛异常而不是返回错误响应：只回一条「工具出错」，模型会换个方式继续做同一个
     * 副作用，暂停必须发生在整轮执行外面。
     */
    public static PendingReviewException pauseForReview(String toolName) {
        return new PendingReviewException(toolName);
    }

    /** 面向用户：外部动作结果未知、已暂停。 */
    public static String pausedMessage() {
        return PAUSED_MESSAGE;
    }

    /** 面向用户：具体工具的暂停说明（流式入口按条呈现）。 */
    public static String pausedMessage(String toolName) {
        return "工具 " + toolName + " 的执行结果未知，已暂停自动执行。请核对后再继续。";
    }

    /**
     * 人工确认点的措辞：工具名 + 评估摘要。
     *
     * <p>流式入口的确认消息与同步入口的确认响应都由这里拼接，两处不再各写一份
     * 格式稍有不同的文本。
     */
    public static String confirmationText(List<InterruptionMetadata.ToolFeedback> feedbacks) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (InterruptionMetadata.ToolFeedback feedback : feedbacks) {
            sb.append("工具: ").append(feedback.getName()).append("\n");
            sb.append("评估: ").append(feedback.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
