<template>
  <transition name="feedback-appear">
    <div v-if="visible" class="feedback-panel">
      <div class="feedback-prompt">
        <span class="prompt-text">这些图片是否符合您的预期？</span>
      </div>
      <div class="feedback-actions">
        <button class="fb-btn satisfied" @click="handleFeedback('satisfied')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
          满意
        </button>
        <button class="fb-btn" @click="handleFeedback('research')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><polyline points="7 23 3 19 7 15" /><path d="M21 13v2a4 4 0 0 1-4 4H3" /></svg>
          换一批
        </button>
        <button class="fb-btn" @click="handleFeedback('regenerate')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" /></svg>
          重新生成
        </button>
        <button class="fb-btn" @click="handleFeedback('describe')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>
          详细描述
        </button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
interface Props {
  visible: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  (e: 'feedback', action: string): void
}>()

const handleFeedback = (action: string) => {
  emit('feedback', action)
}
</script>

<style scoped>
.feedback-panel {
  padding: 14px 16px;
  margin: 0 16px 8px;
  border-radius: 14px;
  text-align: center;
  background: var(--glass-bg, rgba(10, 15, 26, 0.75));
  backdrop-filter: var(--glass-blur, blur(20px));
  -webkit-backdrop-filter: var(--glass-blur, blur(20px));
  border: 1px solid var(--color-border-subtle);
}

.feedback-prompt {
  margin-bottom: 12px;
}

.prompt-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.fb-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 16px;
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast, 150ms ease);
  white-space: nowrap;
}

.fb-btn:hover {
  border-color: var(--color-primary-500);
  color: var(--color-primary-500);
}

.fb-btn.satisfied {
  background: var(--color-success);
  border-color: transparent;
  color: white;
}

.fb-btn.satisfied:hover {
  filter: brightness(1.1);
  color: white;
}

/* Transition */
.feedback-appear-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.feedback-appear-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.feedback-appear-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.feedback-appear-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
