package com.yuwen.visionspace.agent.config;

import com.yuwen.visionspace.agent.mcp.McpToolOutcomeUnknownException;
import com.yuwen.visionspace.agent.recovery.ExternalActionPolicy;
import com.yuwen.visionspace.agent.tools.ImageGenerationException;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

/**
 * 工具异常策略：让「外部调用失败/结果未知」以异常形式到达动作台账。
 *
 * <p>框架默认的处理器是 {@code alwaysThrow=false}：工具抛出的异常会被
 * {@code AgentToolNode.executeSyncTool} 转成一条<b>非 error</b> 的工具响应，
 * 拦截器看到的是「工具正常返回了内容」，于是把一次超时的非幂等调用记成成功。
 * 只测工具本身发现不了，必须走框架的真实调用链才暴露。
 *
 * <p>这里把「结果是否不明」和「不明时能不能重试」都交给
 * {@link ExternalActionPolicy}，本类只决定异常是重抛还是交回框架：
 * <ul>
 *   <li><b>结果未知且分类为待核对</b>（传输超时、响应丢失、生成失败——外部可能已产生
 *       副作用）：原样重抛，动作台账进入待核对并暂停自动执行。覆盖所有非幂等工具，
 *       不针对某个错误文本或某类异常开特例；</li>
 *   <li><b>其余</b>（幂等只读工具的失败、明确业务失败）：交给框架默认行为转成
 *       文本/error 响应，模型可见、可换方式继续，不中断整轮执行。</li>
 * </ul>
 *
 * <p>明确业务失败（服务应答报错）与结果未知在上游已分开（{@code McpToolCallback}）：
 * 前者以普通异常抛出、被框架折成 error 响应记为可重试失败；只有「结果未知」
 * 会走到本处理器。
 */
public final class AgentToolExceptionPolicy {

    /**
     * 兜底处理器：与旧版 {@code alwaysThrow=false} 行为一致（转文本，不中断）。
     * 只处理「不需要重抛」的情况。
     */
    private static final ToolExecutionExceptionProcessor FALLBACK =
            DefaultToolExecutionExceptionProcessor.builder()
                    .alwaysThrow(false)
                    .build();

    private static final ToolExecutionExceptionProcessor PROCESSOR =
            AgentToolExceptionPolicy::process;

    private AgentToolExceptionPolicy() {
    }

    public static ToolExecutionExceptionProcessor processor() {
        return PROCESSOR;
    }

    private static String process(ToolExecutionException exception) {
        Throwable cause = exception.getCause();
        String toolName = toolName(exception);
        // 「这次结果是否不明」和「不明时能不能重试」都交给同一个分类器：
        // 这里只看它给出的结论，不再自己维护一份幂等名单
        boolean outcomeUnknown = cause instanceof ImageGenerationException
                || cause instanceof McpToolOutcomeUnknownException;
        if (outcomeUnknown
                && ExternalActionPolicy.unknownOutcome(toolName)
                        == ExternalActionPolicy.Outcome.NEEDS_REVIEW) {
            // 非幂等工具的结果未知：重抛后由台账进入待核对并暂停后续执行
            throw exception;
        }
        return FALLBACK.process(exception);
    }

    private static String toolName(ToolExecutionException exception) {
        ToolDefinition definition = exception.getToolDefinition();
        return definition == null ? null : definition.name();
    }
}
