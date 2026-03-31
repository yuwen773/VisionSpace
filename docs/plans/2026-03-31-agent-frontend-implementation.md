# Agent Chat 前端实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完整实现 Agent Chat 前端页面，对接后端 SSE 流式接口和反馈接口，适配双主题系统。

**Architecture:** 独立页面 + 组件化设计 + Composable 管理状态，SSE 流式接收使用 fetch + ReadableStream。

**Tech Stack:** Vue 3 + Ant Design Vue + CSS 变量（design-tokens）

---

## 前置检查

在开始前，确保：
1. 后端 `/agent/image/chat/stream` 接口已实现并可用
2. `frontend/src/request.ts` 的 baseURL 指向正确的后端地址
3. 前端开发服务器 `npm run dev` 可以启动

---

## Task 1: 创建 API 接口

**Files:**
- Create: `frontend/src/api/agentController.ts`

**Step 1: 创建 agentController.ts**

```typescript
// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * SSE 流式对话
 * GET /api/agent/image/chat/stream?message=xxx&threadId=xxx
 */
export async function chatStreamUsingGet(
  params: {
    message: string
    threadId: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/chat/stream', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

/**
 * 普通对话（非流式）
 * POST /api/agent/image/chat
 */
export async function chatUsingPost(
  body: {
    message: string
    threadId?: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/**
 * 发送反馈
 * POST /api/agent/image/feedback
 */
export async function feedbackUsingPost(
  body: {
    threadId: string
    userId?: string
    satisfied: boolean
    reason?: string
    action?: string
    currentPhase?: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/feedback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
```

**Step 2: 导出到 index.ts**

Modify: `frontend/src/api/index.ts`

在 import 区域添加：
```typescript
import * as agentController from './agentController'
```

在 export default 对象中添加：
```typescript
agentController,
```

---

## Task 2: 创建 SSE 流式读取 Composable

**Files:**
- Create: `frontend/src/composables/useAgentStream.ts`

**Step 1: 创建 useAgentStream.ts**

```typescript
import { ref, onUnmounted } from 'vue'

export interface StreamMessage {
  type: string      // AGENT_MODEL_STREAMING / AGENT_MODEL_FINISHED / AGENT_TOOL_FINISHED
  content: string   // 消息内容
  node: string      // 节点名称
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  let eventSource: EventSource | null = null
  let abortController: AbortController | null = null

  /**
   * 发送消息并接收 SSE 流
   * @param message 用户消息
   * @param threadId 线程 ID
   */
  const sendMessage = async (message: string, threadId: string) => {
    isStreaming.value = true
    messages.value = []

    // 构建 SSE URL
    const baseUrl = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'
    const url = `${baseUrl}/api/agent/image/chat/stream?message=${encodeURIComponent(message)}&threadId=${encodeURIComponent(threadId)}`

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'text/event-stream',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('ReadableStream not supported')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6))
              if (data.type && data.content !== undefined) {
                messages.value.push({
                  type: data.type,
                  content: data.content,
                  node: data.node || '',
                })
              }
            } catch (e) {
              // 忽略解析错误
            }
          }
        }
      }
    } catch (error) {
      console.error('SSE stream error:', error)
      throw error
    } finally {
      isStreaming.value = false
    }
  }

  /**
   * 中止当前流
   */
  const abort = () => {
    abortController?.abort()
    eventSource?.close()
    isStreaming.value = false
  }

  onUnmounted(() => {
    abort()
  })

  return {
    messages,
    isStreaming,
    sendMessage,
    abort,
  }
}
```

---

## Task 3: 创建消息组件 - UserMessage

**Files:**
- Create: `frontend/src/components/agent/message/UserMessage.vue`

**Step 1: 创建 UserMessage.vue**

```vue
<template>
  <div class="user-message">
    <div class="message-content">
      <div class="message-text">{{ content }}</div>
      <div class="message-time">{{ time }}</div>
    </div>
    <div class="message-avatar">
      <a-avatar :src="avatar" :size="32" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  content: string
  time?: string
  avatar?: string
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  time: '',
  avatar: '',
})

const time = computed(() => {
  if (props.time) return props.time
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.user-message {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 16px 16px 4px 16px;
  background: var(--gradient-aurora);
  color: var(--color-text-inverse);
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.message-avatar {
  flex-shrink: 0;
}
</style>
```

