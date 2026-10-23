<template>
  <div id="moderationPage">
    <header class="page-header">
      <h1 class="page-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        </svg>
        内容处置台
      </h1>
      <p class="page-subtitle">
        隔离违规资源并保留处置记录。处置只改变可见性，不改变归属与成员关系；这里不展示任何资源内容。
      </p>
    </header>

    <!-- 隔离操作 -->
    <section class="panel">
      <h2 class="panel-title">隔离资源</h2>
      <div class="form-grid">
        <a-select v-model:value="form.resourceType" class="form-item">
          <a-select-option value="picture">图片</a-select-option>
          <a-select-option value="feedback-attachment">反馈附件</a-select-option>
        </a-select>
        <a-input-number
          v-model:value="form.resourceId"
          :min="1"
          :precision="0"
          placeholder="资源 ID"
          class="form-item"
        />
        <a-textarea
          v-model:value="form.reason"
          :rows="2"
          :maxlength="500"
          placeholder="处置理由（必填）"
          class="form-item reason"
        />
        <a-button type="primary" :loading="submitting" class="form-item" @click="doQuarantine">
          隔离
        </a-button>
      </div>
    </section>

    <!-- 隔离中的资源 -->
    <section class="panel">
      <div class="panel-head">
        <h2 class="panel-title">隔离中的资源</h2>
        <a-button size="small" :loading="loading" @click="loadList">刷新</a-button>
      </div>

      <p v-if="!loading && list.length === 0" class="empty-text">当前没有隔离中的资源</p>

      <ul v-else class="record-list">
        <li v-for="record in list" :key="recordKey(record)" class="record-item">
          <div class="record-main">
            <span class="record-type">{{ resourceTypeText(record.resourceType) }}</span>
            <span class="record-id">#{{ record.resourceId }}</span>
            <span class="record-name">{{ record.resourceName || '（无名称）' }}</span>
            <span class="record-status">{{ isolationStatusText(record.status) }}</span>
          </div>
          <div class="record-meta">
            <span>处置人：{{ record.operatorUserName || record.operatorId || '-' }}</span>
            <span>处置时间：{{ formatDateTime(record.createTime) }}</span>
            <span v-if="record.reason">理由：{{ record.reason }}</span>
          </div>
          <div class="record-actions">
            <a-button size="small" @click="showHistory(record)">处置历史</a-button>
            <a-button size="small" type="primary" @click="openRelease(record)">恢复</a-button>
          </div>
        </li>
      </ul>
    </section>

    <!-- 恢复确认 -->
    <a-modal
      v-model:open="releaseVisible"
      title="恢复资源"
      ok-text="确认恢复"
      cancel-text="取消"
      @ok="doRelease"
    >
      <p class="modal-target">
        {{ resourceTypeText(releaseTarget?.resourceType) }} #{{ releaseTarget?.resourceId }}
      </p>
      <p class="modal-hint">恢复不改变原有归属与成员关系，也不会扩大任何人的访问范围。</p>
      <a-textarea
        v-model:value="releaseReason"
        :rows="3"
        :maxlength="500"
        placeholder="恢复理由（必填）"
      />
    </a-modal>

    <!-- 处置历史 -->
    <a-modal v-model:open="historyVisible" title="处置历史" :footer="null">
      <p v-if="history.length === 0" class="empty-text">没有处置记录</p>
      <ul v-else class="history-list">
        <li v-for="(item, index) in history" :key="index" class="history-item">
          <span class="history-status">{{ isolationStatusText(item.status) }}</span>
          <span>操作者：{{ item.operatorUserName || item.operatorId || '-' }}</span>
          <span>{{ formatDateTime(item.createTime) }}</span>
          <span v-if="item.reason">理由：{{ item.reason }}</span>
        </li>
      </ul>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  formatDateTime,
  historyUsingGet,
  isolationStatusText,
  listQuarantinedUsingGet,
  quarantineUsingPost,
  releaseUsingPost,
  resourceTypeText,
} from '@/api-custom/resourceProtection'

const loading = ref(false)
const submitting = ref(false)
const list = ref<API.ModerationResourceVO[]>([])
const history = ref<API.ModerationResourceVO[]>([])
const historyVisible = ref(false)
const releaseVisible = ref(false)
const releaseReason = ref('')
const releaseTarget = ref<API.ModerationResourceVO | null>(null)

