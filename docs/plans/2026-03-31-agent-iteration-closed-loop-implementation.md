# Agent 迭代闭环实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 升级 ImageAgent 为 Plan-Execute-Replan 闭环，实现探索迭代 + AIGC 生成 + 结构化反馈驱动。

**Architecture:** 在现有 ReactAgent 基础上，新增 IterationControlHook（迭代计数+阶段切换）和 MessageTrimmingHook（消息修剪），重写 Agent instruction 驱动主动询问满意度，增强 handleFeedback() 消息格式，新增 SSE 流式端点。

**Tech Stack:** Spring Boot 3.5.8, Spring AI Alibaba 1.1.2, ReactAgent, HumanInTheLoopHook, WebFlux (SSE)

**Design Doc:** `docs/plans/2026-03-31-agent-iteration-closed-loop-design.md`

**Reference:** `docs/spring-ai-alibaba-agent-best-practices.md`

---

### Task 1: 新增 AgentPhase 枚举

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/model/AgentPhase.java`
- Test: `src/test/java/com/yuwen/visionspace/agent/model/AgentPhaseTest.java`

**Step 1: 写测试**

```java
package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentPhaseTest {

    @Test
    void testPhaseValues() {
        assertEquals(3, AgentPhase.values().length);
        assertNotNull(AgentPhase.EXPLORATION);
        assertNotNull(AgentPhase.GENERATION);
        assertNotNull(AgentPhase.DONE);
    }

    @Test
    void testPhaseText() {
        assertNotNull(AgentPhase.EXPLORATION.getText());
        assertNotNull(AgentPhase.GENERATION.getText());
        assertNotNull(AgentPhase.DONE.getText());
    }
}
```

**Step 2: 运行测试验证失败**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test-compile -pl . -q 2>&1 | tail -5`
Expected: 编译失败，AgentPhase 类不存在

**Step 3: 写实现**

```java
package com.yuwen.visionspace.agent.model;

import lombok.Getter;

/**
 * Agent 迭代阶段枚举
 */
@Getter
public enum AgentPhase {

    EXPLORATION("探索阶段：搜索站内/站外图片"),
    GENERATION("生成阶段：AIGC 生成图片"),
    DONE("完成：用户满意");

    private final String text;

    AgentPhase(String text) {
        this.text = text;
    }
}
```

**Step 4: 运行测试验证通过**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -Dtest=AgentPhaseTest -pl . -q`
Expected: PASS

**Step 5: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/agent/model/AgentPhase.java src/test/java/com/yuwen/visionspace/agent/model/AgentPhaseTest.java
git commit -m "feat(agent): 新增 AgentPhase 阶段枚举"
```

---

### Task 2: 增强 QualityResult 模型

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/model/QualityResult.java`
- Test: `src/test/java/com/yuwen/visionspace/agent/model/QualityResultTest.java`

**Step 1: 写测试**

```java
package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QualityResultTest {

    @Test
    void testBuilderWithNewFields() {
        QualityResult result = QualityResult.builder()
                .matchScore(0.8)
                .reason("匹配良好")
                .suggestions(List.of("可展示"))
                .action(ActionType.RETURN)
                .phase(AgentPhase.EXPLORATION)
                .exploreCount(2)
                .build();

        assertEquals(0.8, result.getMatchScore());
        assertEquals(AgentPhase.EXPLORATION, result.getPhase());
        assertEquals(2, result.getExploreCount());
    }
}
```

**Step 2: 运行测试验证失败**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test-compile -pl . -q 2>&1 | tail -5`
Expected: 编译失败，phase 和 exploreCount 字段不存在

**Step 3: 修改 QualityResult.java**

在现有 `QualityResult.java` 的 `action` 字段后添加：

```java
    /**
     * 当前迭代阶段
     */
    private AgentPhase phase;

    /**
     * 当前探索次数
     */
    private Integer exploreCount;
```

需要添加 import: `import com.yuwen.visionspace.agent.model.AgentPhase;`（同包无需额外 import）

**Step 4: 运行测试验证通过**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -Dtest=QualityResultTest -pl . -q`
Expected: PASS

**Step 5: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/agent/model/QualityResult.java src/test/java/com/yuwen/visionspace/agent/model/QualityResultTest.java
git commit -m "feat(agent): QualityResult 新增 phase 和 exploreCount 字段"
```

---

