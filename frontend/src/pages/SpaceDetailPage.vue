<template>
  <div id="spaceDetailPage" class="aw-page" :class="{ 'is-light': isLight }">
    <!-- 氛围层 -->
    <div class="aw-atmosphere" aria-hidden="true">
      <div class="aw-aurora aw-aurora-a"></div>
      <div class="aw-aurora aw-aurora-b"></div>
    </div>

    <div class="aw-shell sd-shell">
      <!-- 顶部操作条 -->
      <header class="sd-bar">
        <button class="aw-back" type="button" @click="goBack">
          <ArrowLeft :size="14" :stroke-width="1.8" />
          <span>返回</span>
        </button>

        <div class="sd-bar-actions">
          <button
            v-if="canManageSpaceUsers"
            class="aw-icon-btn"
            type="button"
            title="成员管理"
            aria-label="成员管理"
            @click="goToUserManage"
          >
            <Users :size="17" :stroke-width="1.7" />
          </button>
          <button
            v-if="canManageSpaceUsers"
            class="aw-icon-btn"
            type="button"
            title="空间分析"
            aria-label="空间分析"
            @click="goToAnalyze"
          >
            <ChartColumn :size="17" :stroke-width="1.7" />
          </button>
          <button
            v-if="canEditPicture"
            class="aw-icon-btn"
            type="button"
            title="批量编辑"
            aria-label="批量编辑"
            @click="doBatchEdit"
          >
            <PencilLine :size="17" :stroke-width="1.7" />
          </button>
          <button
            v-if="canUploadPicture"
            class="aw-btn aw-btn--primary sd-create"
            type="button"
            @click="goToUpload"
          >
            <Plus :size="16" :stroke-width="1.9" />
            <span>创建图片</span>
          </button>
        </div>
      </header>

      <!-- 空间档案 -->
      <section class="sd-hero" aria-labelledby="sd-space-title">
        <div class="sd-hero-copy">
          <div class="sd-hero-head">
            <p class="aw-kicker">SPACE ARCHIVE — 空间档案</p>
            <span class="aw-mono sd-hero-id">NO. {{ spaceNo }}</span>
          </div>

          <h1 id="sd-space-title" class="aw-display sd-title">
            {{ space.spaceName || '空间' }}
          </h1>

          <div class="sd-hero-meta">
            <span class="sd-type">{{ typeLabel }}</span>
            <span class="sd-meta-dot" aria-hidden="true"></span>
            <span>{{ total }} 件作品</span>
            <span class="sd-meta-dot" aria-hidden="true"></span>
            <span
              >{{ formatSize(space.totalSize || 0) }} / {{ formatSize(space.maxSize || 0) }}</span
            >
          </div>
        </div>

        <div class="sd-hero-plates" aria-hidden="true">
          <figure
            v-for="(pic, i) in heroPlates"
            :key="pic?.id ?? `empty-${i}`"
            class="sd-plate"
            :class="`sd-plate-${i + 1}`"
          >
            <img
              v-if="pic"
              :src="contentSrc(pic.thumbnailUrl || pic.url)"
              alt=""
              decoding="async"
            />
            <span v-else class="sd-plate-empty aw-mono">EMPTY</span>
            <figcaption class="aw-mono">
              {{ String(i + 1).padStart(2, '0') }} — {{ pic?.name?.slice(0, 14) || '待收录' }}
            </figcaption>
          </figure>
        </div>
      </section>

      <!-- 目录数据 -->
      <section class="sd-catalogue" aria-label="空间数据">
        <div class="sd-cat">
          <span class="aw-mono">WORKS</span>
          <p class="sd-cat-value">
            <b>{{ total }}</b>
            <span class="sd-cat-unit">件</span>
          </p>
        </div>
        <div class="sd-cat">
          <span class="aw-mono">USED</span>
          <p class="sd-cat-value">
            <b>{{ formatSize(space.totalSize || 0) }}</b>
          </p>
        </div>
        <div class="sd-cat">
          <span class="aw-mono">CAPACITY</span>
          <p class="sd-cat-value">
            <b>{{ formatSize(space.maxSize || 0) }}</b>
          </p>
        </div>
        <div class="sd-cat sd-cat--usage">
          <div class="sd-cat-head">
            <span class="aw-mono">USAGE</span>
            <b class="sd-usage-value">{{ usagePercent }}%</b>
          </div>
          <div
            class="sd-rail"
            role="progressbar"
            :aria-valuenow="usagePercent"
            aria-valuemin="0"
            aria-valuemax="100"
            aria-label="空间使用率"
          >
            <i :style="{ transform: `scaleX(${usagePercent / 100})` }"></i>
          </div>
        </div>
      </section>

      <!-- 检索与视图 -->
      <section class="sd-tools" aria-label="检索与视图">
        <div class="sd-search">
          <Search :size="16" :stroke-width="1.7" class="sd-search-icon" aria-hidden="true" />
          <input
            v-model="searchText"
            type="text"
            class="sd-search-input"
            placeholder="搜索作品名称…"
            aria-label="搜索作品名称"
            autocomplete="off"
            @keydown.enter="doSearch"
          />
          <button
            v-if="searchText"
            class="sd-search-clear"
            type="button"
            aria-label="清空搜索"
            @click="clearSearch"
          >
            <X :size="13" :stroke-width="2" />
          </button>
          <button class="sd-search-go" type="button" @click="doSearch">搜索</button>
        </div>

        <div class="sd-filter">
          <span class="aw-mono sd-filter-label">COLOR</span>
          <color-picker
            v-model:pureColor="pickerColor"
            format="hex"
            size="small"
            @pureColorChange="onColorChange"
          />
          <span v-if="activeColor" class="sd-color-chip">
            <i :style="{ background: activeColor }" aria-hidden="true"></i>
            <code>{{ activeColor }}</code>
          </span>
        </div>

        <div class="sd-view" role="group" aria-label="视图切换">
          <button
            type="button"
            :class="{ active: viewMode === 'grid' }"
            :aria-pressed="viewMode === 'grid'"
            title="网格视图"
            @click="setView('grid')"
          >
            <LayoutGrid :size="14" :stroke-width="1.8" />
            <span>GRID</span>
          </button>
          <button
            type="button"
            :class="{ active: viewMode === 'masonry' }"
            :aria-pressed="viewMode === 'masonry'"
            title="瀑布流视图"
            @click="setView('masonry')"
          >
            <Rows3 :size="14" :stroke-width="1.8" />
            <span>MASONRY</span>
          </button>
        </div>
      </section>

      <!-- 生效中的筛选 -->
      <div v-if="hasActiveFilters" class="sd-active">
        <span class="aw-mono">FILTER</span>
        <span v-if="searchText" class="sd-active-item">名称含「{{ searchText }}」</span>
        <span v-if="activeColor" class="sd-active-item">
          颜色 <code>{{ activeColor }}</code>
        </span>
        <button class="sd-clear" type="button" @click="clearFilters">
          <X :size="11" :stroke-width="2" />
          清除筛选
        </button>
      </div>

      <!-- 陈列 -->
      <section class="sd-gallery" aria-labelledby="sd-gallery-title">
        <header class="sd-gallery-head">
          <div class="sd-gallery-title-wrap">
            <span class="aw-index"><b>02</b> — 作品</span>
            <h2 id="sd-gallery-title" class="sd-gallery-title">陈列</h2>
          </div>
          <span class="aw-mono sd-gallery-count" aria-live="polite">
            {{ total }} WORKS · {{ viewMode === 'grid' ? 'GRID' : 'MASONRY' }}
          </span>
        </header>

        <!-- 加载骨架 -->
        <div v-if="loading" class="sd-skeleton" aria-busy="true" aria-label="正在加载作品">
          <div class="sd-skeleton-bar"><i></i></div>
          <div class="sd-skeleton-grid">
            <div v-for="n in 8" :key="n" class="sd-skeleton-tile"></div>
          </div>
        </div>

        <!-- 作品列表（网格 / 瀑布流共用一套结构） -->
        <div
          v-else-if="dataList.length"
          class="sd-list"
          :class="viewMode === 'grid' ? 'is-grid' : 'is-masonry'"
        >
          <article
            v-for="(picture, index) in dataList"
            :key="picture.id"
            class="sd-card"
            :style="{ '--i': index % 8 }"
            role="link"
            tabindex="0"
            :aria-label="`打开作品 ${picture.name || '未命名'}`"
            @click="handlePictureClick(picture)"
            @keydown.enter="handlePictureClick(picture)"
            @keydown.space.prevent="handlePictureClick(picture)"
          >
            <div class="sd-card-media" :style="{ paddingBottom: getAspectRatio(picture) }">
              <div class="sd-card-skeleton" :class="{ loaded: isLoadedImage(picture) }"></div>
              <img
                :src="contentSrc(picture.thumbnailUrl || picture.url)"
                :alt="picture.name || '作品'"
                class="sd-card-img"
                :class="{ loaded: isLoadedImage(picture) }"
                loading="lazy"
                decoding="async"
                @load="picture.id && onImageLoad(picture.id)"
              />

              <div class="sd-card-veil" aria-hidden="true"></div>

              <div class="sd-card-body">
                <h3 class="sd-card-title">{{ picture.name || '未命名作品' }}</h3>
                <p class="sd-card-meta aw-mono">
                  <span>{{ picture.picWidth }}×{{ picture.picHeight }}</span>
                  <i aria-hidden="true">/</i>
                  <span>{{ (picture.picFormat || 'IMG').toUpperCase() }}</span>
                </p>
              </div>

              <div v-if="showOp" class="sd-card-ops">
                <button
                  class="sd-card-op"
                  type="button"
                  title="分享"
                  aria-label="分享作品"
                  @click.stop="doShare(picture)"
                >
                  <Share2 :size="15" :stroke-width="1.8" />
                </button>
                <button
                  v-if="canEditPicture"
                  class="sd-card-op"
                  type="button"
                  title="编辑"
                  aria-label="编辑作品"
                  @click.stop="doEdit(picture)"
                >
                  <PencilLine :size="15" :stroke-width="1.8" />
                </button>
                <button
                  v-if="canDeletePicture"
                  class="sd-card-op sd-card-op--danger"
                  type="button"
                  title="删除"
                  aria-label="删除作品"
                  @click.stop="doDelete(picture)"
                >
                  <Trash2 :size="15" :stroke-width="1.8" />
                </button>
              </div>
            </div>
          </article>
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && dataList.length === 0" class="sd-empty">
          <span class="sd-empty-no aw-mono">— 00 —</span>
          <h3 class="sd-empty-title">
            {{ hasActiveFilters ? '没有匹配的作品。' : '这个展厅还空着。' }}
          </h3>
          <p class="sd-empty-desc">
            {{
              hasActiveFilters
                ? '换一个关键词，或清除筛选重新浏览。'
                : '上传第一张图片，让它成为这里的起点。'
            }}
          </p>
          <div class="sd-empty-actions">
            <button
              v-if="hasActiveFilters"
              class="aw-btn aw-btn--ghost"
              type="button"
              @click="clearFilters"
            >
              清除筛选
            </button>
            <button
              v-if="canUploadPicture && !hasActiveFilters"
              class="aw-btn aw-btn--primary"
              type="button"
              @click="goToUpload"
            >
              <Plus :size="16" :stroke-width="1.9" />
              <span>上传图片</span>
            </button>
          </div>
        </div>

        <!-- 分页 -->
        <nav v-if="total > pageSize" class="sd-pager" aria-label="分页">
          <button
            class="sd-page-arrow"
            type="button"
            :disabled="currentPage <= 1"
            aria-label="上一页"
            @click="prevPage"
          >
            <ChevronLeft :size="16" :stroke-width="1.8" />
          </button>

          <span class="sd-page-index aw-mono">
            PAGE {{ String(currentPage).padStart(2, '0') }} /
            {{ String(getTotalPages()).padStart(2, '0') }}
          </span>

          <div class="sd-page-numbers">
            <button
              v-for="page in getVisiblePages()"
              :key="page"
              class="sd-page-number"
              type="button"
              :class="{ active: page === currentPage, ellipsis: page === '...' }"
              :disabled="page === '...'"
              @click="page !== '...' && goToPage(page)"
            >
              {{ page === '...' ? '…' : String(page).padStart(2, '0') }}
            </button>
          </div>

          <button
            class="sd-page-arrow"
            type="button"
            :disabled="currentPage >= getTotalPages()"
            aria-label="下一页"
            @click="nextPage"
          >
            <ChevronRight :size="16" :stroke-width="1.8" />
          </button>
        </nav>
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  ArrowLeft,
  ChartColumn,
  ChevronLeft,
  ChevronRight,
  LayoutGrid,
  PencilLine,
  Plus,
  Rows3,
  Search,
  Share2,
  Trash2,
  Users,
  X,
} from 'lucide-vue-next'
import {
  deletePictureUsingPost,
  searchPictureByColorUsingPost,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { contentSrc, formatSize, toId } from '@/utils'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'
import { useTheme } from '@/composables/useTheme'
import BatchEditPicture from '@/components/BatchEditPicture.vue'
import ShareModal from '@/components/ShareModal.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'

interface Props {
  id: number | string
}

const props = defineProps<Props>()
const spaceId = computed(() => toId(props.id))
const router = useRouter()
const { currentTheme } = useTheme()

const isLight = computed(() => currentTheme.value === 'pop')

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
const activeColor = ref('')
const pickerColor = ref('#b9a6ff')
const viewMode = ref<'grid' | 'masonry'>('grid')
const loadedImages = ref<Set<number>>(new Set())

// Search params
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 24,
  sortOrder: 'descend',
  sortField: 'createTime',
})

