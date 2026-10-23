package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工具执行边界：每次工具调用都在台账里留下可核对的动作标识、状态和结果摘要。
 *
 * <p>三件事：
 * <ol>
 *   <li><b>已确认完成不重放</b>：动作已经是 {@code SUCCEEDED} 时直接返回既有结果，
 *       不再调用外部；</li>
 *   <li><b>结果未知不重试</b>：只有可证明幂等的工具在结果未知时才允许安全重试，
 *       其余进入待核对并暂停本次自动执行；</li>
 *   <li><b>审计不含正文</b>：只记录工具名、参数摘要和截断后的结果摘要。</li>
 * </ol>
 *
 * <p>这里不承诺外部动作恰好执行一次；它保证的是「不会因为重放而重复产生副作用」。
 */
@Slf4j
@Component
public class AgentActionInterceptor extends ToolInterceptor {

    /**
     * 可证明幂等的工具：同样的参数重复执行不会产生额外外部副作用。
     *
     * <p>搜索、分析、评估都是只读调用；生成类工具会产生新的外部资源，不能证明幂等，
     * 因此默认不在这个集合里——结果未知时一律进入待核对。
     */
    static final Set<String> IDEMPOTENT_TOOLS = Set.of(            "searchContentImages",
            "searchSimilarImages",
            "searchIllustrations",
            "analyzeImageFeatures",
            "evaluateImageQuality"
    );

    private final AgentActionLedger ledger;

