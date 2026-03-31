# Spring AI Alibaba Agent 最佳实践

> 基于 Spring AI Alibaba 1.1.2 官方文档整理，面向 VisionSpace 图片助手 Agent 项目。

---

## 1. ReactAgent 核心模式

### 1.1 ReAct 循环

ReactAgent 基于 **ReAct（Reasoning + Acting）** 范式，在 Graph 运行时中执行以下循环：

```
思考（Reasoning） → 行动（Acting/Tool Call） → 观察（Observation） → 迭代
```

Agent 持续循环直到：模型输出最终答案，或达到迭代限制。

### 1.2 Agent 构建模式

```java
// 基础构建
ReactAgent agent = ReactAgent.builder()
    .name("image_assistant")
    .model(chatModel)
    .instruction("你是一个图片助手...")
    .methodTools(imageSearchTool, logoGeneratorTool)
    .hooks(humanInTheLoopHook)
    .saver(new MemorySaver())
    .build();

// 带高级配置
ReactAgent agent = ReactAgent.builder()
    .name("image_assistant")
    .model(chatModel)
    .instruction(instruction)
    .methodTools(tools...)
    .interceptors(new ToolErrorInterceptor(), new GuardrailInterceptor())
    .hooks(ModelCallLimitHook.builder().runLimit(5).build())
    .saver(redisSaver)  // 生产环境用 Redis
    .build();
```

### 1.3 调用方式

```java
// 方式 1：简单调用，返回文本
AssistantMessage response = agent.call("帮我找一张猫的图片");

// 方式 2：带会话隔离
RunnableConfig config = RunnableConfig.builder().threadId("user_123").build();
AssistantMessage response = agent.call("帮我找一张猫的图片", config);

// 方式 3：获取完整状态
Optional<OverAllState> result = agent.invoke("帮我找一张猫的图片");

// 方式 4：支持中断的调用（用于 Human-in-the-Loop）
Optional<NodeOutput> output = agent.invokeAndGetOutput(message, config);
```

---

## 2. Tool 开发最佳实践

### 2.1 声明式工具（推荐）

```java
@Component
public class ImageSearchTool {

    @Tool(description = "搜索图片：根据关键词在站内和站外搜索图片资源")
    public List<ImageResource> searchImages(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "图片类别：CONTENT/LOGO/ILLUSTRATION") String category) {
        // 实现搜索逻辑
        return results;
    }
}
```

**关键规则：**
- `description` 是模型理解工具用途的唯一依据，必须清晰完整
- 参数用 `@ToolParam` 标注描述，帮助模型正确传参
- 返回值必须是可序列化类型
- 工具名称默认为方法名，必须唯一

### 2.2 工具错误处理

```java
public class ToolErrorInterceptor extends ToolInterceptor {
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        try {
            return handler.call(request);
        } catch (Exception e) {
            // 返回友好的错误信息，而非让 Agent 崩溃
            return ToolCallResponse.of(
                request.getToolCallId(),
                request.getToolName(),
                "Tool failed: " + e.getMessage()
            );
        }
    }

    @Override
    public String getName() { return "ToolErrorInterceptor"; }
}
```

### 2.3 工具分类

| 类型 | 用途 | 示例 |
|------|------|------|
| 信息检索 | 从外部获取数据 | ImageSearchTool, VectorSearchTool |
| 动作执行 | 执行操作/变更 | LogoGeneratorTool, FileUploadTool |

---

## 3. Hook 与 Interceptor 模式

### 3.1 Hook 类型选择

| Hook 类型 | 执行频率 | 适用场景 |
|-----------|---------|---------|
| `AgentHook` | 每次 Agent 调用执行 1 次 | 全局日志、初始化、清理 |
| `MessagesModelHook` | 每次 reasoning-acting 迭代执行 | 消息修剪、上下文注入、动态提示 |
| `ModelHook` | 每次 reasoning-acting 迭代执行 | 自定义停止条件、状态检查 |

### 3.2 消息修剪（防止上下文溢出）

