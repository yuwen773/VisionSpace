package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 摘要 Hook
 * 当消息数量超过阈值时，将早期消息汇总为一条摘要消息
 */
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class SummarizationHook extends MessagesModelHook {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AgentConstants agentConstants;

    @Override
    public String getName() {
        return "summarization";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages.size() < agentConstants.getSummaryThreshold()) {
            return new AgentCommand(previousMessages);
        }
        Optional<String> threadIdOpt = config.threadId();
        if (threadIdOpt.isEmpty()) {
            return new AgentCommand(previousMessages);
        }
        String threadId = threadIdOpt.get();

        try {
            // 提取需要摘要的消息（排除系统消息和最新的保留消息）
            List<Message> messagesToSummarize = previousMessages.stream()
                    .filter(m -> !(m instanceof SystemMessage))
                    .limit(previousMessages.size() - agentConstants.getMessagesToKeep())
                    .collect(Collectors.toList());

            if (messagesToSummarize.isEmpty()) {
                return new AgentCommand(previousMessages);
            }

            // 生成摘要
            String summary = generateSummary(messagesToSummarize);

            // 保存摘要到数据库
            chatHistoryService.saveSummary(threadId, summary, messagesToSummarize.size());

            // 构建新的消息列表
            List<Message> summarizedMessages = buildSummarizedMessages(
                    previousMessages, messagesToSummarize, summary);

            return new AgentCommand(summarizedMessages, UpdatePolicy.REPLACE);
        } catch (Exception e) {
            log.error("摘要生成失败, threadId={}", threadId, e);
            return new AgentCommand(previousMessages);
        }
    }

    /**
     * 生成摘要（简单实现，实际可接入 AI 模型）
     */
    private String generateSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("[摘要] ");
        for (Message msg : messages) {
            if (msg instanceof UserMessage um) {
                sb.append("用户: ").append(um.getText()).append("; ");
            } else if (msg instanceof AssistantMessage am) {
                sb.append("助手: ").append(am.getText()).append("; ");
            }
        }
        // 截断过长内容
        String summary = sb.toString();
        if (summary.length() > agentConstants.getSummaryMaxLength()) {
            summary = summary.substring(0, agentConstants.getSummaryTruncateLength()) + "...";
        }
        return summary;
    }

    /**
     * 构建包含摘要的新消息列表
     */
    private List<Message> buildSummarizedMessages(List<Message> original,
                                                  List<Message> toSummarize,
                                                  String summary) {
        List<Message> result = new ArrayList<>();

        // 1. 添加系统消息（如果有）
        for (Message msg : original) {
            if (msg instanceof SystemMessage) {
                result.add(msg);
                break;
            }
        }

        // 2. 添加摘要消息
        result.add(new AssistantMessage(summary));

        // 3. 添加最新的保留消息
        int keepCount = agentConstants.getMessagesToKeep();
        for (int i = original.size() - keepCount; i < original.size(); i++) {
            if (i >= 0) {
                result.add(original.get(i));
            }
        }

        return result;
    }
}