---

## Task 4: 创建消息组件 - AssistantMessage

**Files:**
- Create: `frontend/src/components/agent/message/AssistantMessage.vue`

**Step 1: 创建 AssistantMessage.vue**

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
      <div class="message-text" :class="{ 'is-loading': isLoading }">
        {{ displayContent }}<span v-if="isLoading" class="typing-cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface Props {
  content: string
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  isLoading: false,
})

const displayContent = ref('')
const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

watch(() => props.content, (newVal) => {
  displayContent.value = newVal
}, { immediate: true })
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

.message-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
  word-break: break-word;
  white-space: pre-wrap;
}

.typing-cursor {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
```

---

## Task 5: 创建消息组件 - ToolRequestMessage / ToolResponseMessage / ToolConfirmMessage

**Files:**
- Create: `frontend/src/components/agent/message/ToolRequestMessage.vue`
- Create: `frontend/src/components/agent/message/ToolResponseMessage.vue`
- Create: `frontend/src/components/agent/message/ToolConfirmMessage.vue`

**Step 1: 创建 ToolRequestMessage.vue**

```vue
<template>
  <div class="tool-request">
    <div class="tool-icon">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
      </svg>
    </div>
    <div class="tool-info">
      <span class="tool-name">{{ toolName }}</span>
      <span class="tool-description">{{ description }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  toolName: string
  description?: string
}

withDefaults(defineProps<Props>(), {
  toolName: 'Tool',
  description: '',
})
</script>

<style scoped>
.tool-request {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin: 8px 16px;
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.tool-icon {
  color: var(--color-info);
}

.tool-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tool-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-info);
}

.tool-description {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
```

**Step 2: 创建 ToolResponseMessage.vue**

```vue
<template>
  <div class="tool-response">
    <div class="tool-header">
      <span class="tool-label">工具返回</span>
      <span class="tool-name">{{ toolName }}</span>
    </div>
    <div class="tool-content">
      {{ content }}
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  toolName: string
  content: string
}

withDefaults(defineProps<Props>(), {
  toolName: '',
  content: '',
})
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
  gap: 8px;
  margin-bottom: 8px;
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

.tool-content {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
```

**Step 3: 创建 ToolConfirmMessage.vue**

```vue
<template>
  <div class="tool-confirm">
    <div class="confirm-icon">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
    </div>
    <div class="confirm-content">
      <div class="confirm-title">需要您的确认</div>
      <div class="confirm-message">{{ message }}</div>
      <div class="confirm-actions">
        <a-button size="small" @click="handleCancel">取消</a-button>
        <a-button type="primary" size="small" @click="handleConfirm">确认</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  message: string
}

const props = defineProps<Props>()
const emit = defineEmits(['confirm', 'cancel'])

const handleConfirm = () => emit('confirm')
const handleCancel = () => emit('cancel')
</script>

<style scoped>
.tool-confirm {
  display: flex;
  gap: 12px;
  padding: 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.4);
}

.confirm-icon {
  color: var(--color-warning);
  flex-shrink: 0;
}

.confirm-content {
  flex: 1;
}

.confirm-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-warning);
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

---

## Task 6: 创建 FeedbackButtons 组件

**Files:**
- Create: `frontend/src/components/agent/FeedbackButtons.vue`

**Step 1: 创建 FeedbackButtons.vue**

```vue
<template>
  <div v-if="visible" class="feedback-buttons">
    <div class="feedback-prompt">
      <span class="prompt-icon">💭</span>
      <span class="prompt-text">这些图片是否符合您的预期？</span>
    </div>
    <div class="button-group">
      <a-button @click="handleFeedback('satisfied')">
        <template #icon><CheckOutlined /></template>
        满意
      </a-button>
      <a-button @click="handleFeedback('research')">
        <template #icon><SwapOutlined /></template>
        换一批
      </a-button>
      <a-button @click="handleFeedback('regenerate')">
        <template #icon><ReloadOutlined /></template>
        重新生成
      </a-button>
      <a-button @click="handleFeedback('describe')">
        <template #icon><EditOutlined /></template>
        详细描述
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CheckOutlined, SwapOutlined, ReloadOutlined, EditOutlined } from '@ant-design/icons-vue'

interface Props {
  visible: boolean
}

defineProps<Props>()
const emit = defineEmits(['feedback'])

const handleFeedback = (action: string) => {
  emit('feedback', action)
}
</script>

<style scoped>
.feedback-buttons {
  padding: 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: var(--gradient-glass);
  border: 1px solid var(--color-border-subtle);
  text-align: center;
}

.feedback-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.prompt-icon {
  font-size: 16px;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
</style>
```

