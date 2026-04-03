<template>
  <div id="spaceDetailPage">
    <!-- 紫漾梦幻氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="floating-shapes">
        <div class="shape-dot dot-1"></div>
        <div class="shape-dot dot-2"></div>
        <div class="shape-dot dot-3"></div>
        <div class="shape-dot dot-4"></div>
        <div class="shape-dot dot-5"></div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航栏 -->
      <header class="detail-header">
        <button @click="goBack" class="back-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>

        <div class="header-actions">
          <button v-if="canUploadPicture" class="action-btn primary" @click="goToUpload">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            <span>创建图片</span>
          </button>
          <button v-if="canManageSpaceUsers" class="action-icon-btn" @click="goToUserManage" title="成员管理">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </button>
          <button v-if="canManageSpaceUsers" class="action-icon-btn" @click="goToAnalyze" title="空间分析">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
          </button>
          <button v-if="canEditPicture" class="action-icon-btn" @click="doBatchEdit" title="批量编辑">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
        </div>
      </header>

      <!-- 空间信息区域 -->
      <section class="space-hero">
        <div class="space-hero-bg">
          <div class="space-glow"></div>
        </div>
        <div class="space-hero-content">
          <div class="space-icon">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
              <circle cx="24" cy="24" r="22" stroke="url(#spaceGradZiyan)" stroke-width="2" stroke-dasharray="6 3"/>
              <circle cx="24" cy="24" r="14" fill="url(#spaceGradZiyan)" opacity="0.2"/>
              <circle cx="24" cy="24" r="8" fill="url(#spaceGradZiyan)" opacity="0.4"/>
              <circle cx="24" cy="24" r="3" fill="url(#spaceGradZiyan)"/>
              <defs>
                <linearGradient id="spaceGradZiyan" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#a855f7"/>
                  <stop offset="50%" stop-color="#ec4899"/>
                  <stop offset="100%" stop-color="#f472b6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div class="space-info">
            <h1 class="space-name">{{ space.spaceName }}</h1>
            <div class="space-meta">
              <span class="space-type-badge" :class="space.spaceType === 1 ? 'team' : 'private'">
                {{ SPACE_TYPE_MAP[space.spaceType] }}
              </span>
              <span class="space-intro" v-if="space.spaceDescription">{{ space.spaceDescription }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 统计卡片 -->
      <section class="stats-section">
        <div class="stats-grid">
          <div class="stat-card" :style="{ animationDelay: '0.1s' }">
            <div class="stat-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-value">{{ total }}</span>
              <span class="stat-label">图片数量</span>
            </div>
            <div class="stat-decoration">
              <div class="stat-orb"></div>
            </div>
          </div>

          <div class="stat-card" :style="{ animationDelay: '0.2s' }">
            <div class="stat-icon purple">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-value">{{ formatSize(space.totalSize || 0) }}</span>
              <span class="stat-label">已用空间</span>
            </div>
            <div class="stat-decoration">
              <div class="stat-orb purple"></div>
            </div>
          </div>

          <div class="stat-card" :style="{ animationDelay: '0.3s' }">
            <div class="stat-icon cyan">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                <path d="M2 17l10 5 10-5"/>
                <path d="M2 12l10 5 10-5"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-value">{{ formatSize(space.maxSize || 0) }}</span>
              <span class="stat-label">总空间</span>
            </div>
            <div class="stat-decoration">
              <div class="stat-orb cyan"></div>
            </div>
          </div>

          <div class="stat-card capacity-card" :style="{ animationDelay: '0.4s' }">
            <div class="capacity-ring">
              <svg viewBox="0 0 100 100" class="ring-svg">
                <circle cx="50" cy="50" r="42" class="ring-bg"/>
                <circle
                  cx="50"
                  cy="50"
                  r="42"
                  class="ring-progress"
                  :stroke-dasharray="`${getUsagePercent()} ${100 - getUsagePercent()} 100`"
                />
              </svg>
              <div class="ring-center">
                <span class="ring-value">{{ getUsagePercent() }}%</span>
                <span class="ring-label">使用率</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 搜索和筛选区域 -->
      <section class="search-section" :class="{ visible: isLoaded }">
        <div class="search-bar">
          <div class="search-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
          </div>
          <input
            v-model="searchText"
            type="text"
            placeholder="搜索图片名称、标签..."
            class="search-input"
            @keydown.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">
            <span>搜索</span>
          </button>
        </div>

        <div class="filter-row">
          <div class="filter-item">
            <span class="filter-label">按颜色筛选</span>
            <color-picker format="hex" @pureColorChange="onColorChange" size="small" />
          </div>
          <button v-if="hasActiveFilters" class="clear-filters-btn" @click="clearFilters">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
            <span>清除筛选</span>
          </button>
        </div>
      </section>

      <!-- 图片画廊 -->
      <section class="gallery-section">
        <div class="gallery-header" v-if="!loading && dataList.length > 0">
          <div class="gallery-count">
            <span class="count-number">{{ total }}</span>
            <span class="count-label">张图片</span>
          </div>
          <div class="gallery-view-toggle">
            <button
              class="view-btn"
              :class="{ active: viewMode === 'grid' }"
              @click="viewMode = 'grid'"
              title="网格视图"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7"/>
                <rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/>
                <rect x="3" y="14" width="7" height="7"/>
              </svg>
            </button>
            <button
              class="view-btn"
              :class="{ active: viewMode === 'masonry' }"
              @click="viewMode = 'masonry'"
              title="瀑布流视图"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="8" y1="6" x2="21" y2="6"/>
                <line x1="8" y1="12" x2="21" y2="12"/>
                <line x1="8" y1="18" x2="21" y2="18"/>
                <line x1="3" y1="6" x2="3.01" y2="6"/>
                <line x1="3" y1="12" x2="3.01" y2="12"/>
                <line x1="3" y1="18" x2="3.01" y2="18"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <div class="loader-orb">
            <div class="orb-inner"></div>
            <div class="orb-ring"></div>
            <div class="orb-ring ring-2"></div>
          </div>
          <span class="loader-text">正在加载精彩内容 ✨</span>
        </div>

        <!-- 网格视图 -->
        <div v-else-if="viewMode === 'grid'" class="picture-grid">
          <div
            v-for="(picture, index) in dataList"
            :key="picture.id"
            class="gallery-item"
            :style="{ animationDelay: `${(index % 12) * 0.05}s` }"
            @click="handlePictureClick(picture)"
          >
            <div class="item-image-wrap" :style="{ paddingBottom: getAspectRatio(picture) }">
              <div class="item-skeleton" v-if="!loadedImages.has(picture.id)"></div>
              <img
                :src="picture.thumbnailUrl || picture.url"
                :alt="picture.name"
                class="item-image"
                :class="{ loaded: loadedImages.has(picture.id) }"
                @load="onImageLoad(picture.id)"
              />
              <div class="item-overlay">
                <div class="overlay-content">
                  <h3 class="item-title">{{ picture.name }}</h3>
                  <div class="item-meta">
                    <span>{{ picture.picWidth }}×{{ picture.picHeight }}</span>
                    <span class="meta-divider">•</span>
                    <span>{{ picture.picFormat?.toUpperCase() }}</span>
                  </div>
                </div>
                <div class="overlay-actions" v-if="canEditPicture || canDeletePicture">
                  <button class="action-btn-icon" @click.stop="doShare(picture)" v-if="showOp" title="分享">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="18" cy="5" r="3"/>
                      <circle cx="6" cy="12" r="3"/>
                      <circle cx="18" cy="19" r="3"/>
                      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                    </svg>
                  </button>
                  <button class="action-btn-icon" @click.stop="doEdit(picture)" v-if="showOp && canEditPicture" title="编辑">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button class="action-btn-icon delete" @click.stop="doDelete(picture)" v-if="showOp && canDeletePicture" title="删除">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 瀑布流视图 -->
        <div v-else class="masonry-wrapper">
          <div class="masonry-column" v-for="col in 4" :key="col">
            <div
              v-for="picture in getColumnPictures(col - 1)"
              :key="picture.id"
              class="gallery-item masonry-item"
              @click="handlePictureClick(picture)"
            >
              <div class="item-image-wrap" :style="{ paddingBottom: getAspectRatio(picture) }">
                <div class="item-skeleton" v-if="!loadedImages.has(picture.id)"></div>
                <img
                  :src="picture.thumbnailUrl || picture.url"
                  :alt="picture.name"
                  class="item-image"
                  :class="{ loaded: loadedImages.has(picture.id) }"
                  @load="onImageLoad(picture.id)"
                />
                <div class="item-overlay">
                  <div class="overlay-content">
                    <h3 class="item-title">{{ picture.name }}</h3>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && dataList.length === 0" class="empty-state">
          <div class="empty-illustration">
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
              <circle cx="60" cy="60" r="50" stroke="var(--color-primary-light)" stroke-width="2" stroke-dasharray="8 4"/>
              <rect x="35" y="35" width="50" height="50" rx="8" stroke="var(--color-primary)" stroke-width="2"/>
              <circle cx="50" cy="50" r="6" fill="var(--color-primary)"/>
              <path d="M35 75 L55 55 L70 70 L85 55 L85 85 L35 85 Z" fill="var(--bg-tertiary)" stroke="var(--color-primary)" stroke-width="2"/>
            </svg>
          </div>
          <h3 class="empty-title">暂无图片</h3>
          <p class="empty-desc">这个空间还没有上传任何图片，开始你的创作吧 ✨</p>
          <button v-if="canUploadPicture" class="empty-action" @click="goToUpload">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            <span>上传图片</span>
          </button>
        </div>

        <!-- 分页 -->
        <div v-if="total > searchParams.pageSize" class="pagination-wrapper">
          <div class="pagination">
            <button class="page-btn" :disabled="searchParams.current <= 1" @click="prevPage">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 18l-6-6 6-6"/>
              </svg>
            </button>
            <div class="page-numbers">
              <button
                v-for="page in getVisiblePages()"
                :key="page"
                class="page-number"
                :class="{ active: page === searchParams.current, ellipsis: page === '...' }"
                :disabled="page === '...'"
                @click="page !== '...' && goToPage(page)"
              >
                {{ page }}
              </button>
            </div>
            <button class="page-btn" :disabled="searchParams.current >= getTotalPages()" @click="nextPage">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 18l6-6-6-6"/>
              </svg>
            </button>
          </div>
        </div>
      </section>
    </div>

    <!-- 批量编辑弹窗 -->
    <BatchEditPicture
      ref="batchEditPictureModalRef"
      :spaceId="id"
      :pictureList="dataList"
      :onSuccess="onBatchEditPictureSuccess"
    />

    <!-- 分享弹窗 -->
    <ShareModal ref="shareModalRef" :link="shareLink" title="分享图片" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { deletePictureUsingPost, searchPictureByColorUsingPost, listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { formatSize } from '@/utils'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'
import BatchEditPicture from '@/components/BatchEditPicture.vue'
import ShareModal from '@/components/ShareModal.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'

interface Props {
  id: number | string
}

const props = defineProps<Props>()
const router = useRouter()

// Refs
const batchEditPictureModalRef = ref()
const shareModalRef = ref()
const shareLink = ref('')

// State
const space = ref<API.SpaceVO>({})
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchText = ref('')
const isLoaded = ref(false)
const viewMode = ref<'grid' | 'masonry'>('grid')
const loadedImages = ref<Set<string>>(new Set())

// Search params
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 24,
  sortOrder: 'descend',
  sortField: 'createTime',
})

