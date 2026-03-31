package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.tools.ImageSearchTool;
import com.yuwen.visionspace.agent.tools.LogoGeneratorTool;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import com.yuwen.visionspace.agent.service.UserPreferenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 图片助手 Agent
 *
 * 核心流程：
 * 1. 用户描述想要的图片
 * 2. Agent 先搜索站内/站外图片
 * 3. 如果搜索结果不满意，Agent 调用 AIGC 生成图片
 */
@Component
public class ImageAgent {

    private static final Logger log = LoggerFactory.getLogger(ImageAgent.class);

    @Resource
    private ChatModel chatModel;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Resource
    private QualityEvaluatorTool qualityEvaluatorTool;

    @Resource
    private UserPreferenceService userPreferenceService;

    private ReactAgent agent;

    @PostConstruct
    public void init() {
        // 创建人工介入 Hook
        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("evaluateImageQuality", ToolConfig.builder()
                        .description("搜索结果评估完成，需要用户确认是否满意")
                        .build())
                .build();

        agent = ReactAgent.builder()
                .name("image_assistant")
                .model(chatModel)
                .instruction("""
                    你是一个图片助手。帮助用户找到或生成他们想要的图片。

                    工作流程：
                    1. 理解用户的图片需求
                    2. 使用搜索工具找到图片
                    3. 使用 QualityEvaluatorTool 评估结果
                    4. 根据评估结果决定：
                       - matchScore >= 0.5: 展示结果给用户
                       - matchScore < 0.5: 询问用户是否满意
                    5. 根据用户反馈决定下一步

                    重要规则：
                    - 每次只返回 3-5 张最相关的图片
                    - 清晰标注图片来源（站外搜索/AIGC生成）
                    - 如果用户不满意，可以重新生成或搜索
                    """)
                .methodTools(imageSearchTool, logoGeneratorTool, qualityEvaluatorTool)
                .hooks(List.of(humanInTheLoopHook))
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 对话入口（带会话隔离）
     *
     * @param userMessage 用户消息
     * @param threadId    会话ID，用于隔离不同对话的上下文
     */
    public String chat(String userMessage, String threadId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        try {
            AssistantMessage response = agent.call(userMessage, config);
            return response.getText();
        } catch (Exception e) {
            log.error("Agent 执行失败, threadId={}", threadId, e);
            return "抱歉，处理您的请求时出现了问题：" + e.getMessage();
        }
    }

    /**
     * 处理用户反馈
     */
    public String handleFeedback(String threadId, String userId,
                                Boolean satisfied, String reason, ActionType action) {

        // 保存反馈到偏好系统
        if (userId != null && !Boolean.TRUE.equals(satisfied)) {
            userPreferenceService.saveFeedback(userId, action, reason);
        }

        // 构建反馈消息
        String feedbackMessage;
        if (Boolean.TRUE.equals(satisfied)) {
            feedbackMessage = "用户对当前结果满意。";
        } else {
            feedbackMessage = "用户不满意，原因: " + (reason != null ? reason : "未说明");
            if (ActionType.REGENERATE.equals(action)) {
                feedbackMessage += "，请重新生成。";
            } else if (ActionType.RESEARCH.equals(action)) {
                feedbackMessage += "，请重新搜索。";
            }
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        try {
            AssistantMessage response = agent.call(feedbackMessage, config);
            return response.getText();
        } catch (Exception e) {
            log.error("处理反馈失败, threadId={}", threadId, e);
            return "抱歉，处理反馈时出现问题：" + e.getMessage();
        }
    }

    /**
     * 支持中断的调用方法
     */
    public Optional<NodeOutput> invokeAndGetOutput(String message, RunnableConfig config) {
        try {
            return agent.invokeAndGetOutput(message, config);
        } catch (Exception e) {
            log.error("Agent 执行失败", e);
            return Optional.empty();
        }
    }
}