// Permission checks
const createPermissionChecker = (permission: string) => {
  return computed(() => (space.value.permissionList ?? []).includes(permission))
}

const canManageSpaceUsers = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)
const showOp = computed(() => canEditPicture.value || canDeletePicture.value)

// Derived
const spaceNo = computed(() => String(spaceId.value ?? 0).padStart(3, '0'))
const typeLabel = computed(() =>
  space.value.spaceType !== undefined ? SPACE_TYPE_MAP[space.value.spaceType] : '空间',
)
const usagePercent = computed(() => {
  if (!space.value.totalSize || !space.value.maxSize) return 0
  return Math.min(100, Math.round((space.value.totalSize / space.value.maxSize) * 100))
})
const hasActiveFilters = computed(() => !!searchText.value || !!activeColor.value)
const heroPlates = computed(() => [0, 1, 2].map((i) => dataList.value[i] ?? null))

// Fetch space detail
const fetchDetailSpace = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({ id: spaceId.value as number })
    if (res.data.code === 0) {
      if (res.data.data) space.value = res.data.data
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
  const params: API.PictureQueryRequest = {
    spaceId: spaceId.value,
    ...searchParams.value,
  }
  try {
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0)
    } else {
      message.error('获取数据失败')
    }
  } catch (error: any) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}

