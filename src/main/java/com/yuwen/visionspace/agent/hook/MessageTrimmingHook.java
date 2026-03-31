package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息修剪 Hook
 * 保留第一条消息（系统提示）+ 最近 N-1 条消息，防止上下文溢出
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {

    private static final int MAX_MESSAGES = 10;

    @Override
    public String getName() {
        return "message_trimming";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages.size() <= MAX_MESSAGES) {
            return new AgentCommand(previousMessages);
        }

        // 保留第一条消息（通常是系统提示）
        Message firstMessage = previousMessages.get(0);
        // 保留最近的消息
        int keepCount = MAX_MESSAGES - 1;
        List<Message> recentMessages = previousMessages.subList(
                previousMessages.size() - keepCount,
                previousMessages.size()
        );

        List<Message> trimmedMessages = new ArrayList<>();
        trimmedMessages.add(firstMessage);
        trimmedMessages.addAll(recentMessages);

        return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
    }
}
