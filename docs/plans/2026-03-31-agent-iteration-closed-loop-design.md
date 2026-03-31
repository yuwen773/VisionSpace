# Agent 迭代闭环设计方案

> 日期：2026-03-31
> 分支：feature/agent-dev
> 状态：已审批

---

## 1. 目标

升级 ImageAgent 为 **Plan-Execute-Replan 闭环**，实现创作 + 搜索 + 迭代机制，使 Agent 具备：
- 主动询问用户满意度
- 自动迭代探索（换关键词搜索）
- 按需触发 AIGC 生成
- 结构化反馈驱动下一轮决策

## 2. 核心决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 迭代模式 | 混合式（探索全自动，生成需确认） | 避免每次小调整中断用户；AIGC 有成本需确认 |
| 默认策略 | 先探索再生成（探索≤3次，生成≤2次） | 搜索成本低于生成；多次搜索无果再生成 |
| 交互形式 | 文本 + 结构化按钮混合 | 兼顾自然语言表达和结构化数据收集 |
| 反馈机制 | 保留 handleFeedback()，增强消息内容 | 正确的抽象层，增强而非替换 |
| 状态管理 | OverAllState + 自定义 Hook | 简单直接，符合框架设计 |
| 流式输出 | SSE 推送，过滤有效 OutputType | 前端实时响应体验 |

## 3. 核心流程

```
用户输入 → 意图理解 → 站内/站外搜索 → 质量评估
                                        ↓
                              matchScore ≥ 0.5? ──否──→ 探索迭代（≤3次）
                                        ↓是
                              展示结果 + 主动询问满意度
                                        ↓
                    用户满意 ←───[满意]────────┐
                        │                    │
                   结束流程              用户不满意？
                        │                    │
                        └──[换一批/重生成]──→│
                                        ↓
                              探索 < 3次? ──是──→ 扩大搜索
                                        ↓否
                              触发 AIGC 生成（需用户确认）
                                        ↓
                              展示生成结果 + 询问
                                        ↓
                                满意? ──否──→ 重新生成(限2次)
                                        ↓是
                                     结束流程
```

## 4. 技术对齐点

### 4.1 迭代计数管理

通过 OverAllState 在 IterationControlHook 中维护：
- `explore_count`：探索次数
- `generate_count`：生成次数
- `model_call_count`：总模型调用次数
- `phase`：当前阶段（EXPLORATION / GENERATION / DONE）

### 4.2 主动询问满意度

通过 Agent instruction 驱动，不是 HumanInTheLoopHook。
HITL Hook 仅用于拦截 AIGC 生成工具（LogoGeneratorTool）调用前的审批。

### 4.3 结构化反馈桥接

保留 handleFeedback()，增强消息格式：
```
[用户反馈] 满意度: 不满意 | 动作: REGENERATE | 原因: 颜色太暗 | 阶段: EXPLORATION | 探索次数: 2
```

### 4.4 流式输出

架构补充 SSE 流式层：
```
用户输入 → Agent.stream() → 前端实时渲染
    ├── AGENT_MODEL_STREAMING → 逐步显示文字
    ├── AGENT_TOOL_FINISHED   → 显示"正在搜索..."
    └── AGENT_MODEL_FINISHED  → 完整响应
```

### 4.5 阶段性工具控制

通过 instruction 引导 Agent 在不同阶段选择不同工具，而非 Interceptor 硬控制。

## 5. 组件清单

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `agent/model/AgentPhase.java` | 阶段枚举：EXPLORATION / GENERATION / DONE |
| `agent/hook/IterationControlHook.java` | 迭代计数 + 自动切换阶段 + 超限停止 |
| `agent/hook/MessageTrimmingHook.java` | 消息修剪，保留最近10条 + 首条系统提示 |

### 5.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `agent/model/QualityResult.java` | 添加 phase、exploreCount 字段 |
| `agent/ImageAgent.java` | 重写 instruction、新增 Hook 配置、增强 handleFeedback()、添加 stream() 方法 |
| `controller/AgentController.java` | 新增流式 SSE 端点 |

### 5.3 保留文件（不变）

| 文件 | 说明 |
|------|------|
| `agent/tools/ImageSearchTool.java` | 搜索工具 |
| `agent/tools/LogoGeneratorTool.java` | AIGC 生成工具 |
| `agent/tools/QualityEvaluatorTool.java` | 质量评估工具 |
| `agent/tools/MermaidDiagramTool.java` | Mermaid 图表工具 |
| `agent/tools/UndrawIllustrationTool.java` | Undraw 插图工具 |
| `agent/service/UserPreferenceService.java` | 用户偏好服务 |
| `agent/model/ActionType.java` | 动作类型枚举 |
| `agent/model/ImageCategoryEnum.java` | 图片类别枚举 |
| `agent/model/ImageResource.java` | 图片资源模型 |

## 6. 硬限制

- 总模型调用 ≤ 15 次（ModelCallLimitHook）
- 探索次数 ≤ 3 次（IterationControlHook）
- 生成次数 ≤ 2 次（IterationControlHook）
- 消息保留最近 10 条 + 首条系统提示（MessageTrimmingHook）

## 7. 关键实现细节

### IterationControlHook

```
BEFORE_MODEL 时执行：
1. 读取 state 中的 explore_count / generate_count / model_call_count / phase
2. model_call_count >= 15 → 注入停止消息，结束 Agent
3. phase == EXPLORATION && explore_count >= 3 → 自动切换到 GENERATION 阶段
4. phase == GENERATION && generate_count >= 2 → 注入停止消息
5. 正常情况：model_call_count + 1
```

### MessageTrimmingHook

```
BEFORE_MODEL 时执行：
1. 消息数 > 10 时触发修剪
2. 保留第一条消息（系统提示）
3. 保留最近 9 条消息
4. 使用 UpdatePolicy.REPLACE 替换消息列表
```

### 增强 handleFeedback()

```
1. 获取当前 exploreCount（从 state 或缓存）
2. 构造标准化反馈消息：[用户反馈] 满意度 | 动作 | 原因 | 阶段 | 探索次数
3. 保存用户偏好（不满意时）
4. 调用 agent.call(feedbackMessage, config) 继续对话
```

### 流式 SSE 端点

```
GET /agent/image/chat/stream?message=xxx&threadId=xxx
→ agent.stream(message, config)
→ 过滤有效 OutputType（MODEL_STREAMING, MODEL_FINISHED, TOOL_FINISHED）
→ 以 SSE 格式推送前端
```

## 8. 参考文档

- `docs/spring-ai-alibaba-agent-best-practices.md` — Spring AI Alibaba Agent 最佳实践
- `docs/feature/agentic-rag.md` — Agent RAG 策略文档
- https://java2ai.com — Spring AI Alibaba 官方文档