// Column assignment for masonry
const columnAssignment = ref<Map<string, number>>(new Map())
const columnHeights = ref<number[]>([0, 0, 0, 0])

// Permission checks
const createPermissionChecker = (permission: string) => {
  return computed(() => (space.value.permissionList ?? []).includes(permission))
}

const canManageSpaceUsers = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)
const showOp = computed(() => canEditPicture.value || canDeletePicture.value)

// Active filters check
const hasActiveFilters = computed(() => {
  return searchText.value || false
})

// Get usage percentage
const getUsagePercent = () => {
  if (!space.value.totalSize || !space.value.maxSize) return 0
  return Math.min(100, Math.round((space.value.totalSize / space.value.maxSize) * 100))
}

// Fetch space detail
const fetchDetailSpace = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({ id: props.id })
    if (res.data.code === 0) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败')
    }
  } catch (error: any) {
    message.error(error.message)
  }
}

// Fetch pictures
const fetchData = async () => {
  loading.value = true
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  try {
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0)
      assignPicturesToColumns(res.data.data.records ?? [])
    } else {
      message.error('获取数据失败')
    }
  } catch (error: any) {
    message.error(error.message)
  } finally {
    loading.value = false
    isLoaded.value = true
  }
}

// Column distribution for masonry
const getColumnPictures = (colIndex: number) => {
  return dataList.value.filter(picture => columnAssignment.value.get(picture.id) === colIndex)
}

