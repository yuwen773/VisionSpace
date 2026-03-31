# Agent 前端优化实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 对 Agent Chat 前端进行三阶段优化：消息体验升级、交互增强、进度可视化。

**Architecture:** 渐进式三阶段实施，使用 markdown-it + highlight.js 渲染 Markdown，Vue transition 实现历史面板滑动，Steps + Timeline 实现进度可视化。

**Tech Stack:** Vue 3 + Ant Design Vue 4 + markdown-it + highlight.js + @vueuse/core

---

## 阶段一：消息体验升级

### Task 1: 安装 Markdown 依赖

**Files:**
- Modify: `frontend/package.json`

**Step 1: 添加依赖**

```json
{
  "dependencies": {
    "markdown-it": "^14.1.0",
    "highlight.js": "^11.9.0"
  },
  "devDependencies": {
    "@types/markdown-it": "^14.1.0"
  }
}
```

**Step 2: 安装依赖**

Run: `cd frontend && npm install markdown-it highlight.js @types/markdown-it`

---

### Task 2: 创建 Markdown 渲染 Composable

**Files:**
- Create: `frontend/src/composables/useMarkdown.ts`

**Step 1: 创建 useMarkdown.ts**

```typescript
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import { ref } from 'vue'

// 创建 markdown-it 实例，启用 GFM
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string) {
    // 如果有语言标识，使用 highlight.js 高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs code-block"><div class="code-header"><span class="code-lang">' +
          lang +
          '</span><button class="copy-btn" onclick="copyCode(this)">复制</button></div><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
      } catch (__) {}
    }
    // 否则返回普通代码块
    return '<pre class="hljs code-block"><div class="code-header"><span class="code-lang">code</span><button class="copy-btn" onclick="copyCode(this)">复制</button></div><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  },
})

export function useMarkdown() {
  const render = (content: string): string => {
    if (!content) return ''
    return md.render(content)
  }

  return {
    render,
  }
}
```

**Step 2: 添加复制代码的全局脚本**

Create: `frontend/src/utils/copy-code.ts`

```typescript
// 全局复制函数，供代码块的复制按钮调用
;(window as any).copyCode = async function (btn: HTMLButtonElement) {
  const codeBlock = btn.closest('.code-block')
  if (!codeBlock) return
  const code = codeBlock.querySelector('code')?.textContent || ''
  try {
    await navigator.clipboard.writeText(code)
    btn.textContent = '已复制'
    setTimeout(() => {
      btn.textContent = '复制'
    }, 2000)
  } catch (err) {
    btn.textContent = '复制失败'
  }
}
```

**Step 3: 在 main.ts 中引入**

Modify: `frontend/src/main.ts` - 在文件顶部添加：

```typescript
import '@/utils/copy-code'
```

**Step 4: Commit**

```bash
cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev"
git add frontend/package.json frontend/src/composables/useMarkdown.ts frontend/src/utils/copy-code.ts frontend/src/main.ts
git commit -m "feat(agent): 添加 markdown-it 和 highlight.js 支持"
```

---

### Task 3: 修改 AssistantMessage 支持 Markdown 渲染

**Files:**
- Modify: `frontend/src/components/agent/message/AssistantMessage.vue`

**Step 1: 修改 AssistantMessage.vue**

```vue
<template>
  <div class="assistant-message">
    <div class="message-avatar">
      <a-avatar :size="32" class="agent-avatar">
        <template #icon>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2"/>
          </svg>
        </template>
      </a-avatar>
    </div>
    <div class="message-content">
      <div class="message-header">
        <span class="agent-name">智能助手</span>
        <span class="message-time">{{ time }}</span>
      </div>
      <div class="message-text markdown-body" v-html="renderedContent"></div>
      <div v-if="isLoading" class="typing-indicator">
        <span class="typing-cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'

interface Props {
  content: string
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  isLoading: false,
})

const { render } = useMarkdown()
const renderedContent = computed(() => render(props.content))
const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
</script>

<style scoped>
.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
}

.agent-avatar {
  background: var(--gradient-aurora);
  color: white;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 16px 16px 16px 4px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-subtle);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.agent-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-primary-500);
}

.message-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

/* Markdown 样式 */
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
}

.markdown-body :deep(p) {
  margin: 0 0 8px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-500);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(code:not(.hljs)) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}

.markdown-body :deep(.hljs.code-block) {
  background: var(--color-bg-primary);
  border-radius: 8px;
  margin: 8px 0;
  overflow: hidden;
}

.markdown-body :deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-subtle);
}

.markdown-body :deep(.code-lang) {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
}

.markdown-body :deep(.copy-btn) {
  background: transparent;
  border: 1px solid var(--color-border-default);
  color: var(--color-text-secondary);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.markdown-body :deep(.copy-btn:hover) {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.markdown-body :deep(.hljs.code-block code) {
  display: block;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  font-family: 'Courier New', monospace;
}

.typing-indicator {
  display: inline-block;
  margin-left: 2px;
}

.typing-cursor {
  animation: blink 1s step-end infinite;
  color: var(--color-primary-500);
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
```

