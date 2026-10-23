<template>
  <div v-if="state && visible" class="recovery-banner" :class="tone">
    <span class="banner-icon" aria-hidden="true">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 12a9 9 0 1 0 3-6.7" />
        <polyline points="3 4 3 9 8 9" />
      </svg>
    </span>

    <div class="banner-body">
      <p class="banner-title">{{ title }}</p>
      <p class="banner-reason">{{ state.reason || fallbackReason }}</p>

      <!-- 待人工确认：只含工具名，不含参数或正文 -->
      <p v-if="state.pendingConfirmation" class="banner-confirm">
        等待确认：{{ state.pendingConfirmation }}
      </p>

      <!-- 待核对动作：逐条确认，不提供「一键重试」 -->
      <ul v-if="state.actions.length > 0" class="action-list">
        <li v-for="action in state.actions" :key="action.actionId" class="action-row">
          <span class="action-name">{{ action.toolName }}</span>
          <span class="action-status" :class="statusClass(action.status)">
            {{ statusLabel(action.status) }}
          </span>
          <span v-if="action.resultSummary" class="action-summary">{{ action.resultSummary }}</span>
          <span v-if="action.status === 'UNKNOWN_NEEDS_REVIEW'" class="action-actions">
            <button class="link-btn" :disabled="busy" @click="review(action, 'confirm')">
              已执行
            </button>
            <button class="link-btn" :disabled="busy" @click="review(action, 'dismiss')">
              未执行
            </button>
          </span>
        </li>
      </ul>

      <div class="banner-actions">
        <button
          v-if="state.resumable"
          class="primary-btn"
          :disabled="busy"
          @click="emit('resume', state.stateVersion)"
        >
          继续执行
        </button>
        <button class="ghost-btn" :disabled="busy" @click="emit('refresh')">刷新状态</button>
      </div>

      <p v-if="state.backupError" class="banner-backup">
        备份失败（{{ state.backupAttempts }} 次）：{{ state.backupError }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RecoveryAction, RecoveryState } from '@/api-custom/agent'

const props = defineProps<{
  state: RecoveryState | null
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'resume', version: number | null): void
  (e: 'refresh'): void
  (e: 'review', actionId: string, decision: 'confirm' | 'dismiss'): void
}>()

// 正常运行时不打扰用户；但「停在人工确认」必须让用户看到，否则
// 状态是 resumable 的，横幅会被隐藏，待确认信息就永远不出现。
const visible = computed(() => {
  if (!props.state) return false
  if (props.state.pendingReviewCount > 0) return true
  if (props.state.runStatus === 'AWAITING_CONFIRMATION') return true
  if (props.state.resumable) return false
  return props.state.runStatus !== 'COMPLETED'
})

const tone = computed(() => {
  if (props.state?.pendingReviewCount) return 'warn'
  if (props.state?.resumable) return 'info'
  return 'muted'
})

const title = computed(() => {
  if (!props.state) return ''
  if (props.state.pendingReviewCount > 0) return '有外部动作结果未知，需要核对'
  if (props.state.runStatus === 'AWAITING_CONFIRMATION') return '等待你确认下一步'
  if (props.state.resumable) return '可以从上次中断处继续'
  if (props.state.legacyArchiveOnly) return '只有历史消息，无法继续执行'
  return '当前无法自动继续'
})

const fallbackReason = computed(() => {
  if (!props.state) return ''
  return props.state.runStatus === 'COMPLETED' ? '' : '会话已暂停，历史消息仍可查看'
})

const STATUS_LABELS: Record<string, string> = {
  EXECUTING: '执行中',
  SUCCEEDED: '已完成',
  FAILED: '明确失败',
  UNKNOWN_NEEDS_REVIEW: '结果未知',
}

const statusLabel = (status: string) => STATUS_LABELS[status] || status

const statusClass = (status: string) => ({
  ok: status === 'SUCCEEDED',
  failed: status === 'FAILED',
  unknown: status === 'UNKNOWN_NEEDS_REVIEW',
  running: status === 'EXECUTING',
})

const review = (action: RecoveryAction, decision: 'confirm' | 'dismiss') => {
  emit('review', action.actionId, decision)
}
</script>

<style scoped>
.recovery-banner {
  display: flex;
  gap: 10px;
  margin: 8px 16px 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--color-border-subtle);
  background: var(--color-bg-elevated);
}

.recovery-banner.warn {
  border-color: rgba(245, 158, 11, 0.35);
  background: var(--color-warning-bg, rgba(245, 158, 11, 0.08));
}

.recovery-banner.info {
  border-color: rgba(59, 130, 246, 0.3);
}

.banner-icon {
  flex-shrink: 0;
  display: flex;
  color: var(--color-text-tertiary);
}

.recovery-banner.warn .banner-icon {
  color: var(--color-warning, #f59e0b);
}

.banner-body {
  flex: 1;
  min-width: 0;
}

.banner-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.banner-reason {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.banner-confirm {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: pre-line;
}

.action-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 4px 0;
  color: var(--color-text-secondary);
}

.action-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.action-status.ok {
  color: var(--color-success, #10b981);
}

.action-status.failed {
  color: var(--color-danger, #ef4444);
}

.action-status.unknown {
  color: var(--color-warning, #f59e0b);
}

.action-summary {
  color: var(--color-text-tertiary);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-actions {
  display: inline-flex;
  gap: 6px;
}

.link-btn {
  border: none;
  background: transparent;
  color: var(--color-primary-500);
  font-size: 12px;
  cursor: pointer;
  padding: 0 2px;
}

.link-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.banner-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.primary-btn,
.ghost-btn {
  padding: 5px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
}

.primary-btn {
  background: var(--color-primary-500);
  color: white;
}

.ghost-btn {
  background: transparent;
  border-color: var(--color-border-subtle);
  color: var(--color-text-secondary);
}

.primary-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.banner-backup {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-danger, #ef4444);
}
</style>