const getShortestColumn = () => {
  return columnHeights.value.indexOf(Math.min(...columnHeights.value))
}

const getAspectRatio = (picture: any) => {
  if (!picture.picWidth || !picture.picHeight) return '75%'
  return `${(picture.picHeight / picture.picWidth) * 100}%`
}

const calculatePictureHeight = (picture: any): number => {
  if (!picture.picWidth || !picture.picHeight) return 300 * 0.75 + 60
  const estimatedCardWidth = (window.innerWidth - 96) / 4 - 20
  const ratio = picture.picHeight / picture.picWidth
  return estimatedCardWidth * ratio + 60
}

const assignPicturesToColumns = (pictures: any[]) => {
  if (viewMode.value !== 'masonry') return
  pictures.forEach(picture => {
    const shortestCol = getShortestColumn()
    columnAssignment.value.set(picture.id, shortestCol)
    const estimatedHeight = calculatePictureHeight(picture)
    columnHeights.value[shortestCol] += estimatedHeight + 20
  })
}

// Image load
const onImageLoad = (pictureId: string) => {
  loadedImages.value.add(pictureId)
}

// Navigation
const goBack = () => router.back()
const goToUpload = () => router.push(`/add_picture?spaceId=${props.id}`)
const goToUserManage = () => router.push(`/spaceUserManage/${props.id}`)
const goToAnalyze = () => router.push(`/space_analyze?spaceId=${props.id}`)