---

## Task 7: 创建 AgentChatInput 组件

**Files:**
- Create: `frontend/src/components/agent/AgentChatInput.vue`

**Step 1: 创建 AgentChatInput.vue**

```vue
<template>
  <div class="agent-chat-input">
    <div class="input-container">
      <a-textarea
        v-model:value="inputText"
        :rows="1"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        :placeholder="placeholder"
        @pressEnter="handleSend"
        class="chat-textarea"
      />
      <a-button
        type="primary"
        :loading="loading"
        :disabled="!inputText.trim()"
        @click="handleSend"
        class="send-button"
      >
        <template #icon>
          <SendOutlined v-if="!loading" />
        </template>
        发送
      </a-button>
    </div>
    <div class="input-hint">
      <span>Enter 发送，Shift+Enter 换行</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SendOutlined } from '@ant-design/icons-vue'

interface Props {
  loading?: boolean
  placeholder?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  placeholder: '输入您的需求...',
})

const emit = defineEmits(['send'])

const inputText = ref('')

const handleSend = () => {
  if (!inputText.value.trim() || inputText.value.trim().length === 0) return
  emit('send', inputText.value.trim())
  inputText.value = ''
}
</script>

<style scoped>
.agent-chat-input {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--color-bg-primary);
}

.input-container {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-textarea {
  flex: 1;
  border-radius: 20px;
  padding: 10px 16px;
  background: var(--color-bg-elevated);
  border-color: var(--color-border-default);
}

.chat-textarea:focus {
  border-color: var(--color-primary-500);
  box-shadow: var(--shadow-glow-sm);
}

.send-button {
  border-radius: 20px;
  padding: 8px 20px;
}

.input-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: center;
  margin-top: 6px;
}
</style>
```

---

## Task 8: 创建 AgentMessageList 组件

**Files:**
- Create: `frontend/src/components/agent/AgentMessageList.vue`

**Step 1: 创建 AgentMessageList.vue**

```vue
<template>
  <div class="agent-message-list" ref="listRef">
    <div class="messages-container">
      <!-- 欢迎消息（空状态） -->
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2"/>
          </svg>
        </div>
        <h2 class="welcome-title">智能图片助手</h2>
        <p class="welcome-desc">告诉我您想要的图片类型，我来帮您找到或生成</p>
      </div>

      <!-- 消息列表 -->
      <template v-for="(msg, index) in messages" :key="index">
        <!-- User Message -->
        <UserMessage
          v-if="msg.type === 'user'"
          :content="msg.content"
        />

        <!-- Assistant Message -->
        <AssistantMessage
          v-else-if="msg.type === 'assistant'"
          :content="msg.content"
          :isLoading="msg.isLoading"
        />

        <!-- Tool Request -->
        <ToolRequestMessage
          v-else-if="msg.type === 'tool-request'"
          :toolName="msg.toolName || '工具'"
          :description="msg.content"
        />

        <!-- Tool Response -->
        <ToolResponseMessage
          v-else-if="msg.type === 'tool-response'"
          :toolName="msg.toolName || ''"
          :content="msg.content"
        />

        <!-- Interrupt / Confirm -->
        <ToolConfirmMessage
          v-else-if="msg.type === 'interrupt'"
          :message="msg.content"
          @confirm="emit('confirm')"
          @cancel="emit('cancel')"
        />
      </template>

      <!-- 加载中 -->
      <div v-if="loading" class="loading-indicator">
        <a-spin size="small" />
        <span>思考中...</span>
      </div>
    </div>

    <!-- 滚动到底部按钮 -->
    <transition name="fade">
      <div v-if="showScrollButton" class="scroll-bottom-btn" @click="scrollToBottom">
        <a-badge :count="unreadCount" :number-style="{ backgroundColor: '#2268f5' }">
          <a-button shape="circle" size="small">
            <template #icon><DownOutlined /></template>
          </a-button>
        </a-badge>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import UserMessage from './message/UserMessage.vue'
import AssistantMessage from './message/AssistantMessage.vue'
import ToolRequestMessage from './message/ToolRequestMessage.vue'
import ToolResponseMessage from './message/ToolResponseMessage.vue'
import ToolConfirmMessage from './message/ToolConfirmMessage.vue'

interface Message {
  type: 'user' | 'assistant' | 'tool-request' | 'tool-response' | 'interrupt' | 'loading'
  content: string
  toolName?: string
  isLoading?: boolean
}

interface Props {
  messages: Message[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  messages: () => [],
  loading: false,
})

const emit = defineEmits(['confirm', 'cancel'])

const listRef = ref<HTMLElement | null>(null)
const showScrollButton = ref(false)
const unreadCount = ref(0)
const isAtBottom = ref(true)

// 滚动到底部
const scrollToBottom = () => {
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
    unreadCount.value = 0
    isAtBottom.value = true
  }
}

// 监听消息变化，自动滚动
watch(() => props.messages.length, () => {
  nextTick(() => {
    if (isAtBottom.value) {
      scrollToBottom()
    } else {
      unreadCount.value++
    }
  })
})
</script>

<style scoped>
.agent-message-list {
  flex: 1;
  overflow-y: auto;
  position: relative;
  padding: 16px 0;
}

.messages-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px;
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  text-align: center;
  color: var(--color-text-secondary);
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--gradient-aurora-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  color: var(--color-primary-500);
}

.welcome-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  max-width: 400px;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.scroll-bottom-btn {
  position: absolute;
  bottom: 16px;
  right: 24px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
```

