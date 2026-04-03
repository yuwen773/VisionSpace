<template>
  <div id="feedbackManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <svg class="title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>反馈管理</span>
        </h1>
        <p class="page-subtitle">处理用户提交的产品建议、内容举报和工单支持</p>
      </div>
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
    </div>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="filter-tabs">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
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
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
              产品建议
            </span>
          </a-select-option>
          <a-select-option :value="2">
            <span class="type-option">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></svg>
              内容举报
            </span>
          </a-select-option>
          <a-select-option :value="3">
            <span class="type-option">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>
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
        :scroll="{ x: 1100 }"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'userVO'">
            <div class="user-cell">
              <a-avatar :src="record.userVO?.userAvatar" :size="32">
                <template #icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></template>
              </a-avatar>
              <div class="user-info">
                <span class="user-name">{{ record.userVO?.userName || '未知用户' }}</span>
                <span class="user-id">{{ record.userVO?.userId }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            <span class="type-tag" :class="getTypeClass(record.type)">
              <svg v-if="record.type === 1" class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
              <svg v-else-if="record.type === 2" class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></svg>
              <svg v-else class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
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
              <span v-if="record.pictureUrls?.length" class="attachment-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                {{ record.pictureUrls.length }}
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'createTime'">
            <span class="time-text">{{ formatDate(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="查看详情">
                <button class="action-btn" @click="showDetail(record)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip v-if="record.status !== 2" title="标记已解决">
                <button class="action-btn success" @click="quickResolve(record)">
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
            <a-avatar :src="currentFeedback.userVO?.userAvatar" :size="48">
              <template #icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></template>
            </a-avatar>
            <div class="detail-user-info">
              <span class="detail-user-name">{{ currentFeedback.userVO?.userName || '未知用户' }}</span>
              <span class="detail-user-id">{{ currentFeedback.userVO?.userId }}</span>
            </div>
          </div>
          <div class="detail-meta">
            <span class="time-text">{{ formatDate(currentFeedback.createTime) }}</span>
            <div class="detail-tags">
              <span class="type-tag" :class="getTypeClass(currentFeedback.type)">
                <svg v-if="currentFeedback.type === 1" class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
                <svg v-else-if="currentFeedback.type === 2" class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></svg>
                <svg v-else class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
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
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            反馈内容
          </h4>
          <div class="detail-content">
            <h3 class="content-title">{{ currentFeedback.title }}</h3>
            <p class="content-body">{{ currentFeedback.content }}</p>
          </div>
        </div>

        <div v-if="currentFeedback.pictureUrls?.length" class="detail-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
            附件截图
          </h4>
          <div class="picture-grid">
            <img
              v-for="(url, index) in currentFeedback.pictureUrls"
              :key="index"
              :src="url"
              class="detail-picture"
              @click="previewImage(url)"
            />
          </div>
        </div>

        <a-divider />

        <div v-if="isAdmin" class="handle-section">
          <h4 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
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
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            历史备注
          </h4>
          <p class="note-content">{{ currentFeedback.handlerNote }}</p>
        </div>
      </div>

      <template #footer v-if="isAdmin">
        <a-button @click="detailVisible = false">取消</a-button>
        <a-button type="primary" @click="doUpdateStatus">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 14px; height: 14px;">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          更新状态
        </a-button>
      </template>
    </a-modal>

    <!-- 图片预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      :title="null"
      :footer="null"
      :centered="true"
      :width="900"
      class="image-preview-modal"
      :bodyStyle="{ padding: 0, background: 'transparent' }"
      :modalStyle="{ background: 'transparent' }"
    >
      <div class="preview-wrapper">
        <div class="preview-toolbar">
          <div class="toolbar-group">
            <button class="toolbar-btn" @click="zoomOut" title="缩小">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                <line x1="8" y1="11" x2="14" y2="11"/>
              </svg>
            </button>
            <span class="zoom-level">{{ Math.round(previewScale * 100) }}%</span>
            <button class="toolbar-btn" @click="zoomIn" title="放大">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                <line x1="11" y1="8" x2="11" y2="14"/>
                <line x1="8" y1="11" x2="14" y2="11"/>
              </svg>
            </button>
            <button class="toolbar-btn" @click="resetZoom" title="重置">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="1 4 1 10 7 10"/>
                <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
              </svg>
            </button>
          </div>
          <div class="toolbar-group">
            <button class="toolbar-btn" @click="rotateImage" title="旋转">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <polyline points="1 20 1 14 7 14"/>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
              </svg>
            </button>
          </div>
        </div>
        <div class="preview-container" @wheel.prevent="handleWheel">
          <img
            :src="previewUrl"
            class="preview-image"
            :style="{
              transform: `translate(${translate.x}px, ${translate.y}px) scale(${previewScale}) rotate(${previewRotate}deg)`,
            }"
            @mousedown="startDrag"
            @mousemove="onDrag"
            @mouseup="endDrag"
            @mouseleave="endDrag"
            :draggable="false"
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  listFeedbackByPageUsingPost,
  updateFeedbackStatusUsingPost,
} from '@/api/feedbackController'
import { useLoginUserStore } from '@/stores/userLogin'

const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const loading = ref(false)
const dataList = ref([])
const detailVisible = ref(false)
const currentFeedback = ref<any>(null)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewScale = ref(1)
const previewRotate = ref(0)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const translate = ref({ x: 0, y: 0 })
const translateStart = ref({ x: 0, y: 0 })

const zoomIn = () => {
  previewScale.value = Math.min(previewScale.value + 0.25, 5)
}

const zoomOut = () => {
  previewScale.value = Math.max(previewScale.value - 0.25, 0.25)
}

const resetZoom = () => {
  previewScale.value = 1
  previewRotate.value = 0
  translate.value = { x: 0, y: 0 }
}

const rotateImage = () => {
  previewRotate.value = (previewRotate.value + 90) % 360
}

const handleWheel = (e: WheelEvent) => {
  if (e.deltaY < 0) {
    zoomIn()
  } else {
    zoomOut()
  }
}

const startDrag = (e: MouseEvent) => {
  if (previewScale.value > 1) {
    isDragging.value = true
    dragStart.value = { x: e.clientX, y: e.clientY }
    translateStart.value = { ...translate.value }
  }
}

const onDrag = (e: MouseEvent) => {
  if (isDragging.value && previewScale.value > 1) {
    translate.value = {
      x: translateStart.value.x + (e.clientX - dragStart.value.x),
      y: translateStart.value.y + (e.clientY - dragStart.value.y),
    }
  }
}

const endDrag = () => {
  isDragging.value = false
}

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

const columns = [
  { title: '用户', key: 'userVO', width: 180, fixed: 'left' },
  { title: '类型', key: 'type', width: 110, align: 'center' as const },
  { title: '标题', key: 'title', width: 220, ellipsis: true },
  { title: '状态', key: 'status', width: 100, align: 'center' as const },
  { title: '提交时间', key: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

const statusTabs = [
  { label: '全部', value: null },
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已解决', value: 2 },
]

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

const formatDate = (date: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
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

const showDetail = (record: any) => {
  currentFeedback.value = record
  handleForm.status = record.status
  handleForm.handlerNote = record.handlerNote || ''
  detailVisible.value = true
}

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
  previewScale.value = 1
  previewRotate.value = 0
  translate.value = { x: 0, y: 0 }
  previewVisible.value = true
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listFeedbackByPageUsingPost(searchParams)
    if (res.data.code === 0) {
      dataList.value = res.data.data.records || []
      pagination.value.total = res.data.data.total || 0
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
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--admin-space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--admin-space-6);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--admin-font);
  font-size: var(--admin-text-3xl);
  font-weight: 700;
  margin: 0 0 var(--admin-space-2) 0;
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  color: var(--admin-text-primary);

  .title-icon {
    width: 32px;
    height: 32px;
    color: var(--admin-primary);
  }
}

.page-subtitle {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-secondary);
  margin: 0;
}

.header-stats {
  display: flex;
  gap: var(--admin-space-4);
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--admin-space-3) var(--admin-space-5);
  background: var(--admin-bg-secondary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  min-width: 80px;

  .stat-value {
    font-size: var(--admin-text-2xl);
    font-weight: 700;
    color: var(--admin-text-primary);
    font-family: var(--admin-font-mono);
  }

  .stat-label {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-secondary);
    margin-top: 2px;
  }
}

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-4);
  background: var(--admin-bg-secondary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  animation: slideUp 0.5s ease-out 0.1s backwards;
}

