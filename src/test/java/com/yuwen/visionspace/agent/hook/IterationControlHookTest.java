package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.model.AgentPhase;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IterationControlHook 单元测试。
 * Hook 通过 @Resource 注入 AgentConstants，纯 new 出来时该字段为 null，必须显式注入。
 */
class IterationControlHookTest {

    private final IterationControlHook hook = newHookWith(new AgentConstants());

    private static IterationControlHook newHookWith(AgentConstants constants) {
        IterationControlHook h = new IterationControlHook();
        ReflectionTestUtils.setField(h, "agentConstants", constants);
        return h;
    }

    private static Map<String, Object> run(IterationControlHook hook,
                                           int explore, int generate, int modelCalls, AgentPhase phase) {
        OverAllState state = new OverAllState(Map.of(
                "explore_count", explore,
                "generate_count", generate,
                "model_call_count", modelCalls,
                "phase", phase
        ));
        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, RunnableConfig.builder().build());
        return future.join();
    }

    @Test
    void testGetName() {
        assertEquals("iteration_control", hook.getName());
    }

    @Test
    void testNormalIncrement() {
        Map<String, Object> result = run(hook, 0, 0, 0, AgentPhase.EXPLORATION);

        assertEquals(1, result.get("model_call_count"));
        assertFalse(result.containsKey("messages"));
    }

    @Test
    void testExploreLimitTriggersPhaseSwitch() {
        Map<String, Object> result = run(hook, 3, 0, 5, AgentPhase.EXPLORATION);

        assertEquals(AgentPhase.GENERATION, result.get("phase"));
        assertTrue(result.containsKey("messages"));
    }

    @Test
    void testExploreBelowLimitDoesNotSwitchPhase() {
        Map<String, Object> result = run(hook, 2, 0, 5, AgentPhase.EXPLORATION);

        assertFalse(result.containsKey("phase"));
        assertEquals(6, result.get("model_call_count"));
    }

    @Test
    void testModelCallLimitStopsAgent() {
        Map<String, Object> result = run(hook, 0, 0, 15, AgentPhase.EXPLORATION);

        assertTrue(result.containsKey("messages"));
        assertFalse(result.containsKey("model_call_count"));
    }

    @Test
    void testGenerateLimitStopsAgent() {
        Map<String, Object> result = run(hook, 3, 2, 8, AgentPhase.GENERATION);

        assertTrue(result.containsKey("messages"));
        assertFalse(result.containsKey("model_call_count"));
    }

    @Test
    void testLimitsComeFromConfiguration() {
        AgentConstants custom = new AgentConstants();
        custom.setMaxIterationCount(5);
        IterationControlHook customHook = newHookWith(custom);

        // 上限 5：调用次数 5 时应停止，而默认配置 15 时不会
        assertTrue(run(customHook, 0, 0, 5, AgentPhase.EXPLORATION).containsKey("messages"));
        assertFalse(run(hook, 0, 0, 5, AgentPhase.EXPLORATION).containsKey("messages"));
    }

    @Test
    void testStopMessageIsNonEmpty() {
        Map<String, Object> result = run(hook, 0, 0, 15, AgentPhase.EXPLORATION);

        @SuppressWarnings("unchecked")
        List<Object> messages = (List<Object>) result.get("messages");
        assertEquals(1, messages.size());
        assertNotNull(messages.get(0));
    }
}
