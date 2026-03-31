<template>
  <div class="tool-request">
    <div class="tool-icon">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
      </svg>
    </div>
    <div class="tool-info">
      <span class="tool-name">{{ toolName }}</span>
      <span class="tool-description">{{ description }}</span>
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
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin: 8px 16px;
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.tool-icon {
  color: var(--color-info);
}

.tool-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tool-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-info);
}

.tool-description {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
