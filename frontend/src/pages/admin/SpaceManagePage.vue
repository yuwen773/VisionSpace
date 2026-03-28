<template>
  <div id="spaceManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <svg class="title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
            <line x1="12" y1="22.08" x2="12" y2="12"></line>
          </svg>
          <span>空间管理</span>
        </h1>
        <p class="page-subtitle">管理系统所有空间、配额和访问权限</p>
      </div>
      <div class="header-actions">
        <button @click="goToCreateSpace" class="toolbar-btn primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          创建空间
        </button>
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
          v-model:value="searchParams.spaceName"
          placeholder="搜索空间名称..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-selects">
        <a-select
          v-model:value="searchParams.spaceType"
          placeholder="空间类型"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="0">私人空间</a-select-option>
          <a-select-option :value="1">团队空间</a-select-option>
        </a-select>
        <a-select
          v-model:value="searchParams.spaceLevel"
          placeholder="空间级别"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="0">普通版</a-select-option>
          <a-select-option :value="1">专业版</a-select-option>
          <a-select-option :value="2">旗舰版</a-select-option>
        </a-select>
        <a-input
          v-model:value="searchParams.userId"
          placeholder="用户ID..."
          class="search-input id-input"
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
        <span>已选择 <strong>{{ selectedRowKeys.length }}</strong> 个空间</span>
      </div>
      <div class="batch-actions">
        <button class="batch-btn" @click="batchEdit">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
          </svg>
          批量编辑
        </button>
        <button class="batch-btn" @click="batchDisable" :disabled="batchLoading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
          </svg>
          批量禁用
        </button>
        <button class="batch-btn" @click="batchEnable" :disabled="batchLoading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="16 12 12 8 8 12"></polyline>
            <line x1="12" y1="16" x2="12" y2="8"></line>
          </svg>
          批量启用
        </button>
        <a-popconfirm
          title="确定要删除选中的空间吗？此操作不可恢复。"
          ok-text="删除"
          cancel-text="取消"
          @confirm="batchDelete"
          placement="topRight"
        >
          <button class="batch-btn danger" :disabled="batchLoading">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
            批量删除
          </button>
        </a-popconfirm>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        :loading="loading"
        :rowKey="(record: API.Space) => record.id"
        :row-selection="rowSelection"
        @change="doTableChange"
        :scroll="{ x: 1500 }"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'spaceName'">
            <div class="space-name-cell">
              <span class="space-name">{{ record.spaceName }}</span>
              <span class="space-id">ID: {{ record.id }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceType'">
            <span class="type-tag" :class="record.spaceType === 0 ? 'private' : 'team'">
              <svg v-if="record.spaceType === 0" class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
              <svg v-else class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
              {{ record.spaceType === 0 ? '私人' : '团队' }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'spaceLevel'">
            <span class="level-tag" :class="getLevelClass(record.spaceLevel)">
              {{ SPACE_LEVEL_MAP[record.spaceLevel] }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'capacity'">
            <div class="capacity-cell">
              <div class="capacity-bar-wrapper">
                <div class="capacity-bar">
                  <div
                    class="capacity-fill"
                    :class="getCapacityClass(record)"
                    :style="{ width: getCapacityPercent(record) + '%' }"
                  ></div>
                </div>
                <span class="capacity-percent">{{ getCapacityPercent(record) }}%</span>
              </div>
              <div class="capacity-info">
                <span class="capacity-used">{{ formatSize(record.totalSize) }}</span>
                <span class="capacity-sep">/</span>
                <span class="capacity-total">{{ formatSize(record.maxSize) }}</span>
              </div>
              <div class="capacity-info count-info">
                <span class="capacity-used">{{ record.totalCount }}</span>
                <span class="capacity-sep">/</span>
                <span class="capacity-total">{{ record.maxCount }} 张</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'userId'">
            <div class="user-cell">
              <span class="user-id">UID {{ record.userId }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="分析空间">
                <button class="action-btn" @click="goToAnalyze(record.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="20" x2="18" y2="10"></line>
                    <line x1="12" y1="20" x2="12" y2="4"></line>
                    <line x1="6" y1="20" x2="6" y2="14"></line>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip title="编辑空间">
                <button class="action-btn" @click="goToEdit(record.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip :title="record.status === 1 ? '禁用空间' : '启用空间'">
                <button class="action-btn" :class="record.status === 1 ? 'warning' : 'success'" @click="toggleStatus(record)">
                  <svg v-if="record.status === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="16 12 12 8 8 12"></polyline>
                    <line x1="12" y1="16" x2="12" y2="8"></line>
                  </svg>
                </button>
              </a-tooltip>
              <a-popconfirm
                title="确定要删除此空间吗？此操作不可恢复。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
                placement="topRight"
              >
                <a-tooltip title="删除空间">
                  <button class="action-btn danger">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
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
import { deleteSpaceUsingPost, listSpaceByPageUsingPost, updateSpaceUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  SPACE_LEVEL_MAP,
  SPACE_LEVEL_ENUM,
} from '@/constants/space.ts'
import { formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70, sorter: true },
  { title: '空间名称', dataIndex: 'spaceName', width: 200 },
  { title: '类型', dataIndex: 'spaceType', width: 100 },
  { title: '级别', dataIndex: 'spaceLevel', width: 100 },
  { title: '容量使用', dataIndex: 'capacity', width: 200 },
  { title: '所有者', dataIndex: 'userId', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 140, sorter: true },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
]

const dataList = ref<API.Space[]>([])
const total = ref<number>(0)
const loading = ref(false)
const batchLoading = ref(false)
const selectedRowKeys = ref<(string | number)[]>([])

const searchParams = reactive<API.SpaceQueryRequest>({
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
  showTotal: (total: number) => `共 ${total} 个空间`,
}))

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedRowKeys.value = keys
  },
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listSpaceByPageUsingPost(searchParams)
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
  searchParams.spaceName = undefined
  searchParams.spaceType = undefined
  searchParams.spaceLevel = undefined
  searchParams.userId = undefined
  searchParams.current = 1
  selectedRowKeys.value = []
  fetchData()
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deleteSpaceUsingPost({ id })
    if (res.data.code === 0) {
      message.success('空间已删除')
      selectedRowKeys.value = selectedRowKeys.value.filter(k => k !== id)
      await fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

const toggleStatus = async (record: API.Space) => {
  if (!record.id) return
  const newStatus = record.status === 1 ? 0 : 1
  try {
    const res = await updateSpaceUsingPost({
      id: record.id,
      status: newStatus,
    } as API.SpaceUpdateRequest)
    if (res.data.code === 0) {
      message.success(newStatus === 1 ? '空间已启用' : '空间已禁用')
      await fetchData()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('操作失败')
  }
}

const batchEdit = () => {
  if (selectedRowKeys.value.length === 1) {
    goToEdit(selectedRowKeys.value[0] as number)
  } else {
    message.warning('请选择一个空间进行编辑')
  }
}

const batchDisable = async () => {
  await batchOperation({
    action: 'disable',
    successMessage: `已禁用 ${selectedRowKeys.value.length} 个空间`,
    errorMessage: '批量禁用失败',
    operation: (id) =>
      updateSpaceUsingPost({
        id,
        status: 0,
      } as API.SpaceUpdateRequest),
  })
}

const batchEnable = async () => {
  await batchOperation({
    action: 'enable',
    successMessage: `已启用 ${selectedRowKeys.value.length} 个空间`,
    errorMessage: '批量启用失败',
    operation: (id) =>
      updateSpaceUsingPost({
        id,
        status: 1,
      } as API.SpaceUpdateRequest),
  })
}

const batchDelete = async () => {
  await batchOperation({
    action: 'delete',
    successMessage: `已删除 ${selectedRowKeys.value.length} 个空间`,
    errorMessage: '批量删除失败',
    operation: (id) => deleteSpaceUsingPost({ id: id as number }),
  })
}

interface BatchOperationOptions {
  action: string
  successMessage: string
  errorMessage: string
  operation: (id: number | string) => Promise<unknown>
}

const batchOperation = async (options: BatchOperationOptions) => {
  const { action, successMessage, errorMessage, operation } = options
  if (!selectedRowKeys.value.length) return
  batchLoading.value = true
  try {
    const promises = selectedRowKeys.value.map(id => operation(id))
    const results = await Promise.allSettled(promises)
    const failed = results.filter(
      r => r.status === 'rejected' || r.value?.data?.code !== 0
    )
    if (failed.length === 0) {
      message.success(successMessage)
      selectedRowKeys.value = []
      await fetchData()
    } else {
      message.error(`${failed.length} 个空间${action}失败`)
    }
  } catch (error) {
    message.error(errorMessage)
  } finally {
    batchLoading.value = false
  }
}

const getLevelClass = (level: number) => {
  const classMap: Record<number, string> = {
    [SPACE_LEVEL_ENUM.COMMON]: 'basic',
    [SPACE_LEVEL_ENUM.PROFESSIONAL]: 'pro',
    [SPACE_LEVEL_ENUM.FLAGSHIP]: 'flagship',
  }
  return classMap[level] || 'basic'
}

const getCapacityPercent = (record: API.Space) => {
  if (!record.maxSize || record.maxSize === 0) return 0
  return Math.min(100, Math.round((record.totalSize / record.maxSize) * 100))
}

const getCapacityClass = (record: API.Space) => {
  const percent = getCapacityPercent(record)
  if (percent >= 90) return 'danger'
  if (percent >= 70) return 'warning'
  return 'normal'
}

const goToCreateSpace = () => {
  router.push('/add_space')
}

const goToAnalyze = (id: number) => {
  router.push(`/space_analyze?spaceId=${id}`)
}

const goToEdit = (id: number) => {
  router.push(`/add_space?id=${id}`)
}
</script>

<style scoped lang="less">
#spaceManagePage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-6);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 700;
  margin: 0 0 var(--space-2) 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: #c9d1d9;

  .title-icon {
    width: 32px;
    height: 32px;
    color: #58a6ff;
  }
}

.page-subtitle {
  font-size: var(--text-sm);
  color: #8b949e;
  margin: 0;
}

.header-actions {
  flex-shrink: 0;
}

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
  animation: slideUp 0.5s ease-out 0.1s backwards;
}

.search-box {
  position: relative;
  flex: 0 0 200px;

  .search-icon {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: #8b949e;
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding-left: 36px;
    background: #0d1117;
    border: 1px solid #30363d;
    color: #c9d1d9;

    &::placeholder {
      color: #6e7681;
    }

    &:focus {
      border-color: #58a6ff;
      box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
    }
  }
}

.id-input {
  width: 120px;
}

.filter-selects {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.filter-select {
  width: 130px;

  :deep(.ant-select-selector) {
    background: #0d1117 !important;
    border-color: #30363d !important;
    color: #c9d1d9 !important;
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-sm);
  font-weight: 500;
  color: #c9d1d9;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: #30363d;
    border-color: #8b949e;
  }

  &.primary {
    color: #ffffff;
    background: #238636;
    border-color: #238636;

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
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: rgba(88, 166, 255, 0.1);
  border: 1px solid rgba(88, 166, 255, 0.3);
  border-radius: var(--radius-lg);
  animation: slideUp 0.3s ease-out;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: #c9d1d9;
  font-size: var(--text-sm);

  svg {
    width: 16px;
    height: 16px;
    color: #58a6ff;
  }

  strong {
    color: #58a6ff;
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-sm);
  font-weight: 500;
  color: #c9d1d9;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: #30363d;
    border-color: #8b949e;
  }

  &.success {
    color: #3fb950;
    border-color: rgba(63, 185, 80, 0.5);
    background: rgba(63, 185, 80, 0.1);

    &:hover {
      background: rgba(63, 185, 80, 0.2);
      border-color: #3fb950;
    }
  }

  &.danger {
    color: #f85149;
    background: rgba(248, 81, 73, 0.1);
    border-color: rgba(248, 81, 73, 0.5);

    &:hover {
      background: rgba(248, 81, 73, 0.2);
      border-color: #f85149;
    }
  }
}

/* ========== 表格容器 ========== */
.table-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
  overflow: hidden;
  animation: slideUp 0.5s ease-out 0.2s backwards;
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
    color: #c9d1d9;
  }

  :deep(.ant-table-wrapper) {
    .ant-table-container {
      border: none;
    }
  }

  :deep(.ant-table-thead > tr > th) {
    background: #21262d;
    border-bottom: 1px solid #30363d;
    color: #8b949e;
    font-weight: 600;
    font-size: var(--text-sm);
    padding: var(--space-4);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid #21262d;
    color: #c9d1d9;
    padding: var(--space-3) var(--space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--transition-fast);

    &:hover > td {
      background: #1c2128;
    }
  }

  :deep(.ant-pagination) {
    padding: var(--space-4);
    background: #161b22;
    margin: 0;

    .ant-pagination-item {
      background: #21262d;
      border-color: #30363d;

      a {
        color: #c9d1d9;
      }

      &:hover {
        background: #30363d;
        border-color: #8b949e;
      }

      &.ant-pagination-item-active {
        background: #238636;
        border-color: #238636;

        a {
          color: #ffffff;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: #21262d;
        border-color: #30363d;
        color: #c9d1d9;

        &:hover {
          background: #30363d;
          border-color: #8b949e;
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: #21262d;
          border-color: #30363d;
          color: #c9d1d9;
        }
      }
    }
  }
}

