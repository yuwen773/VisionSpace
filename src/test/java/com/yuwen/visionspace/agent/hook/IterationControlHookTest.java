package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.yuwen.visionspace.agent.config.AgentConstants;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IterationControlHook 单元测试。
 *
 * <p>Hook 通过 @Resource 注入 AgentConstants，纯 new 出来时该字段为 null，必须显式注入。
 *
 * <p>只有模型调用计数有写入者；探索/生成计数器没有任何地方递增，对应的「到达上限」
 * 判断此前永不触发，已随桩一起删除。这些测试锁定「上限只由调用计数决定」。
 */
class IterationControlHookTest {

    private final IterationControlHook hook = newHookWith(new AgentConstants());

    private static IterationControlHook newHookWith(AgentConstants constants) {
        IterationControlHook h = new IterationControlHook();
        ReflectionTestUtils.setField(h, "agentConstants", constants);
        return h;
    }

    private static Map<String, Object> run(IterationControlHook hook, int modelCalls) {
        OverAllState state = new OverAllState(Map.of("model_call_count", modelCalls));
        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state,
                RunnableConfig.builder().build());
        return future.join();
    }

    @Test
    void testGetName() {
        assertEquals("iteration_control", hook.getName());
    }

    @Test
    void testNormalIncrement() {
        Map<String, Object> result = run(hook, 0);

        assertEquals(1, result.get("model_call_count"));
        assertFalse(result.containsKey("messages"));
        assertFalse(result.containsKey("phase"), "阶段不再由这个钩子切换");
    }

    @Test
    void testModelCallLimitStopsAgent() {
        Map<String, Object> result = run(hook, 15);

        assertTrue(result.containsKey("messages"));
        assertFalse(result.containsKey("model_call_count"));
    }

    @Test
    void testLimitsComeFromConfiguration() {
        AgentConstants custom = new AgentConstants();
        custom.setMaxIterationCount(5);
        IterationControlHook customHook = newHookWith(custom);

        // 上限 5：调用次数 5 时应停止，而默认配置 15 时不会
        assertTrue(run(customHook, 5).containsKey("messages"));
        assertFalse(run(hook, 5).containsKey("messages"));
    }

    @Test
    void testStopMessageIsNonEmpty() {
        Map<String, Object> result = run(hook, 15);

        @SuppressWarnings("unchecked")
        List<Object> messages = (List<Object>) result.get("messages");
        assertEquals(1, messages.size());
        assertNotNull(messages.get(0));
    }
}