// Aspect ratio placeholder
const getAspectRatio = (picture: any) => {
  if (!picture.picWidth || !picture.picHeight) return '75%'
  return `${(picture.picHeight / picture.picWidth) * 100}%`
}

// Image load
const isLoadedImage = (picture: API.PictureVO) =>
  picture.id !== undefined && loadedImages.value.has(picture.id)

const onImageLoad = (pictureId: number) => {
  loadedImages.value.add(pictureId)
}

// Navigation
const goBack = () => router.back()
const goToUpload = () => router.push(`/add_picture?spaceId=${props.id}`)
const goToUserManage = () => router.push(`/spaceUserManage/${props.id}`)
const goToAnalyze = () => router.push(`/space_analyze?spaceId=${props.id}`)

// Search
const doSearch = () => {
  activeColor.value = ''
  searchParams.value = {
    ...searchParams.value,
    current: 1,
    name: searchText.value.trim() || undefined,
  }
  fetchData()
}

const clearSearch = () => {
  searchText.value = ''
  doSearch()
}

// Color search
const onColorChange = async (color: string) => {
  if (!color) return
  searchText.value = ''
  activeColor.value = color
  searchParams.value = { ...searchParams.value, current: 1, name: undefined }
  loading.value = true
  try {
    const res = await searchPictureByColorUsingPost({ picColor: color, spaceId: spaceId.value })
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
  activeColor.value = ''
  pickerColor.value = '#b9a6ff'
  searchParams.value = { ...searchParams.value, current: 1, name: undefined }
  fetchData()
}

// View switch
const setView = (mode: 'grid' | 'masonry') => {
  viewMode.value = mode
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
const pageSize = computed(() => searchParams.value.pageSize ?? 24)
const currentPage = computed(() => searchParams.value.current ?? 1)
const getTotalPages = () => Math.ceil(total.value / pageSize.value)

const getVisiblePages = () => {
  const totalPages = getTotalPages()
  const current = currentPage.value
  const pages: (number | '...')[] = []

  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i)
  } else {
    if (current <= 3) {
      pages.push(1, 2, 3, 4, '...', totalPages)
    } else if (current >= totalPages - 2) {
      pages.push(1, '...', totalPages - 3, totalPages - 2, totalPages - 1, totalPages)
    } else {
      pages.push(1, '...', current - 1, current, current + 1, '...', totalPages)
    }
  }
  return pages
}