// Search
const doSearch = () => {
  searchParams.value = {
    ...searchParams.value,
    current: 1,
    name: searchText.value || undefined,
  }
  fetchData()
}

// Color search
const onColorChange = async (color: string) => {
  if (!color) return
  loading.value = true
  try {
    const res = await searchPictureByColorUsingPost({ picColor: color, spaceId: props.id })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data
      total.value = res.data.data.length
    } else {
      message.error('按颜色搜图出错')
    }
  } catch (error) {
    message.error('按颜色搜图出错')
  } finally {
    loading.value = false
  }
}

// Clear filters
const clearFilters = () => {
  searchText.value = ''
  searchParams.value.current = 1
  fetchData()
}

// Batch edit
const doBatchEdit = () => {
  batchEditPictureModalRef.value?.openModal()
}

const onBatchEditPictureSuccess = () => {
  fetchData()
}

// Picture actions
const handlePictureClick = (picture: any) => {
  router.push(`/picture/${picture.id}`)
}

const doShare = (picture: any) => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  shareModalRef.value?.openModal()
}

const doEdit = (picture: any) => {
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}

const doDelete = async (picture: any) => {
  const confirmed = await new Promise((resolve) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除图片 "${picture.name}" 吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
  if (!confirmed) return

  try {
    const res = await deletePictureUsingPost({ id: picture.id })
    if (res.data.code === 0) {
      message.success('图片已删除')
      fetchData()
    } else {
      message.error('删除失败')
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// Pagination
const getTotalPages = () => Math.ceil(total.value / searchParams.value.pageSize)

const getVisiblePages = () => {
  const total = getTotalPages()
  const current = searchParams.value.current
  const pages: (number | string)[] = []

  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else {
    if (current <= 3) {
      pages.push(1, 2, 3, 4, '...', total)
    } else if (current >= total - 2) {
      pages.push(1, '...', total - 3, total - 2, total - 1, total)
    } else {
      pages.push(1, '...', current - 1, current, current + 1, '...', total)
    }
  }
  return pages
}

const prevPage = () => {
  if (searchParams.value.current > 1) {
    searchParams.value.current--
    fetchData()
  }
}

const nextPage = () => {
  if (searchParams.value.current < getTotalPages()) {
    searchParams.value.current++
    fetchData()
  }
}

const goToPage = (page: number) => {
  searchParams.value.current = page
  fetchData()
}

onMounted(async () => {
  await fetchDetailSpace()
  fetchData()
})

watch(() => props.id, () => {
  fetchDetailSpace()
  fetchData()
})
</script>

<style scoped lang="less">
/* Aurora 主题变量别名 */
#spaceDetailPage {
  /* 颜色别名 - 修复未定义的变量 */
  --color-primary: var(--color-primary-500);
  --color-primary-light: var(--color-primary-300);
  --color-secondary: var(--color-secondary-500);
  --color-violet: var(--color-accent-purple);
  --color-pink: var(--color-accent-pink);
  --color-cyan: var(--color-accent-cyan);
  --color-primary-bg: var(--color-primary-500);

  /* 背景和卡片 */
  --bg-primary: var(--color-bg-primary);
  --bg-secondary: var(--color-bg-secondary);
  --bg-tertiary: var(--color-bg-tertiary);
  --bg-elevated: var(--color-bg-elevated);
  --bg-hover: var(--color-bg-hover);
  --bg-card: var(--gradient-card);

  /* 文字颜色 */
  --text-primary: var(--color-text-primary);
  --text-secondary: var(--color-text-secondary);
  --text-tertiary: var(--color-text-tertiary);
  --text-disabled: var(--color-text-disabled);

  /* 边框颜色 */
  --border-default: var(--color-border-default);
  --border-subtle: var(--color-border-subtle);
  --border-strong: var(--color-border-strong);

  /* 阴影 */
  --shadow-card: 0 4px 20px rgba(0, 0, 0, 0.4);
  --shadow-card-hover: 0 8px 30px rgba(0, 0, 0, 0.5);

  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 紫漾氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.5;
  animation: float-orb 25s ease-in-out infinite;
}

.orb-1 {
  width: 700px;
  height: 700px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.25) 0%, transparent 70%);
  top: -250px;
  right: -150px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%);
  top: 30%;
  left: -200px;
  animation-delay: -8s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
  bottom: -100px;
  right: 10%;
  animation-delay: -16s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(40px, -40px) scale(1.05); }
  50% { transform: translate(-30px, 30px) scale(0.95); }
  75% { transform: translate(25px, 15px) scale(1.03); }
}

