<template>
  <div id="spaceManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">
            <span>☁️</span>
            <span>空间管理</span>
          </h1>
          <p class="page-subtitle">管理系统所有空间和配额</p>
        </div>
        <div class="header-actions">
          <a-space>
            <button @click="goToCreateSpace" class="pop-btn primary">
              <span>➕</span>
              <span>创建空间</span>
            </button>
            <button @click="goToAnalyzePublic" class="pop-btn secondary">
              <span>📊</span>
              <span>分析公共图库</span>
            </button>
            <button @click="goToAnalyzeAll" class="pop-btn outline">
              <span>📈</span>
              <span>分析全部空间</span>
            </button>
          </a-space>
        </div>
      </div>
    </div>

    <!-- 搜索表单 -->
    <div class="filter-section pop-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="filter-form">
        <a-form-item label="🏷️ 空间名称">
          <a-input
            v-model:value="searchParams.spaceName"
            placeholder="请输入空间名称"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item label="⭐ 空间级别">
          <a-select
            v-model:value="searchParams.spaceLevel"
            :options="SPACE_LEVEL_OPTIONS"
            placeholder="请选择空间级别"
            allow-clear
            class="form-select"
          />
        </a-form-item>
        <a-form-item label="📁 空间类别">
          <a-select
            v-model:value="searchParams.spaceType"
            :options="SPACE_TYPE_OPTIONS"
            placeholder="请选择空间类别"
            allow-clear
            class="form-select"
          />
        </a-form-item>
        <a-form-item label="👤 用户ID">
          <a-input
            v-model:value="searchParams.userId"
            placeholder="请输入用户ID"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item>
          <button html-type="submit" class="pop-btn primary search-btn">
            <span>🔍</span>
            <span>搜索</span>
          </button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper pop-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        @change="doTableChange"
        :loading="loading"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'spaceLevel'">
            <span class="level-tag pop-tag" :class="getSpaceLevelClass(record.spaceLevel)">
              {{ SPACE_LEVEL_MAP[record.spaceLevel] }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'spaceType'">
            <span class="type-tag pop-tag" :class="record.spaceType === 0 ? 'sky' : 'grape'">
              {{ SPACE_TYPE_MAP[record.spaceType] }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'spaceUseInfo'">
            <div class="space-info">
              <div class="info-row">
                <span class="info-label">💾</span>
                <span class="info-value">{{ formatSize(record.totalSize) }} / {{ formatSize(record.maxSize) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">🖼️</span>
                <span class="info-value">{{ record.totalCount }} / {{ record.maxCount }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'createTime' || column.dataIndex === 'editTime'">
            <span class="time-text">
              {{ dayjs(record[column.dataIndex]).format('YYYY-MM-DD HH:mm') }}
            </span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <button @click="goToAnalyze(record.id)" class="action-btn pop-btn outline">
                <span>📊</span>
              </button>
              <button @click="goToEdit(record.id)" class="action-btn pop-btn outline">
                <span>✏️</span>
              </button>
              <a-popconfirm
                title="确定要删除此空间吗？"
                ok-text="🗑️ 删除"
                cancel-text="❌ 取消"
                @confirm="doDelete(record.id)"
              >
                <button class="action-btn pop-btn" style="background: var(--color-coral);">
                  <span>🗑️</span>
                </button>
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
import { deleteSpaceUsingPost, listSpaceByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  SPACE_LEVEL_MAP,
  SPACE_LEVEL_OPTIONS,
  SPACE_TYPE_MAP,
  SPACE_TYPE_OPTIONS,
  SPACE_LEVEL_ENUM,
} from '@/constants/space.ts'
import { formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '空间名称', dataIndex: 'spaceName', width: 200 },
  { title: '空间级别', dataIndex: 'spaceLevel', width: 120 },
  { title: '空间类别', dataIndex: 'spaceType', width: 120 },
  { title: '使用情况', dataIndex: 'spaceUseInfo', width: 250 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '编辑时间', dataIndex: 'editTime', width: 160 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
]

const dataList = ref<API.Space[]>([])
const total = ref<number>(0)
const loading = ref(false)

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
  showTotal: (total: number) => `共 ${total} 条`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listSpaceByPageUsingPost(searchParams)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('😅 获取数据失败：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const doDelete = async (id: number) => {
  if (!id) return
  try {
    const res = await deleteSpaceUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 空间已删除')
      await fetchData()
    } else {
      message.error('😅 删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 删除失败')
  }
}

const getSpaceLevelClass = (level: number) => {
  const classMap: Record<number, string> = {
    [SPACE_LEVEL_ENUM.BASIC]: 'coral',
    [SPACE_LEVEL_ENUM.PROFESSIONAL]: 'sky',
    [SPACE_LEVEL_ENUM.FLAGSHIP]: 'sunshine',
  }
  return classMap[level] || 'coral'
}

const goToCreateSpace = () => {
  router.push('/add_space')
}

const goToAnalyzePublic = () => {
  router.push('/space_analyze?queryPublic=1')
}

const goToAnalyzeAll = () => {
  router.push('/space_analyze?queryAll=1')
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
  margin-bottom: var(--space-8);
  animation: fadeIn 0.6s ease-out;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-6);
}

.header-left {
  flex: 1;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-4xl);
  font-weight: 800;
  margin: 0 0 var(--space-3) 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--gradient-pop);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;

  span:first-child {
    -webkit-text-fill-color: initial;
    background: none;
  }
}

.page-subtitle {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
}

.header-actions {
  flex-shrink: 0;
}

/* ========== 搜索表单区域 ========== */
.filter-section {
  margin-bottom: var(--space-6);
  padding: var(--space-5);
  animation: slideUp 0.6s ease-out 0.1s backwards;

  &::before {
    display: none;
  }
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  justify-content: center;

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }

  :deep(.ant-form-item-label) {
    font-weight: 700;
    color: var(--text-primary);
  }
}

.form-input,
.form-select {
  min-width: 160px;
}

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

/* ========== 表格容器 ========== */
.table-wrapper {
  padding: var(--space-6);
  animation: slideUp 0.6s ease-out 0.2s backwards;

  &::before {
    display: none;
  }
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
  }

  :deep(.ant-table-container) {
    border-radius: var(--radius-xl);
    border: 3px solid var(--border-light);
    overflow: hidden;
  }

  :deep(.ant-table-thead > tr > th) {
    background: var(--gradient-fresh);
    border-bottom: 3px solid var(--border-bold);
    color: white;
    font-weight: 700;
    padding: var(--space-4);
    text-align: center;
  }

  :deep(.ant-table-tbody > tr) {
    transition: all var(--transition-bounce);

    &:hover > td {
      background: var(--bg-hover);
    }

    > td {
      border-bottom: 2px solid var(--border-light);
      color: var(--text-secondary);
      padding: var(--space-4);
      text-align: center;
      font-weight: 600;
    }
  }
}

/* ========== 标签和信息 ========== */
.level-tag,
.type-tag {
  padding: 4px 10px;
  font-size: var(--text-xs);
}

.space-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.info-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

.info-label {
  font-size: var(--text-base);
}

.info-value {
  color: var(--text-primary);
  font-weight: 700;
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}

.action-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &.outline {
    border-color: var(--border-light);
  }

  &:hover {
    transform: translate(-2px, -2px);
    box-shadow: var(--shadow-pop);
  }
}

.time-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;

    :deep(.ant-space) {
      width: 100%;
      flex-wrap: wrap;
    }
  }
}

@media (max-width: 768px) {
  #spaceManagePage {
    padding: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .filter-form {
    :deep(.ant-form-item) {
      width: 100%;
    }
  }

  .form-input,
  .form-select {
    min-width: 100%;
  }

  .table-wrapper {
    padding: var(--space-4);
  }

  :deep(.ant-table) {
    font-size: var(--text-sm);
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
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
