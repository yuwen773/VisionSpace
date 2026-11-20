<template>
  <div id="homePage" class="signature-page explore-page">
    <!-- 氛围层：与落地页共用一套静态光晕 + 网格 -->
    <div class="ambient-bg" aria-hidden="true">
      <div class="ambient-glow glow-a"></div>
      <div class="ambient-glow glow-b"></div>
      <div class="ambient-grid"></div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- Hero 区域：搜索是这一页的主角 -->
      <section class="hero-section" ref="heroSection">
        <h1 class="hero-title">发现视觉灵感</h1>
        <p class="hero-subtitle">按颜色、标签或一句话，从社区作品里找到下一张参考。</p>

        <div class="search-capsule" :class="{ 'search-focused': isSearchFocused }">
          <Search :size="20" :stroke-width="2" class="search-icon" />
          <input
            v-model="searchText"
            type="text"
            placeholder="搜索图片、标签或创作者"
            class="search-input"
            aria-label="搜索图片"
            @focus="isSearchFocused = true"
            @blur="isSearchFocused = false"
            @keydown.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">
            <span>探索</span>
            <ArrowRight :size="16" :stroke-width="2" />
          </button>
        </div>

        <div class="category-pills" role="group" aria-label="图片分类">
          <button
            v-for="cat in categoryList"
            :key="cat"
            class="category-pill"
            :class="{ active: selectedCategory === cat }"
            :aria-pressed="selectedCategory === cat"
            @click="selectCategory(cat)"
          >
            {{ cat }}
          </button>
        </div>
      </section>

      <!-- 图片画廊区域 -->
      <section class="gallery-section">
        <!-- 瀑布流画廊 - CSS Columns 实现 -->
        <div class="masonry-wrapper" ref="galleryRef">
          <div class="masonry-column" v-for="col in columnCount" :key="col">
            <div
              v-for="picture in getColumnPictures(col - 1)"
              :key="picture.id"
              class="gallery-item"
              :style="{ animationDelay: `${(pictureList.indexOf(picture) % 12) * 0.05}s` }"
            >
              <div
                class="item-image-wrap"
                :style="{ paddingBottom: getAspectRatio(picture) }"
                role="link"
                tabindex="0"
                :aria-label="`查看 ${picture.name}`"
                @click="handlePictureClick(picture)"
                @keydown.enter="handlePictureClick(picture)"
              >
                <div class="item-skeleton" v-if="!loadedImages.has(picture.id)"></div>
                <img
                  :src="contentSrc(picture.thumbnailUrl || picture.url)"
                  :alt="picture.name"
                  class="item-image"
                  :class="{ loaded: loadedImages.has(picture.id) }"
                  loading="lazy"
                  decoding="async"
                  @load="onImageLoad(picture.id)"
                />
              </div>
              <div class="item-overlay">
                <div class="overlay-content">
                  <h3 class="item-title">{{ picture.name }}</h3>
                  <div class="item-meta tabular">
                    <span>{{ picture.picWidth }} × {{ picture.picHeight }}</span>
                    <span>{{ picture.picFormat?.toUpperCase() }}</span>
                  </div>
                </div>
              </div>
              <!-- 曝光追踪 -->
              <div
                class="impression-tracker"
                :data-picture-id="picture.id"
                :ref="(el) => setImpressionRef(el, picture.id)"
              />
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div class="gallery-loader" ref="loaderRef">
          <!-- 首屏：与瀑布流同构的骨架 -->
          <div
            v-if="loading && pictureList.length === 0"
            class="gallery-skeleton"
            aria-hidden="true"
          >
            <div
              v-for="n in 8"
              :key="n"
              class="skeleton-card"
              :class="`skeleton-${((n - 1) % 4) + 1}`"
            ></div>
          </div>

          <!-- 翻页：安静的三点提示 -->
          <div v-else-if="loading" class="loader-content" role="status" aria-live="polite">
            <span class="loader-dot"></span>
            <span class="loader-dot"></span>
            <span class="loader-dot"></span>
            <span class="loader-text">正在加载更多</span>
          </div>

          <!-- 到底了 -->
          <div class="end-section" v-else-if="!hasMore && pictureList.length > 0">
            <p class="end-title">已经到底了</p>
            <p class="end-subtitle tabular">共发现 {{ total }} 张图片</p>
            <div class="end-actions">
              <button class="end-btn" @click="backToTop">
                <ArrowUp :size="16" :stroke-width="2" />
                <span>回到顶部</span>
              </button>
              <button class="end-btn secondary" @click="refreshGallery">
                <RefreshCw :size="16" :stroke-width="2" />
                <span>换一批</span>
              </button>
            </div>
          </div>

          <!-- 空状态 -->
          <div class="empty-state" v-else-if="!loading && pictureList.length === 0">
            <div class="empty-icon">
              <ImageOff :size="28" :stroke-width="1.5" />
            </div>
            <h3 class="empty-title">这里还没有图片</h3>
            <p class="empty-desc">上传第一张作品，或者换一个分类看看。</p>
            <button class="empty-action" @click="goToUpload">
              <Plus :size="18" :stroke-width="2" />
              <span>上传图片</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, type ComponentPublicInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, ArrowUp, ImageOff, Plus, RefreshCw, Search } from 'lucide-vue-next'