const form = reactive({
  resourceType: 'picture' as string,
  resourceId: undefined as number | undefined,
  reason: '',
})

const recordKey = (record: API.ModerationResourceVO) =>
  `${record.resourceType ?? ''}-${record.resourceId ?? ''}-${record.createTime ?? ''}`

const loadList = async () => {
  loading.value = true
  try {
    const res = await listQuarantinedUsingGet()
    if (res.data.code === 0) {
      list.value = res.data.data ?? []
    } else {
      message.error(res.data.message || '加载隔离列表失败')
    }
  } catch {
    message.error('加载隔离列表失败')
  } finally {
    loading.value = false
  }
}

const doQuarantine = async () => {
  if (!form.resourceId || form.resourceId <= 0) {
    message.warning('请填写资源 ID')
    return
  }
  if (!form.reason.trim()) {
    message.warning('处置理由不能为空')
    return
  }
  submitting.value = true
  try {
    const res = await quarantineUsingPost({
      resourceType: form.resourceType,
      resourceId: form.resourceId,
      reason: form.reason.trim(),
    })
    if (res.data.code === 0) {
      message.success('资源已隔离')
      form.resourceId = undefined
      form.reason = ''
      await loadList()
    } else {
      message.error(res.data.message || '隔离失败')
    }
  } catch {
    message.error('隔离失败')
  } finally {
    submitting.value = false
  }
}

const openRelease = (record: API.ModerationResourceVO) => {
  releaseTarget.value = record
  releaseReason.value = ''
  releaseVisible.value = true
}

const doRelease = async () => {
  const target = releaseTarget.value
  if (!target?.resourceId) return
  if (!releaseReason.value.trim()) {
    message.warning('恢复理由不能为空')
    return
  }
  submitting.value = true
  try {
    const res = await releaseUsingPost({
      resourceType: target.resourceType ?? 'picture',
      resourceId: target.resourceId as number,
      reason: releaseReason.value.trim(),
    })
    if (res.data.code === 0) {
      message.success('资源已恢复')
      releaseVisible.value = false
      await loadList()
    } else {
      message.error(res.data.message || '恢复失败')
    }
  } catch {
    message.error('恢复失败')
  } finally {
    submitting.value = false
  }
}

const showHistory = async (record: API.ModerationResourceVO) => {
  if (!record.resourceId) return
  try {
    const res = await historyUsingGet({
      resourceType: record.resourceType ?? 'picture',
      resourceId: record.resourceId,
    })
    if (res.data.code === 0) {
      history.value = res.data.data ?? []
      historyVisible.value = true
    } else {
      message.error(res.data.message || '加载处置历史失败')
    }
  } catch {
    message.error('加载处置历史失败')
  }
}

onMounted(loadList)
</script>

<style scoped lang="less">
#moderationPage {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-6);
}

.page-header {
  margin-bottom: var(--space-6);
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 0 var(--space-2);
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--color-text-primary);

  svg {
    width: 24px;
    height: 24px;
    color: var(--color-primary);
  }
}

.page-subtitle {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.panel {
  margin-bottom: var(--space-5);
  padding: var(--space-5);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.panel-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-text-primary);
}

.panel-head .panel-title {
  margin-bottom: 0;
}

.form-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: flex-start;
}

.form-item {
  width: 160px;

  &.reason {
    flex: 1;
    min-width: 240px;
  }
}

.empty-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.record-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.record-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--color-bg-tertiary, var(--color-bg-secondary));
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
}

.record-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 240px;
}

.record-type {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--color-text-primary);
}

.record-id {
  font-family: var(--font-mono, monospace);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.record-name {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.record-status {
  padding: 2px 8px;
  font-size: var(--text-xs);
  color: var(--color-error);
  background: var(--color-error-bg);
  border-radius: var(--radius-full);
}

.record-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 220px;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.record-actions {
  display: flex;
  gap: var(--space-2);
  margin-left: auto;
}

.modal-target {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.modal-hint {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  background: var(--color-bg-tertiary, var(--color-bg-secondary));
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.history-status {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

@media (max-width: 768px) {
  #moderationPage {
    padding: var(--space-5) var(--space-4);
  }

  .form-item {
    width: 100%;
  }
}
</style>
