<template>
  <div id="homePage" :class="{ 'is-light': isLight }">
    <!-- 氛围层：网格、极光、颗粒与暗角，全部由 CSS 承担 -->
    <div class="ambient-bg" aria-hidden="true">
      <div class="ex-grid"></div>
      <div class="ex-orb ex-orb-1"></div>
      <div class="ex-orb ex-orb-2"></div>
      <div class="ex-orb ex-orb-3"></div>
      <div class="ex-grain"></div>
      <div class="ex-vignette"></div>
    </div>

    <div class="main-content">
      <!-- 01 探索 -->
      <section class="hero-section" aria-labelledby="explore-title">
        <header class="ex-head">
          <span class="index"><b>01</b> — 探索</span>
          <span class="mono head-note">SEARCH THE COLLECTION</span>
        </header>

        <div class="hero-body">
          <h1 id="explore-title" class="hero-title">
            发现视觉<span class="title-accent"
              >灵感<svg
                class="title-mark"
                viewBox="0 0 200 12"
                preserveAspectRatio="none"
                aria-hidden="true"
              >
                <path d="M3 8.6C46 3.4 118 2.6 197 6.8" vector-effect="non-scaling-stroke" />
              </svg>
            </span>
          </h1>

          <p class="hero-subtitle">
            按名称或简介检索公开图库。也可以只输入一个词，看看会遇见什么。
          </p>

          <form
            class="search-bar"
            :class="{ 'is-focused': isSearchFocused }"
            role="search"
            @submit.prevent="doSearch"
          >
            <svg
              class="search-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.8-3.8" stroke-linecap="round" />
            </svg>
            <label class="sr-only" for="explore-search">搜索作品名称或简介</label>
            <input
              id="explore-search"
              ref="searchInputRef"
              v-model="searchText"
              type="search"
              class="search-input"
              placeholder="搜索作品名称或简介"
              maxlength="60"
              autocomplete="off"
              @focus="isSearchFocused = true"
              @blur="isSearchFocused = false"
            />
            <button
              v-if="searchText"
              type="button"
              class="search-clear"
              aria-label="清空搜索"
              @click="clearSearch"
            >
              <svg
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true"
              >
                <path d="M6 6l12 12M18 6 6 18" stroke-linecap="round" />
              </svg>
            </button>
            <kbd class="search-hint mono">ENTER</kbd>
            <button
              type="submit"
              class="search-btn"
              aria-label="探索"
              :disabled="loading && pictureList.length === 0"
            >
              <span class="search-btn-text">探索</span>
              <svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true"
              >
                <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </form>

          <div class="category-bar" role="group" aria-label="按分类筛选">
            <span class="category-label mono">分类</span>
            <div class="category-list">
              <button
                v-for="cat in categoryList"
                :key="cat"
                type="button"
                class="category-item"
                :class="{ active: selectedCategory === cat }"
                :aria-pressed="selectedCategory === cat"
                @click="selectCategory(cat)"
              >
                {{ cat }}
              </button>
            </div>
          </div>
        </div>

        <button
          class="hero-scroll"
          type="button"
          aria-label="跳到作品列表"
          @click="scrollToGallery"
        >
          <span class="mono">SCROLL</span>
          <span class="scroll-line" aria-hidden="true"></span>
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            aria-hidden="true"
          >
            <path d="M12 4v15M6 13l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </section>

      <!-- 02 画廊 -->
      <section class="gallery-section" aria-labelledby="gallery-title">
        <header class="gallery-head">
          <div class="gallery-head-main">
            <span class="index"><b>02</b> — 灵感画廊</span>
            <h2 id="gallery-title" class="gallery-title">{{ galleryTitle }}</h2>
          </div>
          <p class="mono gallery-count" aria-live="polite">
            <template v-if="loadError">—</template>
            <template v-else-if="displayTotal > 0">{{ displayTotal }} 张作品</template>
            <template v-else-if="isFiltering">0 张作品</template>
            <template v-else>作品持续更新</template>
          </p>
        </header>

        <div class="masonry-wrapper" ref="galleryRef">
          <div class="masonry-column" v-for="col in columnCount" :key="col">
            <article
              v-for="picture in getColumnPictures(col - 1)"
              :key="picture.id"
              class="gallery-item"
              :style="{
                animationDelay: `${(((pictureIndex.get(String(picture.id)) ?? 0) % 12) * 0.05).toFixed(2)}s`,
              }"
              role="link"
              tabindex="0"
              :aria-label="`查看作品：${picture.name}`"
              @click="handlePictureClick(picture)"
              @keydown="onCardKeydown($event, picture)"
            >
              <div class="item-image-wrap" :style="{ paddingBottom: getAspectRatio(picture) }">
                <div v-if="!isLoaded(picture)" class="item-skeleton"></div>
                <img
                  :src="contentSrc(picture.thumbnailUrl || picture.url)"
                  :alt="picture.name"
                  class="item-image"
                  :class="{ loaded: isLoaded(picture), 'is-error': isFailed(picture) }"
                  loading="lazy"
                  decoding="async"
                  @load="onImageLoad(picture.id)"
                  @error="onImageError(picture.id)"
                />
                <div class="item-overlay">
                  <span class="item-index mono">{{ pictureNumber(picture.id) }}</span>
                  <div class="overlay-content">
                    <h3 class="item-title">{{ picture.name }}</h3>
                    <div class="item-meta mono">
                      <span>{{ picture.picWidth }} × {{ picture.picHeight }}</span>
                      <span class="meta-divider">/</span>
                      <span>{{ picture.picFormat?.toUpperCase() || 'IMAGE' }}</span>
                    </div>
                  </div>
                  <div class="overlay-actions">
                    <button
                      type="button"
                      class="action-btn"
                      :class="{ 'is-liked': isLiked(picture) }"
                      :aria-pressed="isLiked(picture)"
                      :aria-label="isLiked(picture) ? '取消喜欢' : '喜欢'"
                      @click.stop="toggleLike(picture)"
                    >
                      <svg
                        width="15"
                        height="15"
                        viewBox="0 0 24 24"
                        :fill="isLiked(picture) ? 'currentColor' : 'none'"
                        stroke="currentColor"
                        stroke-width="1.8"
                        aria-hidden="true"
                      >
                        <path
                          d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1L12 21.2l7.7-7.8 1.1-1a5.5 5.5 0 0 0 0-7.8z"
                          stroke-linejoin="round"
                        />
                      </svg>
                    </button>
                    <button
                      type="button"
                      class="action-btn"
                      aria-label="复制作品链接"
                      @click.stop="sharePicture(picture)"
                    >
                      <svg
                        width="15"
                        height="15"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.7"
                        aria-hidden="true"
                      >
                        <circle cx="18" cy="5" r="2.6" />
                        <circle cx="6" cy="12" r="2.6" />
                        <circle cx="18" cy="19" r="2.6" />
                        <path d="M8.4 13.3l7.2 4.4M15.6 6.3 8.4 10.7" stroke-linecap="round" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
              <!-- 曝光追踪 -->
              <div
                class="impression-tracker"
                :data-picture-id="picture.id"
                :ref="setImpressionRef"
              />
            </article>
          </div>
        </div>

        <div class="gallery-loader" ref="loaderRef">
          <div v-if="loading" class="loader-content" role="status" aria-live="polite">
            <span class="loader-ring" aria-hidden="true"></span>
            <span class="loader-text mono">正在整理灵感</span>
          </div>

          <div v-else-if="!displayHasMore && pictureList.length > 0" class="end-section">
            <span class="end-rule" aria-hidden="true"></span>
            <span class="end-note mono">END OF COLLECTION</span>
            <h3 class="end-title">这一批，看完了</h3>
            <p class="end-subtitle">{{ endSummary }}</p>
            <div class="end-actions">
              <button type="button" class="end-btn primary" @click="backToTop">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                  aria-hidden="true"
                >
                  <path d="M12 20V5M6 11l6-6 6 6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span>回到顶部</span>
              </button>
              <button type="button" class="end-btn ghost" @click="refreshGallery">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                  aria-hidden="true"
                >
                  <path d="M20 5v5h-5" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M4 19v-5h5" stroke-linecap="round" stroke-linejoin="round" />
                  <path
                    d="M5.3 9a7.5 7.5 0 0 1 12.4-2.4L20 10M4 14l2.3 3.4A7.5 7.5 0 0 0 18.7 15"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <span>{{ isFiltering ? '重新检索' : '换一批' }}</span>
              </button>
            </div>
          </div>

          <div v-else-if="!loading && (loadError || pictureList.length === 0)" class="empty-state">
            <div class="empty-illustration" aria-hidden="true">
              <svg width="96" height="96" viewBox="0 0 96 96" fill="none">
                <circle
                  cx="48"
                  cy="48"
                  r="38"
                  stroke="currentColor"
                  stroke-width="1"
                  stroke-dasharray="3 6"
                  opacity="0.55"
                />
                <rect
                  x="30"
                  y="30"
                  width="36"
                  height="36"
                  rx="2"
                  stroke="currentColor"
                  stroke-width="1.2"
                  opacity="0.8"
                />
                <circle cx="40" cy="40" r="3.5" fill="currentColor" opacity="0.5" />
                <path d="M30 58l12-12 9 9 7-7 8 8v10H30z" fill="currentColor" opacity="0.18" />
              </svg>
            </div>

            <template v-if="loadError">
              <h3 class="empty-title">加载失败</h3>
              <p class="empty-desc">{{ loadError }}</p>
              <button type="button" class="empty-action" @click="retryLoad">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  aria-hidden="true"
                >
                  <path d="M20 5v5h-5" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M4 19v-5h5" stroke-linecap="round" stroke-linejoin="round" />
                  <path
                    d="M5.3 9a7.5 7.5 0 0 1 12.4-2.4L20 10M4 14l2.3 3.4A7.5 7.5 0 0 0 18.7 15"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <span>重试</span>
              </button>
            </template>

            <template v-else>
              <h3 class="empty-title">{{ emptyTitle }}</h3>
              <p class="empty-desc">{{ emptyDesc }}</p>
              <button v-if="isFiltering" type="button" class="empty-action" @click="clearFilters">
                <span>清除筛选</span>
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  aria-hidden="true"
                >
                  <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
              <button v-else type="button" class="empty-action" @click="goToUpload">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  aria-hidden="true"
                >
                  <path d="M12 5v14M5 12h14" stroke-linecap="round" />
                </svg>
                <span>上传图片</span>
              </button>
            </template>
          </div>
        </div>
      </section>
    </div>

    <Transition name="toast">
      <div v-if="toast" class="ex-toast mono" role="status" aria-live="polite">{{ toast }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, type ComponentPublicInstance } from 'vue'
