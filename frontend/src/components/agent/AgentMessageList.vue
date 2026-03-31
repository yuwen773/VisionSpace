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
        <transition v-if="msg.type === 'user'" name="message-slide">
          <UserMessage
            :content="msg.content"
            :time="msg.time"
          />
        </transition>

        <!-- Assistant Message -->
        <transition v-else-if="msg.type === 'assistant'" name="message-slide">
          <AssistantMessage
            :content="msg.content"
            :isLoading="msg.isLoading"
          />
        </transition>

        <!-- Tool Request -->
        <transition v-else-if="msg.type === 'tool-request'" name="message-slide">
          <ToolRequestMessage
            :toolName="msg.toolName || '工具'"
            :description="msg.content"
          />
        </transition>

        <!-- Tool Response -->
        <transition v-else-if="msg.type === 'tool-response'" name="message-slide">
          <ToolResponseMessage
            :toolName="msg.toolName || ''"
            :content="msg.content"
          />
        </transition>

        <!-- Interrupt / Confirm -->
        <transition v-else-if="msg.type === 'interrupt'" name="message-slide">
          <ToolConfirmMessage
            :message="msg.content"
            @confirm="emit('confirm')"
            @cancel="emit('cancel')"
          />
        </transition>
      </template>

      <!-- 加载中 -->
      <div v-if="loading" class="typing-indicator">
        <div class="typing-dots">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
        <span class="typing-text">思考中...</span>
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
import { ref, watch, nextTick } from 'vue'
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

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-500);
  animation: bounce 1.4s ease-in-out infinite;
}

.typing-dots .dot:nth-child(1) { animation-delay: 0s; }
.typing-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dots .dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

/* Message slide animation */
.message-slide-enter-active {
  transition: all 0.3s ease-out;
}

.message-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
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
