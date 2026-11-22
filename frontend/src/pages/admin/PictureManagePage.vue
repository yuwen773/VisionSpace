<template>
  <div id="pictureManagePage">
    <!-- 页面头部 -->
    <AdminPageHeader title="图片管理" subtitle="管理平台所有图片和审核状态" size="md">
      <template #icon>
        <Image :size="19" :stroke-width="1.6" />
      </template>
    </AdminPageHeader>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="search-box">
        <svg
          class="search-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
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
    <div class="batch-toolbar" v-if="selectedIds.length > 0">
      <div class="batch-info">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 11 12 14 22 4"></polyline>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
        </svg>
        <span
          >已选择 <strong>{{ selectedIds.length }}</strong> 项</span
        >
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

    <!-- 图片卡片网格 -->
    <div class="picture-grid" v-if="dataList.length > 0">
      <div
        v-for="record in dataList"
        :key="record.id"
        class="picture-card"
        :class="{ selected: record.id !== undefined && selectedIds.includes(record.id) }"
      >
        <div class="card-checkbox">
          <a-checkbox
            :checked="record.id !== undefined && selectedIds.includes(record.id)"
            @change="
              (e: any) => record.id !== undefined && toggleSelect(record.id, e.target.checked)
            "
          />
        </div>

        <div class="card-image">
          <!-- 管理列表只返回元数据；审核需要看图时走受控内容入口，不拿源站直链 -->
          <div v-if="!record.id" class="card-image-placeholder">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
              <circle cx="8.5" cy="8.5" r="1.5"></circle>
              <polyline points="21 15 16 10 5 21"></polyline>
            </svg>
            <span>无图片标识</span>
          </div>
          <a-image
            v-else
            :src="contentSrc(`/picture/content/${record.id}/thumbnail`)"
            class="picture-img"
            :preview="true"
            :fallback="FALLBACK_IMAGE"
            decoding="async"
          />
          <div class="format-badge">{{ record.picFormat?.toUpperCase() }}</div>
          <div class="status-badge" :class="getStatusClass(record.reviewStatus ?? 0)">
            {{
              record.reviewStatus !== undefined ? PIC_REVIEW_STATUS_MAP[record.reviewStatus] : ''
            }}
          </div>
        </div>

        <div class="card-info">
          <div class="info-header">
            <h3 class="picture-name" :title="record.name">{{ record.name || '未命名' }}</h3>
          </div>

          <p class="picture-intro" v-if="record.introduction">{{ record.introduction }}</p>

          <div class="info-tags" v-if="record.category || parseTags(record.tags).length">
            <a-tag v-if="record.category" color="blue" class="category-tag">{{
              record.category
            }}</a-tag>
            <a-tag v-for="tag in parseTags(record.tags).slice(0, 2)" :key="tag" class="tag-item">{{
              tag
            }}</a-tag>
            <span v-if="parseTags(record.tags).length > 2" class="tag-more"
              >+{{ parseTags(record.tags).length - 2 }}</span
            >
          </div>

          <div class="specs-row">
            <span class="spec-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
              </svg>
              {{ record.picWidth }} × {{ record.picHeight }}
            </span>
            <span class="spec-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="7 10 12 15 17 10"></polyline>
                <line x1="12" y1="15" x2="12" y2="3"></line>
              </svg>
              {{ formatSize(record.picSize ?? 0) }}
            </span>
          </div>

          <div class="stats-row">
            <span class="stat-item" title="点赞">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
                ></path>
              </svg>
              {{ record.likeCount || 0 }}
            </span>
            <span class="stat-item" title="收藏">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon
                  points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
                ></polygon>
              </svg>
              {{ record.collectCount || 0 }}
            </span>
            <span class="stat-item" title="下载">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="7 10 12 15 17 10"></polyline>
                <line x1="12" y1="15" x2="12" y2="3"></line>
              </svg>
              {{ record.downloadCount || 0 }}
            </span>
            <span class="stat-item" title="浏览">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              {{ record.viewCount || 0 }}
            </span>
          </div>

          <div class="card-actions">
            <span class="create-time">{{
              dayjs(record.createTime).format('YYYY-MM-DD HH:mm')
            }}</span>
            <div class="action-btns">
              <a-tooltip
                :title="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '已通过' : '编辑'"
              >
                <button
                  class="action-btn"
                  aria-label="编辑图片"
                  :class="{ disabled: record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS }"
                  :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS"
                  @click="record.id !== undefined && goToEdit(record.id)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip
                :title="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '已通过' : '通过'"
              >
                <button
                  class="action-btn success"
                  aria-label="通过审核"
                  :class="{ disabled: record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS }"
                  :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS"
                  @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                </button>
              </a-tooltip>
              <a-tooltip
                :title="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT ? '已拒绝' : '拒绝'"
              >
                <button
                  class="action-btn danger"
                  aria-label="拒绝审核"
                  :class="{
                    disabled:
                      record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT ||
                      record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS,
                  }"
                  :disabled="
                    record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT ||
                    record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS
                  "
                  @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                </button>
              </a-tooltip>
              <a-popconfirm
                title="删除该图片？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
                placement="topRight"
              >
                <a-tooltip title="删除">
                  <button class="action-btn danger" aria-label="删除图片">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path
                        d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                      ></path>
                    </svg>
                  </button>
                </a-tooltip>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <a-empty v-else-if="!loading" description="暂无图片" class="empty-state" />

    <!-- 加载状态 -->
    <div class="loading-wrapper" v-if="loading">
      <a-spin size="large" />
    </div>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <a-pagination
        v-model:current="searchParams.current"
        :page-size="searchParams.pageSize"
        :total="Number(total)"
        @change="handlePageChange"
        show-quick-jumper
        show-size-changer
        :page-size-options="['10', '20', '40', '60']"
        :show-total="(total: number) => `共 ${total} 张图片`"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { Image } from 'lucide-vue-next'