import { useRouter } from 'vue-router'
import {
  ActionSourceEnum,
  ActionTypeEnum,
  type PictureRecommendRequest,
} from '@/api-custom/picture'
import { usePictureAction } from '@/composables/usePictureAction'
import { useRecommendPaging } from '@/composables/useRecommendPaging'
import { useTheme } from '@/composables/useTheme'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController'
import { contentSrc, toId } from '@/utils'

/** 推荐接口返回的图片字段（Long 可能以字符串序列化） */
interface ExplorePicture {
  id: number | string
  name: string
  url?: string
  thumbnailUrl?: string
  picWidth?: number
  picHeight?: number
  picFormat?: string
  tags?: string[]
}

const router = useRouter()
const { currentTheme } = useTheme()
const isLight = computed(() => currentTheme.value === 'pop')

const { report: reportAction } = usePictureAction()
// 推荐流分页：页码、总数与分页上下文都收在 composable 里，翻页时把上下文带回服务端
const {
  page: recommendPage,
  total: recommendTotal,
  totalValid: recommendTotalValid,
  hasMore: recommendHasMore,
  reset: resetPaging,
  loadPage,
  nextPage,
} = useRecommendPaging()

// Refs
const galleryRef = ref<HTMLElement>()
const loaderRef = ref<HTMLElement>()
const searchInputRef = ref<HTMLInputElement>()

// 搜索与筛选
const searchText = ref('')
// 真正提交的查询词：输入过程中不触发请求
const activeSearch = ref('')
const isSearchFocused = ref(false)
const currentType = ref<PictureRecommendRequest['type']>('hot')
const selectedCategory = ref('全部')
const categoryList = ref<string[]>(['全部', '风景', '人物', '艺术', '建筑', '自然', '美食'])

// 数据状态
const pictureList = ref<ExplorePicture[]>([])
// loading 从 true 开始：首屏直接进入加载态，避免空状态闪一下
const loading = ref(true)
const loadError = ref('')
// 每次重置让在途请求作废，避免旧响应把新的筛选结果盖掉
let loadToken = 0
const loadedImages = ref<Set<string>>(new Set())
const failedImages = ref<Set<string>>(new Set())
const likedIds = ref<Set<string>>(new Set())

// 检索结果分页（公开图库接口）
const PAGE_SIZE = 24
const listPage = ref(1)
const listTotal = ref(0)
const listHasMore = ref(false)

// 瀑布流配置
const BREAKPOINT_LG = 1024
const BREAKPOINT_SM = 640
const columnCount = ref(4)
const columnAssignment = ref<Map<string, number>>(new Map())
const columnHeights = ref<number[]>(new Array(4).fill(0))