/* 浮动装饰点 */
.floating-shapes {
  position: absolute;
  inset: 0;
}

.shape-dot {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
  animation: float-dot 15s ease-in-out infinite;
}

.dot-1 {
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  top: 15%;
  left: 10%;
  animation-delay: 0s;
}

.dot-2 {
  width: 8px;
  height: 8px;
  background: var(--color-secondary);
  top: 25%;
  right: 20%;
  animation-delay: -3s;
}

.dot-3 {
  width: 16px;
  height: 16px;
  background: var(--color-violet);
  bottom: 30%;
  left: 15%;
  animation-delay: -6s;
}

.dot-4 {
  width: 10px;
  height: 10px;
  background: var(--color-pink);
  top: 60%;
  right: 25%;
  animation-delay: -9s;
}

.dot-5 {
  width: 6px;
  height: 6px;
  background: var(--color-primary-light);
  bottom: 20%;
  right: 40%;
  animation-delay: -12s;
}

@keyframes float-dot {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  25% { transform: translate(10px, -15px) rotate(90deg); }
  50% { transform: translate(-5px, 10px) rotate(180deg); }
  75% { transform: translate(15px, 5px) rotate(270deg); }
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 32px 80px;
}

/* ========== 顶部导航栏 ========== */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateX(-4px);
    box-shadow: var(--shadow-glow-purple);

    svg {
      transform: translateX(-2px);
    }
  }

  svg {
    transition: transform 0.3s ease;
  }
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 40px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;

  &.primary {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    color: white;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);
    }
  }
}

.action-icon-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 12px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateY(-2px);
    box-shadow: var(--shadow-glow-purple);
  }
}

/* ========== 空间信息区域 ========== */
.space-hero {
  position: relative;
  padding: 48px 0;
  margin-bottom: 32px;
}

.space-hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.space-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 600px;
  height: 300px;
  background: radial-gradient(ellipse, rgba(168, 85, 247, 0.15) 0%, rgba(236, 72, 153, 0.08) 40%, transparent 70%);
  filter: blur(40px);
}

