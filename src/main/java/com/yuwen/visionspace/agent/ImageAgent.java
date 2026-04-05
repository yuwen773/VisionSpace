package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.yuwen.visionspace.agent.hook.IterationControlHook;
import com.yuwen.visionspace.agent.hook.MessageTrimmingHook;
import com.yuwen.visionspace.agent.hook.SummarizationHook;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.AgentPhase;
import com.yuwen.visionspace.agent.service.ChatHistoryService;
import com.yuwen.visionspace.agent.tools.ImageSearchTool;
import com.yuwen.visionspace.agent.tools.ImageGeneratorTool;
import com.yuwen.visionspace.agent.tools.ImageFeatureAnalyzerTool;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import com.yuwen.visionspace.agent.tools.SimilarImageSearchTool;
import com.yuwen.visionspace.agent.service.UserPreferenceService;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import com.yuwen.visionspace.model.dto.agent.MessageType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图片助手 Agent
 *
 * 核心流程：
 * 1. 用户描述想要的图片
 * 2. Agent 先搜索站内/站外图片（EXPLORATION 阶段）
 * 3. 如果搜索结果不满意或探索次数耗尽，进入 GENERATION 阶段
 * 4. AIGC 生成前暂停等待用户确认（HITL）
 * 5. 支持流式输出和迭代闭环
 */
@Component
public class ImageAgent {

    private static final Logger log = LoggerFactory.getLogger(ImageAgent.class);

    @Resource
    private ChatModel chatModel;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private ImageGeneratorTool imageGeneratorTool;

    @Resource
    private QualityEvaluatorTool qualityEvaluatorTool;

    @Resource
    private SimilarImageSearchTool similarImageSearchTool;

    @Resource
    private ImageFeatureAnalyzerTool imageFeatureAnalyzerTool;

    @Resource
    private UserPreferenceService userPreferenceService;

    @Resource
    private IterationControlHook iterationControlHook;

    @Resource
    private MessageTrimmingHook messageTrimmingHook;

    @Resource
    private SummarizationHook summarizationHook;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private BaseCheckpointSaver checkpointSaver;

    private ReactAgent agent;

    @PostConstruct
    public void init() {
        // 创建人工介入 Hook - 仅拦截 generateImages（AIGC 生成前需用户确认）
        HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
                .approvalOn("generateImages", ToolConfig.builder()
                        .description("即将使用 AIGC 生成图片，需要用户确认")
                        .build())
                .build();

        agent = ReactAgent.builder()
                .name("image_assistant")
                .model(chatModel)
                .instruction("""
                    你是一个智能图片助手。

                    ## 工作流程
                    你必须在以下阶段间切换：

                    ### 阶段 1：EXPLORATION（探索）

                    #### 用户提供了参考图片 URL
                    1. 调用 analyzeImageFeatures 分析图片（获取内容描述、色调、风格、氛围、搜索关键词）
                    2. 向用户简要展示分析结果，确认理解是否准确
                    3. 根据分析结果选择搜索策略：
                       a. 调用 searchSimilarImages 以图搜图（寻找视觉相似的图片）
                       b. 用分析结果中的 searchKeywords 调用 searchContentImages 关键词搜索（寻找同类型图片）
                       c. 两种方式可结合使用
                    4. 如果以图搜图失败，直接用 searchKeywords 作为降级方案搜索

                    #### 用户用文字描述需求
                    1. 根据描述提取关键词，调用 searchContentImages 搜索
                    2. 可按需调用 searchIllustrations 搜索插画

                    #### 通用后续步骤
                    - 调用 evaluateImageQuality 评估搜索结果
                    - matchScore >= 0.5：展示结果，询问用户满意度
                    - matchScore < 0.5：调整关键词重新搜索

                    ### 阶段 2：GENERATION（生成）
                    当探索次数耗尽或用户主动要求生成时：
                    1. 告知用户即将使用 AIGC 生成（此时会暂停等待确认）
                    2. 调用 generateImages 生成图片
                    3. 展示生成结果，询问用户满意度

                    ### 满意度询问规则（每次展示结果后必须执行）
                    你必须主动询问：
                    "这些图片是否符合您的预期？"
                    并提示用户可以：
                    - 表示满意 → 结束
                    - 要求换一批 → 回到探索阶段
                    - 要求重新生成 → 回到生成阶段
                    - 描述更具体的需求 → 调整策略

                    ### 反馈消息格式
                    当收到 [用户反馈] 开头的消息时，按以下格式解析：
                    - 满意度：满意/不满意
                    - 动作：RESEARCH / REGENERATE / RETURN
                    - 原因：用户的具体反馈
                    - 当前探索次数：帮助判断是否应切换阶段
                    - 当前阶段：EXPLORATION / GENERATION

                    ## 重要约束
                    - 每次只返回 3-5 张最相关的图片
                    - 清晰标注图片来源（站外搜索 / AIGC 生成）
                    - 探索阶段全自动执行，不暂停
                    - AIGC 生成前必须暂停等待用户确认
                    """)
                .methodTools(imageSearchTool, imageGeneratorTool, qualityEvaluatorTool, similarImageSearchTool, imageFeatureAnalyzerTool)
                .hooks(List.of(
                        hitlHook,
                        iterationControlHook,
                        messageTrimmingHook,
                        summarizationHook,
                        ModelCallLimitHook.builder().runLimit(15).build()
                ))
                .saver(checkpointSaver)
                .build();
    }