/* ========== 单元格样式 ========== */
.space-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .space-name {
    font-weight: 600;
    color: #c9d1d9;
  }

  .space-id {
    font-size: var(--text-xs);
    color: #6e7681;
    font-family: var(--font-mono);
  }
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-full);

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.private {
    color: #58a6ff;
    background: rgba(88, 166, 255, 0.15);
    border: 1px solid rgba(88, 166, 255, 0.3);
  }

  &.team {
    color: #a855f7;
    background: rgba(168, 85, 247, 0.15);
    border: 1px solid rgba(168, 85, 247, 0.3);
  }
}

.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-full);

  &.basic {
    color: #8b949e;
    background: rgba(139, 148, 158, 0.15);
    border: 1px solid rgba(139, 148, 158, 0.3);
  }

  &.pro {
    color: #d29922;
    background: rgba(210, 153, 34, 0.15);
    border: 1px solid rgba(210, 153, 34, 0.3);
  }

  &.flagship {
    color: #f85149;
    background: rgba(248, 81, 73, 0.15);
    border: 1px solid rgba(248, 81, 73, 0.3);
  }
}

.capacity-cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.capacity-bar-wrapper {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.capacity-bar {
  flex: 1;
  height: 6px;
  background: #21262d;
  border-radius: 3px;
  overflow: hidden;
}

.capacity-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;

  &.normal {
    background: #3fb950;
  }

  &.warning {
    background: #d29922;
  }

  &.danger {
    background: #f85149;
  }
}