const prevPage = () => {
  if (currentPage.value > 1) {
    searchParams.value.current = currentPage.value - 1
    fetchData()
  }
}

const nextPage = () => {
  if (currentPage.value < getTotalPages()) {
    searchParams.value.current = currentPage.value + 1
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

watch(
  () => props.id,
  () => {
    fetchDetailSpace()
    fetchData()
  },
)
</script>

<style scoped lang="less">
#spaceDetailPage {
  padding-bottom: clamp(64px, 10vh, 120px);
}

/* ========== 顶部操作条 ========== */
.sd-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 0 18px;
  border-bottom: 1px solid var(--aw-line);
}

.sd-bar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sd-create {
  margin-left: 6px;
  padding: 11px 20px;
  font-size: 13.5px;
}

/* ========== 空间档案 ========== */
.sd-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  gap: clamp(32px, 6vw, 88px);
  align-items: center;
  padding: clamp(44px, 8vh, 96px) 0 clamp(40px, 6vh, 72px);
}

.sd-hero-copy {
  min-width: 0;
}

.sd-hero-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.sd-hero-id {
  flex-shrink: 0;
}

.sd-title {
  overflow-wrap: anywhere;
}

.sd-hero-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
  font-family: var(--aw-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--aw-ink-3);
}

.sd-type {
  padding: 4px 10px;
  border: 1px solid var(--aw-line-2);
  border-radius: 999px;
  color: var(--aw-ink-2);
  letter-spacing: 0.12em;
}

