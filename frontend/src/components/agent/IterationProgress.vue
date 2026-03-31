<template>
  <div class="iteration-progress">
    <!-- 外层 Steps -->
    <a-steps :current="currentStep" size="small" class="progress-steps">
      <a-step title="探索" description="分析需求" />
      <a-step title="评审" description="质量检查" />
      <a-step title="生成" description="创作图片" />
    </a-steps>

    <!-- 内层 Timeline -->
    <a-timeline class="progress-timeline">
      <a-timeline-item
        v-for="(item, index) in iterations"
        :key="index"
        :color="getItemColor(item.status)"
      >
        <div class="iteration-item">
          <span class="iteration-title">{{ item.title }}</span>
          <span class="iteration-desc">{{ item.description }}</span>
        </div>
      </a-timeline-item>
    </a-timeline>

    <!-- 当前状态 -->
    <div class="current-status">
      <span class="status-label">{{ currentStatusLabel }}</span>
      <span v-if="remainingIterations > 0" class="remaining-hint">
        剩余 {{ remainingIterations }} 次迭代
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface IterationItem {
  title: string
  description: string
  status: 'pending' | 'processing' | 'completed'
}

interface Props {
  phase: 'EXPLORATION' | 'REVIEW' | 'GENERATION' | 'DONE'
  iterations: IterationItem[]
  remainingIterations?: number
}

const props = withDefaults(defineProps<Props>(), {
  iterations: () => [],
  remainingIterations: 0,
})

const currentStep = computed(() => {
  const map: Record<string, number> = {
    EXPLORATION: 0,
    REVIEW: 1,
    GENERATION: 2,
    DONE: 3,
  }
  return map[props.phase] ?? 0
})

const currentStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    EXPLORATION: '正在分析您的需求...',
    REVIEW: '正在进行质量评审...',
    GENERATION: '正在生成图片...',
    DONE: '已完成',
  }
  return labels[props.phase] ?? ''
})

const getItemColor = (status: string) => {
  const colors: Record<string, string> = {
    pending: 'gray',
    processing: 'blue',
    completed: 'green',
  }
  return colors[status] ?? 'gray'
}
</script>

<style scoped>
.iteration-progress {
  padding: 16px;
  background: var(--color-bg-secondary);
  border-radius: 12px;
  margin-bottom: 16px;
}

.progress-steps {
  margin-bottom: 24px;
}

.progress-timeline {
  margin: 16px 0;
}

.iteration-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.iteration-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.iteration-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.current-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-subtle);
}

.status-label {
  font-size: 13px;
  color: var(--color-primary-500);
}

.remaining-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
</style>
