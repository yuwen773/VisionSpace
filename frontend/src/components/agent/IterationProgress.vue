<template>
  <div class="iteration-progress">
    <!-- Phase pills -->
    <div class="phase-pills">
      <span
        v-for="(phase, i) in phases"
        :key="i"
        class="phase-pill"
        :class="{
          active: currentStep === i,
          completed: currentStep > i,
        }"
      >
        <span class="phase-dot"></span>
        <span class="phase-label">{{ phase.label }}</span>
      </span>
    </div>

    <!-- Status bar -->
    <div class="status-row">
      <span class="status-text">{{ currentStatusLabel }}</span>
      <span v-if="remainingIterations > 0" class="status-meta">
        剩余 {{ remainingIterations }} 次迭代
      </span>
    </div>

    <!-- Iteration timeline (collapsible) -->
    <div v-if="iterations.length > 0" class="iteration-timeline">
      <div
        v-for="(item, index) in iterations"
        :key="index"
        class="timeline-node"
        :class="item.status"
      >
        <span class="node-dot"></span>
        <span class="node-label">{{ item.title }}</span>
        <span class="node-desc">{{ item.description }}</span>
      </div>
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

const phases = [
  { key: 'EXPLORATION', label: '探索' },
  { key: 'REVIEW', label: '评审' },
  { key: 'GENERATION', label: '生成' },
]

const currentStep = computed(() => {
  const map: Record<string, number> = { EXPLORATION: 0, REVIEW: 1, GENERATION: 2, DONE: 3 }
  return map[props.phase] ?? 0
})

const currentStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    EXPLORATION: '正在分析您的需求…',
    REVIEW: '正在进行质量评审…',
    GENERATION: '正在生成图片…',
    DONE: '已完成',
  }
  return labels[props.phase] ?? ''
})
</script>

<style scoped>
.iteration-progress {
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border-subtle);
  background: var(--glass-bg, rgba(10, 15, 26, 0.75));
  backdrop-filter: var(--glass-blur, blur(20px));
  -webkit-backdrop-filter: var(--glass-blur, blur(20px));
}

/* Phase pills */
.phase-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.phase-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  background: var(--color-bg-hover, var(--color-bg-secondary));
  transition: all 0.25s ease;
}

.phase-pill.active {
  color: white;
  background: var(--gradient-aurora);
}

.phase-pill.completed {
  color: var(--color-success);
}

.phase-dot {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.phase-pill.active .phase-dot {
  animation: dotPulse 1.5s ease-in-out infinite;
}

@keyframes dotPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Status row */
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-primary-500);
}

.status-meta {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

/* Timeline */
.iteration-timeline {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-subtle);
}

.timeline-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}

.node-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-text-tertiary);
}

.timeline-node.completed .node-dot {
  background: var(--color-success);
}

.timeline-node.processing .node-dot {
  background: var(--color-primary-500);
  animation: dotPulse 1.5s ease-in-out infinite;
}

.node-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.node-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
}
</style>