// 曝光追踪
const observedPictures = ref<Set<string>>(new Set())
let impressionObserver: IntersectionObserver | null = null
let scrollObserver: IntersectionObserver | null = null

// 轻提示
const toast = ref('')
let toastTimer: number | null = null
let resizeTimer: number | null = null

const isFiltering = computed(
  () => activeSearch.value.trim() !== '' || selectedCategory.value !== '全部',
)

const displayTotal = computed(() => (isFiltering.value ? listTotal.value : recommendTotal.value))
const displayHasMore = computed(() =>
  isFiltering.value ? listHasMore.value : recommendHasMore.value,
)

const galleryTitle = computed(() => {
  const raw = activeSearch.value.trim()
  const query = raw.length > 20 ? `${raw.slice(0, 20)}…` : raw
  const category = selectedCategory.value
  if (query && category !== '全部') return `“${query}” · ${category}`
  if (query) return `“${query}”`
  if (category !== '全部') return `${category} · 精选`
  return '正在流行'
})

const endSummary = computed(() => {
  if (displayTotal.value <= 0) return '调整关键词或分类再试一次'
  return isFiltering.value
    ? `共 ${displayTotal.value} 张作品 · 调整关键词再试一次`
    : `共 ${displayTotal.value} 张作品 · 换一批会有新的组合`
})

const emptyTitle = computed(() => (isFiltering.value ? '没有找到匹配的作品' : '这里还没有作品'))

const emptyDesc = computed(() =>
  isFiltering.value
    ? '换一个关键词，或者回到全部作品看看。'
    : '公开图库里暂时没有可展示的图片。上传一张，成为第一个。',
)

const pictureIndex = computed(() => {
  const index = new Map<string, number>()
  pictureList.value.forEach((picture, i) => index.set(String(picture.id), i))
  return index
})

const pictureNumber = (pictureId: number | string) => {
  const index = pictureIndex.value.get(String(pictureId)) ?? 0
  return String(index + 1).padStart(2, '0')
}

const isLoaded = (picture: ExplorePicture) => loadedImages.value.has(String(picture.id))
const isFailed = (picture: ExplorePicture) => failedImages.value.has(String(picture.id))
const isLiked = (picture: ExplorePicture) => likedIds.value.has(String(picture.id))

const showToast = (message: string) => {
  toast.value = message
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toast.value = ''
    toastTimer = null
  }, 1800)
}

/**
 * 图片加载完成
 */
const onImageLoad = (pictureId: number | string) => {
  loadedImages.value.add(String(pictureId))
}

const onImageError = (pictureId: number | string) => {
  failedImages.value.add(String(pictureId))
  loadedImages.value.add(String(pictureId))
}

/**
 * 计算图片宽高比
 */
const getAspectRatio = (picture: ExplorePicture) => {
  if (!picture.picWidth || !picture.picHeight) return '75%'
  return `${(picture.picHeight / picture.picWidth) * 100}%`
}

/**
 * 获取指定列的图片
 */
const getColumnPictures = (colIndex: number) => {
  return pictureList.value.filter((picture) => {
    return columnAssignment.value.get(String(picture.id)) === colIndex
  })
}

const getShortestColumn = () => {
  if (columnHeights.value.length === 0) return 0
  let shortest = 0
  for (let i = 1; i < columnHeights.value.length; i += 1) {
    if (columnHeights.value[i] < columnHeights.value[shortest]) shortest = i
  }
  return shortest
}

/**
 * 与样式表保持一致的列间距
 */
const columnGap = () => {
  const width = window.innerWidth
  if (width <= 640) return 10
  if (width <= 768) return 12
  if (width <= 1200) return 16
  return 18
}

/**
 * 按宽高比估算卡片高度，用于把新图放进最短列
 */
const calculatePictureHeight = (picture: ExplorePicture): number => {
  const gap = columnGap()
  const wrapWidth = galleryRef.value?.clientWidth || Math.max(window.innerWidth - 96, 320)
  const cardWidth = Math.max((wrapWidth - gap * (columnCount.value - 1)) / columnCount.value, 120)
  const ratio = picture.picWidth && picture.picHeight ? picture.picHeight / picture.picWidth : 0.75
  return cardWidth * ratio + gap
}

const assignPicturesToColumns = (pictures: ExplorePicture[]) => {
  pictures.forEach((picture) => {
    const shortest = getShortestColumn()
    columnAssignment.value.set(String(picture.id), shortest)
    columnHeights.value[shortest] += calculatePictureHeight(picture)
  })
}

/**
 * 列数变化后重新分配，否则旧列号会指向已经不存在的列，图片会直接消失
 */
const rebalanceColumns = () => {
  columnHeights.value = new Array(columnCount.value).fill(0)
  columnAssignment.value = new Map()
  if (pictureList.value.length > 0) assignPicturesToColumns(pictureList.value)
}

/**
 * 结果集只有几张图时按数量收敛列数，别让一张图孤零零地占着四分之一屏
 */
const resolveColumnCount = (width: number, total: number) => {
  const base = width >= BREAKPOINT_LG ? 4 : width >= BREAKPOINT_SM ? 3 : 2
  if (!total) return base
  return Math.min(base, Math.max(2, Math.ceil(Math.sqrt(total))))
}

/**
 * 当前结果集的总量：检索用接口返回的 total，推荐流在总数不可信时退回已加载数量
 */
const currentLayoutTotal = () => {
  if (isFiltering.value) return listTotal.value
  const total = Number(recommendTotal.value)
  return recommendTotalValid.value && Number.isFinite(total) && total > 0
    ? total
    : pictureList.value.length
}

const applyColumnCount = (total: number) => {
  const next = resolveColumnCount(window.innerWidth, total)
  if (next === columnCount.value) return
  columnCount.value = next
  columnHeights.value = new Array(next).fill(0)
  columnAssignment.value = new Map()
}

const updateColumnCount = () => {
  if (resizeTimer) window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    resizeTimer = null
    const next = resolveColumnCount(window.innerWidth, currentLayoutTotal())
    if (next === columnCount.value) return
    columnCount.value = next
    rebalanceColumns()
  }, 120)
}

const resetGallery = () => {
  loadToken += 1
  resetPaging()
  listPage.value = 1
  listTotal.value = 0
  listHasMore.value = false
  loadError.value = ''
  pictureList.value = []
  columnAssignment.value = new Map()
  columnHeights.value = new Array(columnCount.value).fill(0)
  loadedImages.value.clear()
  failedImages.value.clear()
}

/**
 * 分类选择
 */
const selectCategory = (cat: string) => {
  if (selectedCategory.value === cat) return
  selectedCategory.value = cat
  resetGallery()
  loadPictures()
  scrollToGallery()
}

/**
 * 搜索：提交后才切换数据源，输入过程中不请求
 */