```java
@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {
    private static final int MAX_MESSAGES = 10;

    @Override
    public String getName() { return "message_trimming"; }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages.size() > MAX_MESSAGES) {
            Message firstMsg = previousMessages.get(0); // 保留系统提示
            List<Message> recentMessages = previousMessages.subList(
                previousMessages.size() - MAX_MESSAGES + 1,
                previousMessages.size()
            );
            List<Message> trimmed = new ArrayList<>();
            trimmed.add(firstMsg);
            trimmed.addAll(recentMessages);
            return new AgentCommand(trimmed, UpdatePolicy.REPLACE);
        }
        return new AgentCommand(previousMessages);
    }
}
```

### 3.3 自定义停止条件

```java
@HookPositions({HookPosition.BEFORE_MODEL})
public class CustomStopConditionHook extends ModelHook {
    @Override
    public String getName() { return "custom_stop_condition"; }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        boolean answerFound = (Boolean) state.value("answer_found").orElse(false);
        int errorCount = (Integer) config.context().get("error_count").orElse(0);

        if (answerFound || errorCount > 3) {
            List<Message> messages = new ArrayList<>();
            messages.add(new AssistantMessage(
                answerFound ? "已找到答案，Agent 执行完成。"
                            : "错误次数过多，Agent 执行终止。"
            ));
            return CompletableFuture.completedFuture(Map.of("messages", messages));
        }
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

### 3.4 动态提示词（Context Engineering）

```java
public class DynamicPromptInterceptor extends ModelInterceptor {
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String userRole = (String) request.getContext().getOrDefault("user_role", "default");
        String dynamicPrompt = switch (userRole) {
            case "expert" -> "使用专业术语，深入技术细节";
            case "beginner" -> "使用简单语言，解释基础概念";
            default -> "保持友好和专业";
        };

        SystemMessage enhancedSystemMessage = new SystemMessage(
            request.getSystemMessage().getText() + "\n\n" + dynamicPrompt
        );

        ModelRequest modified = ModelRequest.builder(request)
            .systemMessage(enhancedSystemMessage)
            .build();
        return handler.call(modified);
    }

    @Override
    public String getName() { return "DynamicPromptInterceptor"; }
}
```

### 3.5 迭代控制

```java
// 使用内置的迭代限制 Hook
ReactAgent agent = ReactAgent.builder()
    .name("my_agent")
    .model(chatModel)
    .hooks(ModelCallLimitHook.builder().runLimit(5).build())  // 最多调用 5 次模型
    .saver(new MemorySaver())
    .build();
```

---

## 4. Memory 管理策略

### 4.1 短期记忆（会话内）

```java
// 开发环境：内存存储
ReactAgent agent = ReactAgent.builder()
    .saver(new MemorySaver())
    .build();

// 生产环境：Redis 持久化
RedisSaver redisSaver = new RedisSaver(redissonClient);
ReactAgent agent = ReactAgent.builder()
    .saver(redisSaver)
    .build();

// 通过 threadId 隔离会话
RunnableConfig config = RunnableConfig.builder()
    .threadId("user_123")  // 不同用户/对话使用不同 threadId
    .build();
```

### 4.2 长期记忆（跨会话）

```java
// 使用 Store 接口存储跨会话数据
// namespace 按 [用户ID, 领域] 组织
store.putItem(StoreItem.of(
    List.of("user_123", "preferences"),  // namespace
    "image_style",                        // key
    Map.of("style", "minimal", "colors", List.of("blue", "white"))  // value
));

// 在工具中访问 Store
public class UserPreferenceTool {
    @Tool(description = "获取用户图片偏好")
    public String getUserPreferences(ToolContext context) {
        RunnableConfig config = (RunnableConfig) context.getContext().get("config");
        Store store = config.store();
        StoreItem item = store.getItem(
            List.of("user_123", "preferences"),
            "image_style"
        );
        return item != null ? item.value().toString() : "无偏好记录";
    }
}
```

### 4.3 消息管理策略

| 策略 | 实现方式 | 适用场景 |
|------|---------|---------|
| 修剪（Trim） | 保留首条 + 最近 N 条 | 长对话保持上下文 |
| 删除（Delete） | 移除过期消息 | 清理不相关信息 |
| 摘要（Summarize） | 压缩历史消息为摘要 | 超长对话压缩 |
| 自定义过滤 | 按条件过滤消息 | 特定业务需求 |

---

## 5. Human-in-the-Loop 模式

### 5.1 基础配置

```java
HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
    .approvalOn("evaluateImageQuality", ToolConfig.builder()
        .description("搜索结果评估完成，需要用户确认是否满意")
        .build())
    .build();

