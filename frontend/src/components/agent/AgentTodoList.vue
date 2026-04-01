<template>
  <div class="agent-todo-list" :class="{ expanded }">
    <!-- 折叠行：单行展示 -->
    <div class="todo-collapsed" @click="emit('toggle')">
      <div class="steps-row">
        <span
          v-for="(step, index) in steps"
          :key="index"
          class="step-indicator"
          :class="getStepClass(index)"
        >
          <span class="step-icon">
            <svg v-if="step.status === 'completed'" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <span v-else-if="step.status === 'processing'" class="spin-dot"></span>
            <span v-else class="empty-dot"></span>
          </span>
          <span class="step-name">{{ step.title }}</span>
          <span v-if="index < steps.length - 1" class="step-arrow">→</span>
        </span>
      </div>
      <button class="expand-btn" aria-label="展开详情">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: expanded ? 'rotate(180deg)' : '' }">
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>
    </div>

    <!-- 展开详情 -->
    <transition name="slide-down">
      <div v-if="expanded" class="todo-expanded">
        <div
          v-for="(step, index) in steps"
          :key="index"
          class="todo-item"
          :class="step.status"
        >
          <span class="item-dot"></span>
          <span class="item-title">{{ step.title }}</span>
          <span class="item-desc">{{ step.description }}</span>
          <span class="item-status">{{ getStatusLabel(step.status) }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
export interface TodoStep {
  title: string
  description: string
  status: 'pending' | 'processing' | 'completed'
}

interface Props {
  steps: TodoStep[]
  currentStep?: number
}

const props = withDefaults(defineProps<Props>(), {
  currentStep: 0,
  steps: () => [],
})

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const expanded = defineModel<boolean>('expanded', { default: false })

const getStepClass = (index: number) => {
  if (index < props.currentStep) return 'completed'
  if (index === props.currentStep) return 'processing'
  return 'pending'
}

const getStatusLabel = (status: string) => {
  if (status === 'completed') return '完成'
  if (status === 'processing') return '进行中'
  return '等待'
}
</script>

<style scoped>
.agent-todo-list {
  border-bottom: 1px solid var(--color-border-subtle);
  background: var(--color-bg-secondary);
  transition: background 0.2s ease;
}

.todo-collapsed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  cursor: pointer;
  user-select: none;
}

.todo-collapsed:hover {
  background: var(--color-bg-hover);
}

.steps-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.step-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-tertiary);
}

.step-indicator.completed {
  color: var(--color-success);
}

.step-indicator.processing {
  color: var(--color-primary-500);
}

.step-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
}

.empty-dot {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  opacity: 0.4;
}

.spin-dot {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-500);
  animation: dotPulse 1.2s ease-in-out infinite;
}

@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

.step-arrow {
  color: var(--color-text-tertiary);
  opacity: 0.5;
  margin: 0 2px;
  font-size: 11px;
}

.expand-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: color 0.15s ease;
}

.expand-btn:hover {
  color: var(--color-text-primary);
}

.expand-btn svg {
  transition: transform 0.25s ease;
}

/* Expanded list */
.todo-expanded {
  padding: 8px 20px 12px;
  border-top: 1px solid var(--color-border-subtle);
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-text-tertiary);
  opacity: 0.4;
}

.todo-item.completed .item-dot {
  background: var(--color-success);
  opacity: 1;
}

.todo-item.processing .item-dot {
  background: var(--color-primary-500);
  opacity: 1;
  animation: dotPulse 1.2s ease-in-out infinite;
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  min-width: 60px;
}

.item-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  flex: 1;
}

.item-status {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-tertiary);
}

.todo-item.completed .item-status {
  color: var(--color-success);
}

.todo-item.processing .item-status {
  color: var(--color-primary-500);
}

/* Transitions */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