const doSearch = () => {
  activeSearch.value = searchText.value.trim()
  resetGallery()
  loadPictures()
  // 结果在首屏之下，提交后把画廊带到视口里
  scrollToGallery()
}

const clearSearch = () => {
  const hadQuery = activeSearch.value !== ''
  searchText.value = ''
  searchInputRef.value?.focus()
  // 已经提交过搜索时，清空输入框就意味着撤销这次筛选
  if (hadQuery) {
    activeSearch.value = ''
    resetGallery()
    loadPictures()
  }
}

/**
 * 清除筛选，回到推荐流
 */
const clearFilters = () => {
  searchText.value = ''
  activeSearch.value = ''
  selectedCategory.value = '全部'
  resetGallery()
  loadPictures()
  scrollToGallery()
}

const retryLoad = () => {
  loadError.value = ''
  loadPictures()
}

/**
 * 公开图库检索：搜索词匹配名称/简介，分类为精确匹配
 */
const fetchFilteredPage = async () => {
  const res = await listPictureVoByPageUsingPost({
    current: listPage.value,
    pageSize: PAGE_SIZE,
    searchText: activeSearch.value.trim() || undefined,
    category: selectedCategory.value === '全部' ? undefined : selectedCategory.value,
    sortField: 'createTime',
  })
  const body = res.data
  if (body.code !== 0) {
    throw new Error(body.message || '检索失败')
  }
  const payload = body.data
  const records = (payload?.records ?? []) as ExplorePicture[]
  const total = Number(payload?.total ?? 0)
  listTotal.value = Number.isFinite(total) ? total : 0
  // total 缺失时退回「本页是否满页」判断，避免第一页就被判成没有更多
  listHasMore.value =
    records.length === PAGE_SIZE &&
    (listTotal.value === 0 || listPage.value * PAGE_SIZE < listTotal.value)
  return { records, current: listPage.value }
}

const fetchRecommendPage = async () => {
  const records = (await loadPage(currentType.value)) as ExplorePicture[]
  // 接口返回业务错误时 data 为空且总数非法，别把它当成「图库为空」
  if (!recommendTotalValid.value && records.length === 0) {
    throw new Error('推荐服务暂时不可用')
  }
  return { records, current: recommendPage.value }
}

/**
 * 加载图片
 */
const loadPictures = async () => {
  const token = (loadToken += 1)
  loading.value = true
  loadError.value = ''

  try {
    const result = isFiltering.value ? await fetchFilteredPage() : await fetchRecommendPage()
    // 重置后到达的旧响应直接丢弃
    if (token !== loadToken) return
    if (result.records.length > 0) {
      if (result.current === 1) {
        // 换了一批结果：列数按结果总量重新定，再把这一页整体铺进去
        const total = isFiltering.value
          ? listTotal.value
          : recommendTotalValid.value
            ? Number(recommendTotal.value) || 0
            : 0
        applyColumnCount(total || result.records.length)
        pictureList.value = result.records
        columnHeights.value = new Array(columnCount.value).fill(0)
        columnAssignment.value = new Map()
        assignPicturesToColumns(result.records)
      } else {
        const fresh = result.records.filter(
          (picture: ExplorePicture) => !columnAssignment.value.has(String(picture.id)),
        )
        assignPicturesToColumns(fresh)
        pictureList.value.push(...result.records)
      }
    }
  } catch (error) {
    if (token !== loadToken) return
    if (isFiltering.value) {
      loadError.value = '检索暂时不可用，请稍后再试。'
    } else {
      loadError.value =
        error instanceof Error && error.message === '推荐服务暂时不可用'
          ? '推荐服务暂时不可用，请稍后再试。'
          : '加载失败，请稍后再试。'
    }
    console.warn('加载图片失败', error)
  } finally {
    if (token === loadToken) {
      loading.value = false
      fillViewport()
    }
  }
}

/**
 * 首屏没填满时继续取下一页，避免无限滚动在没有滚动条的屏幕上停住
 */
const fillViewport = () => {
  if (loadError.value || !displayHasMore.value || loading.value || !loaderRef.value) return
  const rect = loaderRef.value.getBoundingClientRect()
  if (rect.top < window.innerHeight + 200) {
    window.setTimeout(() => {
      if (loading.value || !displayHasMore.value || !loaderRef.value) return
      // 列表渲染完成后 loader 可能已经被推下去，按新位置再判断一次
      if (loaderRef.value.getBoundingClientRect().top > window.innerHeight + 200) return
      loadMore()
    }, 60)
  }
}

/**
 * 无限滚动加载
 */
const loadMore = () => {
  if (loadError.value || loading.value || !displayHasMore.value) return
  if (isFiltering.value) listPage.value += 1
  else nextPage()
  loadPictures()
}

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

const backToTop = () => {
  window.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
}

const scrollToGallery = () => {
  const section = galleryRef.value?.parentElement
  if (!section) return
  section.scrollIntoView({
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    block: 'start',
  })
}

/**
 * 换一批
 */
const refreshGallery = () => {
  resetGallery()
  // 回到顶部的这段等待里保持加载态，避免闪一下空状态
  loading.value = true
  backToTop()
  window.setTimeout(() => loadPictures(), prefersReducedMotion() ? 0 : 300)
}

const goToUpload = () => {
  router.push('/add_picture')
}

const handlePictureClick = async (picture: ExplorePicture) => {
  const numericId = toId(String(picture.id))
  if (numericId !== undefined) {
    await reportAction(numericId, ActionTypeEnum.CLICK, ActionSourceEnum.HOME_RECOMMEND)
  }
  router.push(`/picture/${picture.id}`)
}

/**
 * 卡片获得焦点时按回车/空格才进入详情，避免卡片内按钮的按键冒泡触发跳转
 */
const onCardKeydown = (event: KeyboardEvent, picture: ExplorePicture) => {
  if (event.target !== event.currentTarget) return
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  handlePictureClick(picture)
}

/**
 * 喜欢：当前没有收藏接口，这里上报 LIKE 行为并保留本地状态
 */
const toggleLike = (picture: ExplorePicture) => {
  const id = String(picture.id)
  const next = new Set(likedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
    const numericId = toId(id)
    if (numericId !== undefined) {
      reportAction(numericId, ActionTypeEnum.LIKE, ActionSourceEnum.HOME_RECOMMEND)
    }
    showToast('已加入喜欢')
  }
  likedIds.value = next
}

/**
 * 复制文本：剪贴板权限被拒时退回 execCommand，保证不是安全上下文也能用
 */
const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)
    return copied
  }
}

/**
 * 分享：优先系统分享面板，失败或桌面端不支持时复制链接
 */