import { ref, reactive, onMounted } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { listSpaceByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { PIC_REVIEW_STATUS_ENUM, PIC_REVIEW_STATUS_MAP } from '@/constants/picture.ts'
import { contentSrc, formatSize } from '@/utils'

// 内容入口可能对无权限或无资源记录的图片返回 403/404，用中性的纸面占位兜底。
// 占位本身也做客制化：一个细描边的空画框 + 双语小字，深浅主题下都不突兀。
const FALLBACK_IMAGE =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    [
      '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="320" viewBox="0 0 400 320">',
      '<g fill="none" stroke="#9b93b8" stroke-opacity="0.45" stroke-width="1.2">',
      '<rect x="164" y="118" width="72" height="56" rx="2"/>',
      '<path d="M164 158l18-16 14 12 10-8 30 26"/>',
      '<circle cx="216" cy="134" r="4"/>',
      '</g>',
      '<text x="200" y="200" text-anchor="middle" fill="#9b93b8" font-family="sans-serif" font-size="12">无可用预览</text>',
      '<text x="200" y="218" text-anchor="middle" fill="#9b93b8" fill-opacity="0.55" font-family="monospace" font-size="8" letter-spacing="2">NO PREVIEW</text>',
      '</svg>',
    ].join(''),
  )

const router = useRouter()

// 管理端列表走 /picture/list/page，只返回元数据（不含原图、缩略图与预览地址）；
// 统计字段由图片统计接口单独提供，这里声明为可选，避免伪造出「一定存在」的类型。
type AdminPictureRow = API.Picture & {
  likeCount?: number
  collectCount?: number
  downloadCount?: number
  viewCount?: number
}

const dataList = ref<AdminPictureRow[]>([])
const spaceList = ref<API.Space[]>([])
const total = ref<number>(0)
const loading = ref(false)
const selectedIds = ref<number[]>([])

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 20,
  sortOrder: 'descend',
  sortField: 'createTime',
})

// 空间下拉要取全量：只取第一页 100 条时，空间一多就有选项静默消失，筛选永远找不到。
// 逐页取到 total 为止，并留一个上限避免异常数据把请求打爆。
const SPACE_OPTION_PAGE_SIZE = 100
const SPACE_OPTION_MAX_PAGES = 20