.sd-meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--aw-line-2);
}

/* 三张作品组成的档案拼贴 */
.sd-hero-plates {
  position: relative;
  height: clamp(230px, 26vw, 340px);
  min-width: 0;
}

.sd-plate {
  position: absolute;
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--aw-line);
  background: var(--aw-bg-2);
  box-shadow: 0 26px 60px -40px rgba(0, 0, 0, 0.8);
}

.sd-plate img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 1.1s var(--aw-ease);
}

.sd-plate:hover img {
  transform: scale(1.03);
}

.sd-plate-1 {
  left: 0;
  top: 12%;
  width: 41%;
  height: 74%;
  z-index: 1;
}

.sd-plate-2 {
  left: 29%;
  top: 0;
  width: 44%;
  height: 88%;
  z-index: 2;
}

.sd-plate-3 {
  right: 0;
  top: 16%;
  width: 36%;
  height: 68%;
  z-index: 1;
}

.sd-plate figcaption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24px 10px 9px;
  font-size: 9.5px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(180deg, transparent 0%, rgba(6, 5, 11, 0.76) 100%);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 第三张画板左侧被第二张压住，题注右对齐才能完整落在可见区域 */
.sd-plate-3 figcaption {
  text-align: right;
}

.sd-plate-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--aw-line);
  font-size: 9px;
  letter-spacing: 0.2em;
}

#spaceDetailPage.is-light .sd-plate {
  box-shadow: 0 26px 60px -42px rgba(76, 29, 149, 0.36);
}

/* ========== 目录数据 ========== */
.sd-catalogue {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(220px, 1.2fr);
  border-top: 1px solid var(--aw-line);
  border-bottom: 1px solid var(--aw-line);
}

.sd-cat {
  padding: 22px 0;
  min-width: 0;
}

.sd-cat + .sd-cat {
  padding-left: clamp(16px, 2.4vw, 32px);
  border-left: 1px solid var(--aw-line);
}

.sd-cat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 8px 0 0;
}

.sd-cat-value b {
  font-family: var(--aw-serif);
  font-size: clamp(22px, 2.2vw, 30px);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--aw-ink);
  white-space: nowrap;
}

.sd-cat-unit {
  font-family: var(--aw-mono);
  font-size: 10px;
  color: var(--aw-ink-3);
}

.sd-cat--usage {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.sd-cat-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.sd-usage-value {
  font-family: var(--aw-serif);
  font-size: 16px;
  font-weight: 600;
  color: var(--aw-ink);
}

.sd-rail {
  position: relative;
  height: 2px;
  background: var(--aw-line);
  overflow: hidden;
}

.sd-rail i {
  position: absolute;
  inset: 0;
  transform-origin: left;
  background: var(--aw-accent);
  transition: transform 1.2s var(--aw-ease);
}

/* ========== 检索与视图 ========== */
.sd-tools {
  display: flex;
  align-items: center;
  gap: clamp(20px, 3vw, 40px);
  flex-wrap: wrap;
  padding: 22px 0;
  border-bottom: 1px solid var(--aw-line);
}

.sd-search {
  flex: 1 1 260px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--aw-line);
  transition: border-color 0.4s var(--aw-ease);
}

.sd-search:focus-within {
  border-bottom-color: var(--aw-accent);
}

.sd-search-icon {
  flex-shrink: 0;
  color: var(--aw-ink-3);
}

.sd-search-input {
  flex: 1;
  min-width: 0;
  padding: 10px 0;
  border: 0;
  background: transparent;
  color: var(--aw-ink);
  font-family: var(--aw-sans);
  font-size: 15px;
  outline: none;
}

.sd-search-input::placeholder {
  color: var(--aw-ink-3);
}

.sd-search-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--aw-accent-soft);
  color: var(--aw-ink-2);
  cursor: pointer;
  transition:
    color 0.3s var(--aw-ease),
    transform 0.3s var(--aw-ease);
}

.sd-search-clear:hover {
  color: var(--aw-ink);
  transform: rotate(90deg);
}