const sharePicture = async (picture: ExplorePicture) => {
  const url = `${window.location.origin}/picture/${picture.id}`
  let shared = false

  if (navigator.share) {
    try {
      await navigator.share({ title: picture.name, url })
      shared = true
    } catch (error) {
      // 用户主动取消：给个反馈，不再退到复制
      if (error instanceof DOMException && error.name === 'AbortError') {
        showToast('已取消分享')
        return
      }
    }
  }

  if (!shared) {
    const copied = await copyToClipboard(url)
    showToast(copied ? '链接已复制' : '复制失败，请手动复制')
  }

  const numericId = toId(String(picture.id))
  if (numericId !== undefined) {
    reportAction(numericId, ActionTypeEnum.SHARE, ActionSourceEnum.HOME_RECOMMEND)
  }
}

/**
 * 上报曝光
 */
const reportImpression = async (pictureId: number) => {
  await reportAction(pictureId, ActionTypeEnum.IMPRESSION, ActionSourceEnum.HOME_RECOMMEND)
}

const setImpressionRef = (el: Element | ComponentPublicInstance | null) => {
  if (!(el instanceof HTMLElement)) return
  if (impressionObserver) impressionObserver.observe(el)
}

const initImpressionObserver = () => {
  impressionObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        const pictureId = (entry.target as HTMLElement).dataset.pictureId
        if (!pictureId || observedPictures.value.has(pictureId)) return
        observedPictures.value.add(pictureId)
        const id = toId(pictureId)
        if (id !== undefined) reportImpression(id)
      })
    },
    { threshold: 0.5 },
  )
}

const initScrollObserver = () => {
  if (!loaderRef.value) return

  scrollObserver = new IntersectionObserver(
    (entries) => {
      if (!entries[0].isIntersecting || loadError.value || !displayHasMore.value || loading.value) {
        return
      }
      // 首屏列表渲染后 loader 会被推下去，先按新位置复核一次，避免一次加载两页
      window.requestAnimationFrame(() => {
        if (loadError.value || loading.value || !displayHasMore.value || !loaderRef.value) return
        if (loaderRef.value.getBoundingClientRect().top > window.innerHeight + 200) return
        loadMore()
      })
    },
    { root: null, rootMargin: '200px', threshold: 0 },
  )

  scrollObserver.observe(loaderRef.value)
}

/**
 * 按 `/` 聚焦搜索框
 */
const onWindowKeydown = (event: KeyboardEvent) => {
  if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey) return
  const target = event.target as HTMLElement | null
  if (
    target &&
    (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)
  ) {
    return
  }
  event.preventDefault()
  searchInputRef.value?.focus()
}

onMounted(() => {
  columnCount.value = resolveColumnCount(window.innerWidth, 0)
  columnHeights.value = new Array(columnCount.value).fill(0)

  void listPictureTagCategoryUsingGet()
    .then((res) => {
      if (res.data.code !== 0 || !res.data.data) return
      const cats = (res.data.data.categoryList || []).filter((cat) => cat !== '全部').slice(0, 6)
      categoryList.value = ['全部', ...cats]
    })
    .catch((error) => console.warn('获取分类失败', error))

  initImpressionObserver()
  initScrollObserver()
  loadPictures()

  window.addEventListener('resize', updateColumnCount)
  window.addEventListener('keydown', onWindowKeydown)
})

onUnmounted(() => {
  if (impressionObserver) impressionObserver.disconnect()
  if (scrollObserver) scrollObserver.disconnect()
  if (resizeTimer) window.clearTimeout(resizeTimer)
  if (toastTimer) window.clearTimeout(toastTimer)
  window.removeEventListener('resize', updateColumnCount)
  window.removeEventListener('keydown', onWindowKeydown)
})
</script>

<style scoped lang="less">
#homePage {
  /* 页面自有调色，与落地页同一套深空极光语言 */
  --ex-bg: #05060c;
  --ex-bg-2: #090b15;
  --ex-ink: #f4f1ea;
  --ex-ink-2: rgba(244, 241, 234, 0.72);
  --ex-ink-3: rgba(244, 241, 234, 0.52);
  --ex-line: rgba(244, 241, 234, 0.1);
  --ex-line-2: rgba(244, 241, 234, 0.2);
  --ex-violet: #b7a2ff;
  --ex-violet-deep: #6f4bd8;
  --ex-mint: #86e3c8;
  /* 压在照片上的文字/图标始终保持亮色，不随浅色主题变深 */
  --ex-mint-media: #86e3c8;
  --ex-surface: rgba(244, 241, 234, 0.04);
  --ex-surface-2: rgba(244, 241, 234, 0.08);

  --ex-serif: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --ex-sans: 'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --ex-mono: 'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace;
  --ex-ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  min-height: 100vh;
  background: var(--ex-bg);
  color: var(--ex-ink);
  font-family: var(--ex-sans);
  font-weight: 400;
  overflow-x: clip;
  isolation: isolate;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#homePage ::selection {
  background: rgba(183, 162, 255, 0.28);
  color: #fff;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* ========== 氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(120% 80% at 60% -10%, #0b0d1c 0%, var(--ex-bg) 55%, #03040a 100%);
}

.ex-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(244, 241, 234, 0.03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(244, 241, 234, 0.03) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: radial-gradient(ellipse 75% 60% at 55% 18%, #000 0%, transparent 76%);
  -webkit-mask-image: radial-gradient(ellipse 75% 60% at 55% 18%, #000 0%, transparent 76%);
}

.ex-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  animation: ex-drift 26s ease-in-out infinite alternate;
}

.ex-orb-1 {
  width: 60vw;
  height: 44vw;
  top: -20vw;
  right: -12vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.34) 0%, transparent 68%);
}

.ex-orb-2 {
  width: 46vw;
  height: 40vw;
  top: 30%;
  left: -18vw;
  background: radial-gradient(circle, rgba(134, 227, 200, 0.1) 0%, transparent 70%);
  animation-delay: -9s;
}

.ex-orb-3 {
  width: 34vw;
  height: 34vw;
  bottom: -10vw;
  right: 8%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.12) 0%, transparent 70%);
  animation-delay: -16s;
}

@keyframes ex-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-3%, 4%, 0) scale(1.08);
  }
}

.ex-grain {
  position: absolute;
  inset: -40%;
  opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  animation: ex-grain-shift 8s steps(6) infinite;
}

@keyframes ex-grain-shift {
  0%,
  100% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(-2%, 3%);
  }
  50% {
    transform: translate(3%, -2%);
  }
  75% {
    transform: translate(-3%, -3%);
  }
}

.ex-vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(3, 4, 10, 0.7) 100%
  );
}

/* ========== 版式工具 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 clamp(16px, 2.4vw, 32px);
}

.mono {
  font-family: var(--ex-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--ex-ink-3);
}

.index {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: var(--ex-mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--ex-ink-3);

  b {
    font-weight: 400;
    color: var(--ex-violet);
  }
}

/* ========== 章节头 ========== */
.ex-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--ex-line);
  animation: ex-fade-up 0.8s var(--ex-ease) both;
}

