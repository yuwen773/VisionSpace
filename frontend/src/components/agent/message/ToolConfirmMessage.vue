<template>
  <div class="tool-confirm">
    <div class="confirm-icon">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
    </div>
    <div class="confirm-content">
      <div class="confirm-title">需要您的确认</div>
      <div class="confirm-message">{{ message }}</div>
      <div class="confirm-actions">
        <a-button size="small" @click="handleCancel">取消</a-button>
        <a-button type="primary" size="small" @click="handleConfirm">确认</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  message: string
}

const props = defineProps<Props>()
const emit = defineEmits(['confirm', 'cancel'])

const handleConfirm = () => emit('confirm')
const handleCancel = () => emit('cancel')
</script>

<style scoped>
.tool-confirm {
  display: flex;
  gap: 12px;
  padding: 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.4);
}

.confirm-icon {
  color: var(--color-success);
  flex-shrink: 0;
}

.confirm-content {
  flex: 1;
}

.confirm-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-success);
  margin-bottom: 4px;
}

.confirm-message {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.confirm-actions {
  display: flex;
  gap: 8px;
}
</style>