---

## Task 9: 创建 AgentChatHeader 组件

**Files:**
- Create: `frontend/src/components/agent/AgentChatHeader.vue`

**Step 1: 创建 AgentChatHeader.vue**

```vue
<template>
  <div class="agent-chat-header">
    <div class="header-left">
      <h1 class="page-title">
        <span class="title-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2"/>
          </svg>
        </span>
        智能助手
      </h1>
    </div>
    <div class="header-right">
      <a-button @click="emit('newChat')">
        <template #icon><PlusOutlined /></template>
        新对话
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'

const emit = defineEmits(['newChat'])
</script>

<style scoped>
.agent-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border-subtle);
  background: var(--color-bg-primary);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.title-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--gradient-aurora);
  color: white;
}
</style>
```

---

## Task 10: 创建 AgentChatPage 页面

**Files:**
- Create: `frontend/src/pages/AgentChatPage.vue`

**Step 1: 创建 AgentChatPage.vue**

```vue
<template>
  <div class="agent-chat-page">
    <AgentChatHeader @newChat="handleNewChat" />

    <AgentMessageList
      :messages="displayMessages"
      :loading="streaming"
    />

    <FeedbackButtons
      :visible="showFeedback"
      @feedback="handleFeedback"
    />

    <AgentChatInput
      :loading="streaming"
      @send="handleSend"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import AgentChatHeader from '@/components/agent/AgentChatHeader.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'
import FeedbackButtons from '@/components/agent/FeedbackButtons.vue'
import { useAgentStream } from '@/composables/useAgentStream'
import { chatUsingPost, feedbackUsingPost } from '@/api/agentController'

// 生成 UUID
const generateThreadId = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

const threadId = ref(generateThreadId())
const showFeedback = ref(false)
const assistantContent = ref('')

const { messages, isStreaming, sendMessage } = useAgentStream()
const streaming = isStreaming

// 显示消息列表
const displayMessages = computed(() => {
  const result: any[] = []

  // 合并同 type 的消息
  let lastType = ''
  let lastContent = ''

  for (const msg of messages.value) {
    if (msg.type === 'AGENT_MODEL_STREAMING') {
      if (lastType === 'assistant') {
        lastContent += msg.content
      } else {
        if (lastType) {
          result.push({ type: lastType, content: lastContent })
        }
        lastType = 'assistant'
        lastContent = msg.content
      }
    } else if (msg.type === 'AGENT_TOOL_FINISHED') {
      if (lastType === 'assistant') {
        result.push({ type: lastType, content: lastContent })
        lastType = ''
        lastContent = ''
      }
      result.push({ type: 'tool-response', content: msg.content, toolName: msg.node })
    } else if (msg.type === 'AGENT_MODEL_FINISHED') {
      if (lastType === 'assistant' && lastContent) {
        result.push({ type: 'assistant', content: lastContent })
      }
      lastType = ''
      lastContent = ''
    }
  }

  if (lastType === 'assistant' && lastContent) {
    result.push({ type: 'assistant', content: lastContent })
  }

  return result
})

// 发送消息
const handleSend = async (text: string) => {
  if (!text.trim()) return

  showFeedback.value = false
  assistantContent.value = ''

  try {
    await sendMessage(text, threadId.value)

    // 检查是否需要显示反馈按钮
    const lastMsg = displayMessages.value[displayMessages.value.length - 1]
    if (lastMsg?.type === 'assistant') {
      // 简单判断：如果内容包含"满意"等关键词，显示反馈按钮
      if (/满意|符合|是否/.test(lastMsg.content)) {
        showFeedback.value = true
      }
    }
  } catch (error) {
    message.error('发送失败，请重试')
  }
}

// 处理反馈
const handleFeedback = async (action: string) => {
  showFeedback.value = false

  try {
    await feedbackUsingPost({
      threadId: threadId.value,
      satisfied: action === 'satisfied',
      reason: action,
      action: action.toUpperCase(),
      currentPhase: 'EXPLORATION',
    })

    // 重新发送一个空消息触发 Agent 继续
    await sendMessage('继续', threadId.value)
  } catch (error) {
    message.error('反馈发送失败')
  }
}

// 新对话
const handleNewChat = () => {
  threadId.value = generateThreadId()
  messages.value = []
  showFeedback.value = false
}

onMounted(() => {
  // 初始时可以显示引导
})
</script>

<style scoped>
.agent-chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg-primary);
}
</style>
```