.head-note {
  text-align: right;
  letter-spacing: 0.14em;
}

/* ========== Hero ========== */
.hero-section {
  position: relative;
  padding: clamp(44px, 8vh, 92px) 0 clamp(36px, 6vh, 64px);
}

.hero-body {
  max-width: 900px;
}

.hero-title {
  margin: clamp(28px, 5vh, 48px) 0 0;
  font-family: var(--ex-serif);
  font-size: clamp(46px, 7.2vw, 96px);
  font-weight: 600;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--ex-ink);
  text-wrap: balance;
  animation: ex-fade-up 0.95s var(--ex-ease) 0.04s both;
}

.title-accent {
  position: relative;
  display: inline-block;
  padding-bottom: 0.06em;
  color: var(--ex-violet);
}

.title-mark {
  position: absolute;
  left: 0;
  bottom: -0.06em;
  width: 100%;
  height: 0.11em;
  overflow: visible;
  pointer-events: none;

  path {
    fill: none;
    stroke: var(--ex-mint);
    stroke-width: 1.6;
    stroke-linecap: round;
    opacity: 0.85;
  }
}

.hero-subtitle {
  max-width: 44em;
  margin: clamp(18px, 3vh, 26px) 0 0;
  font-size: clamp(15px, 1.25vw, 17px);
  line-height: 1.9;
  color: var(--ex-ink-2);
  text-wrap: pretty;
  animation: ex-fade-up 0.9s var(--ex-ease) 0.1s both;
}

@keyframes ex-fade-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ========== 搜索 ========== */
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 720px;
  min-height: 64px;
  margin-top: clamp(28px, 4.5vh, 40px);
  padding: 0 8px 0 20px;
  background: var(--ex-surface);
  border: 1px solid var(--ex-line);
  border-radius: 3px;
  transition:
    border-color 0.4s var(--ex-ease),
    background-color 0.4s var(--ex-ease),
    box-shadow 0.4s var(--ex-ease);
  animation: ex-fade-up 0.9s var(--ex-ease) 0.16s both;

  &:hover {
    border-color: var(--ex-line-2);
  }

  &.is-focused {
    border-color: var(--ex-violet);
    background: var(--ex-surface-2);
    box-shadow: 0 0 0 3px rgba(183, 162, 255, 0.12);
  }
}

.search-icon {
  flex-shrink: 0;
  color: var(--ex-ink-3);
  transition: color 0.3s var(--ex-ease);
}

.search-bar.is-focused .search-icon {
  color: var(--ex-violet);
}

.search-input {
  flex: 1;
  min-width: 0;
  padding: 18px 0;
  background: none;
  border: 0;
  outline: none;
  font-family: var(--ex-sans);
  font-size: 16px;
  letter-spacing: 0.02em;
  color: var(--ex-ink);

  &::placeholder {
    color: var(--ex-ink-3);
  }

  &::-webkit-search-cancel-button {
    display: none;
  }
}

.search-clear {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border: 0;
  border-radius: 50%;
  background: none;
  color: var(--ex-ink-3);
  cursor: pointer;
  transition:
    color 0.25s var(--ex-ease),
    background-color 0.25s var(--ex-ease);

  &:hover {
    color: var(--ex-ink);
    background: var(--ex-surface-2);
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 2px;
  }
}

.search-hint {
  flex-shrink: 0;
  padding: 3px 6px;
  border: 1px solid var(--ex-line);
  border-radius: 2px;
  font-size: 9px;
  letter-spacing: 0.14em;
}

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 22px;
  flex-shrink: 0;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(120deg, #efe9ff 0%, #c9b6ff 55%, #a98bff 100%);
  color: #0b0714;
  font-family: var(--ex-sans);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 10px 30px -14px rgba(167, 139, 250, 0.7);
  transition:
    transform 0.4s var(--ex-ease),
    box-shadow 0.5s var(--ex-ease),
    opacity 0.3s var(--ex-ease);

  svg {
    transition: transform 0.4s var(--ex-ease);
  }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 16px 36px -16px rgba(167, 139, 250, 0.85);

    svg {
      transform: translateX(3px);
    }
  }

  &:active {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.55;
    cursor: default;
    transform: none;
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 3px;
  }
}

/* ========== 分类 ========== */
.category-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: clamp(22px, 3.4vh, 30px);
  animation: ex-fade-up 0.9s var(--ex-ease) 0.22s both;
}

.category-label {
  flex-shrink: 0;
  padding-right: 18px;
  border-right: 1px solid var(--ex-line);
  letter-spacing: 0.2em;
}

.category-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 22px;
}

.category-item {
  position: relative;
  padding: 7px 0;
  border: 0;
  background: none;
  font-family: var(--ex-sans);
  font-size: 14px;
  letter-spacing: 0.04em;
  color: var(--ex-ink-3);
  cursor: pointer;
  transition: color 0.3s var(--ex-ease);

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 1px;
    background: var(--ex-violet);
    transform: scaleX(0);
    transform-origin: left;
    transition: transform 0.45s var(--ex-ease);
  }

  &:hover {
    color: var(--ex-ink);
  }

  &.active {
    color: var(--ex-ink);

    &::after {
      transform: scaleX(1);
    }
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 3px;
    border-radius: 2px;
  }
}

/* ========== 跳到画廊 ========== */
.hero-scroll {
  position: absolute;
  right: 0;
  bottom: clamp(40px, 7vh, 72px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--ex-ink-3);
  cursor: pointer;
  transition: color 0.3s var(--ex-ease);

  .mono {
    font-size: 9px;
    letter-spacing: 0.24em;
  }

  svg {
    transition: transform 0.4s var(--ex-ease);
  }

  &:hover {
    color: var(--ex-ink);

    svg {
      transform: translateY(3px);
    }
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 4px;
  }
}

.scroll-line {
  position: relative;
  width: 1px;
  height: 48px;
  overflow: hidden;
  background: linear-gradient(var(--ex-line-2), transparent);

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(var(--ex-violet), transparent);
    transform: translateY(-100%);
    animation: ex-scan 2.6s var(--ex-ease) infinite;
  }
}

@keyframes ex-scan {
  0% {
    transform: translateY(-100%);
  }
  60%,
  100% {
    transform: translateY(100%);
  }
}

/* ========== 画廊 ========== */
.gallery-section {
  padding: clamp(10px, 2vh, 24px) 0 clamp(64px, 10vh, 120px);
  scroll-margin-top: 96px;
}

.gallery-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 16px;
  margin-bottom: clamp(20px, 3vh, 32px);
  border-bottom: 1px solid var(--ex-line);
  animation: ex-fade-up 0.8s var(--ex-ease) 0.28s both;
}

