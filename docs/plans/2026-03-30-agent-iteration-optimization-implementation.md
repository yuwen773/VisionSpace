# Agent 迭代优化机制实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 ImageAgent 添加智能迭代优化能力，包括质量评估、用户反馈和偏好学习

**Architecture:** 采用 Spring AI Alibaba 的 ReactAgent + HumanInTheLoopHook + MemoryStore 架构，实现短期对话上下文 + 长期用户偏好的双层记忆系统

**Tech Stack:** Spring AI Alibaba 1.1.2.2, ReactAgent, HumanInTheLoopHook, MemoryStore

---

## 前置准备

### Task 0: 同步代码到 ai-dev 工作树

由于 ai-dev 工作树尚未包含 agent 代码，需要先从 feature-agent-dev 同步

**Files:**
- Sync: `src/main/java/com/yuwen/visionspace/agent/**` (从 feature-agent-dev)
- Sync: `src/main/java/com/yuwen/visionspace/model/dto/agent/**` (从 feature-agent-dev)
- Sync: `src/main/java/com/yuwen/visionspace/controller/AgentController.java` (从 feature-agent-dev)

**Step 1: 同步代码文件**

```bash
# 从 feature-agent-dev 复制 agent 相关代码到 ai-dev
cp -r ../feature-agent-dev/src/main/java/com/yuwen/visionspace/agent ./src/main/java/com/yuwen/visionspace/
cp -r ../feature-agent-dev/src/main/java/com/yuwen/visionspace/model/dto/agent ./src/main/java/com/yuwen/visionspace/model/dto/
cp ../feature-agent-dev/src/main/java/com/yuwen/visionspace/controller/AgentController.java ./src/main/java/com/yuwen/visionspace/controller/
```

**Step 2: 验证编译通过**

Run: `mvn compile -q`
Expected: 编译成功，无 ERROR

---

## Phase 1: 基础反馈机制

### Task 1: 创建 FeedbackRequest DTO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/dto/agent/FeedbackRequest.java`

**Step 1: 创建 DTO 文件**

```java
package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

/**
 * Agent 用户反馈请求
 */
@Data
public class FeedbackRequest {

    /**
     * 会话ID
     */
    private String threadId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 是否满意
     */
    private Boolean satisfied;

    /**
     * 不满意原因 (可选)
     */
    private String reason;

    /**
     * 建议动作: regenerate, research, return (可选)
     */
    private String action;

    /**
     * 用户自定义消息
     */
    private String message;
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/FeedbackRequest.java
git commit -m "feat(agent): 添加用户反馈请求 DTO"
```

---

### Task 2: 创建 AgentFeedbackController

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/controller/AgentFeedbackController.java`

**Step 1: 创建 Controller**

```java
package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.dto.agent.FeedbackRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 反馈接口
 */
@Slf4j
@RestController
@RequestMapping("/agent/image")
public class AgentFeedbackController {

    @Resource
    private com.yuwen.visionspace.agent.ImageAgent imageAgent;

