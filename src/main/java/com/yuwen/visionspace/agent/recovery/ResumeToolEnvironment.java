package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.agent.interceptor.TextMcpModelInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 续跑时的工具环境重建。
 *
 * <p>续跑不能一律用内置 agent：上一次执行如果用了 MCP 工具，恢复后模型会"少一只手"，
 * 要么卡住，要么改用别的方式完成同一件事——后者等于绕过了用户对 MCP 服务的撤销。
 *
 * <p>也不能沿用旧的工具回调：回调里带着解析时的授权，服务停用、删除或换归属之后
 * 它依然能连。因此只持久化服务 code，续跑时用<b>当前用户</b>重新回读并授权。
 *
 * <p>任何一个记录在案的服务不再可用，就拒绝续跑：少一个工具继续跑不是等价恢复。
 */
@Slf4j
@Component
public class ResumeToolEnvironment {

    private final TextMcpModelInterceptor mcpInterceptor;

    public ResumeToolEnvironment(TextMcpModelInterceptor mcpInterceptor) {
        this.mcpInterceptor = mcpInterceptor;
    }

    /**
     * 重建后的工具环境。
     *
     * @param callbacks 可用的 MCP 回调；为空表示本次续跑不需要 MCP
     * @param usesMcp   上次执行是否用过 MCP（决定要不要重建 MCP agent）
     */
    public record Environment(ToolCallback[] callbacks, boolean usesMcp) {
    }

    /**
     * 按上次记录的服务 code 重建工具环境。
     *
     * @throws ResumeToolUnavailableException 记录在案的服务已停用、撤销或删除
     */
    public Environment resolve(List<String> recordedServerCodes, Long userId) {
        if (recordedServerCodes == null || recordedServerCodes.isEmpty()) {
            return new Environment(new ToolCallback[0], false);
        }

        TextMcpModelInterceptor.McpResolution resolution =
                mcpInterceptor.resolveForCodes(recordedServerCodes, userId);
        if (resolution.serverCodes().size() != recordedServerCodes.size()) {
            log.warn("续跑所需的 MCP 服务已不可用, recorded={}, available={}",
                    recordedServerCodes.size(), resolution.serverCodes().size());
            throw new ResumeToolUnavailableException(
                    "上次执行使用的 MCP 服务已停用或撤销，无法继续，请重新发起请求");
        }
        return new Environment(resolution.callbacks(), true);
    }
}