    public AgentActionInterceptor(AgentActionLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public String getName() {
        return "agent_action_ledger";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        RunnableConfig config = request.getExecutionContext()
                .map(ctx -> ctx.config())
                .orElse(null);
        String threadId = config == null ? null : config.threadId().orElse(null);
        Long userId = ownerUserId(config);

        // 没有可信归属时不写台账，也不阻止执行：归属判定由 AgentSessionGuard 在更外层完成
        if (threadId == null || userId == null) {
            return handler.call(request);
        }

        String toolName = request.getToolName();
        String actionId = AgentActionLedger.actionId(threadId, request.getToolCallId());
        boolean idempotent = isIdempotent(toolName);

        AgentActionClaim claim = ledger.claim(threadId, userId, actionId, toolName,
                request.getArguments(),
                idempotent ? AgentActionRecord.IDEMPOTENCY_PROVEN
                        : AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        if (!claim.acquired()) {
            return blocked(request, toolName, claim.outcome(), idempotent);
        }

        try {
            ToolCallResponse response = handler.call(request);
            boolean settled;
            if (response != null && response.isError()) {
                // 工具明确报错：外部没有成功，可以安全重试
                settled = ledger.markFailed(claim, safeSummary(response.getResult()));
            } else {
                settled = ledger.markSucceeded(claim,
                        safeSummary(response == null ? null : response.getResult()));
            }
            if (!settled) {
                // 认领已被别的执行方收敛（例如被判定为中断）：结果没有落进台账，
                // 下一次执行会把它当成结果未知，这里必须留痕而不是静默
                log.warn("动作结果未能写入台账（认领已失效）, tool={}, actionId={}",
                        toolName, actionId);
            }
            return response;
        } catch (RuntimeException e) {
            // 异常分不清「外部没做」还是「做了但没拿到结果」：按结果未知处理。
            // 只有可证明幂等的动作退回 FAILED 允许重试，其余进入待核对。
            ledger.markUnknown(claim, idempotent,
                    "执行异常: " + e.getClass().getSimpleName());
            if (!idempotent) {
                throw new PendingReviewException(toolName);
            }
            throw e;
        }
    }

    /**
     * 没抢到动作时的统一出口。
     *
     * <p>评审的缺陷正是这里：以前「没抢到」一律被说成「已确认完成」，于是待核对的动作
     * 和台账故障都被伪装成成功，模型据此继续推进。现在每种情况分开说，而且只有
     * {@code ALREADY_SUCCEEDED} 才能用「已完成」的说法。
     */
    private ToolCallResponse blocked(ToolCallRequest request, String toolName,
                                     AgentActionClaim.Outcome outcome, boolean idempotent) {
        String toolCallId = request.getToolCallId();
        return switch (outcome) {
            case ALREADY_SUCCEEDED -> {
                log.info("动作已确认完成，跳过重复执行, tool={}", toolName);
                yield ToolCallResponse.of(toolCallId, toolName, "该动作已确认完成，不再重复执行。");
            }
            case IN_FLIGHT -> {
                log.warn("动作正在被另一个执行方处理，拒绝并发执行, tool={}", toolName);
                // 不能只回一条「正在执行中」就放行：模型会换个 toolCallId 再来一次，
                // 于是同一个外部副作用产生两次。没有完成证据就不能继续，
                // 这与「结果未知」是同一件事——差别只在证据还没到。
                yield needsReview(toolCallId, toolName, idempotent,
                        "该动作正在执行中，本次不会重复执行。请稍后刷新结果。");
            }
            case NEEDS_REVIEW -> {
                log.warn("动作结果未知，需要人工核对后才能继续, tool={}", toolName);
                yield needsReview(toolCallId, toolName, idempotent);
            }
            case DISMISSED -> {
                log.info("动作已被用户核对为未执行，不再自动执行, tool={}", toolName);
                yield ToolCallResponse.error(toolCallId, toolName,
                        "该动作已被核对为未执行，不会自动重试。如需重新生成，请重新发起请求。");
            }
            case UNAVAILABLE -> {
                log.error("动作台账不可用，无法确认动作是否已执行, tool={}", toolName);
                yield ToolCallResponse.error(toolCallId, toolName,
                        "动作台账暂不可用，无法确认该动作是否已执行，已暂停以免重复产生副作用。请稍后重试。");
            }
            // ACQUIRED 走不到这里；出现说明调用方逻辑错误，按不可证明处理
            case ACQUIRED -> ToolCallResponse.error(toolCallId, toolName,
                    "动作状态异常，已暂停执行。");
        };
    }

    /**
     * 待核对的动作：不能继续自动执行。
     *
     * <p>不可证明幂等的动作必须抛 {@link PendingReviewException} 中断本次运行，
     * 否则模型会收到一条「工具出错」的消息后换个方式继续做副作用。
     * 幂等动作没有副作用，回一条错误信息即可。
     */
    private ToolCallResponse needsReview(String toolCallId, String toolName, boolean idempotent) {
        return needsReview(toolCallId, toolName, idempotent,
                "该动作上次执行结果未知，请先核对后再继续。");
    }

    /**
     * @param idempotentMessage 幂等工具回到模型的那句提示；非幂等工具会直接中断，用不到它
     */
    private ToolCallResponse needsReview(String toolCallId, String toolName, boolean idempotent,
                                         String idempotentMessage) {
        if (!idempotent) {
            throw new PendingReviewException(toolName);
        }
        return ToolCallResponse.error(toolCallId, toolName, idempotentMessage);
    }

    /** 异常策略（config 包）也要按同一份幂等集合判定重抛与否，保持公开 */
    public static boolean isIdempotent(String toolName) {
        return toolName != null && IDEMPOTENT_TOOLS.contains(toolName);
    }

    private Long ownerUserId(RunnableConfig config) {
        if (config == null) {
            return null;
        }
        return config.metadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY)
                .map(value -> value instanceof Number number ? number.longValue()
                        : Long.valueOf(String.valueOf(value)))
                .orElse(null);
    }

    /**
     * 结果摘要：只记录「有没有结果、多大」，不记录内容本身。
     *
     * <p>工具返回里可能包含用户提示词、图片描述或外部服务的原始响应，
     * 写进审计列就是泄露。核对需要的是「这个动作产出了东西」，不是产出什么。
     */
    private String safeSummary(String result) {
        if (result == null) {
            return null;
        }
        return "结果长度 " + result.length();
    }
}