**Step 2: Commit**

```bash
git add frontend/src/components/agent/message/AssistantMessage.vue
git commit -m "feat(agent): AssistantMessage 支持 Markdown 渲染和代码高亮"
```

---

### Task 4: 添加代码高亮主题样式

**Files:**
- Create: `frontend/src/styles/code-theme.css`

**Step 1: 创建 code-theme.css**

```css
/* 代码高亮主题 - 适配双主题 */

/* 极光主题（深色）的代码高亮 */
[data-theme='aurora'] .hljs {
  background: #0a0f1a;
  color: #c9d1d9;
}

[data-theme='aurora'] .hljs-keyword,
[data-theme='aurora'] .hljs-selector-tag,
[data-theme='aurora'] .hljs-literal,
[data-theme='aurora'] .hljs-section,
[data-theme='aurora'] .hljs-link {
  color: #ff7b72;
}

[data-theme='aurora'] .hljs-function .hljs-keyword {
  color: #ff7b72;
}

[data-theme='aurora'] .hljs-string,
[data-theme='aurora'] .hljs-title.function_,
[data-theme='aurora'] .hljs-regexp,
[data-theme='aurora'] .hljs-selector-attr,
[data-theme='aurora'] .hljs-selector-pseudo {
  color: #a5d6ff;
}

[data-theme='aurora'] .hljs-comment,
[data-theme='aurora'] .hljs-quote {
  color: #8b949e;
  font-style: italic;
}

[data-theme='aurora'] .hljs-number {
  color: #79c0ff;
}

[data-theme='aurora'] .hljs-built_in,
[data-theme='aurora'] .hljs-type {
  color: #ffa657;
}

[data-theme='aurora'] .hljs-attr {
  color: #79c0ff;
}

[data-theme='aurora'] .hljs-variable,
[data-theme='aurora'] .hljs-template-variable {
  color: #ffa657;
}

/* 紫漾主题（浅色）的代码高亮 */
[data-theme='pop'] .hljs {
  background: #f8f5ff;
  color: #24292e;
}

[data-theme='pop'] .hljs-keyword,
[data-theme='pop'] .hljs-selector-tag,
[data-theme='pop'] .hljs-literal,
[data-theme='pop'] .hljs-section,
[data-theme='pop'] .hljs-link {
  color: #d73a49;
}

[data-theme='pop'] .hljs-function .hljs-keyword {
  color: #d73a49;
}

[data-theme='pop'] .hljs-string,
[data-theme='pop'] .hljs-title.function_,
[data-theme='pop'] .hljs-regexp,
[data-theme='pop'] .hljs-selector-attr,
[data-theme='pop'] .hljs-selector-pseudo {
  color: #032f62;
}

[data-theme='pop'] .hljs-comment,
[data-theme='pop'] .hljs-quote {
  color: #6a737d;
  font-style: italic;
}

[data-theme='pop'] .hljs-number {
  color: #005cc5;
}

[data-theme='pop'] .hljs-built_in,
[data-theme='pop'] .hljs-type {
  color: #e36209;
}

[data-theme='pop'] .hljs-attr {
  color: #005cc5;
}

[data-theme='pop'] .hljs-variable,
[data-theme='pop'] .hljs-template-variable {
  color: #e36209;
}
```

**Step 2: 在 main.ts 中引入**

Modify: `frontend/src/main.ts` - 添加样式引入：

