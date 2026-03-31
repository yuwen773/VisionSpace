<template>
  <div class="tool-response">
    <div class="tool-header" @click="toggleExpand">
      <div class="tool-info">
        <span class="tool-label">工具返回</span>
        <span class="tool-name">{{ toolName }}</span>
      </div>
      <div class="expand-icon" :class="{ expanded }">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>
    </div>
    <transition name="slide">
      <div v-show="expanded || isExpanded" class="tool-content">
        {{ content }}
      </div>
    </transition>
    <div v-if="!expanded && content.length > 100" class="expand-hint" @click="toggleExpand">
      点击展开
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  toolName: string
  content: string
}

const props = withDefaults(defineProps<Props>(), {
  toolName: '',
  content: '',
})

const expanded = ref(props.content.length <= 100)
const isExpanded = ref(false)

const toggleExpand = () => {
  if (props.content.length > 100) {
    isExpanded.value = !isExpanded.value
  }
}
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
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.tool-header:hover {
  opacity: 0.8;
}

.tool-info {
  display: flex;
  align-items: center;
  gap: 8px;
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

.expand-icon {
  color: var(--color-text-tertiary);
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.tool-content {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-hint {
  font-size: 12px;
  color: var(--color-primary-500);
  margin-top: 8px;
  cursor: pointer;
  text-align: center;
}

.expand-hint:hover {
  text-decoration: underline;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  max-height: 0;
}

.slide-enter-to,
.slide-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
