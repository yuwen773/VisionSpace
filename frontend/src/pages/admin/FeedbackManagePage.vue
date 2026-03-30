<template>
  <div id="feedbackManagePage">
    <div class="page-header">
      <h1 class="page-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>反馈管理</span>
      </h1>
      <p class="page-subtitle">处理用户提交的产品建议、内容举报和工单支持</p>
    </div>

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
      <div class="filter-buttons">
        <a-select
          v-model:value="searchParams.type"
          placeholder="类型筛选"
          class="type-filter"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="1">产品建议</a-select-option>
          <a-select-option :value="2">内容举报</a-select-option>
          <a-select-option :value="3">工单支持</a-select-option>
        </a-select>
      </div>
    </div>

    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'userVO'">
            <div class="user-cell">
              <a-avatar :src="record.userVO?.userAvatar" :size="32" />
              <span>{{ record.userVO?.userName || '未知用户' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="getTypeColor(record.type)">
              {{ getTypeText(record.type) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="showDetail(record)">
              查看详情
            </a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="detailVisible"
      title="反馈详情"
      width="700px"
      :footer="isAdmin ? undefined : null"
    >
      <div v-if="currentFeedback" class="feedback-detail">
        <div class="detail-header">
          <div class="detail-user">
            <a-avatar :src="currentFeedback.userVO?.userAvatar" :size="48" />
            <div>
              <div class="detail-user-name">
                {{ currentFeedback.userVO?.userName }}
              </div>
              <div class="detail-time">
                {{ formatDate(currentFeedback.createTime) }}
              </div>
            </div>
          </div>
          <div class="detail-tags">
            <a-tag :color="getTypeColor(currentFeedback.type)">
              {{ getTypeText(currentFeedback.type) }}
            </a-tag>
            <a-tag :color="getStatusColor(currentFeedback.status)">
              {{ getStatusText(currentFeedback.status) }}
            </a-tag>
          </div>
        </div>

        <div class="detail-content">
          <h4>{{ currentFeedback.title }}</h4>
          <p>{{ currentFeedback.content }}</p>
        </div>

        <div v-if="currentFeedback.pictureUrls?.length" class="detail-pictures">
          <span class="detail-label">附件截图：</span>
          <div class="picture-list">
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
          <div class="handle-form">
            <label>处理状态：</label>
            <a-select v-model:value="handleForm.status" style="width: 150px">
              <a-select-option :value="0">待处理</a-select-option>
              <a-select-option :value="1">处理中</a-select-option>
              <a-select-option :value="2">已解决</a-select-option>
            </a-select>
          </div>
          <div class="handle-form">
            <label>处理备注：</label>
            <a-textarea
              v-model:value="handleForm.handlerNote"
              :rows="3"
              placeholder="请填写处理备注..."
            />
          </div>
        </div>

        <div v-if="currentFeedback.handlerNote" class="handler-note">
          <label>历史备注：</label>
          <p>{{ currentFeedback.handlerNote }}</p>
        </div>
      </div>

      <template #footer v-if="isAdmin">
        <a-button @click="detailVisible = false">取消</a-button>
        <a-button type="primary" @click="doUpdateStatus">更新状态</a-button>
      </template>
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
  showTotal: (total: number) => `共 ${total} 条`,
}))

const columns = [
  { title: '用户', key: 'userVO', width: 150, fixed: 'left' },
  { title: '类型', key: 'type', width: 100 },
  { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '提交时间', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

const statusTabs = [
  { label: '全部', value: null },
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已解决', value: 2 },
]

const getTypeColor = (type: number) => {
  const map: Record<number, string> = { 1: 'blue', 2: 'red', 3: 'orange' }
  return map[type] || 'default'
}

const getTypeText = (type: number) => {
  const map: Record<number, string> = { 1: '产品建议', 2: '内容举报', 3: '工单支持' }
  return map[type] || '未知'
}

const getStatusColor = (status: number) => {
  const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success' }
  return map[status] || 'default'
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '已解决' }
  return map[status] || '未知'
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
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
  window.open(url, '_blank')
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listFeedbackByPageUsingPost(searchParams)
    if (res.data.code === 0) {
      dataList.value = res.data.data.records || []
      pagination.value.total = res.data.data.total || 0
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

<style lang="less" scoped>
#feedbackManagePage {
  .page-header {
    margin-bottom: var(--admin-space-6);

    .page-title {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--admin-text-primary);
      margin-bottom: var(--space-2);

      svg {
        width: 24px;
        height: 24px;
      }
    }

    .page-subtitle {
      color: var(--admin-text-secondary);
      font-size: 0.875rem;
    }
  }

  .table-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--admin-space-4);
    padding: var(--admin-space-4);
    background: var(--admin-bg-card);
    border-radius: var(--admin-radius-lg);
    border: 1px solid var(--admin-border-default);
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
    transition: all 0.2s;

    &:hover {
      border-color: var(--admin-color-primary);
      color: var(--admin-color-primary);
    }

    &.active {
      background: var(--admin-color-primary);
      border-color: var(--admin-color-primary);
      color: white;
    }
  }

  .table-card {
    background: var(--admin-bg-card);
    border-radius: var(--admin-radius-lg);
    border: 1px solid var(--admin-border-default);
    overflow: hidden;
  }

  .user-cell {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }
}

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

  .detail-user-name {
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .detail-time {
    font-size: 0.875rem;
    color: var(--admin-text-secondary);
  }

  .detail-tags {
    display: flex;
    gap: var(--space-2);
  }

  .detail-content {
    margin-bottom: var(--admin-space-5);

    h4 {
      font-size: 1rem;
      font-weight: 600;
      color: var(--admin-text-primary);
      margin-bottom: var(--space-3);
    }

    p {
      color: var(--admin-text-secondary);
      line-height: 1.6;
    }
  }

  .detail-pictures {
    margin-bottom: var(--admin-space-5);

    .detail-label {
      display: block;
      font-weight: 600;
      color: var(--admin-text-primary);
      margin-bottom: var(--space-3);
    }

    .picture-list {
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
      transition: transform 0.2s;

      &:hover {
        transform: scale(1.05);
      }
    }
  }

  .handle-section {
    .handle-form {
      margin-bottom: var(--admin-space-4);

      label {
        display: block;
        font-weight: 600;
        color: var(--admin-text-primary);
        margin-bottom: var(--space-2);
      }
    }
  }

  .handler-note {
    margin-top: var(--admin-space-4);
    padding: var(--admin-space-4);
    background: var(--admin-bg-secondary);
    border-radius: var(--admin-radius-md);

    label {
      font-weight: 600;
      color: var(--admin-text-primary);
    }

    p {
      margin-top: var(--space-2);
      color: var(--admin-text-secondary);
    }
  }
}
</style>
