<template>
  <div class="agent-message-list" ref="listRef">
    <div class="messages-container">
      <!-- Welcome Screen -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-orb" aria-hidden="true">
          <span class="orb-ring"></span>
          <span class="orb-core"></span>
        </div>
        <div class="welcome-text">
          <h2 class="welcome-title">智能图片助手</h2>
          <p class="welcome-desc">描述您想要的画面，我来帮您寻找或创作</p>
        </div>
        <div class="welcome-hints">
          <button
            v-for="(hint, i) in quickHints"
            :key="i"
            class="hint-chip"
            :style="{ animationDelay: i * 50 + 'ms' }"
            @click="emit('send', hint)"
          >
            {{ hint }}
          </button>
        </div>
      </div>

      <!-- Messages -->
      <template v-for="(msg, index) in messages" :key="index">
        <div class="msg-animate">
          <UserMessage v-if="msg.type === 'user'" :content="msg.content" :time="msg.time" />
          <ReasoningMessage v-else-if="msg.type === 'reasoning'" :content="msg.content" />
          <AssistantMessage
  v-else-if="msg.type === 'assistant'"
  :content="msg.content"
  :isLoading="msg.isLoading"
  :images="images"
  :has-resources="hasResources"
  @toggle-resources="emit('toggleResources')"
/>
          <ToolRequestMessage v-else-if="msg.type === 'tool-request'" :toolName="msg.toolName || '工具'" :toolCalls="msg.toolCalls" :content="msg.content" />
          <ToolResponseMessage v-else-if="msg.type === 'tool-response'" :toolName="msg.toolName || ''" :content="msg.content" />
          <ToolConfirmMessage v-else-if="msg.type === 'tool-confirm' || msg.type === 'interrupt'" :message="msg.content" @confirm="emit('confirm')" @cancel="emit('cancel')" />
        </div>
      </template>

      <!-- Typing indicator -->
      <transition name="typing-fade">
        <div v-if="loading" class="typing-row">
          <div class="typing-pill">
            <span class="typing-bar"></span>
            <span class="typing-bar"></span>
            <span class="typing-bar"></span>
          </div>
        </div>
      </transition>
    </div>

    <!-- Scroll-to-bottom -->
    <transition name="scroll-reveal">
      <button v-if="showScrollBtn" class="scroll-btn" @click="scrollToBottom" aria-label="滚动到底部">
        <svg class="scroll-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9" />
        </svg>
        <span v-if="unreadCount > 0" class="scroll-badge">{{ unreadCount }}</span>
      </button>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue'
import UserMessage from './message/UserMessage.vue'
import AssistantMessage from './message/AssistantMessage.vue'
import ReasoningMessage from './message/ReasoningMessage.vue'
import ToolRequestMessage from './message/ToolRequestMessage.vue'
import ToolResponseMessage from './message/ToolResponseMessage.vue'
import ToolConfirmMessage from './message/ToolConfirmMessage.vue'
import type { ToolCallArg } from '@/composables/useAgentStream'

interface Message {
  type: 'user' | 'assistant' | 'reasoning' | 'tool-request' | 'tool-response' | 'tool-confirm' | 'interrupt' | 'loading'
  content: string
  toolName?: string
  toolCalls?: ToolCallArg[]
  isLoading?: boolean
  time?: string
  images?: string[]
}

interface Props {
  messages: Message[]
  loading?: boolean
  images?: { url: string; title?: string }[]
  links?: { url: string; title: string; snippet: string; domain: string }[]
}

const props = withDefaults(defineProps<Props>(), {
  messages: () => [],
  loading: false,
  images: () => [],
  links: () => [],
})

const hasResources = computed(() =>
  (props.images && props.images.length > 0) || (props.links && props.links.length > 0)
)

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'send', text: string): void
  (e: 'toggleResources'): void
}>()

const quickHints = [
  '一张赛博朋克风格的城市夜景',
  '用水彩风格画一只猫',
  '复古海报风格的旅行插画',
]

const listRef = ref<HTMLElement | null>(null)
const showScrollBtn = ref(false)
const unreadCount = ref(0)
const isAtBottom = ref(true)

const scrollToBottom = () => {
  if (listRef.value) {
    listRef.value.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' })
    unreadCount.value = 0
    isAtBottom.value = true
  }
}

const checkBottom = () => {
  if (!listRef.value) return
  const el = listRef.value
  isAtBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

onMounted(() => {
  listRef.value?.addEventListener('scroll', checkBottom, { passive: true })
})

onUnmounted(() => {
  listRef.value?.removeEventListener('scroll', checkBottom)
})

watch(() => props.messages.length, () => {
  nextTick(() => {
    if (isAtBottom.value) {
      scrollToBottom()
    } else {
      unreadCount.value++
      showScrollBtn.value = true
    }
  })
})

watch(() => props.loading, (val) => {
  if (val) {
    nextTick(() => {
      if (isAtBottom.value) scrollToBottom()
    })
  }
})
</script>

<style scoped>
.agent-message-list {
  flex: 1;
  overflow-y: auto;
  position: relative;
  padding: 20px 0;
}

.messages-container {
  max-width: 100%;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  position: relative;
}

/* ============ Welcome Screen ============ */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  text-align: center;
  padding: 40px 20px;
}