ReactAgent agent = ReactAgent.builder()
    .name("image_assistant")
    .model(chatModel)
    .hooks(hitlHook)
    .saver(new MemorySaver())  // HITL 必须配置 saver
    .build();
```

### 5.2 中断-恢复流程

```java
// 第一次调用：触发中断
RunnableConfig config = RunnableConfig.builder().threadId("session_1").build();
Optional<NodeOutput> output = agent.invokeAndGetOutput("帮我找猫的图片", config);

// 检查是否被中断
if (output.isPresent() && output.get() instanceof InterruptionMetadata) {
    InterruptionMetadata metadata = (InterruptionMetadata) output.get();
    // 等待用户决策...
}

// 用户决策后，恢复执行
InterruptionMetadata feedback = InterruptionMetadata.builder()
    .toolFeedback(InterruptionMetadata.ToolFeedback.builder()
        .toolCallId(toolCallId)
        .result(FeedbackResult.APPROVED)  // APPROVED / EDITED / REJECTED
        .build())
    .build();

RunnableConfig resumeConfig = RunnableConfig.builder()
    .threadId("session_1")
    .addMetadata(HumanInTheLoopHook.HUMAN_FEEDBACK_METADATA_KEY, feedback)
    .build();

Optional<NodeOutput> resumedOutput = agent.invokeAndGetOutput("", resumeConfig);
```

### 5.3 三种决策类型

| 决策 | 含义 | 用途 |
|------|------|------|
| `APPROVED` | 按原样执行 | 用户满意，继续 |
| `EDITED` | 修改后执行 | 用户微调参数 |
| `REJECTED` | 拒绝执行 | 用户不满意，附带反馈原因 |

---

## 6. Context Engineering（上下文工程）

> **上下文工程是 Agent 可靠性的头号障碍**。大多数 Agent 失败不是因为 LLM 能力不足，而是提供了错误的上下文。

### 6.1 三种上下文类型

| 类型 | 生命周期 | 内容 |
|------|---------|------|
| Model Context | 瞬态（单次调用） | 系统提示、消息历史、工具、模型配置 |
| Tool Context | 持久（会话级） | 状态、存储、运行时上下文 |
| Lifecycle Context | 持久（会话级） | 摘要、护栏、日志（模型和工具调用之间） |

### 6.2 三种数据来源

| 来源 | 作用域 | 示例 |
|------|--------|------|
| Runtime Context | 静态配置/会话 | 用户 ID、API Key、DB 连接、权限 |
| State | 短期记忆/会话 | 当前消息、上传文件、认证状态、工具结果 |
| Store | 长期记忆/跨会话 | 用户偏好、提取的知识、历史记录 |

### 6.3 关键实践

- **动态提示**：基于状态（如对话长度）调整系统提示
- **个性化提示**：从 Store 加载用户偏好注入上下文
- **消息过滤**：用 `MessageFilterInterceptor` 保留最近 N 条消息
- **工具选择控制**：用 `ContextualToolInterceptor` 根据用户角色动态控制可用工具
- **Hook 用于持久更新**（如摘要），Interceptor 用于瞬态修改

---

## 7. 多 Agent 编排模式

### 7.1 模式对比

| 模式 | 控制方式 | 适用场景 |
|------|---------|---------|
| **Tool Calling** | 中心化，Supervisor 控制 | 结构化工作流、任务编排 |
| **Handoffs** | 去中心化，Agent 之间交接 | 跨领域对话、专家切换 |
| **Sequential** | 串行执行，输出传递 | 流水线处理 |
| **Parallel** | 并行执行，结果合并 | 独立子任务同时处理 |

### 7.2 Supervisor + Tool Calling 模式（推荐用于 VisionSpace）

```java
// 子 Agent：专注搜索
ReactAgent searchAgent = ReactAgent.builder()
    .name("image_search_agent")
    .instruction("你是图片搜索专家...")
    .model(chatModel)
    .outputKey("search_result")
    .returnReasoningContents(false)  // 不暴露内部推理
    .includeContents(false)          // 不继承父上下文
    .build();