.sd-search-go {
  flex-shrink: 0;
  padding: 6px 0;
  border: 0;
  background: none;
  color: var(--aw-ink-2);
  font-family: var(--aw-mono);
  font-size: 10.5px;
  letter-spacing: 0.16em;
  cursor: pointer;
  transition: color 0.35s var(--aw-ease);
}

.sd-search-go:hover {
  color: var(--aw-ink);
}

.sd-filter {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sd-filter-label {
  font-size: 9.5px;
  letter-spacing: 0.18em;
}

.sd-color-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 9px;
  border: 1px solid var(--aw-line);
  border-radius: 999px;
}

.sd-color-chip i {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.18);
}

.sd-color-chip code {
  font-family: var(--aw-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  color: var(--aw-ink-2);
}

/* 第三方取色器只保留一个圆点触发器，与编辑体工具栏同一质感 */
.sd-filter :deep(.vc-color-wrap) {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--aw-line-2);
  border-radius: 50%;
  background: transparent;
  box-shadow: none;
  overflow: hidden;
  transition:
    border-color 0.35s var(--aw-ease),
    transform 0.35s var(--aw-ease);
}

.sd-filter :deep(.vc-color-wrap:hover) {
  border-color: var(--aw-accent);
  transform: translateY(-1px);
}

.sd-filter :deep(.vc-color-wrap .current-color) {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.sd-view {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.sd-view button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 0;
  border: 0;
  background: none;
  color: var(--aw-ink-3);
  font-family: var(--aw-mono);
  font-size: 9.5px;
  letter-spacing: 0.16em;
  cursor: pointer;
  transition: color 0.35s var(--aw-ease);
}

.sd-view button::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--aw-ink);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.45s var(--aw-ease);
}

.sd-view button:hover {
  color: var(--aw-ink-2);
}

.sd-view button.active {
  color: var(--aw-ink);
}

.sd-view button.active::after {
  transform: scaleX(1);
}

.sd-view button:focus-visible,
.sd-search-go:focus-visible,
.sd-search-clear:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 3px;
}

/* ========== 生效中的筛选 ========== */
.sd-active {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--aw-line);
  font-size: 12px;
  color: var(--aw-ink-2);
}

.sd-active-item {
  padding: 3px 9px;
  border: 1px solid var(--aw-line);
  border-radius: 999px;
}

.sd-active-item code {
  font-family: var(--aw-mono);
  font-size: 10px;
}

.sd-clear {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 4px 0;
  border: 0;
  background: none;
  color: var(--aw-ink-3);
  font-family: var(--aw-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  cursor: pointer;
  transition: color 0.3s var(--aw-ease);
}

.sd-clear:hover {
  color: var(--aw-danger);
}

/* ========== 陈列 ========== */
.sd-gallery {
  padding-top: clamp(32px, 5vh, 52px);
}

.sd-gallery-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--aw-line);
  margin-bottom: clamp(24px, 4vh, 40px);
}

.sd-gallery-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sd-gallery-title {
  margin: 0;
  font-family: var(--aw-serif);
  font-size: clamp(24px, 2.6vw, 34px);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--aw-ink);
}

.sd-gallery-count {
  text-align: right;
}

/* 列表：网格与瀑布流共用一套卡片 */
.sd-list.is-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 30px 22px;
}

/* 网格视图统一画幅，行与行之间不再被不同长宽比撑出空洞 */
.sd-list.is-grid .sd-card-media {
  padding-bottom: 125% !important;
}

.sd-list.is-masonry {
  column-count: 4;
  column-gap: 22px;
}

.sd-list.is-masonry .sd-card {
  break-inside: avoid;
  margin-bottom: 26px;
}

.sd-card {
  position: relative;
  display: block;
  min-width: 0;
  cursor: pointer;
  outline: none;
  animation: sd-card-in 0.7s var(--aw-ease) both;
  animation-delay: calc(var(--i, 0) * 45ms);
}

@keyframes sd-card-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.sd-card:focus-visible .sd-card-media {
  outline: 2px solid var(--aw-accent);
  outline-offset: 3px;
}

.sd-card-media {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--aw-bg-2);
  box-shadow: inset 0 0 0 1px var(--aw-line);
}

.sd-card-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition:
    opacity 0.8s var(--aw-ease),
    transform 1s var(--aw-ease);
}

.sd-card-img.loaded {
  opacity: 1;
}

.sd-card:hover .sd-card-img.loaded,
.sd-card:focus-visible .sd-card-img.loaded {
  transform: scale(1.035);
}

