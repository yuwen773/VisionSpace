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