import { type PictureRecommendRequest } from '@/api-custom/picture'
import { ActionTypeEnum, ActionSourceEnum } from '@/api-custom/picture'
import { usePictureAction } from '@/composables/usePictureAction'
import { useRecommendPaging } from '@/composables/useRecommendPaging'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController'
import { contentSrc, toId } from '@/utils'

const router = useRouter()
const { report: reportAction } = usePictureAction()
// 分页状态：页码、总数与分页上下文都收在这里，翻页时把上下文带回服务端
const { page, total, hasMore, reset: resetPaging, loadPage, nextPage } = useRecommendPaging()

// Refs
const heroSection = ref<HTMLElement>()
const galleryRef = ref<HTMLElement>()
const loaderRef = ref<HTMLElement>()

// 搜索状态
const searchText = ref('')
const isSearchFocused = ref(false)

// 数据状态
const currentType = ref<PictureRecommendRequest['type']>('hot')
const pictureList = ref<any[]>([])
const loading = ref(false)

// 图片加载状态
const loadedImages = ref<Set<string>>(new Set())

// 分类数据
const categoryList = ref<string[]>(['全部', '风景', '人物', '艺术', '建筑', '自然', '美食'])
const selectedCategory = ref('全部')

// 瀑布流配置
const BREAKPOINT_LG = 1024
const BREAKPOINT_SM = 640
const columnCount = ref(4)

// 响应式列数
let resizeTimer: number | null = null
const updateColumnCount = () => {
  if (resizeTimer) return
  resizeTimer = window.setTimeout(() => {
    const width = window.innerWidth
    if (width >= BREAKPOINT_LG) columnCount.value = 4
    else if (width >= BREAKPOINT_SM) columnCount.value = 3
    else columnCount.value = 2
    resizeTimer = null
  }, 100)
}

// 列分配映射
const columnAssignment = ref<Map<string, number>>(new Map())
// 列高度追踪
const columnHeights = ref<number[]>(new Array(columnCount.value).fill(0))

// 曝光追踪
const observedPictures = ref<Set<string>>(new Set())
let impressionObserver: IntersectionObserver | null = null
let scrollObserver: IntersectionObserver | null = null

/**
 * 图片加载完成
 */
const onImageLoad = (pictureId: string) => {
  loadedImages.value.add(pictureId)
}

/**
 * 计算图片宽高比
 */
const getAspectRatio = (picture: any) => {
  if (!picture.picWidth || !picture.picHeight) return '75%'
  return `${(picture.picHeight / picture.picWidth) * 100}%`
}

/**
 * 获取指定列的图片
 */
const getColumnPictures = (colIndex: number) => {
  return pictureList.value.filter((picture) => {
    return columnAssignment.value.get(picture.id) === colIndex
  })
}

/**
 * 获取当前最短列的索引
 */
const getShortestColumn = () => {
  return columnHeights.value.indexOf(Math.min(...columnHeights.value))
}

/**
 * 计算图片应该放入哪一列（基于宽高比估算高度）
 */