    /**
     * 处理用户反馈
     */
    @PostMapping("/feedback")
    public BaseResponse<String> feedback(@RequestBody FeedbackRequest request) {
        log.info("收到反馈, threadId={}, satisfied={}, reason={}",
                request.getThreadId(), request.getSatisfied(), request.getReason());

        String result = imageAgent.handleFeedback(
                request.getThreadId(),
                request.getUserId(),
                request.getMessage(),
                request.getSatisfied(),
                request.getReason(),
                request.getAction()
        );

        return ResultUtils.success(result);
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译失败，ImageAgent 缺少 handleFeedback 方法 (预期)

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/controller/AgentFeedbackController.java
git commit -m "feat(agent): 添加反馈接口 AgentFeedbackController"
```

---

### Task 3: 修改 AgentChatRequest 添加 userId

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/model/dto/agent/AgentChatRequest.java`

**Step 1: 添加 userId 字段**

```java
// 在 AgentChatRequest.java 中添加
/**
 * 用户ID (可选，用于长期偏好学习)
 */
private String userId;
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/AgentChatRequest.java
git commit -m "feat(agent): AgentChatRequest 添加 userId 字段支持长期偏好"
```

---

## Phase 2: 质量评估

### Task 4: 创建 QualityResult 模型

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/model/QualityResult.java`

**Step 1: 创建 QualityResult**

```java
package com.yuwen.visionspace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片质量评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResult implements Serializable {

    /**
     * 匹配度评分 (0.0-1.0)
     */
    private Double matchScore;

    /**
     * 评分理由
     */
    private String reason;

    /**
     * 改进建议
     */
    private List<String> suggestions;

    /**
     * 推荐动作: return (返回结果) | regenerate (重新生成) | research (重新搜索)
     */
    private String action;
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/model/QualityResult.java
git commit -m "feat(agent): 添加 QualityResult 质量评估结果模型"
```

---

### Task 5: 创建 QualityEvaluator Tool

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/tools/QualityEvaluatorTool.java`

**Step 1: 创建 QualityEvaluator Tool**

```java
package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片质量评估工具
 * 评估搜索结果与用户意图的匹配度
 */
@Slf4j
@Component
public class QualityEvaluatorTool {

    /**
     * 评估图片列表与用户意图的匹配度
     *
     * @param images 图片列表
     * @param userIntent 用户原始意图描述
     * @return 评估结果
     */
    @Tool(description = "评估图片列表与用户意图的匹配度，返回匹配分数和改进建议")
    public QualityResult evaluateImageQuality(
            @ToolParam(description = "要评估的图片列表") List<ImageResource> images,
            @ToolParam(description = "用户的原始意图描述") String userIntent) {

        // 数量检查
        if (images == null || images.isEmpty()) {
            return QualityResult.builder()
                    .matchScore(0.0)
                    .reason("没有搜索结果")
                    .suggestions(List.of("建议使用 AIGC 生成图片"))
                    .action("regenerate")
                    .build();
        }

        int count = images.size();

        // 简单规则判断：数量 < 3 时建议生成
        if (count < 3) {
            return QualityResult.builder()
                    .matchScore(0.3)
                    .reason("搜索结果数量较少")
                    .suggestions(List.of("搜索结果不足，建议使用 AIGC 补充生成"))
                    .action("regenerate")
                    .build();
        }

        // 数量 >= 3 时，返回中等评分，让 Agent 决定
        return QualityResult.builder()
                .matchScore(0.6)
                .reason("搜索结果数量充足，可以展示给用户")
                .suggestions(List.of("可以先展示搜索结果，询问用户满意度"))
                .action("return")
                .build();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/tools/QualityEvaluatorTool.java
git commit -m "feat(agent): 添加 QualityEvaluatorTool 质量评估工具"
```

---

### Task 6: 集成 QualityEvaluatorTool 到 ImageAgent

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 修改 ImageAgent 集成质量评估**

在 ImageAgent.java 中：
1. 添加 QualityEvaluatorTool 注入
2. 在 methodTools 中添加 qualityEvaluatorTool
3. 修改 instruction 添加质量评估流程说明

```java
// 添加注入
@Resource
private QualityEvaluatorTool qualityEvaluatorTool;

// 修改 methodTools
.methodTools(imageSearchTool, logoGeneratorTool, qualityEvaluatorTool)
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 集成 QualityEvaluatorTool 到 ImageAgent"
```

---

## Phase 3: Human-in-the-Loop

### Task 7: 配置 HumanInTheLoopHook

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 添加 HumanInTheLoopHook 配置**

在 ImageAgent.java 中添加：

```java
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;

// 在 init() 方法中添加 hook
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
                - 清晰标注图片来源
                - 如果用户不满意，可以重新生成或搜索
                """)
            .methodTools(imageSearchTool, logoGeneratorTool, qualityEvaluatorTool)
            .hooks(List.of(humanInTheLoopHook))
            .saver(new MemorySaver())
            .build();
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 配置 HumanInTheLoopHook 实现人工介入"
```

---

### Task 8: 修改 Chat 接口支持 HITL 中断处理

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/AgentController.java`

**Step 1: 修改 Controller 支持中断处理**

```java
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import java.util.Optional;

// 修改 chat 方法
@PostMapping("/chat")
public BaseResponse<String> chat(@RequestBody AgentChatRequest request) {
    String threadId = request.getThreadId();
    if (threadId == null || threadId.isBlank()) {
        threadId = UUID.randomUUID().toString();
    }

    RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .build();

    log.info("Agent 对话请求, threadId={}, message={}", threadId, request.getMessage());

    // 使用 invokeAndGetOutput 而不是 call，支持中断
    Optional<NodeOutput> result = imageAgent.invokeAndGetOutput(request.getMessage(), config);

    // 检查是否返回了中断
    if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
        InterruptionMetadata interruption = (InterruptionMetadata) result.get();
        // 返回中断信息给前端
        return ResultUtils.success(buildInterruptionResponse(interruption));
    }

    // 正常返回
    if (result.isPresent()) {
        return ResultUtils.success(result.get().toString());
    }

    return ResultUtils.success("处理完成");
}

private String buildInterruptionResponse(InterruptionMetadata interruption) {
    // 构建中断响应，告知前端需要用户确认
    StringBuilder sb = new StringBuilder();
    sb.append("【需要确认】\n\n");

    for (InterruptionMetadata.ToolFeedback feedback : interruption.toolFeedbacks()) {
        sb.append("工具: ").append(feedback.getName()).append("\n");
        sb.append("评估结果: ").append(feedback.getDescription()).append("\n");
    }

    sb.append("\n请选择是否满意上述结果，或提供改进建议。");
    return sb.toString();
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功（可能有方法不存在错误，后续 Task 实现）

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/controller/AgentController.java
git commit -m "feat(agent): Chat 接口支持 HITL 中断处理"
```

---

### Task 9: 实现 handleFeedback 方法

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 添加 handleFeedback 方法**

```java
/**
 * 处理用户反馈
 */
public String handleFeedback(String threadId, String userId, String message,
                             Boolean satisfied, String reason, String action) {

    // 构建反馈消息
    String feedbackMessage;
    if (Boolean.TRUE.equals(satisfied)) {
        feedbackMessage = "用户对当前结果满意。";
    } else {
        feedbackMessage = "用户不满意，原因: " + (reason != null ? reason : "未说明");
        if ("regenerate".equals(action)) {
            feedbackMessage += "，请重新生成。";
        } else if ("research".equals(action)) {
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
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 实现 handleFeedback 方法处理用户反馈"
```

---

## Phase 4: 记忆系统

### Task 10: 配置 MemoryStore

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 添加 MemoryStore 配置**

```java
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;

// 添加 MemoryStore 字段
private MemoryStore memoryStore;

// 在 init() 中初始化
@PostConstruct
public void init() {
    // 初始化长期记忆存储
    memoryStore = new MemoryStore();

    // 创建人工介入 Hook
    HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
            .approvalOn("evaluateImageQuality", ToolConfig.builder()
                    .description("搜索结果评估完成，需要用户确认是否满意")
                    .build())
            .build();

    agent = ReactAgent.builder()
            .name("image_assistant")
            .model(chatModel)
            // ... 其他配置
            .saver(new MemorySaver())
            .build();
}

// 修改 chat 方法，传递 store
public String chat(String userMessage, String threadId, String userId) {
    RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .store(memoryStore)  // 添加 store
            .addMetadata("user_id", userId)  // 添加用户 ID
            .build();

    try {
        AssistantMessage response = agent.call(userMessage, config);
        return response.getText();
    } catch (Exception e) {
        log.error("Agent 执行失败, threadId={}", threadId, e);
        return "抱歉，处理您的请求时出现了问题：" + e.getMessage();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 集成 MemoryStore 长期记忆存储"
```

---

### Task 11: 创建 UserPreferenceService

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/service/UserPreferenceService.java`

**Step 1: 创建偏好服务**

```java
package com.yuwen.visionspace.agent.service;

import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用户偏好服务
 * 负责保存和加载用户偏好到 MemoryStore
 */
@Slf4j
@Service
public class UserPreferenceService {

    private static final String NAMESPACE_PREFIX = "preferences";

    private final MemoryStore memoryStore;

    public UserPreferenceService(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 保存用户反馈
     */
    public void saveFeedback(String userId, String action, String reason) {
        List<String> namespace = List.of(NAMESPACE_PREFIX, userId);
        String key = "user_profile";

        // 获取现有偏好
        Map<String, Object> profile = getOrCreateProfile(namespace, key);

        // 更新反馈历史
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) profile.getOrDefault("feedback_history", new ArrayList<>());
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("time", new Date().toString());
        feedback.put("action", action);
        feedback.put("reason", reason);
        history.add(feedback);
        profile.put("feedback_history", history);

        // 分析并更新偏好
        if (action != null && action.equals("regenerate") && reason != null) {
            updatePreferencesFromFeedback(profile, reason);
        }

        // 保存
        StoreItem item = StoreItem.of(namespace, key, profile);
        memoryStore.putItem(item);
        log.info("保存用户偏好, userId={}, profile={}", userId, profile);
    }

    /**
     * 获取用户偏好
     */
    public Map<String, Object> getUserPreferences(String userId) {
        List<String> namespace = List.of(NAMESPACE_PREFIX, userId);
        Optional<StoreItem> item = memoryStore.getItem(namespace, "user_profile");

        if (item.isPresent()) {
            return item.get().getValue();
        }
        return new HashMap<>();
    }

    private Map<String, Object> getOrCreateProfile(List<String> namespace, String key) {
        Optional<StoreItem> item = memoryStore.getItem(namespace, key);
        if (item.isPresent()) {
            return new HashMap<>(item.get().getValue());
        }
        return new HashMap<>();
    }

    private void updatePreferencesFromFeedback(Map<String, Object> profile, String reason) {
        // 简单的关键词提取
        List<String> likedStyles = getOrCreateList(profile, "liked_styles");
        List<String> dislikedStyles = getOrCreateList(profile, "disliked_styles");
        List<String> likedColors = getOrCreateList(profile, "liked_colors");

        // 检测颜色偏好
        if (reason.contains("暗")) {
            dislikedStyles.add("暗色调");
            likedColors.add("亮色系");
        }
        if (reason.contains("亮") || reason.contains("太亮")) {
            dislikedStyles.add("过亮色调");
        }

        // 检测风格偏好
        if (reason.contains("赛博朋克")) {
            likedStyles.add("赛博朋克");
        }
        if (reason.contains("写实")) {
            dislikedStyles.add("过于写实");
        }

        profile.put("liked_styles", likedStyles);
        profile.put("disliked_styles", dislikedStyles);
        profile.put("liked_colors", likedColors);
    }

    @SuppressWarnings("unchecked")
    private List<String> getOrCreateList(Map<String, Object> profile, String key) {
        Object value = profile.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/service/UserPreferenceService.java
git commit -m "feat(agent): 创建 UserPreferenceService 用户偏好服务"
```

---

### Task 12: 创建 UserPreferenceLoader ModelHook

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/hook/UserPreferenceLoaderHook.java`

**Step 1: 创建 ModelHook**

```java
package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 用户偏好加载 Hook
 * 在模型调用前自动加载用户偏好到上下文
 */
public class UserPreferenceLoaderHook implements ModelHook {

    @Override
    public String getName() {
        return "user_preference_loader";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
            com.alibaba.cloud.ai.graph.OverAllState state,
            RunnableConfig config) {

        // 从配置中获取用户ID
        Optional<String> userIdOpt = config.metadata("user_id");
        if (userIdOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        String userId = userIdOpt.get();

        // 获取 store
        Optional<com.alibaba.cloud.ai.graph.store.stores.Store> storeOpt = Optional.ofNullable(config.store());
        if (storeOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        com.alibaba.cloud.ai.graph.store.stores.Store store = storeOpt.get();

        // 从记忆存储中加载用户偏好
        Optional<StoreItem> itemOpt = store.getItem(List.of("preferences", userId), "user_profile");
        if (itemOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        Map<String, Object> profile = itemOpt.get().getValue();

        // 构建用户上下文
        StringBuilder context = new StringBuilder();
        context.append("\n\n【用户偏好上下文】\n");

        @SuppressWarnings("unchecked")
        List<String> likedStyles = (List<String>) profile.getOrDefault("liked_styles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> dislikedStyles = (List<String>) profile.getOrDefault("disliked_styles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> likedColors = (List<String>) profile.getOrDefault("liked_colors", Collections.emptyList());

        if (!likedStyles.isEmpty()) {
            context.append("- 用户喜欢的风格: ").append(String.join(", ", likedStyles)).append("\n");
        }
        if (!dislikedStyles.isEmpty()) {
            context.append("- 用户不喜欢的风格: ").append(String.join(", ", dislikedStyles)).append("\n");
        }
        if (!likedColors.isEmpty()) {
            context.append("- 用户喜欢的颜色: ").append(String.join(", ", likedColors)).append("\n");
        }

        if (likedStyles.isEmpty() && dislikedStyles.isEmpty() && likedColors.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        context.append("请参考用户偏好，生成更符合用户口味的内容。");

        // 返回需要注入的上下文
        Map<String, Object> result = new HashMap<>();
        result.put("user_preference_context", context.toString());

        return CompletableFuture.completedFuture(result);
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/hook/UserPreferenceLoaderHook.java
git commit -m "feat(agent): 创建 UserPreferenceLoaderHook 用户偏好加载拦截器"
```

---

### Task 13: 集成 UserPreferenceService 到 ImageAgent

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 注入 UserPreferenceService**

```java
@Resource
private UserPreferenceService userPreferenceService;

// 在 handleFeedback 中保存偏好
public String handleFeedback(String threadId, String userId, String message,
                             Boolean satisfied, String reason, String action) {

    // 保存反馈到偏好系统
    if (userId != null && !Boolean.TRUE.equals(satisfied)) {
        userPreferenceService.saveFeedback(userId, action, reason);
    }

    // ... 其余代码
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: 编译成功

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 集成 UserPreferenceService 保存用户反馈"
```

---

## 收尾

### Task 14: 创建单元测试

**Files:**
- Create: `src/test/java/com/yuwen/visionspace/agent/AgentIterationTest.java`

**Step 1: 编写测试**

```java
package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentIterationTest {

    @Resource
    private QualityEvaluatorTool qualityEvaluatorTool;

    @Test
    void testEvaluateEmptyImages() {
        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                new ArrayList<>(), "赛博朋克风格城市");
        assertEquals(0.0, result.getMatchScore());
        assertEquals("regenerate", result.getAction());
    }

    @Test
    void testEvaluateFewImages() {
        List<ImageResource> images = new ArrayList<>();
        images.add(ImageResource.builder().url("http://test.jpg").build());

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                images, "赛博朋克风格城市");
        assertEquals(0.3, result.getMatchScore());
        assertEquals("regenerate", result.getAction());
    }

    @Test
    void testEvaluateEnoughImages() {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            images.add(ImageResource.builder().url("http://test" + i + ".jpg").build());
        }

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                images, "赛博朋克风格城市");
        assertEquals(0.6, result.getMatchScore());
        assertEquals("return", result.getAction());
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -Dtest=AgentIterationTest`
Expected: 所有测试通过

**Step 3: 提交**

```bash
git add src/test/java/com/yuwen/visionspace/agent/AgentIterationTest.java
git commit -m "test(agent): 添加迭代优化机制单元测试"
```

---

### Task 15: 最终编译和测试

**Step 1: 完整编译**

Run: `mvn clean compile -q`
Expected: 编译成功，无 ERROR

**Step 2: 运行所有测试**

Run: `mvn test -q`
Expected: 所有测试通过

**Step 3: 提交所有更改**

```bash
git add -A
git commit -m "feat(agent): 完成迭代优化机制实现

- 添加 QualityEvaluatorTool 质量评估工具
- 配置 HumanInTheLoopHook 实现人工介入
- 集成 MemoryStore 长期记忆存储
- 创建 UserPreferenceService 用户偏好服务
- 实现 UserPreferenceLoaderHook 自动加载偏好

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 总结

完成上述 15 个 Task 后，你将拥有：

1. **质量评估能力** - QualityEvaluatorTool 自动评估搜索结果
2. **人工介入机制** - HumanInTheLoopHook 在关键时刻暂停等待用户确认
3. **用户反馈 API** - `/api/agent/image/feedback` 接收用户满意度反馈
4. **长期偏好学习** - MemoryStore + UserPreferenceService 记住用户喜好
5. **智能上下文注入** - UserPreferenceLoaderHook 每次调用前加载偏好