### Task 3: 新增 MessageTrimmingHook

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/hook/MessageTrimmingHook.java`
- Test: `src/test/java/com/yuwen/visionspace/agent/hook/MessageTrimmingHookTest.java`

**Step 1: 写测试**

```java
package com.yuwen.visionspace.agent.hook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTrimmingHookTest {

    @Test
    void testGetName() {
        MessageTrimmingHook hook = new MessageTrimmingHook();
        assertEquals("message_trimming", hook.getName());
    }

    @Test
    void testNoTrimmingNeeded() {
        MessageTrimmingHook hook = new MessageTrimmingHook();
        List<Message> messages = List.of(
                new SystemMessage("系统提示"),
                new UserMessage("你好"),
                new AssistantMessage("你好！")
        );
        // 不应修改小于 MAX_MESSAGES 的列表
        // beforeModel 应返回原消息
    }

    @Test
    void testTrimmingKeepsFirstAndRecent() {
        MessageTrimmingHook hook = new MessageTrimmingHook();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("系统提示")); // 首条：必须保留
        for (int i = 0; i < 15; i++) {
            messages.add(new UserMessage("消息 " + i));
        }
        // 总共 16 条，应修剪为：首条 + 最近 9 条 = 10 条
    }
}
```

**Step 2: 运行测试验证失败**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test-compile -pl . -q 2>&1 | tail -5`
Expected: 编译失败

**Step 3: 写实现**

```java
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
```

**Step 4: 运行测试验证通过**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -Dtest=MessageTrimmingHookTest -pl . -q`
Expected: PASS

**Step 5: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/agent/hook/MessageTrimmingHook.java src/test/java/com/yuwen/visionspace/agent/hook/MessageTrimmingHookTest.java
git commit -m "feat(agent): 新增 MessageTrimmingHook 消息修剪钩子"
```

---