const calculatePictureHeight = (picture: any): number => {
  if (!picture.picWidth || !picture.picHeight) {
    return 300 * 0.75 + 60
  }
  const estimatedCardWidth = (window.innerWidth - 96) / columnCount.value - 20
  const ratio = picture.picHeight / picture.picWidth
  return estimatedCardWidth * ratio + 60
}

/**
 * 将新加载的图片分配到最短列
 */
const assignPicturesToColumns = (newPictures: any[]) => {
  newPictures.forEach((picture) => {
    const shortestCol = getShortestColumn()
    columnAssignment.value.set(picture.id, shortestCol)
    const estimatedHeight = calculatePictureHeight(picture)
    columnHeights.value[shortestCol] += estimatedHeight + 20
  })
}

/**
 * 分类选择
 */
const selectCategory = (cat: string) => {
  selectedCategory.value = cat
  resetPaging()
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  loadedImages.value.clear()
  loadPictures()
}

/**
 * 搜索
 */
const doSearch = () => {
  resetPaging()
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  loadedImages.value.clear()
  loadPictures()
}

/**
 * 加载图片
 */
const loadPictures = async () => {
  if (loading.value) return
  loading.value = true

  try {
    const data = await loadPage(currentType.value)
    if (data.length > 0) {
      assignPicturesToColumns(data)

      if (page.value === 1) {
        pictureList.value = data
      } else {
        pictureList.value.push(...data)
      }
    }
  } finally {
    loading.value = false
  }
}

/**
 * 无限滚动加载
 */
const loadMore = () => {
  if (loading.value || !hasMore.value) return
  nextPage()
  loadPictures()
}

/**
 * 回到顶部
 */
const backToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * 刷新画廊
 */
const refreshGallery = () => {
  resetPaging()
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  loadedImages.value.clear()
  backToTop()
  setTimeout(() => loadPictures(), 300)
}

/**
 * 跳转到上传页面
 */
const goToUpload = () => {
  router.push('/add_picture')
}

/**
 * 点击图片
 */
const handlePictureClick = async (picture: any) => {
  await reportAction(picture.id, ActionTypeEnum.CLICK, ActionSourceEnum.HOME_RECOMMEND)
  router.push(`/picture/${picture.id}`)
}

/**
 * 上报曝光
 */
const reportImpression = async (pictureId: number) => {
  await reportAction(pictureId, ActionTypeEnum.IMPRESSION, ActionSourceEnum.HOME_RECOMMEND)
}

/**
 * 设置曝光追踪 ref
 */
const setImpressionRef = (el: Element | ComponentPublicInstance | null, pictureId: number) => {
  if (!(el instanceof HTMLElement)) return
  if (impressionObserver) {
    impressionObserver.observe(el)
  }
}

/**
 * 初始化曝光 Observer
 */
const initImpressionObserver = () => {
  impressionObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const pictureId = (entry.target as HTMLElement).dataset.pictureId as string
          if (pictureId && !observedPictures.value.has(pictureId)) {
            observedPictures.value.add(pictureId)
            const id = toId(pictureId)
            if (id !== undefined) reportImpression(id)
          }
        }
      })
    },
    { threshold: 0.5 },
  )
}

/**
 * 初始化滚动 Observer
 */
const initScrollObserver = () => {
  if (!loaderRef.value) return

  scrollObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && hasMore.value && !loading.value) {
        loadMore()
      }
    },
    { root: null, rootMargin: '200px', threshold: 0 },
  )

  scrollObserver.observe(loaderRef.value)
}

onMounted(async () => {
  // 获取分类
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      const cats = res.data.data.categoryList || []
      categoryList.value = ['全部', ...cats.slice(0, 6)]
    }
  } catch (error) {
    console.error('获取分类失败', error)
  }

  updateColumnCount()
  initImpressionObserver()
  initScrollObserver()
  loadPictures()

  window.addEventListener('resize', updateColumnCount)
})

onUnmounted(() => {
  if (impressionObserver) impressionObserver.disconnect()
  if (scrollObserver) scrollObserver.disconnect()
  window.removeEventListener('resize', updateColumnCount)
})
</script>

<style scoped lang="less">
/* ============================================================
   Explore - signature layer
   圆角：卡片 16px / 控件 999px / 图标容器 20px
   单一强调色：--color-primary（极光蓝 / 紫漾紫）
   ============================================================ */

