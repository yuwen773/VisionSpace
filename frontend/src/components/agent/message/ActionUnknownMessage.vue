<template>
  <div class="action-unknown" role="alert">
    <span class="unknown-icon" aria-hidden="true">
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="10" />
        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    </span>
    <div class="unknown-body">
      <p class="unknown-label">结果未知，需要核对</p>
      <p class="unknown-msg">{{ content }}</p>
      <p v-if="toolName" class="unknown-tool">工具：{{ toolName }}</p>
      <p class="unknown-hint">
        这一步不是「失败」，而是无法确认外部是否已经执行。请先核对，再决定是否继续；
        直接重试可能产生重复副作用。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  content: string
  toolName?: string
}

defineProps<Props>()
</script>

<style scoped>
.action-unknown {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: var(--color-warning-bg, rgba(245, 158, 11, 0.12));
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.unknown-icon {
  flex-shrink: 0;
  color: var(--color-warning, #f59e0b);
  display: flex;
}

.unknown-body {
  flex: 1;
  min-width: 0;
}

.unknown-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-warning, #f59e0b);
  margin-bottom: 4px;
}

.unknown-msg {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
  line-height: 1.5;
  word-break: break-word;
}

.unknown-tool {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 6px;
}

.unknown-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}
</style>