.space-hero-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: 24px;
  animation: fade-up 0.8s ease-out forwards;
}

.space-icon {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 20px;
  box-shadow: var(--shadow-card);
  animation: pulse-glow 3s ease-in-out infinite;

  svg {
    filter: drop-shadow(0 0 15px rgba(168, 85, 247, 0.3));
  }
}

@keyframes pulse-glow {
  0%, 100% { transform: scale(1); box-shadow: var(--shadow-card); }
  50% { transform: scale(1.03); box-shadow: var(--shadow-glow-purple); }
}

.space-info {
  flex: 1;
}

.space-name {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 12px 0;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 50%, var(--color-pink) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.space-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.space-type-badge {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;

  &.private {
    background: rgba(168, 85, 247, 0.1);
    color: var(--color-primary);
    border: 1px solid rgba(168, 85, 247, 0.2);
  }

  &.team {
    background: rgba(236, 72, 153, 0.1);
    color: var(--color-secondary);
    border: 1px solid rgba(236, 72, 153, 0.2);
  }
}

.space-intro {
  font-size: 14px;
  color: var(--text-tertiary);
}

/* ========== 统计卡片 ========== */
.stats-section {
  margin-bottom: 40px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.stat-card {
  position: relative;
  padding: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  animation: card-reveal 0.6s ease-out forwards;
  opacity: 0;
  transform: translateY(20px);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-4px);
  }
}

@keyframes card-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.1);
  border-radius: 14px;
  color: var(--color-primary);
  flex-shrink: 0;

  &.purple {
    background: rgba(168, 85, 247, 0.15);
    color: var(--color-primary);
  }

  &.cyan {
    background: rgba(34, 211, 238, 0.1);
    color: var(--color-cyan);
  }
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  z-index: 1;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
}

.stat-decoration {
  position: absolute;
  right: -20px;
  bottom: -20px;
  pointer-events: none;
}

.stat-orb {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%);

  &.purple {
    background: radial-gradient(circle, rgba(168, 85, 247, 0.25) 0%, transparent 70%);
  }

  &.cyan {
    background: radial-gradient(circle, rgba(34, 211, 238, 0.2) 0%, transparent 70%);
  }
}

/* Capacity card */
.capacity-card {
  padding: 20px;
}

.capacity-ring {
  position: relative;
  width: 100px;
  height: 100px;
  margin: 0 auto;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: var(--border-default);
  stroke-width: 8;
}

.ring-progress {
  fill: none;
  stroke: var(--color-primary);
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dasharray 0.5s ease;
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-value {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.ring-label {
  font-size: 10px;
  color: var(--text-tertiary);
}

/* ========== 搜索区域 ========== */
.search-section {
  margin-bottom: 32px;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s ease;

  &.visible {
    opacity: 1;
    transform: translateY(0);
  }
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 6px 6px 20px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 60px;
  margin-bottom: 16px;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:focus-within {
    border-color: var(--color-primary);
    box-shadow: var(--shadow-glow-purple);
  }
}

.search-icon {
  color: var(--text-tertiary);
  display: flex;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 16px;
  color: var(--text-primary);
  min-width: 0;

  &::placeholder {
    color: var(--text-tertiary);
  }
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);
  }
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
}

.clear-filters-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 20px;
  color: #dc2626;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(239, 68, 68, 0.15);
    transform: translateY(-1px);
  }
}

/* ========== 画廊区域 ========== */
.gallery-section {
  min-height: 400px;
}

.gallery-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
}

.gallery-count {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.count-number {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.count-label {
  font-size: 14px;
  color: var(--text-tertiary);
}

.gallery-view-toggle {
  display: flex;
  gap: 8px;
}

.view-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.active {
    background: rgba(168, 85, 247, 0.1);
    border-color: var(--color-primary);
    color: var(--color-primary);
  }
}

/* ========== 加载状态 ========== */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 80px 40px;
}

.loader-orb {
  position: relative;
  width: 60px;
  height: 60px;
}

.orb-inner {
  position: absolute;
  inset: 15px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-secondary));
  border-radius: 50%;
  animation: pulse-orb 1.5s ease-in-out infinite;
  box-shadow: 0 0 30px rgba(168, 85, 247, 0.5);
}