```typescript
import '@/styles/code-theme.css'
```

**Step 3: Commit**

```bash
git add frontend/src/styles/code-theme.css frontend/src/main.ts
git commit -m "feat(agent): 添加代码高亮主题样式"
```

---

## 阶段二：交互增强

### Task 5: 修改 ToolConfirmMessage 为绿色样式

**Files:**
- Modify: `frontend/src/components/agent/message/ToolConfirmMessage.vue`

**Step 1: 修改样式**

```vue
<style scoped>
.tool-confirm {
  display: flex;
  gap: 12px;
  padding: 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: rgba(34, 197, 94, 0.1);  /* 绿色背景 */
  border: 1px solid rgba(34, 197, 94, 0.4);  /* 绿色边框 */
}

.confirm-icon {
  color: var(--color-success);  /* 绿色图标 */
  flex-shrink: 0;
}

.confirm-content {
  flex: 1;
}

.confirm-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-success);  /* 绿色标题 */
  margin-bottom: 4px;
}

.confirm-message {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.confirm-actions {
  display: flex;
  gap: 8px;
}
</style>
```

**Step 2: Commit**

```bash
git add frontend/src/components/agent/message/ToolConfirmMessage.vue
git commit -m "feat(agent): ToolConfirmMessage 改为绿色确认样式"
```

---

### Task 6: 增强 ToolResponseMessage 可折叠

**Files:**
- Modify: `frontend/src/components/agent/message/ToolResponseMessage.vue`

**Step 1: 修改为可折叠**

```vue
<template>
  <div class="tool-response">
    <div class="tool-header" @click="toggleExpand">
      <div class="tool-info">
        <span class="tool-label">工具返回</span>
        <span class="tool-name">{{ toolName }}</span>
      </div>
      <div class="expand-icon" :class="{ expanded }">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>
    </div>
    <transition name="slide">
      <div v-show="expanded || isExpanded" class="tool-content">
        {{ content }}
      </div>
    </transition>
    <div v-if="!expanded && content.length > 100" class="expand-hint" @click="toggleExpand">
      点击展开
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  toolName: string
  content: string
}

const props = withDefaults(defineProps<Props>(), {
  toolName: '',
  content: '',
})

const expanded = ref(props.content.length <= 100)
const isExpanded = ref(false)

const toggleExpand = () => {
  if (props.content.length > 100) {
    isExpanded.value = !isExpanded.value
  }
}
</script>

<style scoped>
.tool-response {
  margin: 8px 16px;
  padding: 12px;
  border-radius: 8px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-subtle);
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.tool-header:hover {
  opacity: 0.8;
}

.tool-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.tool-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.expand-icon {
  color: var(--color-text-tertiary);
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.tool-content {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-hint {
  font-size: 12px;
  color: var(--color-primary-500);
  margin-top: 8px;
  cursor: pointer;
  text-align: center;
}

.expand-hint:hover {
  text-decoration: underline;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  max-height: 0;
}

.slide-enter-to,
.slide-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
```

**Step 2: Commit**

```bash
git add frontend/src/components/agent/message/ToolResponseMessage.vue
git commit -m "feat(agent): ToolResponseMessage 支持可折叠展开"
```

---

### Task 7: 创建历史记录面板 HistoryPanel

**Files:**
- Create: `frontend/src/components/agent/HistoryPanel.vue`

**Step 1: 创建 HistoryPanel.vue**