    /**
     * 对话入口（带会话隔离）
     *
     * @param userMessage 用户消息
     * @param imageUrls   图片URL列表
     * @param threadId    会话ID，用于隔离不同对话的上下文
     */
    public String chat(String userMessage, List<String> imageUrls, String threadId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        try {
            String fullMessage = buildUserMessage(userMessage, imageUrls);
            AssistantMessage response = agent.call(fullMessage, config);
            chatHistoryService.saveUserMessage(threadId, null, fullMessage);
            chatHistoryService.saveAssistantMessage(threadId, response.getText(), "text");
            return response.getText();
        } catch (Exception e) {
            log.error("Agent 执行失败, threadId={}", threadId, e);
            return "抱歉，处理您的请求时出现了问题：" + e.getMessage();
        }
    }

    /**
     * 流式对话入口
     */
    public Flux<MessageDTO> stream(String userMessage, List<String> imageUrls, String threadId, Long userId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        String fullMessage = buildUserMessage(userMessage, imageUrls);
        chatHistoryService.saveUserMessage(threadId, userId, fullMessage);

        try {
            Flux<NodeOutput> stream = agent.stream(fullMessage, config);

            StringBuilder assistantContent = new StringBuilder();
            StringBuilder reasoningContent = new StringBuilder();

            return stream
                    // 过滤结束事件
                    .filter(output -> {
                        if (output instanceof StreamingOutput<?> so) {
                            String typeName = so.getOutputType() != null ? so.getOutputType().name() : "";
                            return !"AGENT_MODEL_FINISHED".equals(typeName)
                                && !"AGENT_HOOK_FINISHED".equals(typeName);
                        }
                        return true;
                    })
                    .mapNotNull(output -> {
                        if (output instanceof StreamingOutput<?> streamingOutput) {
                            Object msg = streamingOutput.message();
                            if (msg instanceof AssistantMessage assistantMessage) {
                                MessageDTO messageDTO = new MessageDTO();
                                Map<String, Object> metadata = assistantMessage.getMetadata();

                                // 工具调用消息：先 flush 前面积累的 reasoning block，再保存 tool-call
                                if (assistantMessage.hasToolCalls()) {
                                    String prevReasoning = reasoningContent.toString();
                                    if (!prevReasoning.isEmpty()) {
                                        chatHistoryService.saveReasoningMessage(threadId, prevReasoning);
                                        reasoningContent.setLength(0);
                                    }
                                    messageDTO.setMessageType(MessageType.TOOL_REQUEST);
                                    messageDTO.setContent(assistantMessage.getText());
                                    List<Map<String, Object>> toolCallingList = assistantMessage.getToolCalls().stream()
                                            .map(toolCall -> {
                                                Map<String, Object> tcMap = new HashMap<>();
                                                tcMap.put("name", toolCall.name());
                                                tcMap.put("arguments", toolCall.arguments());
                                                return tcMap;
                                            }).toList();
                                    messageDTO.setToolCalls(toolCallingList);
                                    String toolDesc = toolCallingList.stream()
                                            .map(tc -> tc.get("name") + ": " + tc.get("arguments"))
                                            .collect(Collectors.joining("\n"));
                                    chatHistoryService.saveToolCallMessage(threadId, toolDesc);
                                }
                                // reasoning 消息：累积到当前 block（不立即存）
                                else if (metadata.get("reasoningContent") != null
                                        && !"".equals(metadata.get("reasoningContent"))) {
                                    String reasoningText = (String) metadata.get("reasoningContent");
                                    reasoningContent.append(reasoningText);
                                    messageDTO.setContent(reasoningText);
                                    messageDTO.setMessageType(MessageType.REASONING);
                                }
                                // 普通文本消息 - 累积内容
                                else {
                                    String text = assistantMessage.getText();
                                    assistantContent.append(text);
                                    messageDTO.setContent(text);
                                    messageDTO.setMessageType(MessageType.ASSISTANT);
                                }
                                return messageDTO;
                            }
                        } else if (output instanceof InterruptionMetadata interruptionMetadata) {
                            MessageDTO messageDTO = new MessageDTO();
                            messageDTO.setMessageType(MessageType.TOOL_CONFIRM);
                            StringBuilder sb = new StringBuilder();
                            for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
                                sb.append("工具: ").append(feedback.getName()).append("\n");
                                sb.append("评估: ").append(feedback.getDescription()).append("\n");
                            }
                            messageDTO.setContent(sb.toString());
                            chatHistoryService.saveToolConfirmMessage(threadId, sb.toString());
                            return messageDTO;
                        }
                        return null;
                    })
                    .doOnComplete(() -> {
                        String fullReasoning = reasoningContent.toString();
                        if (!fullReasoning.isEmpty()) {
                            chatHistoryService.saveReasoningMessage(threadId, fullReasoning);
                        }
                        String fullResponse = assistantContent.toString();
                        if (!fullResponse.isEmpty()) {
                            chatHistoryService.saveAssistantMessage(threadId, fullResponse, "text");
                        }
                    });

        } catch (GraphRunnerException e) {
            log.error("流式对话执行异常, threadId={}", threadId, e);
            return Flux.error(new RuntimeException("Agent 流式执行失败", e));
        }
    }

    /**
     * 处理用户反馈（增强版）
     */
    public String handleFeedback(String threadId, String userId,
                                 Boolean satisfied, String reason,
                                 ActionType action, AgentPhase currentPhase) {

        int exploreCount = getExploreCount(threadId);

        // 保存反馈到偏好系统
        if (userId != null && !Boolean.TRUE.equals(satisfied)) {
            userPreferenceService.saveFeedback(userId, action, reason);
        }

        // 构造增强的反馈消息
        String feedbackMessage;
        if (Boolean.TRUE.equals(satisfied)) {
            feedbackMessage = String.format(
                    "[用户反馈] 满意度: 满意 | 阶段: %s | 探索次数: %d",
                    currentPhase, exploreCount
            );
        } else {
            feedbackMessage = String.format(
                    "[用户反馈] 满意度: 不满意 | 动作: %s | 原因: %s | 阶段: %s | 探索次数: %d",
                    action,
                    reason != null ? reason : "未说明",
                    currentPhase,
                    exploreCount
            );
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
     * 获取当前探索次数（从 state 中读取）
     * TODO: 需要通过 MemorySaver 获取会话状态，暂返回 0
     */
    private int getExploreCount(String threadId) {
        // 后续可通过 MemorySaver 或 Redis 读取实际计数
        return 0;
    }

    /**
     * 构建带有图片分析标签的用户消息
     */
    private String buildUserMessage(String userMessage, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return userMessage != null ? userMessage : "";
        }
        StringBuilder sb = new StringBuilder();
        for (String url : imageUrls) {
            sb.append("<image-analysis>").append(url).append("</image-analysis>\n");
        }
        sb.append("\n").append(userMessage != null ? userMessage : "");
        return sb.toString();
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