.sd-card-skeleton {
  position: absolute;
  inset: 0;
  background: var(--aw-accent-soft);
  overflow: hidden;
  transition: opacity 0.4s var(--aw-ease);
}

.sd-card-skeleton::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(242, 238, 230, 0.06) 50%,
    transparent 100%
  );
  animation: sd-shimmer 1.6s ease-in-out infinite;
}

#spaceDetailPage.is-light .sd-card-skeleton::after {
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.7) 50%,
    transparent 100%
  );
}

.sd-card-skeleton.loaded {
  opacity: 0;
  pointer-events: none;
}

@keyframes sd-shimmer {
  to {
    transform: translateX(100%);
  }
}

.sd-card-veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    transparent 42%,
    rgba(6, 5, 11, 0.28) 68%,
    rgba(6, 5, 11, 0.86) 100%
  );
  opacity: 0.92;
  transition: opacity 0.5s var(--aw-ease);
  pointer-events: none;
}

#spaceDetailPage.is-light .sd-card-veil {
  background: linear-gradient(
    180deg,
    transparent 42%,
    rgba(30, 27, 75, 0.16) 68%,
    rgba(30, 27, 75, 0.78) 100%
  );
}

.sd-card:hover .sd-card-veil,
.sd-card:focus-visible .sd-card-veil {
  opacity: 1;
}

.sd-card-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 14px 15px 13px;
  color: #fff;
  pointer-events: none;
}

.sd-card-title {
  margin: 0;
  font-family: var(--aw-serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.01em;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-shadow: 0 1px 18px rgba(0, 0, 0, 0.4);
}

.sd-card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 5px 0 0;
  font-size: 10px;
  letter-spacing: 0.1em;
  color: rgba(255, 255, 255, 0.76);
}

.sd-card-meta i {
  font-style: normal;
  opacity: 0.5;
}

.sd-card-ops {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateY(-6px);
  transition:
    opacity 0.35s var(--aw-ease),
    transform 0.35s var(--aw-ease);
}

.sd-card:hover .sd-card-ops,
.sd-card:focus-within .sd-card-ops {
  opacity: 1;
  transform: none;
}

@media (hover: none) {
  .sd-card-ops {
    opacity: 1;
    transform: none;
  }
}

.sd-card-op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 50%;
  background: rgba(6, 5, 11, 0.5);
  backdrop-filter: blur(6px);
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition:
    background-color 0.3s var(--aw-ease),
    border-color 0.3s var(--aw-ease),
    transform 0.3s var(--aw-ease);
}

.sd-card-op:hover {
  background: rgba(6, 5, 11, 0.78);
  border-color: rgba(255, 255, 255, 0.6);
  transform: translateY(-1px);
}

.sd-card-op--danger:hover {
  background: rgba(153, 27, 27, 0.8);
  border-color: rgba(255, 255, 255, 0.6);
}

.sd-card-op:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 2px;
}

/* ========== 骨架屏 ========== */
.sd-skeleton-bar {
  height: 2px;
  margin-bottom: 28px;
  background: var(--aw-line);
  overflow: hidden;
}

.sd-skeleton-bar i {
  display: block;
  width: 30%;
  height: 100%;
  background: var(--aw-accent);
  animation: sd-bar 1.4s var(--aw-ease) infinite;
}

@keyframes sd-bar {
  0% {
    transform: translateX(-110%);
  }
  100% {
    transform: translateX(360%);
  }
}

.sd-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 26px 22px;
}

.sd-skeleton-tile {
  position: relative;
  aspect-ratio: 3 / 4;
  background: var(--aw-accent-soft);
  overflow: hidden;
}

.sd-skeleton-tile::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(242, 238, 230, 0.05) 50%,
    transparent 100%
  );
  animation: sd-shimmer 1.6s ease-in-out infinite;
}

#spaceDetailPage.is-light .sd-skeleton-tile::after {
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.7) 50%,
    transparent 100%
  );
}

/* ========== 空状态 ========== */
.sd-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: clamp(64px, 12vh, 140px) 20px;
}

.sd-empty-no {
  font-size: 10px;
  letter-spacing: 0.24em;
}

.sd-empty-title {
  margin: 20px 0 0;
  font-family: var(--aw-serif);
  font-size: clamp(26px, 3.2vw, 40px);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--aw-ink);
}

.sd-empty-desc {
  max-width: 34ch;
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--aw-ink-3);
  text-wrap: pretty;
}

.sd-empty-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

/* ========== 分页 ========== */
.sd-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(16px, 3vw, 36px);
  margin-top: clamp(44px, 7vh, 72px);
  padding-top: 24px;
  border-top: 1px solid var(--aw-line);
}

