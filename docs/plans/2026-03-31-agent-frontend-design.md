# Agent Chat 前端集成设计方案

> 日期：2026-03-31
> 分支：feature/agent-dev
> 状态：已审批

---

## 1. 目标

将 Agent Chat 功能从前端完整实现，对接后端 `/agent/image/chat/stream` 和 `/agent/image/feedback` 接口。

参考 `spring-ai-alibaba-studio/agent-chat-ui` 的 UX 模式，用 Vue 3 + Ant Design Vue 从头实现，适配项目的双主题系统（极光/紫漾）。

---

## 2. 技术决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 技术栈 | Vue 3 + Ant Design Vue | 复用现有技术栈，不引入新框架 |
| 样式方案 | CSS 变量复用 design-tokens | 双主题自动切换 |
| SSE 接收 | fetch + ReadableStream | 支持 POST 发送参数，更灵活 |
| 页面形式 | 独立页面 `/pages/AgentChatPage.vue` | 上下文独立，易维护 |
| 反馈按钮 | Agent 主动询问后显示 | 自然对话逻辑，避免界面拥挤 |
| 入口 | 导航栏 + 首页双重入口 | 最大化曝光 |

---

## 3. 页面架构

```
AgentChatPage.vue（独立页面）
├── AgentChatHeader.vue（标题 + 新对话按钮）
├── AgentMessageList.vue（消息列表 + 滚动）
│   ├── UserMessage.vue（用户消息）
│   ├── AssistantMessage.vue（AI 回复，流式）
│   ├── ToolRequestMessage.vue（工具请求）
│   ├── ToolResponseMessage.vue（工具响应）
│   └── ToolConfirmMessage.vue（待确认工具）
├── FeedbackButtons.vue（动态反馈按钮）
└── AgentChatInput.vue（输入框 + 发送）
```

---

## 4. 消息类型与渲染

| 类型 | 触发时机 | 渲染样式 |
|------|----------|----------|
| `user` | 用户发送 | 右对齐，主题色背景 |
| `assistant` | AI 回复 | 左对齐，渐变边框，流式文字 |
| `tool-request` | 工具调用请求 | 蓝色边框，显示工具名 |
| `tool-confirm` | 需用户确认 | 强调样式，审批按钮 |
| `tool-response` | 工具返回结果 | 灰色背景，显示结果 |
| `interrupt` | 中断待确认 | 特殊高亮，操作面板 |

---

## 5. 数据流

### 发送消息
```
AgentChatInput.vue
  → agentController.chatStream(message, threadId)
    → fetch POST /agent/image/chat/stream
      → SSE 流开始
```

### 接收消息
```
useAgentStream.ts (Composable)
  → 解析 SSE 事件 {type, content, node}
    → 更新 messages 数组
      → AgentMessageList.vue 响应式渲染
```

### 反馈发送
```
FeedbackButtons.vue (用户点击)
  → agentController.feedback(threadId, satisfied, reason, action, phase)
    → POST /agent/image/feedback
      → Agent 继续对话
```

---

## 6. 文件结构

```
frontend/src/
├── api/
│   └── agentController.ts              # 新增：chat/chatStream/feedback
├── pages/
│   └── AgentChatPage.vue               # 新增：页面入口
├── components/
│   └── agent/                          # 新增目录
│       ├── AgentChatHeader.vue         # 头部
│       ├── AgentMessageList.vue        # 消息列表
│       ├── AgentChatInput.vue          # 输入框
│       ├── FeedbackButtons.vue         # 反馈按钮
│       └── message/
│           ├── UserMessage.vue
│           ├── AssistantMessage.vue
│           ├── ToolRequestMessage.vue
│           ├── ToolResponseMessage.vue
│           └── ToolConfirmMessage.vue
├── composables/
│   └── useAgentStream.ts               # 新增：SSE 流式读取
└── router/
    └── index.ts                        # 修改：添加 /agent 路由
```

---

## 7. 双主题适配

复用现有 CSS 变量：

**极光主题**（深空黑）：
- 背景：`var(--color-bg-primary)` → #030712
- 文字：`var(--color-text-primary)` → #f8fafc
- 渐变：`var(--gradient-aurora)` → 蓝紫渐变

**紫漾主题**（浅色紫粉）：
- 背景：`var(--color-bg-primary)` → #fef7ff
- 文字：`var(--color-text-primary)` → #581c87
- 渐变：`var(--gradient-aurora)` → 粉紫渐变

所有组件样式使用 CSS 变量，主题切换自动生效。

---

## 8. 交互细节

### 消息发送
- Enter 发送，Shift+Enter 换行
- 发送时清空输入框
- 自动创建 threadId（UUID）

### 消息接收
- 流式输出（逐字显示）
- 自动滚动到底部
- 新消息时显示"滚动到底部"按钮

### 反馈按钮
- 仅当 Agent 消息中包含满意度询问时显示
- 按钮组：[满意] [换一批] [重新生成] [详细描述]
- 点击后隐藏，发送反馈到后端

---

## 9. API 接口

### 前端 API（agentController.ts）

```typescript
// 发送消息（流式）
chatStream(message: string, threadId: string): Promise<ReadableStream>

// 发送反馈
feedback(request: {
  threadId: string
  userId?: string
  satisfied: boolean
  reason?: string
  action?: string
  currentPhase?: string
}): Promise<string>
```

### 后端接口（已实现）

| 接口 | 方法 | 功能 |
|------|------|------|
| `/agent/image/chat` | POST | 普通对话 |
| `/agent/image/feedback` | POST | 处理用户反馈 |
| `/agent/image/chat/stream` | GET | 流式对话（SSE） |

---

## 10. 入口配置

### 路由
```typescript
{
  path: '/agent',
  component: () => import('@/pages/AgentChatPage.vue'),
  meta: { title: '智能助手' }
}
```

### 导航栏入口
GlobalHeader 添加 "智能助手" 链接

### 首页入口
HomePage 添加 "开始 AI 对话" 引导卡片
