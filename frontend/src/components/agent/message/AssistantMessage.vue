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
      <div class="message-text markdown-body" v-html="renderedContent"></div>
      <div v-if="isLoading" class="typing-indicator">
        <span class="typing-cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'

interface Props {
  content: string
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  isLoading: false,
})

const { render } = useMarkdown()
const renderedContent = computed(() => render(props.content))
const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
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

/* Markdown styling */
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
}

.markdown-body :deep(p) {
  margin: 0 0 8px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-500);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(code:not(.hljs)) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}

.markdown-body :deep(.hljs.code-block) {
  background: var(--color-bg-primary);
  border-radius: 8px;
  margin: 8px 0;
  overflow: hidden;
}

.markdown-body :deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-subtle);
}

.markdown-body :deep(.code-lang) {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
}

.markdown-body :deep(.copy-btn) {
  background: transparent;
  border: 1px solid var(--color-border-default);
  color: var(--color-text-secondary);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.markdown-body :deep(.copy-btn:hover) {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.markdown-body :deep(.hljs.code-block code) {
  display: block;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  font-family: 'Courier New', monospace;
}

.typing-indicator {
  display: inline-block;
  margin-left: 2px;
}

.typing-cursor {
  animation: blink 1s step-end infinite;
  color: var(--color-primary-500);
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
