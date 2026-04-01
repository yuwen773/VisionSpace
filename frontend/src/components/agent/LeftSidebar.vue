<template>
  <div class="left-sidebar">
    <!-- 顶部：品牌 + 新建对话 -->
    <div class="sidebar-header">
      <div class="brand">
        <div class="brand-orb" aria-hidden="true">
          <span class="orb-core"></span>
        </div>
        <span class="brand-name">智能助手</span>
      </div>
      <button class="new-chat-btn" @click="emit('newChat')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span>新对话</span>
      </button>
    </div>

    <!-- 历史列表 -->
    <div class="sidebar-body">
      <div v-if="histories.length === 0" class="empty-state">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10" />
          <polyline points="12 6 12 12 16 14" />
        </svg>
        <span>暂无历史记录</span>
      </div>

      <template v-else>
        <div v-for="group in groupedHistories" :key="group.label" class="history-group">
          <div class="group-label">{{ group.label }}</div>
          <button
            v-for="(item, index) in group.items"
            :key="item.id"
            class="history-item"
            :class="{ active: activeId === item.id }"
            @click="emit('select', item.id)"
          >
            <span class="history-title">{{ item.title || '新对话' }}</span>
            <span class="history-time">{{ item.time }}</span>
          </button>
        </div>
      </template>
    </div>

    <!-- 底部：设置占位 -->
    <div class="sidebar-footer">
      <button class="icon-btn" title="设置" aria-label="设置">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface HistoryItem {
  id: string
  title: string
  time: string
  messages?: any[]
}

interface Props {
  histories: HistoryItem[]
  activeId?: string
}

const props = withDefaults(defineProps<Props>(), {
  histories: () => [],
  activeId: '',
})

const emit = defineEmits<{
  (e: 'newChat'): void
  (e: 'select', id: string): void
}>()

const groupedHistories = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const yesterday = today - 86400000

  const groups: { label: string; items: HistoryItem[] }[] = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '更早', items: [] },
  ]

  for (const item of props.histories) {
    try {
      const itemDate = new Date(item.time).getTime()
      if (itemDate >= today) {
        groups[0].items.push(item)
      } else if (itemDate >= yesterday) {
        groups[1].items.push(item)
      } else {
        groups[2].items.push(item)
      }
    } catch {
      groups[2].items.push(item)
    }
  }

  return groups.filter(g => g.items.length > 0)
})
</script>

<style scoped>
.left-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border-subtle);
}

/* Header */
.sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border-subtle);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-orb {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.orb-core {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--gradient-aurora);
  animation: orbPulse 3s ease-in-out infinite;
}

@keyframes orbPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

.brand-name {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base, 200ms ease);
}

.new-chat-btn:hover {
  border-color: var(--color-primary-500);
  color: var(--color-primary-500);
  background: rgba(168, 85, 247, 0.06);
}

[data-theme="aurora"] .new-chat-btn:hover {
  background: rgba(34, 104, 245, 0.08);
}

/* Body */
.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 16px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.history-group {
  margin-bottom: 8px;
}

.group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  padding: 4px 8px;
  margin-bottom: 2px;
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.history-item:hover {
  background: var(--color-bg-hover, var(--color-bg-tertiary));
}

.history-item.active {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-accent);
}

.history-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

/* Footer */
.sidebar-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--color-border-subtle);
  display: flex;
  justify-content: flex-start;
}

.icon-btn {
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

.icon-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}
</style>