@keyframes welcomeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Welcome Orb */
.welcome-orb {
  position: relative;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  animation: welcomeOrbIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0ms both;
}

.orb-core {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--gradient-aurora);
  display: block;
  z-index: 1;
}

.orb-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid transparent;
  animation: orbRingSpin 8s linear infinite;
}

[data-theme="aurora"] .orb-core {
  box-shadow: 0 0 30px rgba(34, 104, 245, 0.4), 0 0 60px rgba(110, 53, 235, 0.2);
}

[data-theme="aurora"] .orb-ring {
  border-color: rgba(34, 104, 245, 0.2);
  box-shadow: 0 0 20px rgba(34, 104, 245, 0.1);
}

[data-theme="pop"] .orb-core {
  box-shadow: 0 0 24px rgba(168, 85, 247, 0.3), 0 0 48px rgba(236, 72, 153, 0.12);
}

[data-theme="pop"] .orb-ring {
  border-color: rgba(168, 85, 247, 0.15);
}

@keyframes orbRingSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes welcomeOrbIn {
  from { opacity: 0; transform: scale(0.8); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes hintChipIn {
  from { opacity: 0; transform: scale(0.9) translateY(6px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.welcome-text {
  animation: welcomeIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) 150ms both;
}

.welcome-title {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 6px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
  max-width: 360px;
  line-height: 1.5;
}

/* Quick hint chips */
.welcome-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 24px;
  animation: welcomeIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) 300ms both;
}

.hint-chip {
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-elevated);
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base, 200ms ease);
  line-height: 1.4;
  animation: hintChipIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.hint-chip:hover {
  border-color: var(--color-primary-500);
  color: var(--color-primary-500);
}

[data-theme="aurora"] .hint-chip:hover {
  background: rgba(34, 104, 245, 0.08);
  box-shadow: 0 0 12px rgba(34, 104, 245, 0.1);
}

[data-theme="pop"] .hint-chip:hover {
  background: rgba(168, 85, 247, 0.06);
}

/* ============ Typing Indicator ============ */
.typing-row {
  padding: 16px 16px 16px 20px;
}

.typing-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 10px 18px;
  border-radius: 18px 18px 18px 4px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-subtle);
  position: relative;
  overflow: hidden;
}

/* Subtle shimmer sweep across the pill */
.typing-pill::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.04) 40%,
    rgba(255, 255, 255, 0.08) 50%,
    rgba(255, 255, 255, 0.04) 60%,
    transparent 100%
  );
  animation: shimmer 2.8s ease-in-out infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

/* Three bars that breathe in a wave */
.typing-bar {
  display: block;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--color-primary-500);
  opacity: 0.35;
  animation: barPulse 1.6s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  will-change: transform, opacity;
}

.typing-bar:nth-child(1) { animation-delay: 0s; }
.typing-bar:nth-child(2) { animation-delay: 0.18s; }
.typing-bar:nth-child(3) { animation-delay: 0.36s; }

@keyframes barPulse {
  0%, 100% {
    transform: scaleY(0.55);
    opacity: 0.3;
  }
  40% {
    transform: scaleY(1);
    opacity: 0.9;
  }
}

[data-theme="aurora"] .typing-bar {
  background: #2268f5;
}

[data-theme="pop"] .typing-bar {
  background: #a855f7;
}

/* ============ Scroll Button ============ */
.scroll-btn {
  position: sticky;
  bottom: 20px;
  float: right;
  margin-right: 24px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--color-border-subtle);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12), 0 0 0 0 transparent;
}

.scroll-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
}

[data-theme="aurora"] .scroll-btn:hover {
  border-color: rgba(34, 104, 245, 0.4);
  color: #2268f5;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18), 0 0 20px rgba(34, 104, 245, 0.12);
}

[data-theme="pop"] .scroll-btn:hover {
  border-color: rgba(168, 85, 247, 0.4);
  color: #a855f7;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18), 0 0 20px rgba(168, 85, 247, 0.12);
}

.scroll-btn:active {
  transform: scale(0.92);
}

.scroll-icon {
  flex-shrink: 0;
}

.scroll-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  color: #fff;
  pointer-events: none;
}

[data-theme="aurora"] .scroll-badge {
  background: #2268f5;
  box-shadow: 0 0 8px rgba(34, 104, 245, 0.4);
}

[data-theme="pop"] .scroll-badge {
  background: #a855f7;
  box-shadow: 0 0 8px rgba(168, 85, 247, 0.4);
}

/* Scroll button reveal animation */
.scroll-reveal-enter-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.scroll-reveal-leave-active {
  transition: all 0.2s ease;
}

.scroll-reveal-enter-from {
  opacity: 0;
  transform: translateY(8px) scale(0.9);
}

.scroll-reveal-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

/* ============ Message Animation ============ */
.msg-animate {
  padding: 12px 20px;
  animation: msgIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Typing indicator fade */
.typing-fade-enter-active {
  transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

.typing-fade-leave-active {
  transition: opacity 0.2s ease;
}

.typing-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.typing-fade-leave-to {
  opacity: 0;
}
</style>