```vue
<template>
  <teleport to="body">
    <transition name="slide">
      <div v-if="visible" class="history-overlay" @click.self="handleClose">
        <div class="history-panel">
          <div class="panel-header">
            <h3 class="panel-title">历史记录</h3>
            <button class="close-btn" @click="handleClose">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="panel-content">
            <div v-if="histories.length === 0" class="empty-state">
              <p>暂无历史记录</p>
            </div>
            <div v-else class="history-list">
              <div
                v-for="(item, index) in histories"
                :key="index"
                class="history-item"
                :class="{ active: activeIndex === index }"
                @click="handleSelect(index)"
              >
                <div class="history-title">{{ item.title || '新对话' }}</div>
                <div class="history-time">{{ item.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export interface HistoryItem {
  id: string
  title: string
  time: string
  messages: any[]
}

interface Props {
  visible: boolean
  histories: HistoryItem[]
  activeIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  histories: () => [],
  activeIndex: -1,
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', index: number): void
}>()

const handleClose = () => {
  emit('close')
}

const handleSelect = (index: number) => {
  emit('select', index)
}

// ESC 键关闭
watch(() => props.visible, (val) => {
  if (val) {
    document.addEventListener('keydown', handleEscKey)
  } else {
    document.removeEventListener('keydown', handleEscKey)
  }
})

const handleEscKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    handleClose()
  }
}
</script>

<style scoped>
.history-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-start;
}

.history-panel {
  width: 300px;
  height: 100%;
  background: var(--color-bg-secondary);
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--color-text-tertiary);
  font-size: 14px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
}

.history-item:hover {
  background: var(--color-bg-hover);
}

.history-item.active {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-accent);
}

.history-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 滑入滑出动画 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
}

.slide-enter-from .history-panel,
.slide-leave-to .history-panel {
  transform: translateX(-100%);
}

.slide-enter-to .history-panel,
.slide-leave-from .history-panel {
  transform: translateX(0);
}
</style>
```

**Step 2: Commit**

```bash
git add frontend/src/components/agent/HistoryPanel.vue
git commit -m "feat(agent): 添加 HistoryPanel 历史记录面板组件"
```

---

### Task 8: 修改 AgentChatHeader 添加历史按钮

**Files:**
- Modify: `frontend/src/components/agent/AgentChatHeader.vue`

**Step 1: 读取当前文件**

需要先读取完整内容再修改，添加历史按钮。

**Step 2: 添加历史按钮到 header**

在 header 的适当位置添加历史按钮：

```vue
<!-- 在现有按钮旁边添加 -->
<button class="header-btn" @click="emit('openHistory')" title="历史记录">
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <circle cx="12" cy="12" r="10"/>
    <polyline points="12 6 12 12 16 14"/>
  </svg>
</button>
```

添加 emit 定义：

```typescript
const emit = defineEmits(['newChat', 'openHistory'])
```

**Step 3: Commit**

```bash
git add frontend/src/components/agent/AgentChatHeader.vue
git commit -m "feat(agent): AgentChatHeader 添加历史记录按钮"
```

---

### Task 9: 修改 AgentChatPage 集成 HistoryPanel

**Files:**
- Modify: `frontend/src/pages/AgentChatPage.vue`

**Step 1: 添加 HistoryPanel 集成**

```typescript
// 添加状态
const showHistory = ref(false)
const histories = ref<HistoryItem[]>([]) // 从 localStorage 加载

// 添加处理函数
const handleOpenHistory = () => {
  showHistory.value = true
}

const handleCloseHistory = () => {
  showHistory.value = false
}

const handleSelectHistory = (index: number) => {
  // 切换到选中的历史对话
  showHistory.value = false
}
```

在 template 中添加：

```vue
<HistoryPanel
  :visible="showHistory"
  :histories="histories"
  :activeIndex="-1"
  @close="handleCloseHistory"
  @select="handleSelectHistory"
/>
```

**Step 2: Commit**

```bash
git add frontend/src/pages/AgentChatPage.vue
git commit -m "feat(agent): AgentChatPage 集成 HistoryPanel"
```

---

## 阶段三：进度可视化

### Task 10: 创建进度可视化组件

**Files:**
- Create: `frontend/src/components/agent/IterationProgress.vue`

**Step 1: 创建 IterationProgress.vue**