// 子 Agent：专注生成
ReactAgent generateAgent = ReactAgent.builder()
    .name("image_generate_agent")
    .instruction("你是图片生成专家...")
    .model(chatModel)
    .outputKey("generate_result")
    .build();

// 父 Agent：编排调度
ReactAgent supervisor = ReactAgent.builder()
    .name("image_supervisor")
    .instruction("""
        你是图片助手总调度。
        根据用户需求选择：
        - 搜索图片 → 调用 image_search_agent
        - 生成图片 → 调用 image_generate_agent
        """)
    .model(chatModel)
    .tools(
        AgentTool.getFunctionToolCallback(searchAgent),
        AgentTool.getFunctionToolCallback(generateAgent)
    )
    .build();
```

### 7.3 关键参数

| 参数 | 说明 | 建议 |
|------|------|------|
| `outputKey` | 子 Agent 输出的命名 | 使用有意义的名称，便于 `{outputKey}` 引用 |
| `returnReasoningContents` | 是否返回内部推理 | 设为 `false` 减少上下文膨胀 |
| `includeContents` | 是否继承父上下文 | 设为 `false` 让子 Agent 专注自身任务 |

---

## 8. Workflow 工作流

### 8.1 核心概念

- **State**：`Map<String, Object>`，节点间传递数据的核心载体
- **Node**：执行单元，接收 State、返回 State 更新
- **Edge**：控制流，支持固定路由和条件分支

### 8.2 构建 Workflow

```java
StateGraph graph = new StateGraph("image_workflow")
    .addNode("understand_intent", intentNode)
    .addNode("search_images", searchNode)
    .addNode("evaluate_quality", evaluateNode)
    .addNode("generate_image", generateNode)
    .addEdge(START, "understand_intent")
    .addEdge("understand_intent", "search_images")
    .addConditionalEdges("evaluate_quality", state -> {
        double score = (Double) state.value("matchScore").orElse(0.0);
        if (score >= 0.5) return "show_results";
        return "generate_image";
    }, Map.of(
        "show_results", END,
        "generate_image", "generate_image"
    ))
    .addEdge("generate_image", END);

CompiledGraph workflow = graph.compile(CompileConfig.builder()
    .saver(new MemorySaver())
    .build());
```

### 8.3 Agent 作为 Workflow 节点

```java
// 将 ReactAgent 嵌入 Workflow
graph.addNode("search_agent", searchAgent.asNode(false, false));
// 参数: includeContents=false, returnReasoningContents=false
```

---

## 9. RAG 检索增强生成

### 9.1 三种 RAG 架构

| 架构 | 控制度 | 灵活性 | 延迟 | 适用场景 |
|------|--------|--------|------|---------|
| Two-Step RAG | 高 | 低 | 快 | FAQ、文档问答 |
| **Agentic RAG** | 低 | 高 | 可变 | 研究助手、图片搜索 |
| Hybrid RAG | 中 | 中 | 可变 | 质量验证型问答 |

### 9.2 Agentic RAG 实现（推荐用于 VisionSpace）

```java
// 方式 1：通过 Hook 自动注入检索结果
@HookPositions({HookPosition.BEFORE_AGENT})
public class RAGRetrievalHook extends AgentHook {
    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        String query = extractQuery(state);
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(5)
        );
        // 注入检索结果到上下文
        return CompletableFuture.completedFuture(Map.of(
            "retrieved_context", docs.stream().map(Document::getText).toList()
        ));
    }
}

