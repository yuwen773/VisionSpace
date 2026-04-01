<template>
  <div class="agent-chat-input">
    <div class="input-wrapper">
      <div class="input-field">
        <a-textarea
          v-model:value="inputText"
          :rows="1"
          :auto-size="{ minRows: 1, maxRows: 4 }"
          :placeholder="placeholder"
          @pressEnter="handleSend"
          class="chat-textarea"
        />
        <transition name="fade">
          <span v-if="inputText.length > 0" class="char-count">{{ inputText.length }}</span>
        </transition>
      </div>

      <transition name="btn-switch" mode="out-in">
        <button
          v-if="!loading"
          key="send"
          class="action-btn send-btn"
          :disabled="!inputText.trim()"
          @click="handleSend"
          aria-label="发送"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>

        <button
          v-else
          key="stop"
          class="action-btn stop-btn"
          @click="handleStop"
          aria-label="停止"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2" />
          </svg>
        </button>
      </transition>
    </div>

    <div class="input-hint">
      <span>Enter 发送 · Shift+Enter 换行</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  loading?: boolean
  placeholder?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  placeholder: '描述您想要的图片…',
})

const emit = defineEmits<{
  (e: 'send', text: string): void
  (e: 'stop'): void
}>()

const inputText = ref('')

const handleSend = (e?: KeyboardEvent) => {
  if (e?.shiftKey) return // Shift+Enter = newline
  if (!inputText.value.trim()) return
  emit('send', inputText.value.trim())
  inputText.value = ''
}

const handleStop = () => {
  emit('stop')
}
</script>

<style scoped>
.agent-chat-input {
  padding: 12px 16px 10px;
  position: relative;
  z-index: 10;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--glass-bg, rgba(10, 15, 26, 0.75));
  backdrop-filter: var(--glass-blur, blur(20px));
  -webkit-backdrop-filter: var(--glass-blur, blur(20px));
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.input-field {
  flex: 1;
  position: relative;
}

.chat-textarea {
  width: 100%;
  border-radius: 14px;
  padding: 10px 48px 10px 16px;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-subtle);
  color: var(--color-text-primary);
  transition: border-color var(--transition-fast, 150ms ease), box-shadow var(--transition-fast, 150ms ease);
}

.chat-textarea::placeholder {
  color: var(--color-text-tertiary);
}

.chat-textarea:focus {
  border-color: var(--color-primary-500);
  outline: none;
}

[data-theme="aurora"] .chat-textarea:focus {
  box-shadow: 0 0 0 3px rgba(34, 104, 245, 0.15);
}

[data-theme="pop"] .chat-textarea:focus {
  box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.12);
}

.char-count {
  position: absolute;
  right: 14px;
  bottom: 10px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}

/* Action button */
.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--transition-base, 200ms ease);
}

.send-btn {
  background: var(--color-primary-500);
  color: white;
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  filter: saturate(0.3);
}

.send-btn:not(:disabled):hover {
  transform: translateY(-1px);
  filter: brightness(1.1);
}

[data-theme="aurora"] .send-btn:not(:disabled):hover {
  box-shadow: 0 0 18px rgba(34, 104, 245, 0.35);
}

[data-theme="pop"] .send-btn:not(:disabled):hover {
  box-shadow: 0 4px 14px rgba(168, 85, 247, 0.3);
}

.stop-btn {
  background: var(--color-error, #ef4444);
  color: white;
}

.stop-btn:hover {
  filter: brightness(1.15);
}

/* Button switch transition */
.btn-switch-enter-active,
.btn-switch-leave-active {
  transition: all 180ms cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-switch-enter-from {
  opacity: 0;
  transform: scale(0.85);
}

.btn-switch-leave-to {
  opacity: 0;
  transform: scale(0.85);
}

/* Hint */
.input-hint {
  text-align: center;
  margin-top: 6px;
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.02em;
}

/* Fade for char count */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 150ms ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