const fetchSpaceList = async () => {
  try {
    const all: API.Space[] = []
    for (let page = 1; page <= SPACE_OPTION_MAX_PAGES; page += 1) {
      const res = await listSpaceByPageUsingPost({
        current: page,
        pageSize: SPACE_OPTION_PAGE_SIZE,
      })
      if (res.data.code !== 0 || !res.data.data) break
      const records = res.data.data.records ?? []
      all.push(...records)
      const total = Number(res.data.data.total ?? 0)
      if (records.length === 0 || all.length >= total) break
    }
    spaceList.value = all
  } catch (error) {
    console.error('获取空间列表失败', error)
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureByPageUsingPost({
      ...searchParams,
      reviewStatus: searchParams.reviewStatus ? Number(searchParams.reviewStatus) : undefined,
      tags: searchParams.tags?.length ? searchParams.tags : undefined,
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

const handlePageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  if (pageSize !== searchParams.pageSize) {
    searchParams.pageSize = pageSize
    searchParams.current = 1
  }
  selectedIds.value = []
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  selectedIds.value = []
  fetchData()
}

const resetSearch = () => {
  searchParams.searchText = undefined
  searchParams.spaceId = undefined
  searchParams.reviewStatus = undefined
  searchParams.category = undefined
  searchParams.tags = undefined
  searchParams.current = 1
  selectedIds.value = []
  fetchData()
}

const toggleSelect = (id: number, checked: boolean) => {
  if (checked) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((k) => k !== id)
  }
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('图片已删除')
      selectedIds.value = selectedIds.value.filter((k) => k !== id)
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
  if (!selectedIds.value.length) return
  try {
    const promises = selectedIds.value.map((id) =>
      doPictureReviewUsingPost({
        id,
        reviewStatus,
        reviewMessage,
      }),
    )
    const results = await Promise.allSettled(promises)
    const failed = results.filter((r) => r.status === 'rejected' || r.value?.data?.code !== 0)
    if (failed.length === 0) {
      message.success(`已审核 ${selectedIds.value.length} 张图片`)
      selectedIds.value = []
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

// 标签可能是数组，也可能是 JSON / 逗号分隔的字符串，统一归一化后再渲染
const parseTags = (tags: unknown): string[] => {
  if (Array.isArray(tags)) return tags.map(String).filter(Boolean)
  if (typeof tags !== 'string' || !tags.trim()) return []
  try {
    const parsed = JSON.parse(tags)
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
  } catch {
    // 非 JSON，按逗号分隔处理
  }
  return tags
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
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
  max-width: 1440px;
  margin: 0 auto;
  padding: 0;
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
    color: var(--admin-text-tertiary);
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding-left: 36px;
    background: transparent;
    border: 1px solid var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 2px var(--admin-primary-bg);
    }
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

.category-input {
  width: 130px;
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

  &.primary {
    color: var(--admin-accent-ink);
    background: var(--admin-primary);
    border-color: var(--admin-primary);

    &:hover {
      background: var(--admin-primary-hover);
      border-color: var(--admin-primary-hover);
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
  background: var(--admin-primary-bg);
  border: 1px solid color-mix(in srgb, var(--admin-primary) 28%, transparent);
  border-radius: var(--admin-radius-sm);
  animation: slideUp 0.3s var(--admin-ease);
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
  height: 32px;
  padding: 0 var(--admin-space-3);
  font-size: var(--admin-text-sm);
  font-weight: 600;
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition:
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast),
    opacity var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &.success {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border: 1px solid color-mix(in srgb, var(--admin-success) 45%, transparent);

    &:hover {
      background: color-mix(in srgb, var(--admin-success) 18%, transparent);
      border-color: var(--admin-success);
    }
  }

  &.danger {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border: 1px solid color-mix(in srgb, var(--admin-danger) 45%, transparent);

    &:hover {
      background: color-mix(in srgb, var(--admin-danger) 18%, transparent);
      border-color: var(--admin-danger);
    }
  }
}

/* ========== 图片卡片网格 ========== */
.picture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-6);
  counter-reset: plate;
}

.picture-card {
  position: relative;
  counter-increment: plate;
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  overflow: hidden;
  transition:
    border-color var(--admin-transition-fast),
    transform var(--admin-transition-base),
    box-shadow var(--admin-transition-base);

  &:hover {
    border-color: var(--admin-border-strong);
    transform: translateY(-2px);
    box-shadow: var(--admin-shadow-md);
  }

  &.selected {
    border-color: var(--admin-primary);
    box-shadow: 0 0 0 1px var(--admin-primary);
  }
}

.card-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  padding: 2px;
  background: color-mix(in srgb, var(--admin-bg-primary) 88%, transparent);
  border-radius: var(--admin-radius-sm);
  backdrop-filter: blur(4px);
}

.card-image {
  position: relative;

  .card-image-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    color: var(--admin-text-tertiary, #8a8a8a);
    font-size: 12px;

    svg {
      width: 32px;
      height: 32px;
      opacity: 0.6;
    }
  }

  width: 100%;
  height: 176px;
  background: var(--admin-bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  &::before {
    /* 底部压一层淡影，保证 PLATE 与格式徽标在浅色照片上仍可读 */
    content: '';
    position: absolute;
    inset: auto 0 0;
    height: 64px;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.42), transparent);
    pointer-events: none;
  }

  &::after {
    content: 'PLATE ' counter(plate, decimal-leading-zero);
    position: absolute;
    left: 9px;
    bottom: 8px;
    font-family: var(--admin-font-mono);
    font-size: 8px;
    letter-spacing: 0.16em;
    color: rgba(255, 255, 255, 0.8);
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
    pointer-events: none;
  }

  :deep(.ant-image) {
    width: 100%;
    height: 100%;
  }

  :deep(.ant-image-img) {
    object-fit: cover;
    width: 100%;
    height: 100%;
  }
}

.format-badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  padding: 1px 6px;
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: var(--admin-radius-sm);
  backdrop-filter: blur(4px);
}

.status-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 600;
  border-radius: var(--admin-radius-sm);

  &.reviewing {
    color: var(--admin-accent-ink);
    background: var(--admin-warning);
  }

  &.approved {
    color: var(--admin-accent-ink);
    background: var(--admin-success);
  }

  &.rejected {
    color: var(--admin-accent-ink);
    background: var(--admin-danger);
  }
}

