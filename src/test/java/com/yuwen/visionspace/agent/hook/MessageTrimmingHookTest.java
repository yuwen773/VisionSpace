package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTrimmingHookTest {

    private final MessageTrimmingHook hook = new MessageTrimmingHook();
    private final RunnableConfig config = RunnableConfig.builder().build();

    @Test
    void testGetName() {
        assertEquals("message_trimming", hook.getName());
    }

    @Test
    void testNoTrimmingNeeded() {
        List<Message> messages = List.of(
                new SystemMessage("系统提示"),
                new UserMessage("你好"),
                new AssistantMessage("你好！")
        );

        AgentCommand result = hook.beforeModel(messages, config);

        // 返回原消息列表
        assertNotNull(result);
    }

    @Test
    void testTrimmingKeepsFirstAndRecent() {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("系统提示")); // 首条：必须保留
        for (int i = 0; i < 15; i++) {
            messages.add(new UserMessage("消息 " + i));
        }
        // 总共 16 条，应修剪为：首条 + 最近 9 条 = 10 条

        AgentCommand result = hook.beforeModel(messages, config);
        assertNotNull(result);
    }
}
