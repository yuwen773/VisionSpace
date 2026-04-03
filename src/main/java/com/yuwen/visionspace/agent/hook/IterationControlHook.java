package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.model.AgentPhase;
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
        int exploreCount = state.value("explore_count", 0);
        int generateCount = state.value("generate_count", 0);
        int modelCalls = state.value("model_call_count", 0);
        AgentPhase phase = state.value("phase", AgentPhase.EXPLORATION);

        // 硬上限：总模型调用次数
        if (modelCalls >= agentConstants.getMaxIterationCount()) {
            return stopWithMessage("已达最大迭代次数，为您展示当前最佳结果。");
        }

        // 生成阶段上限
        if (phase == AgentPhase.GENERATION && generateCount >= agentConstants.getMaxConfirmCount()) {
            return stopWithMessage("已尝试多次生成，为您展示当前最佳结果。");
        }

        // 探索阶段上限：自动切换到生成阶段
        if (phase == AgentPhase.EXPLORATION && exploreCount >= agentConstants.getMaxExploreCount()) {
            return CompletableFuture.completedFuture(Map.of(
                    "phase", AgentPhase.GENERATION,
                    "messages", List.of(new AssistantMessage(
                            "已尝试多次搜索，未找到完全匹配的图片，建议为您生成一张。是否继续？"
                    ))
            ));
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
