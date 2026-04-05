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
  @toggle-resources="(data: ResourceData) => emit('toggleResources', data)"
/>
          <ToolRequestMessage v-else-if="msg.type === 'tool-request'" :toolName="msg.toolName || '工具'" :toolCalls="msg.toolCalls" :content="msg.content" />
          <ToolResponseMessage v-else-if="msg.type === 'tool-response'" :toolName="msg.toolName || ''" :content="msg.content" />
          <ToolConfirmMessage v-else-if="msg.type === 'mcp-confirm' || msg.type === 'interrupt'" :message="msg.content" @confirm="emit('confirm')" @cancel="emit('cancel')" />
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
      <button
        v-if="showScrollBtn"
        class="scroll-btn"
        :class="{ 'has-unread': unreadCount > 0 }"
        @click="scrollToBottom"
        aria-label="滚动到底部"
      >
        <span v-if="unreadCount > 0" class="scroll-pulse" aria-hidden="true"></span>
        <svg class="scroll-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="7 7 12 12 17 7" />
          <polyline points="7 13 12 18 17 13" opacity="0.4" />
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
import type { ResourceData } from './types'

interface Message {
  type: 'user' | 'assistant' | 'reasoning' | 'tool-request' | 'tool-response' | 'mcp-confirm' | 'interrupt' | 'loading'
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
}

const props = withDefaults(defineProps<Props>(), {
  messages: () => [],
  loading: false,
})

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'send', text: string): void
  (e: 'toggleResources', data: ResourceData): void
}>()

const quickHints = [
  '一张赛博朋克风格的城市夜景',
  '用水彩风格画一只猫',
  '复古海报风格的旅行插画',
]

const listRef = ref<HTMLElement | null>(null)
const unreadCount = ref(0)
const isAtBottom = ref(true)

const showScrollBtn = computed(() => !isAtBottom.value)

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
  const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  isAtBottom.value = distanceToBottom < 80

  if (isAtBottom.value) {
    unreadCount.value = 0
  }
}

onMounted(() => {
  listRef.value?.addEventListener('scroll', checkBottom, { passive: true })
  nextTick(checkBottom)
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
  min-height: 0;
  overflow-y: auto;
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px 0;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-default) transparent;
}

.agent-message-list::-webkit-scrollbar {
  width: 6px;
}

.agent-message-list::-webkit-scrollbar-track {
  background: transparent;
}

.agent-message-list::-webkit-scrollbar-thumb {
  background: var(--color-border-default);
  border-radius: 10px;
}

.agent-message-list::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-tertiary);
}

.messages-container {
  max-width: 100%;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
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
  bottom: 16px;
  align-self: flex-end;
  margin-right: 28px;
  z-index: 50;
  flex: none;

  width: 44px;
  height: 44px;
  aspect-ratio: 1;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-primary-400, var(--color-primary-500)));
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 4px 20px color-mix(in srgb, var(--color-primary-500) 35%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.scroll-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px color-mix(in srgb, var(--color-primary-500) 40%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.scroll-btn:hover .scroll-icon {
  transform: translateY(2px);
}

.scroll-btn:active {
  transform: scale(0.88);
  transition-duration: 0.1s;
}

.scroll-btn:active .scroll-icon {
  transform: translateY(3px);
}

.scroll-icon {
  flex-shrink: 0;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* Aurora theme */
[data-theme="aurora"] .scroll-btn {
  background: linear-gradient(135deg, #2268f5, #5b9aff);
  box-shadow: 0 4px 20px rgba(34, 104, 245, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

[data-theme="aurora"] .scroll-btn:hover {
  box-shadow: 0 8px 28px rgba(34, 104, 245, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

/* Pop theme */
[data-theme="pop"] .scroll-btn {
  background: linear-gradient(135deg, #a855f7, #c084fc);
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

[data-theme="pop"] .scroll-btn:hover {
  box-shadow: 0 8px 28px rgba(168, 85, 247, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

/* Unread pulse ring */
.scroll-pulse {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  pointer-events: none;
  animation: scrollPulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes scrollPulse {
  0% {
    transform: scale(0.95);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.25);
    opacity: 0;
  }
  100% {
    transform: scale(0.95);
    opacity: 0;
  }
}

[data-theme="aurora"] .scroll-pulse {
  border: 2px solid rgba(34, 104, 245, 0.5);
}

[data-theme="pop"] .scroll-pulse {
  border: 2px solid rgba(168, 85, 247, 0.5);
}

/* Badge */
.scroll-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  color: #fff;
  pointer-events: none;
  background: #ef4444;
  box-shadow: 0 0 0 2px var(--color-bg-primary, #fff),
    0 2px 8px rgba(239, 68, 68, 0.4);
  animation: badgePop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badgePop {
  from { transform: scale(0); }
  to { transform: scale(1); }
}

/* Scroll button reveal animation */
.scroll-reveal-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.scroll-reveal-leave-active {
  transition: all 0.2s ease-in;
}

.scroll-reveal-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.85);
}

.scroll-reveal-leave-to {
  opacity: 0;
  transform: scale(0.85);
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