.orb-ring {
  position: absolute;
  inset: 0;
  border: 2px solid transparent;
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin-ring 1.2s linear infinite;
  filter: drop-shadow(0 0 10px rgba(168, 85, 247, 0.5));

  &.ring-2 {
    inset: 5px;
    border-top-color: var(--color-secondary);
    animation-duration: 1.8s;
    animation-direction: reverse;
    filter: drop-shadow(0 0 10px rgba(236, 72, 153, 0.5));
  }
}

@keyframes pulse-orb {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(0.8); opacity: 0.6; }
}

@keyframes spin-ring {
  to { transform: rotate(360deg); }
}

.loader-text {
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: 1px;
}

/* ========== 图片网格 ========== */
.picture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.gallery-item {
  opacity: 0;
  animation: item-reveal 0.6s ease-out forwards;
  position: relative;
  cursor: pointer;

  &:hover {
    .item-overlay {
      opacity: 1;
    }
    .item-image {
      transform: scale(1.05);
    }
  }
}

@keyframes item-reveal {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.item-image-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 16px;
  background: var(--bg-tertiary);
  box-shadow: var(--shadow-sm);
}

.item-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, var(--bg-tertiary) 0%, var(--bg-hover) 50%, var(--bg-tertiary) 100%);
  background-size: 200% 100%;
  animation: skeleton 1.5s infinite;
}

@keyframes skeleton {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.item-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.4s ease, transform 0.4s ease;

  &.loaded {
    opacity: 1;
  }
}

.item-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(30, 27, 75, 0.95) 0%, rgba(30, 27, 75, 0.6) 40%, rgba(30, 27, 75, 0.2) 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 20px;
}

.overlay-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: white;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-divider {
  opacity: 0.5;
}

.overlay-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.action-btn-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.25);
    transform: scale(1.1);
  }

  &.delete:hover {
    background: rgba(239, 68, 68, 0.4);
    border-color: rgba(239, 68, 68, 0.5);
  }
}

/* ========== 瀑布流视图 ========== */
.masonry-wrapper {
  display: flex;
  gap: 20px;
}

.masonry-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.masonry-item {
  break-inside: avoid;
}

/* ========== 空状态 ========== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 40px;
  text-align: center;
}

.empty-illustration {
  margin-bottom: 32px;
  opacity: 0.8;
  animation: float-illustration 3s ease-in-out infinite;
  filter: drop-shadow(0 0 20px rgba(168, 85, 247, 0.2));
}

@keyframes float-illustration {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.empty-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 12px 0;
}

.empty-desc {
  font-size: 15px;
  color: var(--text-tertiary);
  margin: 0 0 32px 0;
  max-width: 300px;
}

.empty-action {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 32px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 25px rgba(168, 85, 247, 0.35);

  &:hover {
    transform: translateY(-3px) scale(1.02);
    box-shadow: 0 8px 35px rgba(168, 85, 247, 0.45);
  }
}

/* ========== 分页 ========== */
.pagination-wrapper {
  margin-top: 48px;
  display: flex;
  justify-content: center;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  box-shadow: var(--shadow-sm);
}

.page-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    color: var(--text-primary);
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.page-number {
  min-width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled):not(.active) {
    background: var(--bg-hover);
    transform: translateY(-1px);
  }

  &.active {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    border-color: transparent;
    color: white;
    box-shadow: 0 4px 15px rgba(168, 85, 247, 0.3);
  }

  &.ellipsis {
    cursor: default;
    border: none;
    background: transparent;
  }

  &:disabled {
    cursor: not-allowed;
  }
}

/* ========== 动画 ========== */
@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .main-content {
    padding: 0 16px 60px;
  }

  .space-hero-content {
    flex-direction: column;
    text-align: center;
  }

  .space-name {
    font-size: 28px;
  }

  .space-meta {
    flex-direction: column;
    gap: 8px;
  }

  .detail-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .picture-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .search-bar {
    flex-wrap: wrap;
  }

  .search-btn {
    width: 100%;
    justify-content: center;
  }

  .gallery-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .masonry-wrapper {
    flex-direction: column;
    gap: 12px;
  }

  .pagination-wrapper {
    overflow-x: auto;
  }
}
</style>
