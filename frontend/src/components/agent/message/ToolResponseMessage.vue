<template>
  <div class="tool-response">
    <div class="tool-header">
      <span class="tool-label">工具返回</span>
      <span class="tool-name">{{ toolName }}</span>
    </div>
    <div class="tool-content">
      {{ content }}
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  toolName: string
  content: string
}

withDefaults(defineProps<Props>(), {
  toolName: '',
  content: '',
})
</script>

<style scoped>
.tool-response {
  margin: 8px 16px;
  padding: 12px;
  border-radius: 8px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-subtle);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tool-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.tool-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.tool-content {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