.filter-tabs {
  display: flex;
  gap: var(--space-2);
}

.filter-tab {
  padding: var(--space-2) var(--admin-space-4);
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-full);
  color: var(--admin-text-secondary);
  cursor: pointer;
  transition: all var(--admin-transition-fast);
  font-size: var(--admin-text-sm);
  font-weight: 500;

  &:hover {
    border-color: var(--admin-primary);
    color: var(--admin-primary);
  }

  &.active {
    background: var(--admin-primary);
    border-color: var(--admin-primary);
    color: white;
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
    background: var(--admin-bg-primary) !important;
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
  gap: var(--space-2);
  padding: var(--space-2) var(--admin-space-4);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  color: var(--admin-text-primary);
  background: var(--admin-bg-hover);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--admin-border-default);
    border-color: var(--admin-text-secondary);
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
  background: var(--admin-bg-secondary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  overflow: hidden;
  animation: slideUp 0.5s ease-out 0.2s backwards;
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
    background: var(--admin-bg-hover);
    border-bottom: 1px solid var(--admin-border-default);
    color: var(--admin-text-secondary);
    font-weight: 600;
    font-size: var(--admin-text-sm);
    padding: var(--admin-space-3) var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--admin-bg-hover);
    color: var(--admin-text-primary);
    padding: var(--admin-space-3) var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--admin-transition-fast);

    &:hover > td {
      background: var(--admin-bg-hover);
    }
  }

  :deep(.ant-pagination) {
    padding: var(--admin-space-4);
    background: var(--admin-bg-secondary);
    margin: 0;

    .ant-pagination-item {
      background: var(--admin-bg-hover);
      border-color: var(--admin-border-default);

      a {
        color: var(--admin-text-primary);
      }

      &:hover {
        background: var(--admin-border-default);
        border-color: var(--admin-text-secondary);
      }

      &.ant-pagination-item-active {
        background: var(--admin-primary);
        border-color: var(--admin-primary);

        a {
          color: #ffffff;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: var(--admin-bg-hover);
        border-color: var(--admin-border-default);
        color: var(--admin-text-primary);

        &:hover {
          background: var(--admin-border-default);
          border-color: var(--admin-text-secondary);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: var(--admin-bg-hover);
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
  gap: var(--space-3);

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
  gap: 4px;
  padding: 3px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  white-space: nowrap;

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.suggestion {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border: 1px solid rgba(9, 105, 218, 0.2);
  }

  &.report {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border: 1px solid rgba(207, 34, 46, 0.2);
  }

  &.ticket {
    color: #9a6700;
    background: var(--admin-warning-bg);
    border: 1px solid rgba(154, 103, 0, 0.2);
  }

  &.default {
    color: var(--admin-text-secondary);
    background: var(--admin-bg-hover);
    border: 1px solid var(--admin-border-default);
  }
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  white-space: nowrap;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  &.pending {
    color: var(--admin-text-secondary);
    background: var(--admin-bg-hover);
    border: 1px solid var(--admin-border-default);

    .status-dot {
      background: var(--admin-text-secondary);
    }
  }

  &.processing {
    color: #9a6700;
    background: var(--admin-warning-bg);
    border: 1px solid rgba(154, 103, 0, 0.2);

    .status-dot {
      background: #d29922;
      animation: pulse 1.5s infinite;
    }
  }

  &.resolved {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border: 1px solid rgba(26, 127, 55, 0.2);

    .status-dot {
      background: var(--admin-success);
    }
  }
}

.title-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);

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
    border-color: rgba(9, 105, 218, 0.15);
  }

  &.success:hover {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border-color: rgba(26, 127, 55, 0.15);
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
    gap: var(--space-3);
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
    gap: var(--space-2);
  }

  .detail-tags {
    display: inline-flex;
    gap: var(--space-2);
    flex-wrap: nowrap;
  }

  .detail-section {
    margin-bottom: var(--admin-space-5);
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: var(--admin-text-sm);
    font-weight: 600;
    color: var(--admin-text-secondary);
    margin-bottom: var(--space-3);

    svg {
      width: 14px;
      height: 14px;
    }
  }

  .detail-content {
    background: var(--admin-bg-secondary);
    border: 1px solid var(--admin-border-subtle);
    border-radius: var(--admin-radius-md);
    padding: var(--admin-space-4);
  }

  .content-title {
    font-size: var(--admin-text-base);
    font-weight: 600;
    color: var(--admin-text-primary);
    margin-bottom: var(--space-3);
  }

  .content-body {
    color: var(--admin-text-secondary);
    line-height: 1.7;
    white-space: pre-wrap;
    margin: 0;
  }

  .picture-grid {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
  }

  .detail-picture {
    width: 120px;
    height: 120px;
    object-fit: cover;
    border-radius: var(--admin-radius-md);
    cursor: pointer;
    transition: transform var(--admin-transition-fast);
    border: 1px solid var(--admin-border-subtle);

    &:hover {
      transform: scale(1.05);
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
    background: var(--admin-bg-secondary);
    border: 1px solid var(--admin-border-subtle);
    border-radius: var(--admin-radius-md);

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
  from { opacity: 0; }
  to { opacity: 1; }
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
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .page-header {
    flex-direction: column;
    gap: var(--admin-space-4);
  }

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
    padding: var(--admin-space-4);
  }

  .page-title {
    font-size: var(--admin-text-2xl);
  }

  .stat-card {
    padding: var(--admin-space-2) var(--admin-space-3);
    min-width: 60px;

    .stat-value {
      font-size: var(--admin-text-xl);
    }
  }
}
</style>

<!-- 图片预览弹窗样式 -->
<style lang="less">
.image-preview-modal {
  .ant-modal-content {
    background: transparent;
    border: none;
    box-shadow: none;
    padding: 0;
  }

  .ant-modal-body {
    padding: 0;
  }

  .ant-modal-close {
    color: white;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);

    &:hover {
      color: white;
      background: rgba(255, 255, 255, 0.1);
    }
  }

  .preview-wrapper {
    display: flex;
    flex-direction: column;
    gap: var(--admin-space-3);
  }

  .preview-toolbar {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--admin-space-4);
    padding: var(--admin-space-3);
    background: rgba(31, 35, 40, 0.9);
    border-radius: var(--admin-radius-lg);
    backdrop-filter: blur(8px);
  }

  .toolbar-group {
    display: flex;
    align-items: center;
    gap: var(--admin-space-2);
  }

  .toolbar-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    padding: 0;
    background: rgba(255, 255, 255, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: var(--admin-radius-md);
    color: white;
    cursor: pointer;
    transition: all var(--admin-transition-fast);

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background: rgba(255, 255, 255, 0.2);
      border-color: rgba(255, 255, 255, 0.3);
    }
  }

  .zoom-level {
    min-width: 50px;
    text-align: center;
    font-size: var(--admin-text-sm);
    font-weight: 600;
    color: white;
    font-family: var(--admin-font-mono);
  }

  .preview-container {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 400px;
    max-height: 75vh;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.9);
    border-radius: var(--admin-radius-lg);
    cursor: grab;

    &:active {
      cursor: grabbing;
    }
  }

  .preview-image {
    max-width: 100%;
    max-height: 75vh;
    object-fit: contain;
    display: block;
    transition: transform 0.15s ease;
    user-select: none;
  }
}
</style>