---

## Task 11: 添加路由

**Files:**
- Modify: `frontend/src/router/index.ts`

**Step 1: 添加 Agent 路由**

在 import 区域添加：
```typescript
import AgentChatPage from '@/pages/AgentChatPage.vue'
```

在 routes 数组的 BasicLayout children 中添加：
```typescript
{
  path: 'agent',
  name: 'AgentChat',
  component: AgentChatPage,
  meta: { title: '智能助手' },
},
```

---

## Task 12: 添加导航栏入口

**Files:**
- Modify: `frontend/src/components/GlobalHeader.vue`

**Step 1: 在 originItems 中添加智能助手入口**

找到 `originItems` 数组，添加新项：
```typescript
{
  key: '/agent',
  label: '🤖 智能助手',
},
```

位置：在 `{ key: '/', label: '🏠 主页' }` 之后

---

## Task 13: 添加首页入口卡片

**Files:**
- Modify: `frontend/src/pages/HomePage.vue`

**Step 1: 添加智能助手入口卡片**

找到合适的位置（在现有功能卡片附近），添加：
```vue
<div class="feature-card agent-card" @click="$router.push('/agent')">
  <div class="card-icon">
    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10"/>
      <path d="M12 6v6l4 2"/>
    </svg>
  </div>
  <h3 class="card-title">智能助手</h3>
  <p class="card-desc">AI 驱动的图片搜索与生成</p>
</div>
```

**Step 2: 添加样式**

在 HomePage 的 `<style>` 中添加：
```css
.agent-card {
  background: var(--gradient-aurora-subtle);
  border: 1px solid var(--color-border-accent);
}
```

---

## Task 14: 编译验证

**Step 1: 运行编译**

```bash
cd frontend
npm run build
```

预期：无编译错误

**Step 2: 启动开发服务器**

```bash
cd frontend
npm run dev
```

预期：开发服务器启动，无错误

**Step 3: 手动测试**

1. 访问 http://localhost:5173/agent
2. 输入 "帮我找一张猫咪图片"
3. 验证 SSE 流式输出是否正常
4. 验证反馈按钮是否正常显示

---

## 提交

所有任务完成后，执行：

```bash
git add frontend/src/
git commit -m "feat(agent): 完成 Agent Chat 前端页面开发

- 新增 agentController.ts API 接口
- 新增 useAgentStream.ts SSE 流式读取
- 新增 AgentMessageList/AgentChatInput/FeedbackButtons 等组件
- 新增 AgentChatPage 独立页面
- 添加路由和导航栏入口

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```
