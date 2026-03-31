<template>
  <div class="agent-chat-page">
    <AgentChatHeader @newChat="handleNewChat" @openHistory="handleOpenHistory" />

    <!-- 添加进度条 -->
    <IterationProgress
      v-if="showProgress"
      :phase="currentPhase"
      :iterations="iterations"
      :remainingIterations="3"
    />

    <HistoryPanel
      :visible="showHistory"
      :histories="histories"
      :activeIndex="-1"
      @close="showHistory = false"
      @select="handleSelectHistory"
    />

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
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import AgentChatHeader from '@/components/agent/AgentChatHeader.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'
import FeedbackButtons from '@/components/agent/FeedbackButtons.vue'
import HistoryPanel from '@/components/agent/HistoryPanel.vue'
import IterationProgress from '@/components/agent/IterationProgress.vue'
import type { HistoryItem } from '@/components/agent/HistoryPanel.vue'
import { useAgentStream } from '@/composables/useAgentStream'
import { chatUsingPost, feedbackUsingPost } from '@/api/agentController'

const STORAGE_KEY = 'agent_chat_histories'

// Load from localStorage
const loadHistories = (): HistoryItem[] => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored ? JSON.parse(stored) : []
  } catch {
    return []
  }
}

// Save to localStorage
const saveHistories = (histories: HistoryItem[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(histories))
  } catch (e) {
    console.warn('Failed to save histories:', e)
  }
}

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
const showHistory = ref(false)

// 进度状态
const currentPhase = ref<'EXPLORATION' | 'REVIEW' | 'GENERATION' | 'DONE'>('EXPLORATION')
const iterations = ref([
  { title: '初始化', description: '开始分析需求', status: 'completed' as const },
])
const showProgress = ref(false)

// Watch for streaming state
watch(streaming, (isStreaming) => {
  if (isStreaming) {
    showProgress.value = true
  }
})

const handleOpenHistory = () => {
  showHistory.value = true
}

const histories = ref<HistoryItem[]>(loadHistories())
const handleSelectHistory = (index: number) => {
  showHistory.value = false
}

const { messages, isStreaming, sendMessage } = useAgentStream()
const streaming = isStreaming

// 用户消息列表（本地维护）
const userMessages = ref<any[]>([])

// 显示消息列表（用户消息 + Agent 消息）
const displayMessages = computed(() => {
  const result: any[] = [...userMessages.value]

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

  // 添加用户消息到列表
  userMessages.value.push({
    type: 'user',
    content: text,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
  })

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
  // Save current conversation if has messages
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