#homePage {
  position: relative;
  min-height: 100dvh;
  overflow-x: hidden;
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-family: var(--font-body);
}

/* ---------- 氛围层 ---------- */
.ambient-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.ambient-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(140px);
  opacity: 0.5;
}

.glow-a {
  width: 64vw;
  height: 64vw;
  top: -30vw;
  right: -20vw;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--color-primary) 26%, transparent) 0%,
    transparent 66%
  );
}

.glow-b {
  width: 56vw;
  height: 56vw;
  bottom: -26vw;
  left: -22vw;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--color-secondary) 18%, transparent) 0%,
    transparent 68%
  );
}

.ambient-grid {
  position: absolute;
  inset: 0;
  background-image: var(--pattern-grid);
  background-size: 72px 72px;
  opacity: 0.45;
  -webkit-mask-image: radial-gradient(ellipse at 50% 0%, #000 0%, transparent 62%);
  mask-image: radial-gradient(ellipse at 50% 0%, #000 0%, transparent 62%);
}

/* ---------- 主内容 ---------- */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 clamp(20px, 4vw, 32px);
}

/* ---------- Hero ---------- */
.hero-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 860px;
  margin: 0 auto;
  padding: clamp(88px, 12vh, 140px) 0 clamp(40px, 6vh, 64px);
  text-align: center;
}

.hero-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(2.6rem, 6vw, 4.25rem);
  font-weight: 700;
  line-height: 1.06;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
  text-wrap: balance;
}

.hero-subtitle {
  margin: clamp(16px, 2.2vh, 22px) 0 0;
  max-width: 30em;
  font-size: clamp(0.98rem, 1.4vw, 1.1rem);
  line-height: 1.7;
  color: var(--color-text-secondary);
}

/* ---------- 搜索胶囊 ---------- */
.search-capsule {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 720px;
  margin-top: clamp(28px, 4vh, 40px);
  padding: 8px 8px 8px 20px;
  border-radius: 999px;
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-elevated);
  box-shadow: var(--shadow-lg);
  transition:
    border-color 0.2s ease,
    box-shadow 0.25s ease;

  &:hover {
    border-color: var(--color-border-strong);
  }

  &.search-focused {
    border-color: var(--color-primary);
    box-shadow:
      0 0 0 4px color-mix(in srgb, var(--color-primary) 16%, transparent),
      var(--shadow-lg);
  }
}

.search-icon {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  transition: color 0.2s ease;
}

.search-capsule.search-focused .search-icon {
  color: var(--color-primary);
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--color-text-primary);

  &::placeholder {
    color: var(--color-text-tertiary);
  }
}

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 12px 24px;
  border: none;
  border-radius: 999px;
  background: var(--color-primary-600);
  color: #ffffff;
  font-family: var(--font-body);
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    background: var(--color-primary-700);
  }

  &:active {
    transform: scale(0.97);
  }
}

/* ---------- 分类 ---------- */
.category-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: clamp(22px, 3vh, 28px);
}

.category-pill {
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border-default);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    border-color: var(--color-border-strong);
    color: var(--color-text-primary);
  }

  &.active {
    border-color: var(--color-primary-600);
    background: var(--color-primary-600);
    color: #ffffff;
  }

  &:active {
    transform: scale(0.97);
  }
}

/* ---------- 画廊 ---------- */
.gallery-section {
  padding: 24px 0 96px;
}

.masonry-wrapper {
  display: flex;
  gap: 18px;
  width: 100%;
}