// 方式 2：作为工具供 Agent 按需调用
@Tool(description = "搜索站内图片向量数据库")
public List<ImageResource> vectorSearch(
    @ToolParam(description = "搜索查询") String query) {
    return vectorStore.similaritySearch(SearchRequest.query(query).withTopK(10));
}
```

### 9.3 RAG 最佳实践

- 用 `AgentHook`（BEFORE_AGENT）做一次性检索，避免每次 reasoning 循环都检索
- 用 `ModelInterceptor` 做每次模型调用前的检索
- 实现查询重写/扩展提高检索相关性
- 缓存常见查询结果
- 控制返回文档数量，避免上下文溢出

---

## 10. 流式输出

### 10.1 OutputType 分类

| OutputType | 说明 |
|------------|------|
| `AGENT_MODEL_STREAMING` | 模型推理的流式增量内容 |
| `AGENT_MODEL_FINISHED` | 模型推理完成 |
| `AGENT_TOOL_STREAMING` | 工具调用的流式增量内容 |
| `AGENT_TOOL_FINISHED` | 工具调用完成 |
| `AGENT_HOOK_STREAMING` | Hook 节点流式输出 |
| `AGENT_HOOK_FINISHED` | Hook 节点完成 |

### 10.2 流式消费

```java
Flux<NodeOutput> stream = agent.stream("帮我找一张猫的图片");
stream.subscribe(output -> {
    if (output instanceof StreamingOutput streamingOutput) {
        OutputType type = streamingOutput.getOutputType();
        Message message = streamingOutput.message();

        if (type == OutputType.AGENT_MODEL_STREAMING) {
            if (message instanceof AssistantMessage am) {
                System.out.print(am.getText());  // 增量输出
            }
        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
            if (message instanceof AssistantMessage am && am.hasToolCalls()) {
                am.getToolCalls().forEach(tc ->
                    System.out.println("[Tool Call] " + tc.name()));
            }
        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
            // 工具执行完成
        }
    }
});
```

### 10.3 消息类型识别

| 场景 | 判断条件 |
|------|---------|
| 模型普通响应 | `AssistantMessage` 且 `metadata.reasoningContent` 为空 |
| 模型 Thinking | `AssistantMessage` 且 `metadata.reasoningContent` 不为空 |
| 工具调用请求 | `AssistantMessage` 且 `hasToolCalls()` 为 `true` |
| 工具响应结果 | `ToolResponseMessage` |

---

## 11. 结构化输出

### 11.1 两种方式

```java
// 方式 1：outputType（推荐，类型安全）
ReactAgent agent = ReactAgent.builder()
    .outputType(PoemOutput.class)
    .saver(new MemorySaver())
    .build();

// 方式 2：outputSchema（灵活，支持动态 Schema）
BeanOutputConverter<TextAnalysisResult> converter =
    new BeanOutputConverter<>(TextAnalysisResult.class);
ReactAgent agent = ReactAgent.builder()
    .outputSchema(converter.getFormat())
    .saver(new MemorySaver())
    .build();
```

### 11.2 选择建议

- `outputType`：类型安全，适合结构固定的场景（**推荐**）
- `outputSchema`：灵活性高，适合动态或复杂的输出格式

---

## 12. VisionSpace ImageAgent 改进建议

基于以上最佳实践，对照当前 `ImageAgent.java` 实现，提出以下改进方向：

### 12.1 当前实现分析

| 组件 | 当前状态 | 改进方向 |
|------|---------|---------|
| ReactAgent | ✅ 基础配置 | 增加 `ModelCallLimitHook` 防止无限循环 |
| Tools | ✅ 5 个工具 | 考虑拆分为搜索 Agent 和生成 Agent |
| HITL | ✅ 基础实现 | 升级为混合模式（小迭代自动，大决策暂停） |
| Memory | ⚠️ 仅 MemorySaver | 生产环境需切换 Redis；需添加消息修剪 |
| Context Engineering | ❌ 静态提示词 | 基于用户偏好/历史动态调整 |
| 迭代控制 | ❌ 无限制 | 添加 `ModelCallLimitHook` 和自定义停止条件 |
| 流式输出 | ❌ 未实现 | 前端体验需要流式输出 |
| 反馈闭环 | ⚠️ 基础实现 | 需要结构化反馈 + 自动迭代策略 |

### 12.2 优先级排序

1. **P0 - 迭代控制**：添加 `ModelCallLimitHook`，防止无限循环
2. **P0 - 消息修剪**：添加 `MessageTrimmingHook`，防止上下文溢出
3. **P1 - 流式输出**：实现 `agent.stream()` 前端实时响应
4. **P1 - 混合式 HITL**：小迭代全自动，AIGC 生成需用户确认
5. **P2 - 动态提示**：基于用户偏好/历史加载个性化提示词
6. **P2 - 多 Agent 拆分**：搜索 Agent 和生成 Agent 分离
7. **P3 - 长期记忆**：接入 Store 实现跨会话用户偏好持久化

---

> **参考来源**：Spring AI Alibaba 官方文档 https://java2ai.com
