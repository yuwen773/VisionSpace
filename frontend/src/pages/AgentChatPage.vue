<template>
  <div class="agent-chat-page">
    <AgentChatLayout
      :left-collapsed="leftCollapsed"
      :right-expanded="resourcePanelVisible"
    >
      <!-- 左栏 -->
      <template #left>
        <LeftSidebar
          :histories="histories"
          :active-id="currentThreadId"
          @new-chat="handleNewChat"
          @select="handleSelectHistory"
        />
      </template>

      <!-- 中间 -->
      <template #center>
        <!-- 左折叠按钮 -->
        <button
          v-if="leftCollapsed"
          class="toggle-left-btn"
          @click="leftCollapsed = false"
          aria-label="展开侧边栏"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>

        <!-- 右栏切换按钮 -->
        <button
          class="toggle-right-btn"
          :class="{ active: resourcePanelVisible }"
          @click="resourcePanelVisible = !resourcePanelVisible"
          aria-label="切换资源面板"
          title="资源面板"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <polyline points="21 15 16 10 5 21" />
          </svg>
          <span v-if="totalResourceCount > 0" class="resource-badge">{{ totalResourceCount }}</span>
        </button>

        <!-- 消息列表 -->
        <AgentMessageList
          :messages="displayMessages"
          :loading="streaming"
          :images="currentImages"
          :links="currentLinks"
          @confirm="handleConfirm"
          @cancel="handleCancel"
          @toggle-resources="toggleResourcePanel"
        />

        <!-- 底部区域：TodoList + 输入框 -->
        <div class="bottom-area">
          <AgentTodoList
            v-if="showTodoList"
            :steps="todoSteps"
            :current-step="currentTodoStep"
            @toggle="toggleTodoListExpanded"
          />
          <AgentChatInput
            :loading="streaming"
            @send="handleSend"
            @stop="abort"
          />
        </div>
      </template>

      <!-- 右栏 -->
      <template #right>
        <AgentResourcePanel
          :images="allImages"
          :links="allLinks"
          @close="resourcePanelVisible = false"
          @preview-image="handlePreviewImage"
          @download-image="handleDownloadImage"
        />
      </template>
    </AgentChatLayout>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import AgentChatLayout from '@/components/agent/AgentChatLayout.vue'
import LeftSidebar from '@/components/agent/LeftSidebar.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'
import AgentTodoList from '@/components/agent/AgentTodoList.vue'
import AgentResourcePanel from '@/components/agent/AgentResourcePanel.vue'
import type { HistoryItem } from '@/components/agent/LeftSidebar.vue'
import { useAgentStream } from '@/composables/useAgentStream'

const STORAGE_KEY = 'agent_chat_histories'

const loadHistories = (): HistoryItem[] => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored ? JSON.parse(stored) : []
  } catch {
    return []
  }
}

const saveHistories = (list: HistoryItem[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch (e) {
    console.warn('Failed to save histories:', e)
  }
}

const generateThreadId = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

// State
const threadId = ref(generateThreadId())
const currentThreadId = ref('')
const leftCollapsed = ref(false)
const resourcePanelVisible = ref(false)
const todoListExpanded = ref(false)
const showTodoList = ref(false)

// Todo steps
const todoSteps = ref([
  { title: '探索', description: '分析用户需求', status: 'pending' as const },
  { title: '评审', description: '检查素材质量', status: 'pending' as const },
  { title: '生成', description: '生成图片', status: 'pending' as const },
  { title: '优化', description: '等待用户反馈', status: 'pending' as const },
])

const currentTodoStep = ref(0)

// Resource data
const allImages = ref<{ url: string; title?: string }[]>([])
const allLinks = ref<{ url: string; title: string; snippet: string; domain: string }[]>([])

const currentImages = computed(() => {
  return allImages.value.slice(-4)
})

const currentLinks = computed(() => {
  return allLinks.value.slice(0, 3)
})

const totalResourceCount = computed(() => allImages.value.length + allLinks.value.length)

// Histories
const histories = ref<HistoryItem[]>(loadHistories())

// Agent stream
const { messages, isStreaming, sendMessage, abort } = useAgentStream()
const streaming = isStreaming

// User messages
const userMessages = ref<any[]>([])

// Display messages - keep the same logic as before
const displayMessages = computed(() => {
  const result: any[] = [...userMessages.value]
  let accumulatedAssistant = ''

  for (const msg of messages.value) {
    if (msg.type === 'assistant') {
      if (accumulatedAssistant) {
        accumulatedAssistant += msg.content
      } else {
        accumulatedAssistant = msg.content
      }
    } else {
      if (accumulatedAssistant) {
        result.push({ type: 'assistant', content: accumulatedAssistant })
        accumulatedAssistant = ''
      }
      if (['tool-request', 'tool-response', 'tool-confirm', 'error'].includes(msg.type)) {
        result.push({ type: msg.type, content: msg.content, toolName: msg.toolName })
      }
    }
  }

  if (accumulatedAssistant) {
    result.push({ type: 'assistant', content: accumulatedAssistant })
  }

  return result
})

// Watch streaming state
watch(streaming, (isStreaming) => {
  if (isStreaming) {
    showTodoList.value = true
    allImages.value = []
    allLinks.value = []
  }
})

// Handlers
const handleSend = async (text: string) => {
  if (!text.trim()) return

  userMessages.value.push({
    type: 'user',
    content: text,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
  })

  try {
    await sendMessage(text, threadId.value)
  } catch (error) {
    message.error('发送失败，请重试')
  }
}

const handleNewChat = () => {
  if (userMessages.value.length > 0 || messages.value.length > 0) {
    const historyItem: HistoryItem = {
      id: threadId.value,
      title: userMessages.value[0]?.content?.slice(0, 30) || '新对话',
      time: new Date().toLocaleString('zh-CN'),
      messages: [...userMessages.value, ...messages.value],
    }
    histories.value.unshift(historyItem)
    saveHistories(histories.value)
  }

  threadId.value = generateThreadId()
  messages.value = []
  userMessages.value = []
  allImages.value = []
  allLinks.value = []
  showTodoList.value = false
  resourcePanelVisible.value = false
}

const handleSelectHistory = (id: string) => {
  const history = histories.value.find(h => h.id === id)
  if (history) {
    currentThreadId.value = id
    userMessages.value = history.messages?.filter((m: any) => m.type === 'user') || []
    messages.value = history.messages?.filter((m: any) => m.type !== 'user') || []
  }
}

const toggleResourcePanel = () => {
  resourcePanelVisible.value = !resourcePanelVisible.value
}

const toggleTodoListExpanded = () => {
  todoListExpanded.value = !todoListExpanded.value
}

const handleConfirm = () => {}
const handleCancel = () => {}
const handlePreviewImage = (url: string) => { window.open(url, '_blank') }
const handleDownloadImage = (url: string) => { window.open(url, '_blank') }
</script>

<style scoped>
.agent-chat-page {
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg-primary);
}

.toggle-left-btn {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-left-btn:hover {
  color: var(--color-text-primary);
  border-color: var(--color-border-default);
}

.toggle-right-btn {
  position: absolute;
  top: 14px;
  right: 20px;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-right-btn:hover {
  color: var(--color-text-primary);
  border-color: var(--color-border-default);
}

.toggle-right-btn.active {
  color: var(--color-primary-500);
  border-color: var(--color-primary-500);
  background: rgba(168, 85, 247, 0.08);
}

.resource-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary-500);
  color: white;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.bottom-area {
  flex-shrink: 0;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
}
</style>