```vue
<template>
  <div class="iteration-progress">
    <!-- 外层 Steps -->
    <a-steps :current="currentStep" size="small" class="progress-steps">
      <a-step title="探索" description="分析需求" />
      <a-step title="评审" description="质量检查" />
      <a-step title="生成" description="创作图片" />
    </a-steps>

    <!-- 内层 Timeline -->
    <a-timeline class="progress-timeline">
      <a-timeline-item
        v-for="(item, index) in iterations"
        :key="index"
        :color="getItemColor(item.status)"
      >
        <div class="iteration-item">
          <span class="iteration-title">{{ item.title }}</span>
          <span class="iteration-desc">{{ item.description }}</span>
        </div>
      </a-timeline-item>
    </a-timeline>

    <!-- 当前状态 -->
    <div class="current-status">
      <span class="status-label">{{ currentStatusLabel }}</span>
      <span v-if="remainingIterations > 0" class="remaining-hint">
        剩余 {{ remainingIterations }} 次迭代
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface IterationItem {
  title: string
  description: string
  status: 'pending' | 'processing' | 'completed'
}

interface Props {
  phase: 'EXPLORATION' | 'REVIEW' | 'GENERATION' | 'DONE'
  iterations: IterationItem[]
  remainingIterations?: number
}

const props = withDefaults(defineProps<Props>(), {
  iterations: () => [],
  remainingIterations: 0,
})

const currentStep = computed(() => {
  const map: Record<string, number> = {
    EXPLORATION: 0,
    REVIEW: 1,
    GENERATION: 2,
    DONE: 3,
  }
  return map[props.phase] ?? 0
})

const currentStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    EXPLORATION: '正在分析您的需求...',
    REVIEW: '正在进行质量评审...',
    GENERATION: '正在生成图片...',
    DONE: '已完成',
  }
  return labels[props.phase] ?? ''
})

const getItemColor = (status: string) => {
  const colors: Record<string, string> = {
    pending: 'gray',
    processing: 'blue',
    completed: 'green',
  }
  return colors[status] ?? 'gray'
}
</script>

<style scoped>
.iteration-progress {
  padding: 16px;
  background: var(--color-bg-secondary);
  border-radius: 12px;
  margin-bottom: 16px;
}

.progress-steps {
  margin-bottom: 24px;
}

.progress-timeline {
  margin: 16px 0;
}

.iteration-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.iteration-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.iteration-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.current-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-subtle);
}

.status-label {
  font-size: 13px;
  color: var(--color-primary-500);
}

.remaining-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
</style>
```

**Step 2: Commit**

```bash
git add frontend/src/components/agent/IterationProgress.vue
git commit -m "feat(agent): 添加 IterationProgress 进度可视化组件"
```

---

### Task 11: 集成 IterationProgress 到 AgentChatPage

**Files:**
- Modify: `frontend/src/pages/AgentChatPage.vue`

**Step 1: 添加进度状态管理**

```typescript
import IterationProgress from '@/components/agent/IterationProgress.vue'

// 进度状态
const currentPhase = ref<'EXPLORATION' | 'REVIEW' | 'GENERATION' | 'DONE'>('EXPLORATION')
const iterations = ref([
  { title: '初始化', description: '开始分析需求', status: 'completed' as const },
])
```

**Step 2: 在模板中添加**

```vue
<AgentChatHeader @newChat="handleNewChat" @openHistory="handleOpenHistory" />

<!-- 添加进度条 -->
<IterationProgress
  v-if="showProgress"
  :phase="currentPhase"
  :iterations="iterations"
  :remainingIterations="3"
/ />
```

**Step 3: Commit**

```bash
git add frontend/src/pages/AgentChatPage.vue
git commit -m "feat(agent): AgentChatPage 集成进度可视化组件"
```

---

## 收尾

### Task 12: 最终检查并推送

**Step 1: 安装依赖并验证构建**

Run: `cd frontend && npm install && npm run build`

**Step 2: 检查所有文件是否正确**

**Step 3: Push 到远程**

```bash
git push origin feature/agent-dev
```

---

## 实施检查清单

| 阶段 | 任务 | 状态 |
|------|------|------|
| 一 | 1. 安装 markdown-it + highlight.js | ⬜ |
| 一 | 2. 创建 useMarkdown.ts | ⬜ |
| 一 | 3. AssistantMessage Markdown 渲染 | ⬜ |
| 一 | 4. 代码高亮主题样式 | ⬜ |
| 二 | 5. ToolConfirmMessage 绿色样式 | ⬜ |
| 二 | 6. ToolResponseMessage 可折叠 | ⬜ |
| 二 | 7. HistoryPanel 组件 | ⬜ |
| 二 | 8. AgentChatHeader 历史按钮 | ⬜ |
| 二 | 9. AgentChatPage 集成 HistoryPanel | ⬜ |
| 三 | 10. IterationProgress 组件 | ⬜ |
| 三 | 11. AgentChatPage 集成进度条 | ⬜ |
| 收尾 | 12. 构建验证 + Push | ⬜ |
