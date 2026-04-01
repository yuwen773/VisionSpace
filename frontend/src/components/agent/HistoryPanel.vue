<template>
  <teleport to="body">
    <transition name="overlay">
      <div v-if="visible" class="history-backdrop" @click.self="handleClose">
        <transition name="panel-slide" appear>
          <aside class="history-panel">
            <div class="panel-header">
              <h3 class="panel-title">历史记录</h3>
              <button class="close-btn" @click="handleClose" aria-label="关闭">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            <div class="panel-body">
              <div v-if="histories.length === 0" class="empty-state">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                <p class="empty-text">暂无历史记录</p>
              </div>

              <div v-else class="history-list">
                <button
                  v-for="(item, index) in histories"
                  :key="item.id"
                  class="history-item"
                  :class="{ active: activeIndex === index }"
                  @click="handleSelect(index)"
                >
                  <span class="history-title">{{ item.title || '新对话' }}</span>
                  <span class="history-time">{{ item.time }}</span>
                </button>
              </div>
            </div>
          </aside>
        </transition>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { watch, onUnmounted } from 'vue'

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

const handleClose = () => emit('close')
const handleSelect = (index: number) => emit('select', index)

// ESC key handler
const handleEscKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') handleClose()
}

watch(() => props.visible, (val) => {
  if (val) {
    document.addEventListener('keydown', handleEscKey)
    document.body.style.overflow = 'hidden'
  } else {
    document.removeEventListener('keydown', handleEscKey)
    document.body.style.overflow = ''
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.history-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-start;
}

[data-theme="aurora"] .history-backdrop {
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}

[data-theme="pop"] .history-backdrop {
  background: rgba(30, 27, 75, 0.2);
  backdrop-filter: blur(6px);
}

.history-panel {
  width: 320px;
  max-width: 85vw;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border-subtle);
}

[data-theme="aurora"] .history-panel {
  background: var(--color-bg-secondary, #0a0f1a);
  box-shadow: 8px 0 32px rgba(0, 0, 0, 0.4);
}

[data-theme="pop"] .history-panel {
  background: var(--color-bg-secondary, #ffffff);
  box-shadow: 8px 0 32px rgba(139, 92, 246, 0.08);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.panel-title {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: var(--color-bg-hover, var(--color-bg-secondary));
  color: var(--color-text-primary);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
}

.empty-icon {
  color: var(--color-text-tertiary);
  opacity: 0.5;
}

.empty-text {
  font-size: 14px;
  color: var(--color-text-tertiary);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.history-item:hover {
  background: var(--color-bg-hover, var(--color-bg-secondary));
}

.history-item.active {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-accent);
}

.history-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* Overlay transition */
.overlay-enter-active,
.overlay-leave-active {
  transition: opacity 0.25s ease;
}

.overlay-enter-from,
.overlay-leave-to {
  opacity: 0;
}

/* Panel slide transition */
.panel-slide-enter-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.panel-slide-leave-active {
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.panel-slide-enter-from {
  transform: translateX(-100%);
}

.panel-slide-leave-to {
  transform: translateX(-100%);
}
</style>
