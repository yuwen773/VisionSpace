package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.yuwen.visionspace.agent.config.AgentToolExceptionPolicy;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.agent.recovery.AgentRecoveryService;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.recovery.PendingReviewException;
import com.yuwen.visionspace.agent.recovery.ResumeToolEnvironment;
import com.yuwen.visionspace.agent.recovery.ResumeToolUnavailableException;
import com.yuwen.visionspace.agent.recovery.RunStatus;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.entity.AgentRunState;
import com.yuwen.visionspace.agent.hook.IterationControlHook;
import com.yuwen.visionspace.agent.hook.MessageTrimmingHook;
import com.yuwen.visionspace.agent.hook.SummarizationHook;
import com.yuwen.visionspace.agent.interceptor.TextMcpModelInterceptor;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.AgentPhase;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.agent.service.ChatHistoryService;
import com.yuwen.visionspace.agent.tools.ImageSearchTool;
import com.yuwen.visionspace.agent.tools.ImageGeneratorTool;
import com.yuwen.visionspace.agent.tools.ImageFeatureAnalyzerTool;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import com.yuwen.visionspace.agent.tools.SimilarImageSearchTool;
import com.yuwen.visionspace.agent.service.UserPreferenceService;
import com.yuwen.visionspace.utils.JsonUtils;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import com.yuwen.visionspace.model.dto.agent.MessageType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private AgentSessionGuard sessionGuard;

    @Resource
    private AgentRunStateStore runStateStore;

    @Resource
    private BaseCheckpointSaver checkpointSaver;

    @Resource
    private TextMcpModelInterceptor textMcpModelInterceptor;

    @Resource
    private com.yuwen.visionspace.agent.recovery.AgentActionInterceptor actionInterceptor;

    @Resource
    private AgentRecoveryService recoveryService;

    @Resource
    private ResumeToolEnvironment resumeToolEnvironment;

    private ReactAgent agent;

    /**
     * 内置工具的 instruction
     */
    private static final String AGENT_INSTRUCTION = """
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
            - 按返回的 verdict 决定下一步，不要用 matchScore 阈值自行判断：
              - MATCHED：展示结果，询问用户满意度
              - UNKNOWN：描述语言与意图不一致（如中文意图配英文图库描述），
                无法据此判断是否匹配。先展示结果让用户确认，不要重搜
              - MISMATCHED：图片确实不符，调整关键词重搜；重搜仍不符则改用 AIGC
            - matchScore 仅作为展示顺序的参考，不作为是否展示的依据

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

            ## 图表输出格式
            当需要输出架构图、流程图、时序图等图表时，必须使用代码块格式输出：

            ### Mermaid 图表
            ```mermaid
            flowchart TD
                A[开始] --> B[判断]
                B -->|是| C[执行]
                B -->|否| D[结束]
            ```

            ### draw.io 图表
            ```drawio
            <mxGraphModel>
              <root>
                <mxCell id="0"/>
                <mxCell id="1" value="节点1"/>
              </root>
            </mxGraphModel>
            ```

            ### PlantUML 图表
            ```plantuml
            @startuml
            Alice --> Bob
            Alice -> Carol
            @enduml
            ```

            注意：
            - 图表代码必须紧跟在 ``` 之后，不添加额外解释
            - Mermaid 和 draw.io 在前端实时渲染
            - PlantUML 通过 plantuml.com 在线服务渲染
            - 禁止直接用文字描述图表而不输出代码
            """;

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
                .instruction(AGENT_INSTRUCTION)
                .methodTools(imageSearchTool, imageGeneratorTool, qualityEvaluatorTool, similarImageSearchTool, imageFeatureAnalyzerTool)
                .hooks(List.of(
                        hitlHook,
                        iterationControlHook,
                        messageTrimmingHook,
                        summarizationHook,
                        ModelCallLimitHook.builder().runLimit(15).build()
                ))
                // 动作台账：已确认完成不重放，结果未知进入待核对
                .interceptors(List.of(actionInterceptor))
                // 生成失败必须以异常到达台账，不能被框架折成一条普通文本
                .toolExecutionExceptionProcessor(AgentToolExceptionPolicy.processor())
                .saver(checkpointSaver)
                .build();
    }

    /**
     * 同步对话入口（带会话隔离）
     *
     * @param userMessage 用户消息
     * @param imageUrls   图片URL列表
     * @param threadId    会话ID，用于隔离不同对话的上下文
     * @param userId      服务端登录用户；会话归属只认它，不认请求体里的 userId
     */
    public String chat(String userMessage, List<String> imageUrls, String threadId, Long userId) {
        // 归属在进入模型之前判定：绑定/核对失败直接抛出，不产生任何执行
        sessionGuard.bindOwner(threadId, userId, userMessage);
        // 有结果未知的动作时，任何入口都不得继续跑
        recoveryService.requireNoPendingReview(threadId, userId);
        // 内置入口不使用 MCP：环境随执行配置声明为「空环境」，与检查点同版本落库
        RunnableConfig config = configFor(threadId, userId, List.of());
        try {
            String fullMessage = buildUserMessage(userMessage, imageUrls);
            AssistantMessage response = agent.call(fullMessage, config);
            chatHistoryService.saveUserMessage(threadId, userId, fullMessage);
            chatHistoryService.saveAssistantMessage(threadId, userId, response.getText(), "text");
            return response.getText();
        } catch (PendingReviewException e) {
            // 结果未知不能被折成一句「稍后重试」：那会让用户以为可以直接再来一次，
            // 而实际必须先核对。暂停状态也要落库，界面才能给出核对入口。
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    pauseForReview(e.getToolName()));
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该步骤的外部执行结果未知，已暂停，请核对后再继续");
        } catch (Exception e) {
            log.error("Agent 执行失败, threadId={}, userId={}", threadId, userId, e);
            // 异常原文可能带目标地址、请求头或服务端回显的凭据，只留在服务端日志
            return "抱歉，处理您的请求时出现了问题，请稍后重试";
        }
    }

    /**
     * 流式对话入口（无 MCP）
     */
    public Flux<MessageDTO> stream(String userMessage, List<String> imageUrls, String threadId, Long userId) {
        return stream(userMessage, imageUrls, threadId, userId, null, false);
    }

    /**
     * 流式对话入口（支持 MCP）
     *
     * @param userMessage 用户消息
     * @param imageUrls 图片URL列表
     * @param threadId 会话ID
     * @param userId 服务端登录用户；会话归属只认它
     * @param enabledMcpServers 启用的 MCP 服务器代码列表（可选）
     * @param disableMcpDefault 是否禁用默认 MCP
     */
    public Flux<MessageDTO> stream(String userMessage, List<String> imageUrls, String threadId, Long userId,
                                   List<String> enabledMcpServers, Boolean disableMcpDefault) {
        // 归属已由调用方（AgentController）在建立流之前核对；这里再核对一次，
        // 保证任何直接调用方也无法把状态写进别人的会话。
        sessionGuard.requireOwned(threadId, userId);

        // 本次执行开始前收敛上次遗留的「执行中」动作：可证明幂等的退回可重试，
        // 其余进入待核对，避免把它们当成「没发生」而重放
        recoveryService.reconcileBeforeRun(threadId, userId);
        // 收敛之后仍有待核对动作：本次执行必须停在原地
        recoveryService.requireNoPendingReview(threadId, userId);

        String fullMessage = buildUserMessage(userMessage, imageUrls);
        chatHistoryService.saveUserMessage(threadId, userId, fullMessage);

        // 解析 MCP 回调
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setEnabledMcpServers(enabledMcpServers);
        chatRequest.setDisableMcpDefault(disableMcpDefault);
        TextMcpModelInterceptor.McpResolution mcp =
                textMcpModelInterceptor.resolve(chatRequest, userId);

        // 执行配置在 MCP 解析之后构建：本次实际用到的服务 code 与执行世代一起
        // 声明进配置，由检查点写入路径与载荷同版本落库。提前直写会在首个新
        // 检查点之前改坏既有恢复点的环境。
        RunnableConfig config = configFor(threadId, userId, mcp.serverCodes());

        // 选择 Agent：无 MCP 用内置 agent，有 MCP 创建 combined agent
        ReactAgent activeAgent = mcp.isEmpty() ? agent : buildMcpAgent(mcp.callbacks());

        try {
            Flux<NodeOutput> stream = activeAgent.stream(fullMessage, config);

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
                                        chatHistoryService.saveReasoningMessage(threadId, userId, prevReasoning);
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
                                    chatHistoryService.saveToolCallMessage(threadId, userId, toolDesc);
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
                            chatHistoryService.saveToolConfirmMessage(threadId, userId, sb.toString());
                            // 人工确认信息随完整状态一起落库：重启后仍能区分
                            // 「停在待确认」与「已经确认过」
                            persistAwaitingConfirmation(threadId, userId, interruptionMetadata);
                            return messageDTO;
                        }
                        return null;
                    })
                    // 结果未知与明确失败分开呈现：前者不能直接重试，必须核对
                    .onErrorResume(PendingReviewException.class, error -> {
                        MessageDTO messageDTO = new MessageDTO();
                        messageDTO.setMessageType(MessageType.ACTION_UNKNOWN);
                        messageDTO.setToolName(error.getToolName());
                        messageDTO.setContent("工具 " + error.getToolName()
                                + " 的执行结果未知，已暂停自动执行。请核对后再继续。");
                        runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                                pauseForReview(error.getToolName()));
                        return Flux.just(messageDTO);
                    })
                    .doOnComplete(() -> {
                        String fullReasoning = reasoningContent.toString();
                        if (!fullReasoning.isEmpty()) {
                            chatHistoryService.saveReasoningMessage(threadId, userId, fullReasoning);
                        }
                        String fullResponse = assistantContent.toString();
                        if (!fullResponse.isEmpty()) {
                            chatHistoryService.saveAssistantMessage(threadId, userId, fullResponse, "text");
                        }
                    });

        } catch (GraphRunnerException e) {
            log.error("流式对话执行异常, threadId={}", threadId, e);
            return Flux.error(new RuntimeException("Agent 流式执行失败", e));
        }
    }

    /**
     * 处理用户反馈（增强版）
     *
     * <p>反馈会触发一次模型执行，因此归属必须在进入模型之前判定：
     * 不属于该用户的会话既不写偏好，也不执行。
     */
    public String handleFeedback(String threadId, Long userId,
                                 Boolean satisfied, String reason,
                                 ActionType action, AgentPhase currentPhase) {

        sessionGuard.requireOwned(threadId, userId);
        // 反馈会触发一次模型执行，同样不能绕过待核对暂停
        recoveryService.requireNoPendingReview(threadId, userId);
        int exploreCount = getExploreCount(threadId);

        // 保存反馈到偏好系统
        if (userId != null && !Boolean.TRUE.equals(satisfied)) {
            userPreferenceService.saveFeedback(String.valueOf(userId), action, reason);
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

        // 反馈入口同样只用内置工具：显式声明空环境，随检查点同版本落库
        RunnableConfig config = configFor(threadId, userId, List.of());

        try {
            AssistantMessage response = agent.call(feedbackMessage, config);
            return response.getText();
        } catch (PendingReviewException e) {
            // 结果未知：与普通失败分开，避免界面显示成「稍后重试」而实际不能重试
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    pauseForReview(e.getToolName()));
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该步骤的外部执行结果未知，已暂停，请核对后再继续");
        } catch (Exception e) {
            log.error("处理反馈失败, threadId={}", threadId, e);
            return "抱歉，处理反馈时出现问题，请稍后重试";
        }
    }

    /**
     * 从完整执行状态续跑。
     *
     * <p>只有 {@link AgentRecoveryService} 判定可续跑时才会走到这里；本方法再
     * 核对一次归属与状态，保证任何直接调用方也无法用消息历史伪造恢复点。
     *
     * <p>失败时区分三种结果，不混成一句「失败」：
     * <ul>
     *   <li>结果未知且不能安全重试：暂停并返回待核对提示；</li>
     *   <li>可安全重试：记录失败，保持可重试；</li>
     *   <li>已确认完成：不重放。</li>
     * </ul>
     */
    public String resume(String threadId, Long userId) {
        sessionGuard.requireOwned(threadId, userId);
        AgentRunState state = runStateStore.loadResumable(threadId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "该会话没有可安全续跑的执行状态，已暂停自动执行"));

        // 与其它入口同一道闸门：续跑也不能绕过待核对暂停
        recoveryService.requireNoPendingReview(threadId, userId);

        Optional<Checkpoint> checkpoint = runStateStore.toCheckpoint(state);
        if (checkpoint.isEmpty()) {
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    state.getPendingConfirmation());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "执行状态无法还原，已暂停自动执行");
        }

        // 世代来自认领后的状态行：claimForResume 在认领时已把它推进到本续跑的世代
        RunnableConfig config = runStateStore.resumeConfig(threadId, userId, state,
                DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY,
                DualCheckpointSaver.RUN_EPOCH_METADATA_KEY);

        // 恢复上一次的工具环境：按持久化的 code 用当前用户重新回读并授权。
        // 一律用内置 agent 会让 MCP 工具凭空消失；沿用旧回调又会让已撤销的权限复活，
        // 因此既不能省略，也不能照搬。
        ReactAgent resumeAgent = rebuildResumeAgent(threadId, userId, state);

        // MySQL 里的完整状态是 Redis 丢失检查点时的唯一依据：续跑前把它写回
        // 框架的存储，否则「有完整状态但 Redis 没了」依然续不上。
        try {
            checkpointSaver.put(config, checkpoint.get());
        } catch (Exception e) {
            log.error("续跑前回填检查点失败, threadId={}, userId={}", threadId, userId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "执行状态无法写回，已暂停自动执行");
        }

        try {
            // 续跑不追加用户消息：继续的是同一次执行，不是新一轮对话
            AssistantMessage response = resumeAgent.call("", config);
            runStateStore.updateStatus(threadId, userId, RunStatus.RUNNING, null);
            return response.getText();
        } catch (PendingReviewException e) {
            // 结果未知：暂停并等待核对，与「明确失败」区分
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    pauseForReview(e.getToolName()));
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该步骤的外部执行结果未知，已暂停，请核对后再继续");
        } catch (Exception e) {
            // 明确失败：可安全重试。不能把失败包装成一条成功的助手回复，
            // 否则界面会把它显示成正常回答。
            log.error("Agent 续跑失败, threadId={}, userId={}", threadId, userId);
            runStateStore.updateStatus(threadId, userId, RunStatus.FAILED, null);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "继续执行失败，请稍后重试");
        }
    }

    /**
     * 按上次执行用到的 MCP 服务重建 Agent。
     *
     * <p>恢复点里只留了服务 code，这里用<b>当前用户</b>重新回读归属与启用状态：
     * <ul>
     *   <li>没有记录过 MCP → 用内置 agent（与首次执行一致）；</li>
     *   <li>记录过且全部仍可用 → 重建对应的 MCP agent；</li>
     *   <li>任一服务已停用、已撤销或已删除 → 拒绝续跑。缺少工具继续执行会让模型
     *       改用别的方式完成同一件事，等于绕过了用户的撤销决定。</li>
     * </ul>
     */
    private ReactAgent rebuildResumeAgent(String threadId, Long userId, AgentRunState state) {
        // 没有记录 ≠ 没有用 MCP：无法确认上次的工具环境时不能猜，
        // 否则会在少了工具的情况下继续跑，模型改用别的方式完成同一件事
        Optional<List<String>> recorded = runStateStore.recordedMcpServerCodes(state);
        if (recorded.isEmpty()) {
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    "无法确认上次执行使用的工具环境，请重新发起请求");
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "无法确认上次执行使用的工具环境，已暂停，请重新发起请求");
        }
        try {
            ResumeToolEnvironment.Environment environment =
                    resumeToolEnvironment.resolve(recorded.get(), userId);
            return environment.usesMcp() ? buildMcpAgent(environment.callbacks()) : agent;
        } catch (ResumeToolUnavailableException e) {
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    "MCP 服务已停用或撤销，需重新选择后再继续");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, e.getMessage());
        }
    }

    /**
     * 结果未知时写进 {@code pendingConfirmation} 的固定文案。
     *
     * <p>流式、反馈、续跑三个入口都要把同一次暂停记成同一种状态；文案分散写三遍
     * 迟早会漂移，界面就分不清「等待核对」和别的暂停原因了。
     */
    private String pauseForReview(String toolName) {
        return "外部动作结果未知，等待核对：" + toolName;
    }

    /**
     * 把「停在人工确认点」这件事持久化。
     *
     * <p>只保存工具名：确认信息要能被下一次执行读到，让界面区分「停在待确认」
     * 与「已经确认过」；但描述里通常带着生成参数或提示词，属于私人正文，
     * 不写进状态列。
     */
    private void persistAwaitingConfirmation(String threadId, Long userId,
                                             InterruptionMetadata interruption) {
        String summary = interruption.toolFeedbacks().stream()
                .map(InterruptionMetadata.ToolFeedback::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("\n"));
        runStateStore.updateStatus(threadId, userId, RunStatus.AWAITING_CONFIRMATION, summary);
    }

    /**
     * 构造带归属信息的执行配置。
     *
     * <p>归属放在 metadata 里交给 Checkpoint 备份做条件写入；threadId 本身
     * 由调用方在进入这里之前已经核对过。
     */
    private RunnableConfig configFor(String threadId, Long userId, List<String> mcpServerCodes) {
        // 世代在本执行开始时签发：它是跨实例的所有权凭据，本执行的检查点
        // 只能以它写入；上一轮执行的迟到载荷从此写不进共享状态。
        long epoch = runStateStore.beginExecution(threadId, userId);
        // 工具环境与检查点在同一份配置里声明：DualCheckpointSaver 会把它们与
        // 载荷、版本放进同一条 UPDATE，环境与恢复点永远同版本配对。
        // 「没有 MCP」必须显式记成 []，NULL 只留给「没有声明」的续跑。
        return RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, userId)
                .addMetadata(DualCheckpointSaver.RUN_EPOCH_METADATA_KEY, epoch)
                .addMetadata(DualCheckpointSaver.MCP_SERVER_CODES_METADATA_KEY,
                        JsonUtils.toJson(mcpServerCodes == null ? List.of() : mcpServerCodes))
                .build();
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
     * 构建支持 MCP 工具的 Agent
     * 合并内置工具和 MCP 回调
     */
    private ReactAgent buildMcpAgent(ToolCallback[] mcpCallbacks) {
        HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
                .approvalOn("generateImages", ToolConfig.builder()
                        .description("即将使用 AIGC 生成图片，需要用户确认")
                        .build())
                .build();

        return ReactAgent.builder()
                .name("image_assistant_mcp")
                .model(chatModel)
                .instruction(AGENT_INSTRUCTION)
                .methodTools(imageSearchTool, imageGeneratorTool, qualityEvaluatorTool, similarImageSearchTool, imageFeatureAnalyzerTool)
                .tools(mcpCallbacks)
                .hooks(List.of(
                        hitlHook,
                        iterationControlHook,
                        messageTrimmingHook,
                        summarizationHook,
                        ModelCallLimitHook.builder().runLimit(15).build()
                ))
                // MCP 工具同样经过台账：外部动作不因来源不同而失去核对能力
                .interceptors(List.of(actionInterceptor))
                // 与内置 agent 同一条策略，续跑重建时也不会退回「失败即成功」
                .toolExecutionExceptionProcessor(AgentToolExceptionPolicy.processor())
                .saver(checkpointSaver)
                .build();
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
     * 支持中断的调用方法。
     *
     * <p>同步入口同样先绑定/核对归属，并在进入模型前落一条用户消息；
     * 归属不成立时抛出业务异常，不产生任何模型或工具执行。
     *
     * @return 执行输出；执行期异常返回空
     */
    public Optional<NodeOutput> invokeAndGetOutput(String message, String threadId, Long userId) {
        sessionGuard.bindOwner(threadId, userId, message);
        // 普通对话入口同样受待核对暂停约束：否则用户再发一条消息就能绕过暂停
        recoveryService.requireNoPendingReview(threadId, userId);
        RunnableConfig config = configFor(threadId, userId, List.of());
        chatHistoryService.saveUserMessage(threadId, userId, message);
        try {
            return agent.invokeAndGetOutput(message, config);
        } catch (PendingReviewException e) {
            // 这是 controller 的 /image/chat 入口：返回空会被说成「处理完成」，
            // 与其它入口的暂停语义不一致
            runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                    pauseForReview(e.getToolName()));
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该步骤的外部执行结果未知，已暂停，请核对后再继续");
        } catch (Exception e) {
            log.error("Agent 执行失败, threadId={}, userId={}", threadId, userId, e);
            return Optional.empty();
        }
    }
}
