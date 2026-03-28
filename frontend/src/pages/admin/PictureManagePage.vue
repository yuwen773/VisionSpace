<template>
  <div id="pictureManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <svg class="title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
            <circle cx="8.5" cy="8.5" r="1.5"></circle>
            <polyline points="21 15 16 10 5 21"></polyline>
          </svg>
          <span>图片管理</span>
        </h1>
        <p class="page-subtitle">管理平台所有图片和审核状态</p>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="search-box">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="搜索图片名称..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-selects">
        <a-select
          v-model:value="searchParams.spaceId"
          placeholder="选择空间"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option v-for="space in spaceList" :key="space.id" :value="space.id">
            {{ space.spaceName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="searchParams.reviewStatus"
          placeholder="审核状态"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option value="0">待审核</a-select-option>
          <a-select-option value="1">已通过</a-select-option>
          <a-select-option value="2">已拒绝</a-select-option>
        </a-select>
        <a-input
          v-model:value="searchParams.category"
          placeholder="搜索分类..."
          class="search-input category-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-buttons">
        <button class="toolbar-btn primary" @click="doSearch">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.35-4.35"></path>
          </svg>
          搜索
        </button>
        <button class="toolbar-btn" @click="resetSearch">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"></path>
            <path d="M21 3v5h-5"></path>
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"></path>
            <path d="M8 16H3v5"></path>
          </svg>
          重置
        </button>
      </div>
    </div>

    <!-- 批量操作工具栏 -->
    <div class="batch-toolbar" v-if="selectedRowKeys.length > 0">
      <div class="batch-info">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 11 12 14 22 4"></polyline>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
        </svg>
        <span>已选择 <strong>{{ selectedRowKeys.length }}</strong> 项</span>
      </div>
      <div class="batch-actions">
        <button class="batch-btn success" @click="batchApprove">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          批量通过
        </button>
        <button class="batch-btn danger" @click="batchReject">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
          批量拒绝
        </button>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        :loading="loading"
        :rowKey="(record: API.Picture) => record.id"
        :row-selection="rowSelection"
        @change="doTableChange"
        :scroll="{ x: 1400 }"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'url'">
            <div class="picture-cell">
              <a-image
                :src="record.url"
                :width="56"
                :height="56"
                class="table-image"
                :preview="true"
              />
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'name'">
            <div class="name-cell">
              <span class="picture-name">{{ record.name || '未命名' }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'introduction'">
            <span class="intro-text">{{ record.introduction || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'category'">
            <span class="category-tag" v-if="record.category">{{ record.category }}</span>
            <span class="text-muted" v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'tags'">
            <div class="tags-cell">
              <span v-for="tag in parseTags(record.tags)" :key="tag" class="tag-item">{{ tag }}</span>
              <span v-if="!parseTags(record.tags).length" class="text-muted">-</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'picInfo'">
            <div class="pic-info-cell">
              <span class="pic-format">{{ record.picFormat?.toUpperCase() }}</span>
              <span class="pic-size">{{ record.picWidth }} x {{ record.picHeight }}</span>
              <span class="pic-size">{{ formatSize(record.picSize) }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceName'">
            <span class="space-name" v-if="record.spaceId">空间 #{{ record.spaceId }}</span>
            <span class="text-muted" v-else>公共图库</span>
          </template>
          <template v-else-if="column.dataIndex === 'userId'">
            <span class="user-id">UID {{ record.userId }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'reviewStatus'">
            <span class="status-tag" :class="getStatusClass(record.reviewStatus)">
              <svg v-if="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS" class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <svg v-else-if="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT" class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
              <svg v-else class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
              {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}
            </span>
            <div class="review-message" v-if="record.reviewMessage">
              {{ record.reviewMessage }}
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="编辑图片" v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS">
                <button class="action-btn" @click="goToEdit(record.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip title="通过审核" v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS">
                <button class="action-btn success" @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip title="拒绝审核" v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT">
                <button class="action-btn danger" @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                </button>
              </a-tooltip>
              <a-popconfirm
                title="确定要删除此图片吗？此操作不可恢复。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
                placement="topRight"
              >
                <a-tooltip title="删除图片">
                  <button class="action-btn danger">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                      <line x1="10" y1="11" x2="10" y2="17"></line>
                      <line x1="14" y1="11" x2="14" y2="17"></line>
                    </svg>
                  </button>
                </a-tooltip>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { listSpaceByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
} from '@/constants/picture.ts'
import { formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70, sorter: true },
  { title: '图片', dataIndex: 'url', width: 80 },
  { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
  { title: '简介', dataIndex: 'introduction', ellipsis: true },
  { title: '分类', dataIndex: 'category', width: 100 },
  { title: '标签', dataIndex: 'tags', width: 160 },
  { title: '图片信息', dataIndex: 'picInfo', width: 140 },
  { title: '空间', dataIndex: 'spaceName', width: 100 },
  { title: '用户', dataIndex: 'userId', width: 80 },
  { title: '审核状态', dataIndex: 'reviewStatus', width: 160 },
  { title: '创建时间', dataIndex: 'createTime', width: 140, sorter: true },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
]

const dataList = ref<API.Picture[]>([])
const spaceList = ref<API.Space[]>([])
const total = ref<number>(0)
const loading = ref(false)
const selectedRowKeys = ref<(string | number)[]>([])

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortOrder: 'descend',
  sortField: 'createTime',
})

const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: Number(total.value),
  showSizeChanger: true,
  position: ['bottomCenter'] as const,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 张图片`,
}))

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedRowKeys.value = keys
  },
  getCheckboxProps: (record: API.Picture) => ({
    disabled: record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS,
  }),
}))

const fetchSpaceList = async () => {
  try {
    const res = await listSpaceByPageUsingPost({
      current: 1,
      pageSize: 100,
    })
    if (res.data.code === 0 && res.data.data) {
      spaceList.value = res.data.data.records ?? []
    }
  } catch (error) {
    console.error('获取空间列表失败', error)
    message.error('获取空间列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureByPageUsingPost({
      ...searchParams,
      reviewStatus: searchParams.reviewStatus ? Number(searchParams.reviewStatus) : undefined,
      tags: searchParams.tags
        ? searchParams.tags.split(/,|，/).map((tag) => tag.trim())
        : undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('获取数据失败：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSpaceList()
  fetchData()
})

const doTableChange = (
  pag: { current?: number; pageSize?: number },
  filters: Record<string, unknown>,
  sorter: { field?: string; order?: 'ascend' | 'descend' }
) => {
  searchParams.current = pag.current
  searchParams.pageSize = pag.pageSize
  if (sorter.field) {
    searchParams.sortField = sorter.field
    searchParams.sortOrder = sorter.order === 'ascend' ? 'ascend' : 'descend'
  }
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  selectedRowKeys.value = []
  fetchData()
}

const resetSearch = () => {
  searchParams.searchText = undefined
  searchParams.spaceId = undefined
  searchParams.reviewStatus = undefined
  searchParams.category = undefined
  searchParams.tags = undefined
  searchParams.current = 1
  selectedRowKeys.value = []
  fetchData()
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('图片已删除')
      selectedRowKeys.value = selectedRowKeys.value.filter(k => k !== id)
      await fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

const handleReview = async (record: API.Picture, reviewStatus: number) => {
  const reviewMessage =
    reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  try {
    const res = await doPictureReviewUsingPost({
      id: record.id,
      reviewStatus,
      reviewMessage,
    })
    if (res.data.code === 0) {
      message.success('审核操作成功')
      await fetchData()
    } else {
      message.error('审核操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('审核操作失败')
  }
}

const batchReview = async (reviewStatus: number, reviewMessage: string) => {
  if (!selectedRowKeys.value.length) return
  try {
    const promises = selectedRowKeys.value.map(id =>
      doPictureReviewUsingPost({
        id,
        reviewStatus,
        reviewMessage,
      })
    )
    const results = await Promise.allSettled(promises)
    const failed = results.filter(
      r => r.status === 'rejected' || r.value?.data?.code !== 0
    )
    if (failed.length === 0) {
      message.success(`已审核 ${selectedRowKeys.value.length} 张图片`)
      selectedRowKeys.value = []
      await fetchData()
    } else {
      message.error(`${failed.length} 张图片审核失败`)
    }
  } catch (error) {
    message.error('批量审核失败')
  }
}

const batchApprove = () => batchReview(PIC_REVIEW_STATUS_ENUM.PASS, '管理员批量通过')
const batchReject = () => batchReview(PIC_REVIEW_STATUS_ENUM.REJECT, '管理员批量拒绝')

const parseTags = (tags: string) => {
  try {
    return JSON.parse(tags || '[]')
  } catch {
    return []
  }
}

const getStatusClass = (status: number) => {
  const classMap: Record<number, string> = {
    [PIC_REVIEW_STATUS_ENUM.REVIEWING]: 'reviewing',
    [PIC_REVIEW_STATUS_ENUM.PASS]: 'approved',
    [PIC_REVIEW_STATUS_ENUM.REJECT]: 'rejected',
  }
  return classMap[status] || 'reviewing'
}

const goToEdit = (id: number) => {
  router.push(`/add_picture?id=${id}`)
}
</script>

<style scoped lang="less">
#pictureManagePage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--admin-space-6);
}

/* ========== 页面头部 ========== */
.page-header {
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

.search-box {
  position: relative;
  flex: 0 0 220px;

  .search-icon {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: var(--admin-text-secondary);
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding-left: 36px;
    background: var(--admin-bg-primary);
    border: 1px solid var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
    }
  }
}

.filter-selects {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.filter-select {
  width: 140px;

  :deep(.ant-select-selector) {
    background: var(--admin-bg-primary) !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }
}

.category-input {
  width: 140px;
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
  padding: var(--admin-space-2) var(--admin-space-4);
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

  &.primary {
    color: #ffffff;
    background: var(--admin-success);
    border-color: var(--admin-success);

    &:hover {
      background: #2ea043;
      border-color: #2ea043;
    }
  }
}

/* ========== 批量操作工具栏 ========== */
.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-3) var(--admin-space-4);
  background: rgba(88, 166, 255, 0.1);
  border: 1px solid rgba(88, 166, 255, 0.3);
  border-radius: var(--admin-radius-lg);
  animation: slideUp 0.3s ease-out;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);
  color: var(--admin-text-primary);
  font-size: var(--admin-text-sm);

  svg {
    width: 16px;
    height: 16px;
    color: var(--admin-primary);
  }

  strong {
    color: var(--admin-primary);
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  padding: var(--admin-space-2) var(--admin-space-3);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &.success {
    color: #ffffff;
    background: var(--admin-success);
    border: 1px solid var(--admin-success);

    &:hover {
      background: #2ea043;
      border-color: #2ea043;
    }
  }

  &.danger {
    color: #ffffff;
    background: #da3633;
    border: 1px solid #da3633;

    &:hover {
      background: var(--admin-danger);
      border-color: var(--admin-danger);
    }
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
    padding: var(--admin-space-4);
    text-transform: uppercase;
    letter-spacing: 0.5px;
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

  :deep(.ant-table-column-sorter) {
    color: var(--admin-text-secondary);
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
        background: var(--admin-success);
        border-color: var(--admin-success);

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
.picture-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.table-image {
  border-radius: var(--admin-radius-md);
  overflow: hidden;
  border: 1px solid var(--admin-border-default);
}

.name-cell {
  .picture-name {
    font-weight: 600;
    color: var(--admin-text-primary);
  }
}

.intro-text {
  color: var(--admin-text-secondary);
  font-size: var(--admin-text-sm);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.category-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: var(--admin-text-xs);
  font-weight: 500;
  color: var(--admin-primary);
  background: rgba(88, 166, 255, 0.15);
  border: 1px solid rgba(88, 166, 255, 0.3);
  border-radius: var(--admin-radius-full);
}

.text-muted {
  color: var(--admin-text-disabled);
  font-size: var(--admin-text-sm);
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;

  .tag-item {
    display: inline-block;
    padding: 2px 6px;
    font-size: var(--admin-text-xs);
    color: #d29922;
    background: rgba(210, 153, 34, 0.15);
    border: 1px solid rgba(210, 153, 34, 0.3);
    border-radius: var(--admin-radius-full);
  }
}

.pic-info-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--admin-text-xs);

  .pic-format {
    color: #3fb950;
    font-weight: 600;
  }

  .pic-size {
    color: var(--admin-text-secondary);
  }
}

.space-name {
  color: #a855f7;
  font-size: var(--admin-text-sm);
}

.user-id {
  color: var(--admin-text-secondary);
  font-size: var(--admin-text-xs);
  font-family: var(--admin-font-mono);
}

/* ========== 审核状态标签 ========== */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  text-transform: uppercase;
  letter-spacing: 0.3px;

  .status-icon {
    width: 12px;
    height: 12px;
  }

  &.reviewing {
    color: #d29922;
    background: rgba(210, 153, 34, 0.15);
    border: 1px solid rgba(210, 153, 34, 0.3);
  }

  &.approved {
    color: #3fb950;
    background: rgba(63, 185, 80, 0.15);
    border: 1px solid rgba(63, 185, 80, 0.3);
  }

  &.rejected {
    color: var(--admin-danger);
    background: rgba(248, 81, 73, 0.15);
    border: 1px solid rgba(248, 81, 73, 0.3);
  }
}

.review-message {
  margin-top: 4px;
  font-size: var(--admin-text-xs);
  color: var(--admin-text-disabled);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--admin-space-2);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--admin-text-secondary);
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
    color: var(--admin-primary);
    background: rgba(88, 166, 255, 0.1);
    border-color: rgba(88, 166, 255, 0.3);
  }

  &.success:hover {
    color: #3fb950;
    background: rgba(63, 185, 80, 0.1);
    border-color: rgba(63, 185, 80, 0.3);
  }

  &.danger:hover {
    color: var(--admin-danger);
    background: rgba(248, 81, 73, 0.1);
    border-color: rgba(248, 81, 73, 0.3);
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

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .table-toolbar {
    flex-wrap: wrap;

    .search-box {
      flex: 1 1 100%;
    }

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
  #pictureManagePage {
    padding: var(--admin-space-4);
  }

  .page-title {
    font-size: var(--admin-text-2xl);
  }

  .batch-toolbar {
    flex-direction: column;
    align-items: flex-start;

    .batch-actions {
      width: 100%;
      justify-content: flex-end;
    }
  }
}
</style>
