package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.yuwen.visionspace.agent.config.AgentConstants;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.getField;

/**
 * MessageTrimmingHook 单元测试。
 * Hook 通过 @Resource 注入 AgentConstants，纯 new 出来时该字段为 null，必须显式注入。
 */
class MessageTrimmingHookTest {

    private final MessageTrimmingHook hook = newHookWith(10);
    private final RunnableConfig config = RunnableConfig.builder().build();

    private static MessageTrimmingHook newHookWith(int maxMessages) {
        AgentConstants constants = new AgentConstants();
        constants.setMaxMessagesToTrim(maxMessages);
        MessageTrimmingHook h = new MessageTrimmingHook();
        ReflectionTestUtils.setField(h, "agentConstants", constants);
        return h;
    }

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

        assertNotNull(result);
        assertEquals(messages, messagesOf(result));
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

        List<Message> trimmed = messagesOf(result);
        assertEquals(10, trimmed.size());
        assertSame(messages.get(0), trimmed.get(0));
        // 保留首条 + 最近 9 条（"消息 6".."消息 14"），"消息 15" 实际不存在
        assertEquals("消息 14", trimmed.get(trimmed.size() - 1).getText());
    }

    @Test
    void testExactLimitIsNotTrimmed() {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("系统提示"));
        for (int i = 0; i < 9; i++) {
            messages.add(new UserMessage("消息 " + i));
        }

        AgentCommand result = hook.beforeModel(messages, config);

        assertEquals(10, messagesOf(result).size());
        assertSame(messages.get(0), messagesOf(result).get(0));
    }

    /**
     * AgentCommand#getMessages() 是包级私有，测试通过反射读取。
     */
    @SuppressWarnings("unchecked")
    private static List<Message> messagesOf(AgentCommand command) {
        return (List<Message>) getField(command, "messages");
    }
}
