package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.yuwen.visionspace.agent.tools.ImageSearchTool;
import com.yuwen.visionspace.agent.tools.LogoGeneratorTool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

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

    private ReactAgent agent;

    @PostConstruct
    public void init() {
        agent = ReactAgent.builder()
                .name("image_assistant")
                .model(chatModel)
                .instruction("""
                    你是一个图片助手。你的任务是帮助用户找到或生成他们想要的图片。

                    工作流程：
                    1. 首先理解用户的图片需求
                    2. 先使用搜索工具尝试找到合适的图片（关键词搜索或以图搜图）
                    3. 如果搜索结果为空或不满意，使用 LogoGeneratorTool 生成图片
                    4. 向用户展示结果，询问是否满意

                    重要规则：
                    - 每次只返回 3-5 张最相关的图片
                    - 清晰标注图片来源（站外搜索/AIGC生成）
                    - 如果用户不满意，可以调整关键词重新搜索或生成
                    """)
                .methodTools(imageSearchTool, logoGeneratorTool)
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
}