.masonry-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.gallery-item {
  position: relative;
  border-radius: 16px;
  cursor: pointer;
  outline-offset: 4px;
  opacity: 0;
  animation: item-reveal 0.55s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes item-reveal {
  from {
    opacity: 0;
    transform: translateY(18px);
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
  border-radius: 16px;
  border: 1px solid var(--color-border-subtle);
  background: var(--color-bg-secondary);
  transition:
    border-color 0.25s ease,
    box-shadow 0.25s ease,
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.gallery-item:hover .item-image-wrap {
  border-color: var(--color-border-default);
  box-shadow: var(--shadow-card);
  transform: translateY(-3px);
}

.item-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    var(--color-bg-tertiary) 0%,
    var(--color-bg-elevated) 50%,
    var(--color-bg-tertiary) 100%
  );
  background-size: 200% 100%;
  animation: skeleton 1.4s ease-in-out infinite;
}

@keyframes skeleton {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.item-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition:
    opacity 0.4s ease,
    transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.item-image.loaded {
  opacity: 1;
}

.gallery-item:hover .item-image {
  transform: scale(1.03);
}

/* 中性遮罩：照片上的文字用白色，不套品牌紫 */
.item-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  border-radius: 16px;
  pointer-events: none;
  color: #ffffff;
  background: linear-gradient(
    to top,
    rgba(6, 8, 15, 0.88) 0%,
    rgba(6, 8, 15, 0.45) 42%,
    rgba(6, 8, 15, 0) 72%
  );
  opacity: 0;
  transition: opacity 0.25s ease;
}

.gallery-item:hover .item-overlay,
.gallery-item:focus-within .item-overlay {
  opacity: 1;
}

.overlay-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.72);
}

/* ---------- 加载 / 到底 / 空态 ---------- */
.gallery-loader {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 160px;
  margin-top: 56px;
}

.gallery-skeleton {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  width: 100%;
}

.skeleton-card {
  border-radius: 16px;
  border: 1px solid var(--color-border-subtle);
  background: linear-gradient(135deg, var(--color-bg-tertiary), var(--color-bg-elevated));
  animation: skeleton-fade 1.6s ease-in-out infinite;
}

.skeleton-1 {
  aspect-ratio: 4 / 5;
}
.skeleton-2 {
  aspect-ratio: 1 / 1;
}
.skeleton-3 {
  aspect-ratio: 3 / 4;
}
.skeleton-4 {
  aspect-ratio: 4 / 3;
}

@keyframes skeleton-fade {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

.loader-content {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 28px 0;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
}

.loader-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: dot-pulse 1.1s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.15s;
  }
  &:nth-child(3) {
    animation-delay: 0.3s;
  }
}

@keyframes dot-pulse {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.loader-text {
  margin-left: 8px;
}

.end-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 48px 0 8px;
  border-top: 1px solid var(--color-border-subtle);
  text-align: center;
}

.end-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.end-subtitle {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-tertiary);
}

.end-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.end-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 999px;
  border: 1px solid var(--color-primary-600);
  background: var(--color-primary-600);
  color: #ffffff;
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    background: var(--color-primary-700);
  }

  &:active {
    transform: scale(0.97);
  }

  &.secondary {
    background: transparent;
    border-color: var(--color-border-strong);
    color: var(--color-text-primary);

    &:hover {
      border-color: var(--color-primary);
      color: var(--color-primary);
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 420px;
  margin: 0 auto;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
  border-radius: 20px;
  border: 1px solid var(--color-border-subtle);
  background: var(--color-bg-elevated);
  color: var(--color-text-tertiary);
}

.empty-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.empty-desc {
  margin: 8px 0 24px;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text-tertiary);
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 22px;
  border: none;
  border-radius: 999px;
  background: var(--color-primary-600);
  color: #ffffff;
  font-family: var(--font-body);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    background: var(--color-primary-700);
  }

  &:active {
    transform: scale(0.97);
  }
}

/* ---------- 曝光追踪 ---------- */
.impression-tracker {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .masonry-wrapper,
  .masonry-column,
  .gallery-skeleton {
    gap: 16px;
  }
}

@media (max-width: 900px) {
  .gallery-skeleton {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 72px 0 32px;
  }

  .hero-title {
    font-size: clamp(2.2rem, 9vw, 3rem);
  }

  .search-capsule {
    padding: 6px 6px 6px 16px;
  }

  .search-btn {
    padding: 10px 18px;
  }

  .masonry-wrapper,
  .masonry-column {
    gap: 12px;
  }

  .gallery-section {
    padding: 16px 0 64px;
  }

  .gallery-skeleton {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .item-overlay {
    padding: 14px;
  }

  .end-actions {
    flex-direction: column;
    width: 100%;
    max-width: 280px;
  }

  .end-btn {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .main-content {
    padding: 0 14px;
  }

  .category-pill {
    padding: 7px 14px;
    font-size: 0.82rem;
  }
}
</style>