.card-info {
  padding: var(--admin-space-3);
}

.info-header {
  margin-bottom: 8px;
}

.picture-name {
  font-size: var(--admin-text-sm);
  font-weight: 600;
  color: var(--admin-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.picture-intro {
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
  margin: 0 0 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;

  :deep(.ant-tag) {
    margin: 0;
    font-size: 10px;
    padding: 0 5px;
    line-height: 18px;
    border-radius: var(--admin-radius-sm);
  }

  .category-tag {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
    border-color: color-mix(in srgb, var(--admin-primary) 24%, transparent);
  }

  .tag-item {
    background: var(--admin-warning-bg);
    color: var(--admin-warning);
    border-color: color-mix(in srgb, var(--admin-warning) 28%, transparent);
  }

  .tag-more {
    font-size: 10px;
    color: var(--admin-text-tertiary);
    line-height: 18px;
  }
}

.specs-row {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.spec-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--admin-text-tertiary);

  svg {
    width: 12px;
    height: 12px;
  }
}

.stats-row {
  display: flex;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--admin-border-subtle);
  margin-bottom: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--admin-text-tertiary);

  svg {
    width: 12px;
    height: 12px;
  }
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--admin-border-subtle);
}

.create-time {
  font-size: 11px;
  color: var(--admin-text-disabled);
}

.action-btns {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover:not(.disabled) {
    color: var(--admin-primary);
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-default);
  }

  &.success:hover:not(.disabled) {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border-color: color-mix(in srgb, var(--admin-success) 30%, transparent);
  }

  &.danger:hover:not(.disabled) {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: color-mix(in srgb, var(--admin-danger) 30%, transparent);
  }

  &.disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

/* ========== 空状态 & 加载 ========== */
.empty-state {
  padding: 60px 0;
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px 0;
}

/* ========== 分页 ========== */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: var(--admin-space-4) 0;
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

  .picture-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

@media (max-width: 768px) {
  #pictureManagePage {
    padding: 0;
  }

  .picture-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: var(--admin-space-3);
  }

  .card-image {
    height: 140px;
  }
}

/* 手机上筛选区按整行铺开：两个下拉各占一半，分类输入独占一行，按钮等宽铺满 */
@media (max-width: 640px) {
  .table-toolbar {
    gap: var(--admin-space-3);
  }

  .filter-selects {
    width: 100%;
    gap: var(--admin-space-2);
  }

  .filter-select {
    flex: 1 1 calc(50% - var(--admin-space-2) / 2);
    width: auto;
  }

  .category-input {
    flex: 1 1 100%;
    width: auto;
  }

  .filter-buttons {
    width: 100%;

    .toolbar-btn {
      flex: 1;
      justify-content: center;
    }
  }

  .picture-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
