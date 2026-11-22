package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.yuwen.visionspace.agent.config.AgentConstants;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 迭代控制 Hook
 * 管理 Agent 的探索/生成阶段切换和迭代次数限制
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class IterationControlHook extends ModelHook {

    @Resource
    private AgentConstants agentConstants;

    @Override
    public String getName() {
        return "iteration_control";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 只有调用计数是真的：它在下面递增。探索/生成计数器从来没有任何写入者，
        // 两个「到达上限」分支因此永不触发——不保留读不到写入方的判断，
        // 需要阶段上限时应当先有真正的阶段所有者。
        int modelCalls = state.value("model_call_count", 0);

        // 硬上限：总模型调用次数
        if (modelCalls >= agentConstants.getMaxIterationCount()) {
            return stopWithMessage("已达最大迭代次数，为您展示当前最佳结果。");
        }

        // 正常递增调用计数
        return CompletableFuture.completedFuture(Map.of(
                "model_call_count", modelCalls + 1
        ));
    }

    private CompletableFuture<Map<String, Object>> stopWithMessage(String message) {
        return CompletableFuture.completedFuture(Map.of(
                "messages", List.of(new AssistantMessage(message))
        ));
    }
}