.capacity-percent {
  font-size: var(--text-xs);
  font-weight: 600;
  color: #8b949e;
  min-width: 36px;
  text-align: right;
}

.capacity-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-xs);

  .capacity-used {
    color: #c9d1d9;
    font-weight: 500;
  }

  .capacity-sep {
    color: #6e7681;
  }

  .capacity-total {
    color: #8b949e;
  }
}

.count-info {
  margin-top: -4px;
}

.user-cell {
  .user-id {
    color: #8b949e;
    font-size: var(--text-xs);
    font-family: var(--font-mono);
  }
}

.time-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: #8b949e;
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--space-2);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #8b949e;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    color: #58a6ff;
    background: rgba(88, 166, 255, 0.1);
    border-color: rgba(88, 166, 255, 0.3);
  }

  &.success:hover {
    color: #3fb950;
    background: rgba(63, 185, 80, 0.1);
    border-color: rgba(63, 185, 80, 0.3);
  }

  &.warning:hover {
    color: #d29922;
    background: rgba(210, 153, 34, 0.1);
    border-color: rgba(210, 153, 34, 0.3);
  }

  &.danger:hover {
    color: #f85149;
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
  #spaceManagePage {
    padding: var(--space-4);
  }

  .page-header {
    flex-direction: column;
    gap: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
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