.gallery-head-main {
  min-width: 0;
}

.gallery-title {
  margin: 10px 0 0;
  font-family: var(--ex-serif);
  font-size: clamp(22px, 2.6vw, 32px);
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.01em;
  color: var(--ex-ink);
  overflow-wrap: anywhere;
}

.gallery-count {
  flex-shrink: 0;
  text-align: right;
  letter-spacing: 0.14em;
}

/* ========== 瀑布流 ========== */
.masonry-wrapper {
  display: flex;
  gap: 18px;
  width: 100%;
}

.masonry-column {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
  gap: 18px;
}

.gallery-item {
  position: relative;
  outline: none;
  cursor: pointer;
  animation: ex-item-in 0.7s var(--ex-ease) both;
}

@keyframes ex-item-in {
  from {
    opacity: 0;
    transform: translateY(22px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.item-image-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 3px;
  background: var(--ex-surface);
  transform: translateZ(0);
  transition: box-shadow 0.6s var(--ex-ease);

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    border: 1px solid rgba(244, 241, 234, 0.08);
    border-radius: 3px;
    pointer-events: none;
  }
}

.gallery-item:hover .item-image-wrap {
  box-shadow: 0 26px 60px -30px rgba(0, 0, 0, 0.85);
}

.gallery-item:focus-visible .item-image-wrap {
  outline: 2px solid var(--ex-violet);
  outline-offset: 3px;
}

.item-skeleton {
  position: absolute;
  inset: 0;
  background: var(--ex-surface-2);
  animation: ex-pulse 1.6s ease-in-out infinite;
}

@keyframes ex-pulse {
  0%,
  100% {
    opacity: 0.45;
  }
  50% {
    opacity: 1;
  }
}

.item-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  filter: saturate(0.9) contrast(1.03);
  transform: scale(1.01);
  transition:
    opacity 0.5s ease,
    transform 0.9s var(--ex-ease);

  &.loaded {
    opacity: 1;
  }

  &.is-error {
    opacity: 0;
  }
}

.gallery-item:hover .item-image,
.gallery-item:focus-visible .item-image {
  transform: scale(1.05);
}

.item-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  background: linear-gradient(
    to top,
    rgba(4, 5, 12, 0.92) 0%,
    rgba(4, 5, 12, 0.5) 42%,
    rgba(4, 5, 12, 0) 78%
  );
  opacity: 0;
  transition: opacity 0.45s var(--ex-ease);
}

.gallery-item:hover .item-overlay,
.gallery-item:focus-visible .item-overlay,
.gallery-item:focus-within .item-overlay {
  opacity: 1;
}

.item-index {
  position: absolute;
  top: 14px;
  left: 16px;
  font-size: 9px;
  letter-spacing: 0.18em;
  color: var(--ex-mint-media);
  opacity: 0.9;
}

.overlay-content {
  display: flex;
  flex-direction: column;
}

.item-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin: 0 0 6px;
  font-family: var(--ex-serif);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.32;
  color: #f7f4ee;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 9.5px;
  letter-spacing: 0.14em;
  color: rgba(244, 241, 234, 0.7);
}

.meta-divider {
  color: rgba(244, 241, 234, 0.35);
}

.overlay-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.action-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #f7f4ee;
  cursor: pointer;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition:
    background-color 0.3s var(--ex-ease),
    border-color 0.3s var(--ex-ease),
    color 0.3s var(--ex-ease),
    transform 0.3s var(--ex-ease);

  &:hover {
    transform: translateY(-1px);
    background: rgba(255, 255, 255, 0.18);
    border-color: rgba(255, 255, 255, 0.3);
  }

  &:focus-visible {
    outline: 2px solid var(--ex-mint-media);
    outline-offset: 2px;
  }

  &.is-liked {
    color: var(--ex-mint-media);
    border-color: rgba(134, 227, 200, 0.55);
    background: rgba(134, 227, 200, 0.14);
  }
}

/* 触屏没有 hover：常驻一条轻量图注，操作按钮交给详情页 */
@media (hover: none) {
  .item-overlay {
    padding: 14px;
    opacity: 1;
    background: linear-gradient(
      to top,
      rgba(4, 5, 12, 0.8) 0%,
      rgba(4, 5, 12, 0.25) 38%,
      transparent 62%
    );
  }

  .item-index,
  .overlay-actions {
    display: none;
  }

  .item-title {
    margin-bottom: 3px;
    font-size: 14px;
  }

  .item-meta {
    font-size: 8.5px;
  }
}

/* ========== 加载 / 结束 / 空 ========== */
.gallery-loader {
  margin-top: clamp(40px, 6vh, 64px);
  min-height: 120px;
}

.loader-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 48px 24px;
}

.loader-ring {
  width: 22px;
  height: 22px;
  border: 1.5px solid var(--ex-line-2);
  border-top-color: var(--ex-violet);
  border-radius: 50%;
  animation: ex-spin 0.9s linear infinite;
}

@keyframes ex-spin {
  to {
    transform: rotate(360deg);
  }
}

.loader-text {
  font-size: 10px;
  letter-spacing: 0.22em;
}

.end-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: clamp(40px, 6vh, 72px) 24px 12px;
  text-align: center;
}

.end-rule {
  width: 1px;
  height: clamp(36px, 6vh, 64px);
  margin-bottom: 22px;
  background: linear-gradient(var(--ex-line-2), transparent);
}

.end-note {
  font-size: 9.5px;
  letter-spacing: 0.24em;
}

.end-title {
  margin: 16px 0 10px;
  font-family: var(--ex-serif);
  font-size: clamp(26px, 3.2vw, 40px);
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--ex-ink);
}

.end-subtitle {
  margin: 0 0 28px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--ex-ink-2);
}

