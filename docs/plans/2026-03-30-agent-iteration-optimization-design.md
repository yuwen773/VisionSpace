# Agent 迭代优化机制设计

## 1. 概述

为 ImageAgent 添加智能迭代优化能力，让 Agent 能够：
1. 评估搜索结果质量
2. 根据质量决定是否生成
3. 支持多轮对话优化
4. 学习用户跨会话偏好

## 2. 架构设计

```
用户请求
    ↓
┌─────────────────────────────────────┐
│          ImageAgent                  │
│                                     │
│  Step 1: 数量阈值判断                │
│  - 搜索结果 < 3 张 → 直接生成        │
│  - 搜索结果 >= 3 张 → Step 2         │
│                                     │
│  Step 2: LLM 质量评估                │
│  - 调用 QualityEvaluator Tool         │
│  - 评估结果与用户意图的匹配度         │
│  - 匹配度低 → 触发 Human-in-the-Loop │
│                                     │
│  Step 3: 用户反馈 (HITL)             │
│  - 展示结果，询问是否满意             │
│  - 用户反馈 → 保存偏好 → 决定下一步  │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│          记忆系统                     │
│                                     │
│  短期记忆: MemorySaver + threadId   │
│  - 同会话内的对话历史                 │
│                                     │
│  长期记忆: MemoryStore               │
│  - namespace: ["preferences", userId]│
│  - 跨会话的用户偏好学习               │
└─────────────────────────────────────┘
```

## 3. 核心组件

### 3.1 QualityEvaluator Tool

```java
@Tool(description = "评估图片列表与用户意图的匹配度")
public QualityResult evaluateImageQuality(
    @ToolParam(description = "图片列表") List<ImageResource> images,
    @ToolParam(description = "用户的原始意图描述") String userIntent
)

// QualityResult 结构
{
  "matchScore": 0.0-1.0,        // 匹配度评分
  "reason": "描述",              // 评分理由
  "suggestions": ["建议1", ...], // 改进建议
  "action": "return|regenerate"  // 推荐动作
}
```

### 3.2 UserFeedbackService

处理用户反馈，保存到长期记忆：

```java
// 反馈请求
public record FeedbackRequest(
    String threadId,       // 会话ID
    String message,        // 用户反馈消息
    Boolean satisfied,     // 是否满意
    String reason,         // 不满意原因 (可选)
    String action          // 建议动作: regenerate, reseach, return (可选)
)

// 存储到 MemoryStore
namespace: ["preferences", userId]
{
  "liked_styles": ["赛博朋克", "霓虹"],
  "disliked_styles": ["暗色调"],
  "liked_colors": ["蓝色", "紫色"],
  "feedback_history": [
    {"time": "2026-03-30", "action": "regenerate", "reason": "颜色太暗"}
  ]
}
```

### 3.3 UserPreferenceLoader (ModelHook)

在每次模型调用前自动加载用户偏好：

```java
// beforeModel hook
- 从 MemoryStore 加载用户偏好
- 注入到 system prompt 或 context
```

## 4. Human-in-the-Loop 集成

### 4.1 中断触发场景

当 QualityEvaluator 返回 `matchScore < 0.5` 时，触发 HITL 中断，询问用户：

```
搜索结果评估：
- 匹配度: 35%
- 理由: 搜索结果偏真实风景，与"赛博朋克风格"差异较大

选项：
A) 满意，返回这些结果
B) 不满意，请重新生成
C) 换个关键词重新搜索
```

### 4.2 决策处理

| 决策 | Agent 行为 |
|-----|-----------|
|满意 | 返回当前结果 |
|不满意-生成 | 调用 LogoGeneratorTool 生成 |
|不满意-重搜 | 用新关键词重新搜索 |
|编辑反馈 | 用户自定义输入 |

## 5. API 设计

### 5.1 聊天接口 (已有)

```
POST /api/agent/image/chat
Request: { "message": "...", "threadId": "...", "userId": "..." }
Response: { "data": "...", "needFeedback": true }
```

### 5.2 反馈接口 (新增)

```
POST /api/agent/image/feedback
Request: {
  "threadId": "xxx",
  "userId": "xxx",
  "satisfied": false,
  "reason": "颜色太暗",
  "action": "regenerate"
}
Response: { "data": "好的，我来重新生成...", "needFeedback": true }
```

## 6. 数据流

```
用户: "我要一张赛博朋克风格的城市图片"

1. Agent 搜索 → 获得 8 张图片
2. Agent 调用 QualityEvaluator
   - 评估: matchScore=0.35, reason="偏真实风景"
   - 判断: 0.35 < 0.5, 触发 HITL
3. 返回给用户:
   - 展示搜索结果
   - needFeedback=true
   - 提示: "这些结果匹配度较低，是否满意？"

用户反馈: {satisfied: false, reason: "太暗", action: "regenerate"}

4. 保存反馈到 MemoryStore
   - 更新 disliked_styles: ["暗色调"]
   - 添加到 feedback_history
5. Agent 调用 LogoGeneratorTool 生成
6. 返回生成结果
```

## 7. 实现计划

### Phase 1: 基础反馈机制
- 创建 FeedbackRequest DTO
- 创建 AgentFeedbackController 接口
- 修改 ImageAgent 支持反馈处理

### Phase 2: 质量评估
- 创建 QualityEvaluator Tool
- 实现评估逻辑
- 集成到 ImageAgent

### Phase 3: Human-in-the-Loop
- 配置 HumanInTheLoopHook
- 实现中断处理逻辑
- 前端适配

### Phase 4: 记忆系统
- 配置 MemoryStore
- 实现 UserPreferenceLoader (ModelHook)
- 实现偏好保存和加载

## 8. 注意事项

1. **threshold 可配置**: 数量阈值(3)和质量阈值(0.5)通过配置文件管理
2. **Mock 测试**: HITL 部分需要模拟用户反馈进行测试
3. **MemoryStore 持久化**: 开发用 MemoryStore，生产用 RedisStore
