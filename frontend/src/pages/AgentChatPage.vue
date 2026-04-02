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
          @collapse="leftCollapsed = true"
        />
      </template>

      <!-- 中间 -->
      <template #center>
        <!-- 左折叠按钮组 -->
        <div v-if="leftCollapsed" class="collapsed-actions">
          <button
            class="toggle-left-btn"
            @click="leftCollapsed = false"
            aria-label="展开侧边栏"
            title="展开侧边栏"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <button
            class="quick-new-chat-btn"
            @click="handleNewChat"
            aria-label="新对话"
            title="新对话"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
        </div>

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
          :messages="messages"
          :loading="streaming"
          :images="currentImages"
          :links="currentLinks"
          @confirm="handleConfirm"
          @cancel="handleCancel"
          @send="handleSend"
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

const generateThreadId = () => crypto.randomUUID()

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

// Histories
const histories = ref<HistoryItem[]>(loadHistories())

// Agent stream
const agentStream = useAgentStream()
const messages = agentStream.messages
const streaming = agentStream.isStreaming
const allImages = agentStream.images
const allLinks = agentStream.links
const sendMessage = agentStream.sendMessage
const abort = agentStream.abort
const pushMessage = agentStream.pushMessage
const resetStream = agentStream.reset

const currentImages = computed(() => {
  return allImages.value.slice(-4)
})

const currentLinks = computed(() => {
  return allLinks.value.slice(0, 3)
})

const totalResourceCount = computed(() => allImages.value.length + allLinks.value.length)

// Watch streaming state
watch(streaming, (isStreaming) => {
  if (isStreaming) {
    showTodoList.value = true
  }
})

// Handlers
const handleSend = async (text: string, files: File[] = []) => {
  if ((!text.trim() && files.length === 0) || streaming.value) return

  // Create preview URLs for images in the user message bubble
  const imagePreviews = files
    .filter(f => f.type.startsWith('image/'))
    .map(f => URL.createObjectURL(f))

  pushMessage({
    type: 'user',
    content: text,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    images: imagePreviews.length > 0 ? imagePreviews : undefined,
  })

  try {
    await sendMessage(text, threadId.value, files.length > 0 ? files : undefined)
  } catch (error) {
    message.error('发送失败，请重试')
    // Revoke preview URLs on error (message won't be shown)
    imagePreviews.forEach(url => URL.revokeObjectURL(url))
  }
}

const handleNewChat = () => {
  if (messages.value.length > 0) {
    const historyItem: HistoryItem = {
      id: threadId.value,
      title: messages.value.find(m => m.type === 'user')?.content?.slice(0, 30) || '新对话',
      time: new Date().toLocaleString('zh-CN'),
      messages: [...messages.value],
    }
    histories.value.unshift(historyItem)
    saveHistories(histories.value)
  }

  threadId.value = generateThreadId()
  resetStream()
  showTodoList.value = false
  resourcePanelVisible.value = false
}

const handleSelectHistory = (id: string) => {
  const history = histories.value.find(h => h.id === id)
  if (history) {
    currentThreadId.value = id
    resetStream()
    messages.value = history.messages || []
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

.collapsed-actions {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toggle-left-btn,
.quick-new-chat-btn {
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

.toggle-left-btn:hover,
.quick-new-chat-btn:hover {
  color: var(--color-text-primary);
  border-color: var(--color-border-default);
}

.quick-new-chat-btn:hover {
  color: var(--color-primary-500);
  border-color: var(--color-primary-500);
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