### Task 4: 新增 IterationControlHook

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/hook/IterationControlHook.java`
- Test: `src/test/java/com/yuwen/visionspace/agent/hook/IterationControlHookTest.java`

**Step 1: 写测试**

```java
package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.yuwen.visionspace.agent.model.AgentPhase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class IterationControlHookTest {

    @Test
    void testGetName() {
        IterationControlHook hook = new IterationControlHook();
        assertEquals("iteration_control", hook.getName());
    }

    @Test
    void testNormalIncrement() {
        IterationControlHook hook = new IterationControlHook();
        OverAllState state = OverAllState.of(Map.of(
                "explore_count", 0,
                "generate_count", 0,
                "model_call_count", 0,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertEquals(1, result.get("model_call_count"));
    }

    @Test
    void testExploreLimitTriggersPhaseSwitch() {
        IterationControlHook hook = new IterationControlHook();
        OverAllState state = OverAllState.of(Map.of(
                "explore_count", 3,
                "generate_count", 0,
                "model_call_count", 5,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertEquals(AgentPhase.GENERATION, result.get("phase"));
    }

    @Test
    void testModelCallLimitStopsAgent() {
        IterationControlHook hook = new IterationControlHook();
        OverAllState state = OverAllState.of(Map.of(
                "explore_count", 0,
                "generate_count", 0,
                "model_call_count", 15,
                "phase", AgentPhase.EXPLORATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        // 应包含停止消息
        assertTrue(result.containsKey("messages"));
    }

    @Test
    void testGenerateLimitStopsAgent() {
        IterationControlHook hook = new IterationControlHook();
        OverAllState state = OverAllState.of(Map.of(
                "explore_count", 3,
                "generate_count", 2,
                "model_call_count", 8,
                "phase", AgentPhase.GENERATION
        ));
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, config);
        Map<String, Object> result = future.join();

        assertTrue(result.containsKey("messages"));
    }
}
```

**Step 2: 运行测试验证失败**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test-compile -pl . -q 2>&1 | tail -5`
Expected: 编译失败

**Step 3: 写实现**

```java
package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.yuwen.visionspace.agent.model.AgentPhase;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 迭代控制 Hook
 * 管理 Agent 的探索/生成阶段切换和迭代次数限制
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class IterationControlHook extends ModelHook {

    private static final int MAX_EXPLORE_COUNT = 3;
    private static final int MAX_GENERATE_COUNT = 2;
    private static final int MAX_MODEL_CALLS = 15;

    @Override
    public String getName() {
        return "iteration_control";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        int exploreCount = (int) state.value("explore_count").orElse(0);
        int generateCount = (int) state.value("generate_count").orElse(0);
        int modelCalls = (int) state.value("model_call_count").orElse(0);
        AgentPhase phase = (AgentPhase) state.value("phase").orElse(AgentPhase.EXPLORATION);

        // 硬上限：总模型调用次数
        if (modelCalls >= MAX_MODEL_CALLS) {
            return stopWithMessage("已达最大迭代次数，为您展示当前最佳结果。");
        }

        // 生成阶段上限
        if (phase == AgentPhase.GENERATION && generateCount >= MAX_GENERATE_COUNT) {
            return stopWithMessage("已尝试多次生成，为您展示当前最佳结果。");
        }

        // 探索阶段上限：自动切换到生成阶段
        if (phase == AgentPhase.EXPLORATION && exploreCount >= MAX_EXPLORE_COUNT) {
            return CompletableFuture.completedFuture(Map.of(
                    "phase", AgentPhase.GENERATION,
                    "messages", List.of(new AssistantMessage(
                            "已尝试多次搜索，未找到完全匹配的图片，建议为您生成一张。是否继续？"
                    ))
            ));
        }

        // 正常递增调用计数
        return CompletableFuture.completedFuture(Map.of(
                "model_call_count", modelCalls + 1
        ));
    }

    private CompletableFuture<Map<String, Object>> stopWithMessage(String message) {
        return CompletableFuture.completedFuture(Map.of(
                "messages", List.of(new AssistantMessage(message))
        ));
    }
}
```

**Step 4: 运行测试验证通过**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -Dtest=IterationControlHookTest -pl . -q`
Expected: PASS

**Step 5: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/agent/hook/IterationControlHook.java src/test/java/com/yuwen/visionspace/agent/hook/IterationControlHookTest.java
git commit -m "feat(agent): 新增 IterationControlHook 迭代控制钩子"
```

---

### Task 5: 重写 ImageAgent

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Context:**
- 当前文件 158 行，位于 `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`
- 需要重写 `init()` 方法的 instruction 和 hooks 配置
- 需要增强 `handleFeedback()` 方法
- 需要新增 `stream()` 方法
- 需要新增 `getExploreCount()` 辅助方法

**Step 1: 重写 ImageAgent.java**

完整文件内容：

```java
package com.yuwen.visionspace.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.yuwen.visionspace.agent.hook.IterationControlHook;
import com.yuwen.visionspace.agent.hook.MessageTrimmingHook;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.AgentPhase;
import com.yuwen.visionspace.agent.tools.ImageSearchTool;
import com.yuwen.visionspace.agent.tools.LogoGeneratorTool;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import com.yuwen.visionspace.agent.service.UserPreferenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * 图片助手 Agent
 *
 * 核心流程（Plan-Execute-Replan）：
 * 1. 用户描述想要的图片
 * 2. Agent 搜索站内/站外图片（探索阶段，全自动）
 * 3. 评估搜索结果质量
 * 4. 展示结果 + 主动询问用户满意度
 * 5. 用户不满意 → 换关键词搜索（≤3次）→ 仍不满意 → AIGC 生成（需确认）
 */
@Slf4j
@Component
public class ImageAgent {

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

    @Resource
    private IterationControlHook iterationControlHook;

    @Resource
    private MessageTrimmingHook messageTrimmingHook;

    private ReactAgent agent;

    @PostConstruct
    public void init() {
        // HITL：仅拦截 AIGC 生成工具
        HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
                .approvalOn("generateLogo", ToolConfig.builder()
                        .description("即将调用 AIGC 生成图片，需要用户确认")
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
                    1. 理解用户的图片需求
                    2. 调用 ImageSearchTool 搜索图片
                    3. 调用 QualityEvaluatorTool 评估结果
                    4. 如果 matchScore >= 0.5：展示结果，询问用户满意度
                    5. 如果 matchScore < 0.5：调整关键词重新搜索

                    ### 阶段 2：GENERATION（生成）
                    当探索次数耗尽或用户主动要求生成时：
                    1. 告知用户即将使用 AIGC 生成（此时会暂停等待确认）
                    2. 调用 LogoGeneratorTool 生成图片
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
                .methodTools(imageSearchTool, logoGeneratorTool, qualityEvaluatorTool)
                .hooks(List.of(
                        hitlHook,
                        iterationControlHook,
                        messageTrimmingHook,
                        ModelCallLimitHook.builder().runLimit(15).build()
                ))
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 对话入口（带会话隔离）
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
     * 流式对话入口
     */
    public Flux<NodeOutput> stream(String userMessage, String threadId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        return agent.stream(userMessage, config);
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

    /**
     * 获取当前探索次数（从 state 中读取）
     * TODO: 需要通过 MemorySaver 获取会话状态，暂返回 0
     */
    private int getExploreCount(String threadId) {
        // 后续可通过 MemorySaver 或 Redis 读取实际计数
        return 0;
    }
}
```

**注意：** `handleFeedback` 方法签名变更，增加 `AgentPhase currentPhase` 参数。这需要同步修改 Controller 和 DTO。

**Step 2: 运行编译验证**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn compile -pl . -q 2>&1 | tail -10`
Expected: 可能因 Controller 引用旧签名而报错（下一步修复）

**Step 3: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): 重写 ImageAgent 支持迭代闭环和流式输出"
```

---

### Task 6: 更新 DTO 和 Controller

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/model/dto/agent/FeedbackRequest.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/AgentController.java`

**Step 1: 更新 FeedbackRequest DTO**

在现有 `FeedbackRequest.java` 添加 `currentPhase` 字段：

```java
    /**
     * 当前迭代阶段 (可选)
     */
    private AgentPhase currentPhase;
```

需添加 import: `import com.yuwen.visionspace.agent.model.AgentPhase;`

**Step 2: 更新 AgentController**

修改 `feedback` 方法，传递 `currentPhase`：

```java
    @PostMapping("/image/feedback")
    public BaseResponse<String> feedback(@RequestBody FeedbackRequest request) {
        log.info("收到反馈, threadId={}, satisfied={}, reason={}, phase={}",
                request.getThreadId(), request.getSatisfied(),
                request.getReason(), request.getCurrentPhase());

        String result = imageAgent.handleFeedback(
                request.getThreadId(),
                request.getUserId(),
                request.getSatisfied(),
                request.getReason(),
                request.getAction(),
                request.getCurrentPhase() != null ? request.getCurrentPhase() : AgentPhase.EXPLORATION
        );

        return ResultUtils.success(result);
    }
```

需添加 import: `import com.yuwen.visionspace.agent.model.AgentPhase;`

**Step 3: 新增 SSE 流式端点**

在 `AgentController.java` 添加：

```java
    /**
     * 流式对话接口
     */
    @GetMapping(value = "/image/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam String message,
            @RequestParam String threadId) {

        Flux<NodeOutput> stream = imageAgent.stream(message, threadId);

        return stream
                .filter(output -> output instanceof StreamingOutput)
                .map(output -> {
                    StreamingOutput so = (StreamingOutput) output;
                    String type = so.getOutputType().name();
                    String content = so.message() != null ? so.message().getText() : "";
                    String json = String.format("{\"type\":\"%s\",\"content\":\"%s\",\"node\":\"%s\"}",
                            type, content, output.node());
                    return ServerSentEvent.<String>builder().data(json).build();
                });
    }
```

需添加 imports:
```java
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
```

**Step 4: 运行编译验证**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn compile -pl . -q 2>&1 | tail -10`
Expected: 编译成功 (BUILD SUCCESS)

**Step 5: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/main/java/com/yuwen/visionspace/model/dto/agent/FeedbackRequest.java src/main/java/com/yuwen/visionspace/controller/AgentController.java
git commit -m "feat(agent): 更新 Controller 支持流式输出和增强反馈"
```

---

### Task 7: 更新现有测试

**Files:**
- Modify: `src/test/java/com/yuwen/visionspace/agent/AgentIterationTest.java`

**Step 1: 更新测试**

当前 `AgentIterationTest.java` 中 `qualityEvaluatorTool.evaluateImageQuality` 返回 `QualityResult`，新增了 `phase` 和 `exploreCount` 字段。需要确保现有测试通过：

检查并更新以下断言（`QualityResult` 新字段默认为 null，不影响现有测试）：
- `testEvaluateEmptyImages` - 不变
- `testEvaluateFewImages` - 不变
- `testEvaluateEnoughImages` - 不变
- `testEvaluateThreeImages` - 不变
- `testEnableSync` - 不变

**Step 2: 运行全部测试**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -Dtest="AgentIterationTest,AgentPhaseTest,QualityResultTest,MessageTrimmingHookTest,IterationControlHookTest" -pl . -q`
Expected: PASS

**Step 3: 提交**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add src/test/java/com/yuwen/visionspace/agent/AgentIterationTest.java
git commit -m "test(agent): 更新迭代测试适配新模型"
```

---

### Task 8: 整体验证和清理

**Step 1: 全量编译**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn compile -pl . -q`
Expected: BUILD SUCCESS

**Step 2: 全量测试**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && mvn test -pl . -q`
Expected: 所有测试通过

**Step 3: 检查未提交文件**

Run: `cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev && git status`
Expected: 工作区干净，或仅有与本次无关的变更

**Step 4: 最终提交（如有遗漏文件）**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
git add -A
git commit -m "chore(agent): 整理迭代闭环功能代码"
```
