<template>
  <div v-if="visible" class="feedback-buttons">
    <div class="feedback-prompt">
      <svg class="prompt-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        <circle cx="12" cy="10" r="1"/>
        <circle cx="8" cy="10" r="1"/>
        <circle cx="16" cy="10" r="1"/>
      </svg>
      <span class="prompt-text">这些图片是否符合您的预期？</span>
    </div>
    <div class="button-group">
      <a-button @click="handleFeedback('satisfied')">
        <template #icon><CheckOutlined /></template>
        满意
      </a-button>
      <a-button @click="handleFeedback('research')">
        <template #icon><SwapOutlined /></template>
        换一批
      </a-button>
      <a-button @click="handleFeedback('regenerate')">
        <template #icon><ReloadOutlined /></template>
        重新生成
      </a-button>
      <a-button @click="handleFeedback('describe')">
        <template #icon><EditOutlined /></template>
        详细描述
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CheckOutlined, SwapOutlined, ReloadOutlined, EditOutlined } from '@ant-design/icons-vue'

interface Props {
  visible: boolean
}

defineProps<Props>()
const emit = defineEmits(['feedback'])

const handleFeedback = (action: string) => {
  emit('feedback', action)
}
</script>

<style scoped>
.feedback-buttons {
  padding: 16px;
  margin: 8px 16px;
  border-radius: 12px;
  background: var(--gradient-glass);
  border: 1px solid var(--color-border-subtle);
  text-align: center;
}

.feedback-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.prompt-icon {
  font-size: 16px;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.button-group :deep(.ant-btn):focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 2px;
}
</style>
