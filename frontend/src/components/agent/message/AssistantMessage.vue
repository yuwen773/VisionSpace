<template>
  <div class="assistant-message">
    <div class="message-avatar" aria-hidden="true">
      <span class="agent-orb"></span>
    </div>
    <div class="message-content">
      <div class="message-header">
        <span class="agent-name">智能助手</span>
      </div>
      <div class="message-body markdown-body" v-html="renderedContent"></div>
      <div v-if="isLoading" class="typing-cursor" aria-hidden="true">|</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
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
</script>

<style scoped>
.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 16px;
}

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-orb {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gradient-aurora);
  display: block;
}

[data-theme="aurora"] .agent-orb {
  box-shadow: 0 0 14px rgba(34, 104, 245, 0.3);
}

[data-theme="pop"] .agent-orb {
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.2);
}

.message-content {
  max-width: 70%;
  padding: 10px 16px;
  border-radius: 18px 18px 18px 4px;
  border: 1px solid var(--color-border-subtle);
  position: relative;
}

[data-theme="aurora"] .message-content {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-default);
}

[data-theme="pop"] .message-content {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-subtle);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.agent-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary-500);
}

.message-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
  word-break: break-word;
}

/* Markdown styles */
.markdown-body :deep(p) {
  margin: 0 0 8px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-500);
  text-decoration: none;
  font-weight: 500;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(code:not(.hljs)) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-mono);
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
  letter-spacing: 0.04em;
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
  background: var(--color-bg-hover, var(--color-bg-secondary));
  color: var(--color-text-primary);
}

.markdown-body :deep(.hljs.code-block code) {
  display: block;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  font-family: var(--font-mono);
}

.typing-cursor {
  display: inline;
  animation: blink 1s step-end infinite;
  color: var(--color-primary-500);
  font-weight: 300;
  margin-left: 1px;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
