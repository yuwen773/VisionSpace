<template>
  <teleport to="body">
    <transition name="slide">
      <div v-if="visible" class="history-overlay" @click.self="handleClose">
        <div class="history-panel">
          <div class="panel-header">
            <h3 class="panel-title">历史记录</h3>
            <button class="close-btn" @click="handleClose">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="panel-content">
            <div v-if="histories.length === 0" class="empty-state">
              <p>暂无历史记录</p>
            </div>
            <div v-else class="history-list">
              <div
                v-for="(item, index) in histories"
                :key="index"
                class="history-item"
                :class="{ active: activeIndex === index }"
                @click="handleSelect(index)"
              >
                <div class="history-title">{{ item.title || '新对话' }}</div>
                <div class="history-time">{{ item.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export interface HistoryItem {
  id: string
  title: string
  time: string
  messages: any[]
}

interface Props {
  visible: boolean
  histories: HistoryItem[]
  activeIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  histories: () => [],
  activeIndex: -1,
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', index: number): void
}>()

const handleClose = () => {
  emit('close')
}

const handleSelect = (index: number) => {
  emit('select', index)
}

// ESC 键关闭
watch(() => props.visible, (val) => {
  if (val) {
    document.addEventListener('keydown', handleEscKey)
  } else {
    document.removeEventListener('keydown', handleEscKey)
  }
})

const handleEscKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    handleClose()
  }
}
</script>

<style scoped>
.history-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-start;
}

.history-panel {
  width: 300px;
  height: 100%;
  background: var(--color-bg-secondary);
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--color-text-tertiary);
  font-size: 14px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
}

.history-item:hover {
  background: var(--color-bg-hover);
}

.history-item.active {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-accent);
}

.history-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 滑入滑出动画 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
}

.slide-enter-from .history-panel,
.slide-leave-to .history-panel {
  transform: translateX(-100%);
}

.slide-enter-to .history-panel,
.slide-leave-from .history-panel {
  transform: translateX(0);
}
</style>