.sd-page-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--aw-line);
  border-radius: 50%;
  background: transparent;
  color: var(--aw-ink-2);
  cursor: pointer;
  transition:
    color 0.3s var(--aw-ease),
    border-color 0.3s var(--aw-ease),
    background-color 0.3s var(--aw-ease);
}

.sd-page-arrow:hover:not(:disabled) {
  color: var(--aw-ink);
  border-color: var(--aw-line-2);
  background: var(--aw-accent-soft);
}

.sd-page-arrow:disabled {
  opacity: 0.32;
  cursor: not-allowed;
}

.sd-page-arrow:focus-visible,
.sd-page-number:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 3px;
}

.sd-page-index {
  font-size: 9.5px;
  letter-spacing: 0.16em;
  white-space: nowrap;
}

.sd-page-numbers {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sd-page-number {
  position: relative;
  min-width: 30px;
  padding: 6px 4px;
  border: 0;
  background: none;
  color: var(--aw-ink-3);
  font-family: var(--aw-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: color 0.3s var(--aw-ease);
}

.sd-page-number::after {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: 2px;
  height: 1px;
  background: var(--aw-ink);
  transform: scaleX(0);
  transition: transform 0.35s var(--aw-ease);
}

.sd-page-number:hover {
  color: var(--aw-ink-2);
}

.sd-page-number.active {
  color: var(--aw-ink);
}

.sd-page-number.active::after {
  transform: scaleX(1);
}

.sd-page-number.ellipsis {
  cursor: default;
  opacity: 0.6;
}

/* ========== 浅色主题 ========== */
#spaceDetailPage.is-light .sd-plate-empty {
  color: var(--aw-ink-3);
}

#spaceDetailPage.is-light .sd-card-op {
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(30, 27, 75, 0.42);
}

#spaceDetailPage.is-light .sd-card-op:hover {
  background: rgba(30, 27, 75, 0.68);
}

/* ========== 响应式 ========== */
@media (max-width: 1180px) {
  .sd-list.is-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .sd-list.is-masonry {
    column-count: 3;
  }

  .sd-skeleton-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .sd-catalogue {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sd-cat:nth-child(3) {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid var(--aw-line);
  }

  .sd-cat:nth-child(4) {
    border-top: 1px solid var(--aw-line);
  }
}

@media (max-width: 1080px) {
  .sd-hero {
    grid-template-columns: 1fr;
    gap: 40px;
  }

  .sd-hero-plates {
    max-width: 560px;
    height: clamp(220px, 34vw, 320px);
  }
}

@media (max-width: 880px) {
  .sd-list.is-grid,
  .sd-skeleton-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sd-list.is-masonry {
    column-count: 2;
  }

  .sd-title {
    font-size: clamp(30px, 6.6vw, 46px);
  }
}

@media (max-width: 820px) {
  .sd-view {
    margin-left: 0;
  }

  .sd-tools {
    gap: 18px;
  }
}

@media (max-width: 640px) {
  .sd-bar {
    padding-top: 18px;
    gap: 12px;
  }

  .sd-bar-actions {
    gap: 8px;
  }

  .sd-create span {
    display: none;
  }

  .sd-create {
    padding: 11px;
  }

  .sd-hero {
    padding-top: 36px;
  }

  .sd-hero-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .sd-hero-plates {
    height: 210px;
  }

  .sd-catalogue {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sd-cat {
    padding: 18px 0;
  }

  .sd-cat + .sd-cat {
    padding-left: 14px;
  }

  .sd-search {
    flex-basis: 100%;
  }

  .sd-view {
    margin-left: auto;
  }

  .sd-gallery-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .sd-gallery-count {
    text-align: left;
  }

  .sd-pager {
    flex-wrap: wrap;
    gap: 14px;
  }

  .sd-page-index {
    order: -1;
    width: 100%;
    text-align: center;
  }

  .sd-page-numbers {
    order: 1;
  }
}

@media (max-width: 480px) {
  .sd-list.is-grid,
  .sd-skeleton-grid {
    gap: 18px 12px;
  }

  .sd-list.is-masonry {
    column-gap: 12px;
  }

  .sd-list.is-masonry .sd-card {
    margin-bottom: 18px;
  }

  .sd-card-title {
    font-size: 13px;
  }

  .sd-card-body {
    padding: 10px 10px 9px;
  }

  .sd-card-meta {
    font-size: 9px;
  }

  .sd-card-ops {
    top: 6px;
    right: 6px;
  }

  .sd-card-op {
    width: 28px;
    height: 28px;
  }

  .sd-empty {
    padding: 56px 12px;
  }
}
</style>
