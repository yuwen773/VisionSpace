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
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import AgentChatHeader from '@/components/agent/AgentChatHeader.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'
import FeedbackButtons from '@/components/agent/FeedbackButtons.vue'
import HistoryPanel from '@/components/agent/HistoryPanel.vue'
import IterationProgress from '@/components/agent/IterationProgress.vue'
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
const showHistory = ref(false)

// 进度状态
const currentPhase = ref<'EXPLORATION' | 'REVIEW' | 'GENERATION' | 'DONE'>('EXPLORATION')
const iterations = ref([
  { title: '初始化', description: '开始分析需求', status: 'completed' as const },
])
const showProgress = ref(true)

const handleOpenHistory = () => {
  showHistory.value = true
}

// 占位：后续完善历史记录功能
const histories = ref<any[]>([])
const handleSelectHistory = (index: number) => {
  showHistory.value = false
}

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
