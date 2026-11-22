<template>
  <div id="feedbackManagePage">
    <!-- 页面头部 -->
    <AdminPageHeader title="反馈管理" subtitle="处理用户提交的产品建议、内容举报和工单支持">
      <template #icon>
        <MessageSquare :size="19" :stroke-width="1.6" />
      </template>
      <template #extra>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-value">{{ stats.pending }}</span>
            <span class="stat-label">待处理</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.processing }}</span>
            <span class="stat-label">处理中</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.resolved }}</span>
            <span class="stat-label">已解决</span>
          </div>
        </div>
      </template>
    </AdminPageHeader>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="filter-tabs">
        <button
          v-for="tab in statusTabs"
          :key="tab.value ?? 'all'"
          class="filter-tab"
          :class="{ active: searchParams.status === tab.value }"
          @click="changeStatus(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="filter-selects">
        <a-select
          v-model:value="searchParams.type"
          placeholder="类型筛选"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="1">
            <span class="type-option">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 18V5l12-2v13" />
                <circle cx="6" cy="18" r="3" />
                <circle cx="18" cy="16" r="3" />
              </svg>
              产品建议
            </span>
          </a-select-option>
          <a-select-option :value="2">
            <span class="type-option">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
                <line x1="4" y1="22" x2="4" y2="15" />
              </svg>
              内容举报
            </span>
          </a-select-option>
          <a-select-option :value="3">
            <span class="type-option">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="16" y1="13" x2="8" y2="13" />
                <line x1="16" y1="17" x2="8" y2="17" />
                <line x1="10" y1="9" x2="8" y2="9" />
              </svg>
              工单支持
            </span>
          </a-select-option>
        </a-select>
      </div>
      <div class="filter-buttons">
        <button class="toolbar-btn" @click="loadData">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"></path>
            <path d="M21 3v5h-5"></path>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="pagination"
        :rowKey="(record: any) => record.id"
        @change="handleTableChange"
        :scroll="tableScroll"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'userVO'">
            <div class="user-cell">
              <a-avatar :size="32">
                <template #icon
                  ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                    <circle cx="12" cy="7" r="4" /></svg
                ></template>
              </a-avatar>
              <div class="user-info">
                <span class="user-name">{{ record.userName || '未知用户' }}</span>
                <a-tooltip :title="record.userId" placement="right">
                  <span class="user-id">{{ shortUserId(record.userId) }}</span>
                </a-tooltip>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            <span class="type-tag" :class="getTypeClass(record.type)">
              <svg
                v-if="record.type === 1"
                class="tag-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M9 18V5l12-2v13" />
                <circle cx="6" cy="18" r="3" />
                <circle cx="18" cy="16" r="3" />
              </svg>
              <svg
                v-else-if="record.type === 2"
                class="tag-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
                <line x1="4" y1="22" x2="4" y2="15" />
              </svg>
              <svg
                v-else
                class="tag-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
              {{ getTypeText(record.type) }}
            </span>
          </template>
          <template v-else-if="column.key === 'status'">
            <span class="status-tag" :class="getStatusClass(record.status)">
              <span class="status-dot"></span>
              {{ getStatusText(record.status) }}
            </span>
          </template>
          <template v-else-if="column.key === 'title'">
            <div class="title-cell">
              <span class="title-text">{{ record.title }}</span>
              <span v-if="record.attachments?.length" class="attachment-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <polyline points="21 15 16 10 5 21" />
                </svg>
                {{ record.attachments.length }}
              </span>
              <span v-if="blockedAttachmentCount(record) > 0" class="attachment-badge blocked">
                阻断 {{ blockedAttachmentCount(record) }}
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'createTime'">
            <span class="time-text">{{ formatDate(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="查看详情">
                <button class="action-btn" aria-label="查看详情" @click="showDetail(record)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip v-if="record.status !== 2" title="标记已解决">
                <button
                  class="action-btn success"
                  aria-label="标记已解决"
                  @click="quickResolve(record)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                </button>
              </a-tooltip>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="反馈详情"
      width="680px"
      :footer="isAdmin ? undefined : null"
      class="feedback-modal"
    >
      <div v-if="currentFeedback" class="feedback-detail">
        <div class="detail-header">
          <div class="detail-user">
            <a-avatar :size="48">
              <template #icon
                ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                  <circle cx="12" cy="7" r="4" /></svg
              ></template>
            </a-avatar>
            <div class="detail-user-info">
              <span class="detail-user-name">{{ currentFeedback.userName || '未知用户' }}</span>
              <span class="detail-user-id">{{ currentFeedback.userId }}</span>
            </div>
          </div>
          <div class="detail-meta">
            <span class="time-text">{{ formatDate(currentFeedback.createTime) }}</span>
            <div class="detail-tags">
              <span class="type-tag" :class="getTypeClass(currentFeedback.type)">
                <svg
                  v-if="currentFeedback.type === 1"
                  class="tag-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M9 18V5l12-2v13" />
                  <circle cx="6" cy="18" r="3" />
                  <circle cx="18" cy="16" r="3" />
                </svg>
                <svg
                  v-else-if="currentFeedback.type === 2"
                  class="tag-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
                  <line x1="4" y1="22" x2="4" y2="15" />
                </svg>
                <svg
                  v-else
                  class="tag-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
                  <polyline points="14 2 14 8 20 8" />
                </svg>
                {{ getTypeText(currentFeedback.type) }}
              </span>
              <span class="status-tag" :class="getStatusClass(currentFeedback.status)">
                <span class="status-dot"></span>
                {{ getStatusText(currentFeedback.status) }}
              </span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            反馈内容
          </h4>
          <div class="detail-content">
            <h3 class="content-title">{{ currentFeedback.title }}</h3>
            <p class="content-body">{{ currentFeedback.content }}</p>
          </div>
        </div>

        <div v-if="currentFeedback.attachments?.length" class="detail-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <polyline points="21 15 16 10 5 21" />
            </svg>
            附件截图
          </h4>
          <div class="picture-grid">
            <template v-for="attachment in currentFeedback.attachments" :key="attachment.id">
              <img
                v-if="attachment.url"
                :src="contentSrc(attachment.url)"
                class="detail-picture"
                @click="previewImage(contentSrc(attachment.url))"
              />
              <!-- 没有受控入口的附件（归属不明、已隔离）只显示状态，不给可直连地址 -->
              <div
                v-else
                class="detail-picture blocked"
                :title="attachmentStatusText(attachment.status)"
              >
                <span>{{ attachmentStatusText(attachment.status) }}</span>
              </div>
            </template>
          </div>
        </div>

        <a-divider />

        <div v-if="isAdmin" class="handle-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
            处理反馈
          </h4>
          <div class="handle-form">
            <div class="form-row">
              <label>处理状态：</label>
              <a-select v-model:value="handleForm.status" style="width: 160px">
                <a-select-option :value="0">
                  <span class="status-tag pending"><span class="status-dot"></span>待处理</span>
                </a-select-option>
                <a-select-option :value="1">
                  <span class="status-tag processing"><span class="status-dot"></span>处理中</span>
                </a-select-option>
                <a-select-option :value="2">
                  <span class="status-tag resolved"><span class="status-dot"></span>已解决</span>
                </a-select-option>
              </a-select>
            </div>
            <div class="form-row">
              <label>处理备注：</label>
              <a-textarea
                v-model:value="handleForm.handlerNote"
                :rows="3"
                placeholder="请填写处理备注，记录处理过程和结果..."
              />
            </div>
          </div>
        </div>

        <div v-if="currentFeedback.handlerNote" class="handler-note">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
            </svg>
            历史备注
          </h4>
          <p class="note-content">{{ currentFeedback.handlerNote }}</p>
        </div>
      </div>

      <template #footer v-if="isAdmin">
        <a-button @click="detailVisible = false">取消</a-button>
        <a-button type="primary" @click="doUpdateStatus">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            style="width: 14px; height: 14px"
          >
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          更新状态
        </a-button>
      </template>
    </a-modal>

    <!-- 图片预览弹窗 -->
    <ImagePreview v-model:open="previewVisible" :url="previewUrl" />
  </div>
</template>

<script setup lang="ts">
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { MessageSquare } from 'lucide-vue-next'
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useAdminTableDensity } from '@/composables/useAdminTableDensity'
import dayjs from 'dayjs'
import ImagePreview from '@/components/ImagePreview.vue'
import {
  getFeedbackByIdUsingGet,
  listFeedbackByPageUsingPost,
  updateFeedbackStatusUsingPost,
} from '@/api/feedbackController'
import { attachmentStatusText } from '@/api-custom/resourceProtection'
import { contentSrc } from '@/utils'
import { useLoginUserStore } from '@/stores/userLogin'

const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const loading = ref(false)
const dataList = ref<API.FeedbackVO[]>([])
const detailVisible = ref(false)
const currentFeedback = ref<any>(null)
const previewVisible = ref(false)
const previewUrl = ref('')

const stats = reactive({
  pending: 0,
  processing: 0,
  resolved: 0,
})

const handleForm = reactive({
  status: 0,
  handlerNote: '',
})

const searchParams = reactive({
  type: null as number | null,
  status: null as number | null,
  current: 1,
  pageSize: 10,
})

const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: 0,
  showSizeChanger: true,
  position: ['bottomCenter'] as const,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条反馈`,
}))

const density = useAdminTableDensity()

// 窄屏先收类型与提交时间，保留「谁提的、什么状态、怎么处理」三件关键信息。
// 手机上不再固定操作列，正文只剩两百多像素，固定列会把用户与标题整段压住。
const columns = computed(() => {
  const user = { title: '用户', key: 'userVO', width: 180 }
  const type = { title: '类型', key: 'type', width: 110, align: 'center' as const }
  const title = { title: '标题', key: 'title', width: 220, ellipsis: true }
  const status = { title: '状态', key: 'status', width: 100, align: 'center' as const }
  const createTime = { title: '提交时间', key: 'createTime', width: 160 }
  const action = { title: '操作', key: 'action', width: 100 }
  const pinnedAction = { ...action, fixed: 'right' as const }

  if (density.value === 'phone') {
    return [{ ...user, width: 150 }, { ...title, width: 200 }, status, action]
  }
  if (density.value === 'narrow') return [user, title, status, pinnedAction]
  if (density.value === 'medium') return [user, title, status, createTime, pinnedAction]
  return [{ ...user, fixed: 'left' as const }, type, title, status, createTime, pinnedAction]
})

const tableScroll = computed(() => {
  if (density.value === 'wide') return { x: 870 }
  if (density.value === 'medium') return { x: 760 }
  if (density.value === 'narrow') return { x: 600 }
  return { x: 550 }
})

const statusTabs = [
  { label: '全部', value: null },
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已解决', value: 2 },
]

// 雪花 ID 过长会撑开用户列，列表里只留尾号，完整值交给 tooltip
const shortUserId = (userId?: string | number) => {
  const id = String(userId ?? '')
  if (!id) return '—'
  return id.length > 12 ? `ID ${id.slice(-6)}` : id
}

const getTypeClass = (type: number) => {
  const map: Record<number, string> = { 1: 'suggestion', 2: 'report', 3: 'ticket' }
  return map[type] || 'default'
}

const getTypeText = (type: number) => {
  const map: Record<number, string> = { 1: '产品建议', 2: '内容举报', 3: '工单支持' }
  return map[type] || '未知'
}

const getStatusClass = (status: number) => {
  const map: Record<number, string> = { 0: 'pending', 1: 'processing', 2: 'resolved' }
  return map[status] || 'pending'
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '已解决' }
  return map[status] || '未知'
}

// 与其余后台表格统一成 YYYY-MM-DD HH:mm，别让同一屏出现两种日期写法
const formatDate = (date: string) => {
  if (!date) return '—'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const updateStats = (records: any[]) => {
  stats.pending = records.filter((r: any) => r.status === 0).length
  stats.processing = records.filter((r: any) => r.status === 1).length
  stats.resolved = records.filter((r: any) => r.status === 2).length
}

const changeStatus = (status: number | null) => {
  searchParams.status = status
  searchParams.current = 1
  loadData()
}

const handleTableChange = (pag: any) => {
  searchParams.current = pag.current
  searchParams.pageSize = pag.pageSize
  loadData()
}

const showDetail = async (record: any) => {
  currentFeedback.value = record
  handleForm.status = record.status
  handleForm.handlerNote = record.handlerNote || ''
  detailVisible.value = true
  // 列表只返回附件状态，不返回受控入口：详情需要单独取一次才能看到附件内容
  await loadDetail(record.id)
}

/**
 * 取反馈详情。附件的受控入口在服务端按当前反馈权限判定，
 * 没有权限的附件不会出现在响应里。
 */
const loadDetail = async (id: number) => {
  if (!id) return
  try {
    const res = await getFeedbackByIdUsingGet({ id })
    if (res.data.code === 0 && res.data.data) {
      currentFeedback.value = res.data.data
    } else {
      message.error(res.data.message || '获取反馈详情失败')
    }
  } catch {
    message.error('获取反馈详情失败')
  }
}

/** 归属不明、已被阻断的附件数量 */
const blockedAttachmentCount = (record: API.FeedbackVO): number =>
  (record?.attachments ?? []).filter((attachment) => !attachment.url).length

const quickResolve = async (record: any) => {
  try {
    const res = await updateFeedbackStatusUsingPost({
      id: record.id,
      status: 2,
      handlerNote: '已快速标记为已解决',
    })
    if (res.data.code === 0) {
      message.success('已标记为已解决')
      loadData()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (err) {
    message.error('操作失败')
  }
}

const doUpdateStatus = async () => {
  if (!currentFeedback.value) return
  try {
    const res = await updateFeedbackStatusUsingPost({
      id: currentFeedback.value.id,
      status: handleForm.status,
      handlerNote: handleForm.handlerNote,
    })
    if (res.data.code === 0) {
      message.success('状态更新成功')
      detailVisible.value = false
      loadData()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (err) {
    message.error('更新失败')
  }
}

const previewImage = (url: string) => {
  previewUrl.value = url
  previewVisible.value = true
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listFeedbackByPageUsingPost({
      ...searchParams,
      type: searchParams.type ?? undefined,
      status: searchParams.status ?? undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      updateStats(dataList.value)
    }
  } finally {
    loading.value = false
  }
}

const doSearch = () => {
  searchParams.current = 1
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="less">
#feedbackManagePage {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0;
}

.header-stats {
  display: flex;
  gap: 0;
  border-left: 1px solid var(--admin-border-default);
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 84px;
  padding: 2px 18px;
  border-right: 1px solid var(--admin-border-default);

  .stat-value {
    font-family: var(--admin-serif);
    font-size: var(--admin-text-2xl);
    font-weight: 600;
    line-height: 1;
    color: var(--admin-text-primary);
  }

  .stat-label {
    margin-top: 5px;
    font-family: var(--admin-font-mono);
    font-size: 8px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--admin-text-tertiary);
  }
}

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-4);
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  animation: slideUp 0.45s var(--admin-ease) 0.05s backwards;
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.filter-tab {
  height: 32px;
  padding: 0 var(--admin-space-4);
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-secondary);
  font-family: var(--admin-font);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast);
  font-size: var(--admin-text-sm);
  font-weight: 600;
  /* 标签是两字词，压缩时宁可溢出滚动也不要折成两行 */
  white-space: nowrap;
  flex-shrink: 0;

  &:hover {
    border-color: var(--admin-primary);
    color: var(--admin-primary);
  }

  &.active {
    background: var(--admin-primary);
    border-color: var(--admin-primary);
    color: var(--admin-accent-ink);
  }
}

.filter-selects {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.filter-select {
  width: 130px;

  :deep(.ant-select-selector) {
    background: transparent !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  margin-left: auto;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  height: 36px;
  padding: 0 var(--admin-space-4);
  font-size: var(--admin-text-sm);
  font-weight: 600;
  color: var(--admin-text-primary);
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-strong);
  }
}

.type-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  svg {
    width: 14px;
    height: 14px;
  }
}

/* ========== 表格容器 ========== */
.table-card {
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  overflow: hidden;
  animation: slideUp 0.45s var(--admin-ease) 0.1s backwards;
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
    color: var(--admin-text-primary);
  }

  :deep(.ant-table-wrapper) {
    .ant-table-container {
      border: none;
    }
  }

  :deep(.ant-table-thead > tr > th) {
    background: transparent;
    border-bottom: 1px solid var(--admin-border-strong);
    color: var(--admin-text-tertiary);
    font-family: var(--admin-font-mono);
    font-weight: 500;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    padding: var(--admin-space-3) var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--admin-border-subtle);
    color: var(--admin-text-primary);
    padding: var(--admin-space-3) var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--admin-transition-fast);

    &:hover > td {
      background: var(--admin-bg-tertiary);
    }
  }

  :deep(.ant-pagination) {
    padding: var(--admin-space-4);
    background: transparent;
    margin: 0;

    .ant-pagination-item {
      background: transparent;
      border-color: var(--admin-border-default);

      a {
        color: var(--admin-text-secondary);
      }

      &:hover {
        background: var(--admin-primary-bg);
        border-color: var(--admin-primary);
      }

      &.ant-pagination-item-active {
        background: var(--admin-primary);
        border-color: var(--admin-primary);

        a {
          color: var(--admin-accent-ink);
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: transparent;
        border-color: var(--admin-border-default);
        color: var(--admin-text-secondary);

        &:hover {
          background: var(--admin-primary-bg);
          border-color: var(--admin-primary);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: transparent;
          border-color: var(--admin-border-default);
          color: var(--admin-text-primary);
        }
      }
    }
  }
}

/* ========== 单元格样式 ========== */
.user-cell {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);

  .user-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .user-name {
    font-weight: 600;
    font-size: var(--admin-text-base);
    color: var(--admin-text-primary);
  }

  .user-id {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-secondary);
    font-family: var(--admin-font-mono);
  }
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-sm);
  white-space: nowrap;

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.suggestion {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border: 1px solid color-mix(in srgb, var(--admin-primary) 24%, transparent);
  }

  &.report {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border: 1px solid color-mix(in srgb, var(--admin-danger) 24%, transparent);
  }

  &.ticket {
    color: var(--admin-warning);
    background: var(--admin-warning-bg);
    border: 1px solid color-mix(in srgb, var(--admin-warning) 24%, transparent);
  }

  &.default {
    color: var(--admin-text-secondary);
    background: transparent;
    border: 1px solid var(--admin-border-default);
  }
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-sm);
  white-space: nowrap;

  .status-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
  }

  &.pending {
    color: var(--admin-text-secondary);
    background: transparent;
    border: 1px solid var(--admin-border-default);

    .status-dot {
      background: var(--admin-text-secondary);
    }
  }

  &.processing {
    color: var(--admin-warning);
    background: var(--admin-warning-bg);
    border: 1px solid color-mix(in srgb, var(--admin-warning) 24%, transparent);

    .status-dot {
      background: var(--admin-warning);
      animation: pulse 1.5s infinite;
    }
  }

  &.resolved {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border: 1px solid color-mix(in srgb, var(--admin-success) 24%, transparent);

    .status-dot {
      background: var(--admin-success);
    }
  }
}

.title-cell {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);

  .title-text {
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .attachment-badge {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 2px 6px;
    font-size: 10px;
    color: var(--admin-text-secondary);
    background: var(--admin-bg-hover);
    border-radius: var(--admin-radius-sm);
    flex-shrink: 0;

    svg {
      width: 10px;
      height: 10px;
    }

    &.blocked {
      color: var(--admin-danger);
      background: var(--admin-danger-bg);
      border: 1px solid color-mix(in srgb, var(--admin-danger) 24%, transparent);
    }
  }
}

.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--admin-space-1);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 15px;
    height: 15px;
  }

  &:hover {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border-color: color-mix(in srgb, var(--admin-primary) 22%, transparent);
  }

  &.success:hover {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border-color: color-mix(in srgb, var(--admin-success) 22%, transparent);
  }
}

/* ========== 弹窗样式 ========== */
.feedback-detail {
  .detail-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: var(--admin-space-5);
  }

  .detail-user {
    display: flex;
    align-items: center;
    gap: var(--admin-space-3);
  }

  .detail-user-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .detail-user-name {
    font-weight: 600;
    font-size: var(--admin-text-lg);
    color: var(--admin-text-primary);
  }

  .detail-user-id {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-secondary);
    font-family: var(--admin-font-mono);
  }

  .detail-meta {
    text-align: right;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: var(--admin-space-2);
  }

  .detail-tags {
    display: inline-flex;
    gap: var(--admin-space-2);
    flex-wrap: nowrap;
  }

  .detail-section {
    margin-bottom: var(--admin-space-5);
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: var(--admin-space-2);
    font-family: var(--admin-font-mono);
    font-size: 9px;
    font-weight: 500;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--admin-text-tertiary);
    margin-bottom: var(--admin-space-3);

    svg {
      width: 14px;
      height: 14px;
      color: var(--admin-primary);
    }
  }

  .detail-content {
    background: var(--admin-bg-tertiary);
    border: 1px solid var(--admin-border-subtle);
    border-radius: var(--admin-radius-sm);
    padding: var(--admin-space-4);
  }

  .content-title {
    font-size: var(--admin-text-base);
    font-weight: 600;
    color: var(--admin-text-primary);
    margin-bottom: var(--admin-space-3);
  }

  .content-body {
    color: var(--admin-text-secondary);
    line-height: 1.75;
    white-space: pre-wrap;
    margin: 0;
  }

  .picture-grid {
    display: flex;
    flex-wrap: wrap;
    gap: var(--admin-space-3);
  }

  .detail-picture {
    width: 120px;
    height: 120px;
    object-fit: cover;
    border-radius: var(--admin-radius-sm);
    cursor: pointer;
    transition:
      transform var(--admin-transition-fast),
      border-color var(--admin-transition-fast);
    border: 1px solid var(--admin-border-subtle);

    &:hover {
      transform: translateY(-2px);
      border-color: var(--admin-border-strong);
    }

    // 无受控入口的附件：只显示状态，不给可直连地址
    &.blocked {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--admin-space-2);
      text-align: center;
      font-size: var(--admin-text-xs);
      color: var(--admin-danger);
      background: var(--admin-danger-bg);
      border-color: rgba(207, 34, 46, 0.2);
      cursor: default;

      &:hover {
        transform: none;
      }
    }
  }

  .handle-section {
    .handle-form {
      .form-row {
        display: flex;
        align-items: center;
        gap: var(--admin-space-3);
        margin-bottom: var(--admin-space-4);

        label {
          font-weight: 600;
          color: var(--admin-text-primary);
          font-size: var(--admin-text-sm);
          white-space: nowrap;
        }
      }
    }
  }

  .handler-note {
    margin-top: var(--admin-space-4);
    padding: var(--admin-space-4);
    background: var(--admin-bg-tertiary);
    border: 1px solid var(--admin-border-subtle);
    border-radius: var(--admin-radius-sm);

    .note-content {
      color: var(--admin-text-secondary);
      line-height: 1.6;
      margin: 0;
      white-space: pre-wrap;
    }
  }
}

/* ========== 动画 ========== */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .header-stats {
    width: 100%;
    justify-content: flex-start;
  }

  .table-toolbar {
    flex-wrap: wrap;

    .filter-selects {
      flex-wrap: wrap;
    }

    .filter-buttons {
      width: 100%;
      margin-left: 0;
      justify-content: flex-end;
    }
  }
}

@media (max-width: 768px) {
  #feedbackManagePage {
    padding: 0;
  }

  .stat-card {
    padding: var(--admin-space-2) var(--admin-space-3);
    min-width: 60px;

    .stat-value {
      font-size: var(--admin-text-xl);
    }
  }
}

/* 手机上筛选区整行铺开：状态标签可横向滑动，类型筛选与刷新按钮各占满一行 */
@media (max-width: 640px) {
  .filter-tabs {
    width: 100%;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .filter-selects {
    width: 100%;

    .filter-select {
      flex: 1;
      width: auto;
    }
  }

  .filter-buttons {
    width: 100%;

    .toolbar-btn {
      flex: 1;
      justify-content: center;
    }
  }
}
</style>
