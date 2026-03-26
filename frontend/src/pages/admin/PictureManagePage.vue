<template>
  <div id="pictureManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">
            <span>🖼️</span>
            <span>图片管理</span>
          </h1>
          <p class="page-subtitle">管理平台所有图片和审核状态</p>
        </div>
        <div class="header-actions">
          <a-space>
            <button @click="goToCreate" class="pop-btn primary">
              <span>➕</span>
              <span>创建图片</span>
            </button>
            <button @click="goToBatchCreate" class="pop-btn secondary">
              <span>📚</span>
              <span>批量创建</span>
            </button>
          </a-space>
        </div>
      </div>
    </div>

    <!-- 搜索表单 -->
    <div class="filter-section pop-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="filter-form">
        <a-form-item label="🔍 关键词">
          <a-input
            v-model:value="searchParams.searchText"
            placeholder="从名称和简介搜索"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item label="📂 分类">
          <a-input
            v-model:value="searchParams.category"
            placeholder="请输入分类"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item label="🏷️ 标签">
          <a-input
            v-model:value="searchParams.tags"
            placeholder="多个标签用逗号分隔"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item label="✓ 审核状态">
          <a-select
            v-model:value="searchParams.reviewStatus"
            mode="tags"
            :options="PIC_REVIEW_STATUS_OPTIONS"
            placeholder="请选择审核状态"
            allow-clear
            class="form-select"
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
        :scroll="{ x: 1800 }"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'url'">
            <a-image
              :src="record.url"
              :width="60"
              :height="60"
              class="table-image"
              :preview="true"
            />
          </template>
          <template v-else-if="column.dataIndex === 'tags'">
            <a-space :size="4" wrap>
              <span v-for="tag in parseTags(record.tags)" :key="tag" class="pop-tag coral" style="padding: 2px 8px;">
                {{ tag }}
              </span>
            </a-space>
          </template>
          <template v-else-if="column.dataIndex === 'picInfo'">
            <div class="pic-info">
              <span class="info-item pop-tag sky">{{ record.picFormat }}</span>
              <span class="info-item pop-tag sunshine">{{ record.picWidth }} × {{ record.picHeight }}</span>
              <span class="info-item pop-tag mint">{{ formatSize(record.picSize) }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'reviewMessage'">
            <div class="review-info">
              <span class="review-tag pop-tag" :class="getReviewStatusClass(record.reviewStatus)">
                {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}
              </span>
              <div v-if="record.reviewMessage" class="review-message">
                {{ record.reviewMessage }}
              </div>
              <div v-if="record.reviewTime" class="review-time">
                {{ dayjs(record.reviewTime).format('YYYY-MM-DD HH:mm') }}
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
              <button
                v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS"
                @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
                class="action-btn pop-btn"
                style="background: var(--color-mint);"
              >
                <span>✓</span>
              </button>
              <button
                v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT"
                @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
                class="action-btn pop-btn"
                style="background: var(--color-pink);"
              >
                <span>✕</span>
              </button>
              <button @click="goToEdit(record.id)" class="action-btn pop-btn outline">
                <span>✏️</span>
              </button>
              <a-popconfirm
                title="确定要删除此图片吗？"
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
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '@/constants/picture.ts'
import { formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '图片', dataIndex: 'url', width: 100 },
  { title: '名称', dataIndex: 'name', width: 150, ellipsis: true },
  { title: '简介', dataIndex: 'introduction', width: 200, ellipsis: true },
  { title: '分类', dataIndex: 'category', width: 100 },
  { title: '标签', dataIndex: 'tags', width: 200 },
  { title: '图片信息', dataIndex: 'picInfo', width: 200 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '空间ID', dataIndex: 'spaceId', width: 100 },
  { title: '审核信息', dataIndex: 'reviewMessage', width: 200 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '编辑时间', dataIndex: 'editTime', width: 160 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
]

const dataList = ref<API.Picture[]>([])
const total = ref<number>(0)
const loading = ref(false)

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
  showTotal: (total: number) => `共 ${total} 条`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureByPageUsingPost({
      ...searchParams,
      reviewStatus: searchParams.reviewStatus?.[0]
        ? Number(searchParams.reviewStatus[0])
        : undefined,
      tags: searchParams.tags
        ? searchParams.tags.split(/,|，/).map((tag) => tag.trim())
        : undefined,
    })
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
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 图片已删除')
      await fetchData()
    } else {
      message.error('😅 删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 删除失败')
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
      message.success('✓ 审核操作成功')
      await fetchData()
    } else {
      message.error('😅 审核操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 审核操作失败')
  }
}

const parseTags = (tags: string) => {
  try {
    return JSON.parse(tags || '[]')
  } catch {
    return []
  }
}

const getReviewStatusClass = (status: number) => {
  const classMap: Record<number, string> = {
    [PIC_REVIEW_STATUS_ENUM.REVIEWING]: 'sunshine',
    [PIC_REVIEW_STATUS_ENUM.PASS]: 'mint',
    [PIC_REVIEW_STATUS_ENUM.REJECT]: 'coral',
  }
  return classMap[status] || 'coral'
}

const goToCreate = () => {
  router.push('/add_picture')
}

const goToBatchCreate = () => {
  router.push('/add_picture/batch')
}

const goToEdit = (id: number) => {
  router.push(`/add_picture?id=${id}`)
}
</script>

<style scoped lang="less">
#pictureManagePage {
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
  min-width: 180px;
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
  overflow-x: auto;

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
    overflow-x: auto;
  }

  :deep(.ant-table-thead > tr > th) {
    background: var(--gradient-fresh);
    border-bottom: 3px solid var(--border-bold);
    color: white;
    font-weight: 700;
    padding: var(--space-4);
    text-align: center;
    white-space: nowrap;
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

  :deep(.ant-table-placeholder) {
    .ant-empty-description {
      color: var(--text-tertiary);
      font-weight: 600;
    }
  }
}

/* ========== 表格内容 ========== */
.table-image {
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 3px solid var(--border-bold);
}

.pic-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: center;
}

.info-item {
  font-size: var(--text-xs);
  padding: 2px 8px;
}

.review-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: center;
}

.review-tag {
  font-weight: 700;
  padding: 4px 10px;
}

.review-message {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-time {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-family: var(--font-mono);
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
  font-size: var(--text-sm);

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
      justify-content: flex-start;
    }
  }
}

@media (max-width: 768px) {
  #pictureManagePage {
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
