package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.yuwen.visionspace.agent.model.AgentPhase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class IterationControlHookTest {

    private final IterationControlHook hook = new IterationControlHook();

    @Test
    void testGetName() {
        assertEquals("iteration_control", hook.getName());
    }

    @Test
    void testNormalIncrement() {
        OverAllState state = new OverAllState(Map.of(
                "explore_count", 0,
                "generate_count", 0,
                "model_call_count", 0,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertEquals(1, result.get("model_call_count"));
    }

    @Test
    void testExploreLimitTriggersPhaseSwitch() {
        OverAllState state = new OverAllState(Map.of(
                "explore_count", 3,
                "generate_count", 0,
                "model_call_count", 5,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertEquals(AgentPhase.GENERATION, result.get("phase"));
    }

    @Test
    void testModelCallLimitStopsAgent() {
        OverAllState state = new OverAllState(Map.of(
                "explore_count", 0,
                "generate_count", 0,
                "model_call_count", 15,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertTrue(result.containsKey("messages"));
    }

    @Test
    void testGenerateLimitStopsAgent() {
        OverAllState state = new OverAllState(Map.of(
                "explore_count", 3,
                "generate_count", 2,
                "model_call_count", 8,
                "phase", AgentPhase.GENERATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertTrue(result.containsKey("messages"));
    }
}