.end-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.end-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 24px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-family: var(--ex-sans);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    transform 0.4s var(--ex-ease),
    background-color 0.4s var(--ex-ease),
    border-color 0.4s var(--ex-ease),
    box-shadow 0.5s var(--ex-ease);

  svg {
    transition: transform 0.45s var(--ex-ease);
  }

  &:active {
    transform: scale(0.985);
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 3px;
  }

  &.primary {
    color: #0b0714;
    background: linear-gradient(120deg, #efe9ff 0%, #c9b6ff 55%, #a98bff 100%);
    box-shadow: 0 10px 30px -14px rgba(167, 139, 250, 0.6);

    &:hover {
      transform: translateY(-2px);
    }
  }

  &.ghost {
    color: var(--ex-ink);
    border-color: var(--ex-line-2);
    background: rgba(244, 241, 234, 0.02);

    &:hover {
      transform: translateY(-2px);
      border-color: rgba(244, 241, 234, 0.42);
      background: rgba(244, 241, 234, 0.05);

      svg {
        transform: rotate(180deg);
      }
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: clamp(40px, 7vh, 72px) 24px;
  text-align: center;
}

.empty-illustration {
  margin-bottom: 26px;
  color: var(--ex-ink-3);
  animation: ex-float 6s ease-in-out infinite;

  svg {
    display: block;
  }
}

@keyframes ex-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.empty-title {
  margin: 0 0 10px;
  font-family: var(--ex-serif);
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 600;
  color: var(--ex-ink);
}

.empty-desc {
  max-width: 34em;
  margin: 0 0 26px;
  font-size: 14px;
  line-height: 1.85;
  color: var(--ex-ink-2);
  text-wrap: pretty;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 46px;
  padding: 0 24px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(120deg, #efe9ff 0%, #c9b6ff 55%, #a98bff 100%);
  color: #0b0714;
  font-family: var(--ex-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.4s var(--ex-ease),
    box-shadow 0.5s var(--ex-ease);

  &:hover {
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--ex-violet);
    outline-offset: 3px;
  }
}

/* ========== 曝光追踪器 ========== */
.impression-tracker {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

/* ========== 轻提示 ========== */
.ex-toast {
  position: fixed;
  left: 50%;
  bottom: 32px;
  z-index: 60;
  padding: 10px 18px;
  border: 1px solid rgba(244, 241, 234, 0.18);
  border-radius: 999px;
  background: #0b0714;
  color: #f4f1ea;
  font-size: 10px;
  letter-spacing: 0.14em;
  box-shadow: 0 18px 40px -18px rgba(0, 0, 0, 0.8);
  transform: translateX(-50%);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.3s ease,
    transform 0.3s var(--ex-ease);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

/* ===================================================================
   浅色主题（theme-pop / 紫漾）：同一套版式，换成纸面与墨字
   颜色取自 design-tokens-ziyan.css
   =================================================================== */
#homePage.is-light {
  --ex-bg: #faf7ff;
  --ex-bg-2: #f5f3ff;
  --ex-ink: #1e1b4b;
  --ex-ink-2: rgba(30, 27, 75, 0.74);
  /* 0.64 ≈ 4.8:1，小号等宽标签在浅纸上仍可读 */
  --ex-ink-3: rgba(30, 27, 75, 0.64);
  --ex-line: rgba(124, 58, 237, 0.16);
  --ex-line-2: rgba(124, 58, 237, 0.34);
  --ex-violet: #9333ea;
  --ex-violet-deep: #7c3aed;
  --ex-mint: #047857;
  --ex-surface: #ffffff;
  --ex-surface-2: #f5f3ff;
}

#homePage.is-light .ambient-bg {
  background: radial-gradient(120% 80% at 50% -10%, #ffffff 0%, var(--ex-bg) 55%, #efe9ff 100%);
}

#homePage.is-light .ex-orb {
  opacity: 0.3;
}

#homePage.is-light .ex-orb-1 {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 68%);
}

#homePage.is-light .ex-orb-2 {
  background: radial-gradient(circle, rgba(236, 72, 153, 0.16) 0%, transparent 70%);
}

#homePage.is-light .ex-orb-3 {
  background: radial-gradient(circle, rgba(34, 211, 238, 0.14) 0%, transparent 70%);
}

#homePage.is-light .ex-grid {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.07) 1px, transparent 1px);
}

#homePage.is-light .ex-grain {
  opacity: 0.028;
}

#homePage.is-light .ex-vignette {
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(237, 233, 254, 0.55) 100%
  );
}

#homePage.is-light .search-bar.is-focused {
  box-shadow: 0 0 0 3px rgba(147, 51, 234, 0.12);
}

/* 白字压在紫→品红渐变上，对比度 ≥ 5:1 */
#homePage.is-light .search-btn,
#homePage.is-light .end-btn.primary,
#homePage.is-light .empty-action {
  color: #fff;
  background: linear-gradient(120deg, #7c3aed 0%, #9333ea 55%, #c026d3 100%);
  box-shadow: 0 10px 26px -14px rgba(147, 51, 234, 0.55);
}

#homePage.is-light .item-image-wrap {
  background: #f1ecff;

  &::after {
    border-color: rgba(124, 58, 237, 0.12);
  }
}

#homePage.is-light .gallery-item:hover .item-image-wrap {
  box-shadow: 0 26px 50px -32px rgba(76, 29, 149, 0.5);
}

#homePage.is-light .end-btn.ghost {
  background: rgba(255, 255, 255, 0.7);

  &:hover {
    background: #fff;
    border-color: rgba(124, 58, 237, 0.5);
  }
}

#homePage.is-light .ex-toast {
  background: #1e1b4b;
}

/* ========== 响应式 ========== */
@media (max-width: 1200px) {
  .masonry-wrapper,
  .masonry-column {
    gap: 16px;
  }
}

@media (max-width: 1080px) {
  .hero-scroll {
    display: none;
  }
}

@media (max-width: 900px) {
  .hero-body {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .masonry-wrapper,
  .masonry-column {
    gap: 12px;
  }

  .hero-section {
    padding-top: 36px;
  }

  .hero-title {
    font-size: clamp(38px, 11vw, 56px);
  }

  .search-bar {
    gap: 10px;
    min-height: 58px;
    padding: 0 6px 0 16px;
  }

  .search-hint {
    display: none;
  }

  .search-btn {
    height: 44px;
    padding: 0 18px;
  }

  .category-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .category-label {
    padding-right: 0;
    border-right: 0;
  }

  .category-list {
    gap: 2px 18px;
  }

  .gallery-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .gallery-count {
    text-align: left;
  }
}

@media (max-width: 640px) {
  .masonry-wrapper,
  .masonry-column {
    gap: 10px;
  }

  .ex-head {
    gap: 14px;
  }

  .head-note {
    font-size: 9px;
    letter-spacing: 0.1em;
  }

  .search-btn {
    padding: 0 16px;
  }
}

@media (max-width: 360px) {
  /* 320px 宽度下把文案让给输入框，按钮只留箭头 */
  .search-btn-text {
    display: none;
  }

  .search-btn {
    padding: 0 16px;
  }
}

/* ========== 降低动态效果 ========== */
@media (prefers-reduced-motion: reduce) {
  .ex-orb,
  .ex-grain,
  .loader-ring,
  .empty-illustration,
  .scroll-line::after {
    animation: none !important;
  }

  .ex-head,
  .hero-title,
  .hero-subtitle,
  .search-bar,
  .category-bar,
  .gallery-head,
  .gallery-item {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }

  .item-image,
  .action-btn,
  .end-btn,
  .search-btn {
    transition: none !important;
  }

  .gallery-item:hover .item-image,
  .gallery-item:focus-visible .item-image {
    transform: none;
  }
}
</style>
