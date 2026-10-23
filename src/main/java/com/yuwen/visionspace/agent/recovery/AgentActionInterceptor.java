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
    static final Set<String> IDEMPOTENT_TOOLS = Set.of(
            "searchContentImages",
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

        boolean allowed;
        try {
            allowed = ledger.begin(threadId, userId, actionId, toolName, request.getArguments(),
                    idempotent ? AgentActionRecord.IDEMPOTENCY_PROVEN
                            : AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        } catch (Exception e) {
            // 台账不可用时按「不可证明」处理，不因此放开外部执行
            log.warn("登记动作台账失败，按未证明幂等处理, tool={}", toolName);
            allowed = false;
        }

        if (!allowed) {
            log.info("动作已确认完成，跳过重复执行, tool={}", toolName);
            return ToolCallResponse.of(request.getToolCallId(), toolName,
                    "该动作已确认完成，不再重复执行。");
        }

        try {
            ToolCallResponse response = handler.call(request);
            if (response != null && response.isError()) {
                // 工具明确报错：外部没有成功，可以安全重试
                ledger.markFailed(threadId, userId, actionId, safeSummary(response.getResult()));
            } else {
                ledger.markSucceeded(threadId, userId, actionId,
                        safeSummary(response == null ? null : response.getResult()));
            }
            return response;
        } catch (RuntimeException e) {
            // 异常分不清「外部没做」还是「做了但没拿到结果」：按结果未知处理
            ledger.markUnknown(threadId, userId, actionId, idempotent,
                    "执行异常: " + e.getClass().getSimpleName());
            if (!idempotent) {
                throw new PendingReviewException(toolName);
            }
            throw e;
        }
    }

    static boolean isIdempotent(String toolName) {
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

    /** 结果摘要：只留很短的前缀，避免把返回正文写进审计 */
    private String safeSummary(String result) {
        if (result == null) {
            return null;
        }
        String singleLine = result.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 80 ? singleLine.substring(0, 80) + "..." : singleLine;
    }
}
