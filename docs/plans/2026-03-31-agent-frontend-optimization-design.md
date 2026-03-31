# Agent 前端优化设计方案

> 日期：2026-03-31
> 分支：feature/agent-dev
> 状态：已审批

---

## 1. 目标

基于 deepresearch 和 spring-ai-alibaba-studio 两个项目的最佳实践，对 Agent Chat 前端进行渐进式三阶段优化，提升消息体验、交互质量和进度可视化能力。

---

## 2. 阶段一：消息体验升级

### 2.1 Markdown 渲染

- 使用 `markdown-it` 解析消息内容
- 支持 GFM（表格、删除线、任务列表）
- 使用 `highlight.js` 进行代码语法高亮

### 2.2 代码块增强

- 右上角添加「复制」按钮（点击后显示 ✅ 已复制）
- 显示语言标签
- 长代码区域滚动条优化

### 2.3 流式打字动画

- 字符逐个显示的流畅动画
- 打字过程中光标闪烁
- `nextTick` 确保 DOM 更新平滑

---

## 3. 阶段二：交互增强

### 3.1 历史记录面板

- 自定义滑出面板（300px 宽）
- 使用 Vue `transition` 实现平滑滑入/滑出动画（300ms ease）
- 消息按时间分组显示
- 支持切换历史对话
- 关闭按钮 + 点击外部关闭

### 3.2 工具调用样式

| 类型 | 样式 |
|------|------|
| `tool-request` | 蓝色边框 + 工具图标 |
| `tool-confirm` | 绿色强调 + 审批按钮 |
| `tool-response` | 灰色背景 + 折叠展开 |

### 3.3 滚动体验优化

- 滚动时显示「滚动到底部」浮动按钮
- 新消息到达时：
  - 如果在底部：自动滚动
  - 如果不在底部：显示未读数 badge

---

## 4. 阶段三：进度可视化

### 4.1 多阶段工作流

```
[探索阶段] → [评审阶段] → [生成阶段] → [完成]
```

### 4.2 外层：Steps 进度条

- 使用 Ant Design `Steps` 组件
- 三个主阶段：探索 → 评审 → 生成
- 当前阶段高亮，已完成阶段打勾

### 4.3 内层：Timeline 迭代细节

- 展示每次迭代：问题 → 研究 → 反馈 → 改进
- 使用 `a-timeline` 组件
- 颜色区分：
  - 蓝色：进行中
  - 绿色：已完成
  - 灰色：待处理

### 4.4 状态展示

- 当前阶段详细状态文字
- Agent 正在进行的操作提示
- 剩余迭代次数（如有）

---

## 5. 文件结构变更

```
frontend/src/
├── components/agent/
│   ├── AgentChatHeader.vue      # 修改：添加历史按钮
│   ├── HistoryPanel.vue         # 新增：自定义滑出历史面板
│   ├── AgentMessageList.vue     # 修改：增强滚动体验
│   ├── AgentChatInput.vue       # 修改：支持 markdown 预览
│   └── message/
│       ├── AssistantMessage.vue # 修改：Markdown 渲染 + 代码高亮
│       ├── ToolRequestMessage.vue # 修改：蓝色样式
│       └── ToolConfirmMessage.vue  # 修改：绿色强调样式
├── composables/
│   └── useMarkdown.ts           # 新增：Markdown 渲染 composable
└── styles/
    └── code-theme.css           # 新增：代码高亮主题适配
```

---

## 6. 双主题适配

所有组件使用 CSS 变量，自动适配极光/紫漾主题：

```css
/* 极光主题 */
--color-primary-500: #2268f5;
--color-tool-request: #3b82f6;
--color-tool-confirm: #22c55e;

/* 紫漾主题 */
--color-primary-500: #7c3aed;
--color-tool-request: #8b5cf6;
--color-tool-confirm: #ec4899;
```

---

## 7. 实施优先级

| 优先级 | 任务 | 所属阶段 |
|--------|------|----------|
| 1 | Markdown 渲染 + 代码高亮 | 阶段一 |
| 2 | 流式打字动画优化 | 阶段一 |
| 3 | 工具调用样式区分 | 阶段二 |
| 4 | 滚动体验优化 | 阶段二 |
| 5 | 历史记录面板 | 阶段二 |
| 6 | Steps + Timeline 进度条 | 阶段三 |
