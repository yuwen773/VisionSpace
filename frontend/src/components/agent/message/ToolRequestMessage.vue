<template>
  <div class="tool-request">
    <span class="tool-icon" aria-hidden="true">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
      </svg>
    </span>
    <div class="tool-body">
      <span class="tool-label">{{ toolName }}</span>
      <span v-if="description" class="tool-desc">{{ description }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  toolName: string
  description?: string
}

withDefaults(defineProps<Props>(), {
  toolName: 'Tool',
  description: '',
})
</script>

<style scoped>
.tool-request {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 6px 16px;
  padding: 6px 14px;
  border-radius: 10px;
  background: var(--color-info-bg, rgba(59, 130, 246, 0.1));
  border: 1px solid transparent;
  max-width: max-content;
}

[data-theme="aurora"] .tool-request {
  border-color: rgba(59, 130, 246, 0.2);
}

[data-theme="pop"] .tool-request {
  border-color: rgba(139, 92, 246, 0.15);
}

.tool-icon {
  display: flex;
  color: var(--color-info);
  flex-shrink: 0;
}

.tool-body {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.tool-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-info);
}

.tool-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
