<template>
  <div class="error-message" role="alert">
    <span class="error-icon" aria-hidden="true">
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
    </span>
    <div class="error-body">
      <p class="error-text">{{ content }}</p>
      <p class="error-hint">本次回复没有完成，可以重新发送这条消息。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  content: string
}

withDefaults(defineProps<Props>(), {
  content: '系统繁忙，请稍后重试',
})
</script>

<style scoped>
.error-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 4px 16px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--color-error);
  background: var(--color-error-bg);
  color: var(--color-error);
}

.error-icon {
  display: flex;
  flex-shrink: 0;
  margin-top: 2px;
}

.error-body {
  min-width: 0;
}

.error-text {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  word-break: break-word;
}

.error-hint {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.75;
}
</style>
