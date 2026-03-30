<template>
  <div id="homePage">
    <!-- 沉浸式背景层 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <canvas id="particleCanvas" ref="particleCanvas"></canvas>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- Hero 区域 -->
      <section class="hero-section" ref="heroSection">
        <!-- 品牌标识 -->
        <div class="brand-section">
          <div class="brand-logo">
            <svg class="brand-icon" viewBox="0 0 48 48" fill="none">
              <circle cx="24" cy="24" r="20" stroke="url(#brandGradient)" stroke-width="3"/>
              <circle cx="24" cy="24" r="12" fill="url(#brandGradient)" opacity="0.3"/>
              <circle cx="24" cy="24" r="6" fill="url(#brandGradient)"/>
              <defs>
                <linearGradient id="brandGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#f472b6"/>
                  <stop offset="50%" stop-color="#a855f7"/>
                  <stop offset="100%" stop-color="#9333ea"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div class="brand-text">
            <span class="brand-name">VisionSpace</span>
            <span class="brand-tagline">视觉灵感空间</span>
          </div>
        </div>

        <!-- 主标题 -->
        <h1 class="hero-title">
          <span class="title-word" v-for="(word, i) in titleWords" :key="i"
                :style="{ animationDelay: `${i * 0.15}s` }">
            {{ word }}
          </span>
        </h1>
        <p class="hero-subtitle">探索无限视觉灵感，在光影之间寻找共鸣</p>

        <!-- 搜索胶囊 - 毛玻璃效果 -->
        <div class="search-capsule" :class="{ 'search-focused': isSearchFocused }">
          <div class="search-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
          </div>
          <input
            v-model="searchText"
            type="text"
            placeholder="搜索图片、标签或创作者..."
            class="search-input"
            @focus="isSearchFocused = true"
            @blur="isSearchFocused = false"
            @keydown.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">
            <span>探索</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </button>
        </div>

        <!-- 分类标签 - 毛玻璃效果 -->
        <div class="category-pills">
          <button
            v-for="cat in categoryList"
            :key="cat"
            class="category-pill"
            :class="{ active: selectedCategory === cat }"
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
                  <div class="overlay-actions">
                    <button class="action-btn" title="收藏">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                      </svg>
                    </button>
                    <button class="action-btn" title="分享">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="18" cy="5" r="3"/>
                        <circle cx="6" cy="12" r="3"/>
                        <circle cx="18" cy="19" r="3"/>
                        <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                        <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
              <!-- 曝光追踪 -->
              <div
                class="impression-tracker"
                :data-picture-id="picture.id"
                :ref="el => setImpressionRef(el, picture.id)"
              />
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div class="gallery-loader" ref="loaderRef">
          <!-- 加载动画 -->
          <div class="loader-content" v-if="loading">
            <div class="loader-orb">
              <div class="orb-inner"></div>
              <div class="orb-ring"></div>
              <div class="orb-ring ring-2"></div>
            </div>
            <span class="loader-text">正在加载精彩内容</span>
          </div>

          <!-- 到底了 -->
          <div class="end-section" v-else-if="!hasMore && pictureList.length > 0">
            <div class="end-decoration">
              <div class="star star-1">✦</div>
              <div class="star star-2">✧</div>
              <div class="star star-3">✦</div>
            </div>
            <div class="end-content">
              <h3 class="end-title">你已完成探索</h3>
              <p class="end-subtitle">发现了 {{ total }} 张精彩图片</p>
            </div>
            <div class="end-actions">
              <button class="end-btn" @click="backToTop">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 15l-6-6-6 6"/>
                </svg>
                <span>回到顶部</span>
              </button>
              <button class="end-btn secondary" @click="refreshGallery">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6"/>
                  <path d="M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
                <span>换一批</span>
              </button>
            </div>
            <div class="end-glow"></div>
          </div>

          <!-- 空状态 -->
          <div class="empty-state" v-else-if="!loading && pictureList.length === 0">
            <div class="empty-illustration">
              <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
                <circle cx="60" cy="60" r="50" stroke="var(--color-border-default)" stroke-width="2" stroke-dasharray="8 4"/>
                <rect x="35" y="35" width="50" height="50" rx="8" stroke="var(--color-text-tertiary)" stroke-width="2"/>
                <circle cx="50" cy="50" r="6" fill="var(--color-text-disabled)"/>
                <path d="M35 75 L55 55 L70 70 L85 55 L85 85 L35 85 Z" fill="var(--color-bg-tertiary)" stroke="var(--color-text-tertiary)" stroke-width="2"/>
              </svg>
            </div>
            <h3 class="empty-title">暂无图片</h3>
            <p class="empty-desc">还没有上传任何图片，开始你的创作吧</p>
            <button class="empty-action" @click="goToUpload">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              <span>上传图片</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommendListUsingGet } from '@/api/pictureRecommend'
import { reportPictureAction, ActionTypeEnum, ActionSourceEnum } from '@/api/pictureAction'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController'

const router = useRouter()

// Refs
const heroSection = ref<HTMLElement>()
const galleryRef = ref<HTMLElement>()
const loaderRef = ref<HTMLElement>()
const particleCanvas = ref<HTMLCanvasElement>()

// 搜索状态
const searchText = ref('')
const isSearchFocused = ref(false)

// 数据状态
const currentType = ref('hot')
const pictureList = ref<any[]>([])
const page = ref(1)
const size = ref  (24)
const loading = ref(false)
const hasMore = ref(true)
const total = ref(0)

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

const titleWords = ['发现', '视觉', '灵感']

// 曝光追踪
const observedPictures = ref<Set<string>>(new Set())
let impressionObserver: IntersectionObserver | null = null
let scrollObserver: IntersectionObserver | null = null
let particleAnimationId: number | null = null

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
  return pictureList.value.filter(picture => {
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
  newPictures.forEach(picture => {
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
  page.value = 1
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  hasMore.value = true
  loadedImages.value.clear()
  loadPictures()
}

/**
 * 搜索
 */
const doSearch = () => {
  page.value = 1
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  hasMore.value = true
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
    const res: any = await getRecommendListUsingGet({
      type: currentType.value,
      page: page.value,
      size: size.value,
    })
    const data = res?.data?.data
    if (data && data.length > 0) {
      assignPicturesToColumns(data)

      if (page.value === 1) {
        pictureList.value = data
      } else {
        pictureList.value.push(...data)
      }
      hasMore.value = data.length === size.value
      total.value = res?.data?.total || 0
    } else {
      hasMore.value = false
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
  page.value++
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
  page.value = 1
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  hasMore.value = true
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
  await reportClick(picture.id)
  router.push(`/picture/${picture.id}`)
}

/**
 * 上报点击
 */
const reportClick = async (pictureId: string) => {
  try {
    await reportPictureAction({
      pictureId: pictureId,
      actionType: ActionTypeEnum.CLICK,
      source: ActionSourceEnum.HOME_RECOMMEND
    })
  } catch (error) {
    console.error('上报点击失败:', error)
  }
}

/**
 * 上报曝光
 */
const reportImpression = async (pictureId: string) => {
  try {
    await reportPictureAction({
      pictureId: pictureId,
      actionType: ActionTypeEnum.IMPRESSION,
      source: ActionSourceEnum.HOME_RECOMMEND
    })
  } catch (error) {
    console.error('上报曝光失败:', error)
  }
}

/**
 * 设置曝光追踪 ref
 */
const setImpressionRef = (el: HTMLElement | null, pictureId: string) => {
  if (!el) return
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
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const pictureId = entry.target.dataset.pictureId as string
          if (pictureId && !observedPictures.value.has(pictureId)) {
            observedPictures.value.add(pictureId)
            reportImpression(pictureId)
          }
        }
      })
    },
    { threshold: 0.5 }
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
    { root: null, rootMargin: '200px', threshold: 0 }
  )

  scrollObserver.observe(loaderRef.value)
}

/**
 * 初始化粒子背景
 */
const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const resizeCanvas = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)

  const particles: { x: number; y: number; size: number; speedX: number; speedY: number; opacity: number }[] = []
  const particleCount = 50

  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      size: Math.random() * 2 + 0.5,
      speedX: (Math.random() - 0.5) * 0.3,
      speedY: (Math.random() - 0.5) * 0.3,
      opacity: Math.random() * 0.5 + 0.1
    })
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    particles.forEach(p => {
      p.x += p.speedX
      p.y += p.speedY

      if (p.x < 0) p.x = canvas.width
      if (p.x > canvas.width) p.x = 0
      if (p.y < 0) p.y = canvas.height
      if (p.y > canvas.height) p.y = 0

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(168, 85, 247, ${p.opacity})`
      ctx.fill()
    })

    particleAnimationId = requestAnimationFrame(animate)
  }

  animate()
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
  initParticles()
  loadPictures()

  window.addEventListener('resize', updateColumnCount)
})

onUnmounted(() => {
  if (impressionObserver) impressionObserver.disconnect()
  if (scrollObserver) scrollObserver.disconnect()
  if (particleAnimationId) cancelAnimationFrame(particleAnimationId)
  window.removeEventListener('resize', updateColumnCount)
})
</script>

<style scoped lang="less">
#homePage {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 氛围背景 ========== */
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
  filter: blur(80px);
  opacity: 0.4;
  animation: float-orb 20s ease-in-out infinite;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 70%);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.25) 0%, transparent 70%);
  top: 40%;
  left: -150px;
  animation-delay: -7s;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%);
  bottom: -100px;
  right: 20%;
  animation-delay: -14s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -30px) scale(1.05); }
  50% { transform: translate(-20px, 20px) scale(0.95); }
  75% { transform: translate(20px, 10px) scale(1.02); }
}

#particleCanvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 32px;
}

/* ========== Hero 区域 ========== */
.hero-section {
  min-height: 90vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 60px 24px 120px;
  position: relative;
}

/* 品牌标识 */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  animation: fade-up 0.8s ease-out forwards;
  opacity: 0;
}

.brand-logo {
  width: 64px;
  height: 64px;
  animation: pulse-glow 3s ease-in-out infinite;
}

.brand-icon {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 0 20px rgba(168, 85, 247, 0.5));
}

@keyframes pulse-glow {
  0%, 100% { transform: scale(1); filter: drop-shadow(0 0 20px rgba(168, 85, 247, 0.5)); }
  50% { transform: scale(1.05); filter: drop-shadow(0 0 30px rgba(168, 85, 247, 0.7)); }
}

.brand-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.brand-name {
  font-family: var(--font-brand);
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 8px;
  background: linear-gradient(135deg, #f8fafc 0%, #f472b6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-transform: uppercase;
}

.brand-tagline {
  font-size: 12px;
  color: var(--color-text-tertiary);
  letter-spacing: 4px;
}

/* 主标题 */
.hero-title {
  font-family: var(--font-display);
  font-size: clamp(48px, 10vw, 96px);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 20px 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px 24px;
}

.title-word {
  display: inline-block;
  animation: title-reveal 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  opacity: 0;
  transform: translateY(40px);

  &:nth-child(1) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7c3aed 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(2) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7e22ce 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(3) {
    background: linear-gradient(135deg, #f472b6 0%, #ec4899 50%, #db2777 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

@keyframes title-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-subtitle {
  font-size: clamp(16px, 2vw, 20px);
  color: var(--color-text-secondary);
  margin: 0 0 36px 0;
  font-weight: 400;
  letter-spacing: 1px;
  animation: fade-up 0.8s ease-out 0.4s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 搜索胶囊 - 毛玻璃效果 ========== */
.search-capsule {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 6px 6px 20px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 60px;
  width: 100%;
  max-width: 720px;
  margin-bottom: 28px;
  transition: all 0.3s ease;
  animation: fade-up 0.8s ease-out 0.6s forwards;
  opacity: 0;
  transform: translateY(20px);

  &:hover {
    background: var(--glass-bg-light);
    border-color: var(--glass-border);
  }

  &.search-focused {
    background: var(--glass-bg-light);
    border-color: var(--color-primary);
    box-shadow: 0 0 40px rgba(168, 85, 247, 0.3);
  }
}

.search-icon {
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: color 0.3s ease;
}

.search-capsule.search-focused .search-icon {
  color: var(--color-primary);
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 16px;
  color: var(--text-primary);
  min-width: 0;
  letter-spacing: 0.5px;

  &::placeholder {
    color: var(--text-tertiary);
  }
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: linear-gradient(135deg, #f472b6 0%, #a855f7 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  flex-shrink: 0;
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-2px) scale(1.02);
    box-shadow: 0 8px 30px rgba(168, 85, 247, 0.5);
  }
}

/* ========== 分类标签 - 毛玻璃效果 ========== */
.category-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  animation: fade-up 0.8s ease-out 0.8s forwards;
  opacity: 0;
  transform: translateY(20px);
}

.category-pill {
  padding: 10px 22px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur-light);
  -webkit-backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);
  border-radius: 30px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--glass-bg-light);
    border-color: var(--color-primary);
    transform: translateY(-2px);
  }

  &.active {
    background: linear-gradient(135deg, var(--color-secondary) 0%, var(--color-primary) 100%);
    border-color: transparent;
    color: white;
    box-shadow: 0 8px 25px rgba(168, 85, 247, 0.5);
  }
}

/* ========== 滚动指示器 ========== */
.scroll-indicator {
  position: absolute;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  animation: fade-up 0.8s ease-out 1.2s forwards;
  opacity: 0;
}

.scroll-text {
  font-size: 12px;
  color: var(--text-tertiary);
  letter-spacing: 3px;
  text-transform: uppercase;
  font-weight: 500;
}

.scroll-arrow {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);
  border-radius: 50%;
  color: var(--text-secondary);
  animation: bounce-arrow 2s ease-in-out infinite;

  svg {
    animation: arrow-pulse 2s ease-in-out infinite;
  }
}

@keyframes bounce-arrow {
  0%, 100% { transform: translateY(0); opacity: 0.7; }
  50% { transform: translateY(8px); opacity: 1; }
}

@keyframes arrow-pulse {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(4px); }
}

/* 底部渐变遮罩 - 暗示下方有内容 */
.hero-fade {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(to bottom, transparent 0%, rgba(168, 85, 247, 0.15) 70%, var(--bg-primary) 100%);
  pointer-events: none;
}

/* ========== 画廊区域 ========== */
.gallery-section {
  padding: 80px 0 120px;
}

/* ========== 瀑布流画廊 ========== */
.masonry-wrapper {
  display: flex;
  gap: 20px;
  width: 100%;
}

.masonry-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.gallery-item {
  opacity: 0;
  animation: item-reveal 0.6s ease-out forwards;
  break-inside: avoid;
  position: relative;

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
    transform: translateY(30px);
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
  background: var(--color-bg-secondary);
}

.item-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    var(--color-bg-secondary) 0%,
    var(--color-bg-tertiary) 50%,
    var(--color-bg-secondary) 100%
  );
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
  background: linear-gradient(
    to top,
    rgba(139, 92, 246, 0.85) 0%,
    rgba(139, 92, 246, 0.4) 40%,
    transparent 100%
  );
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
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  font-size: 13px;
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-divider {
  opacity: 0.5;
}

.overlay-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.action-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.2);
    transform: scale(1.1);
  }
}

/* ========== 加载状态 ========== */
.gallery-loader {
  margin-top: 80px;
  min-height: 200px;
}

/* 加载动画 */
.loader-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  padding: 40px;
}

.loader-orb {
  position: relative;
  width: 60px;
  height: 60px;
}

.orb-inner {
  position: absolute;
  inset: 15px;
  background: linear-gradient(135deg, #f472b6, #ec4899, #db2777);
  border-radius: 50%;
  animation: pulse-orb 1.5s ease-in-out infinite;
  box-shadow: 0 0 30px rgba(244, 114, 182, 0.6);
}

.orb-ring {
  position: absolute;
  inset: 0;
  border: 2px solid transparent;
  border-top-color: #f472b6;
  border-radius: 50%;
  animation: spin-ring 1.2s linear infinite;
  filter: drop-shadow(0 0 10px rgba(244, 114, 182, 0.6));

  &.ring-2 {
    inset: 5px;
    border-top-color: #fb923c;
    animation-duration: 1.8s;
    animation-direction: reverse;
    filter: drop-shadow(0 0 10px rgba(251, 146, 60, 0.6));
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
  color: var(--color-text-secondary);
  letter-spacing: 1px;
}

/* 到底了状态 */
.end-section {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 40px;
  text-align: center;
  overflow: hidden;
  margin-top: 40px;
}

.end-decoration {
  position: relative;
  width: 100px;
  height: 30px;
  margin-bottom: 24px;
}

.star {
  position: absolute;
  font-size: 20px;
  color: #f472b6;
  animation: twinkle 2s ease-in-out infinite;
  text-shadow: 0 0 10px rgba(244, 114, 182, 0.8);

  &.star-1 { left: 0; top: 50%; transform: translateY(-50%); animation-delay: 0s; }
  &.star-2 { left: 50%; top: 0; transform: translateX(-50%); animation-delay: 0.3s; font-size: 16px; }
  &.star-3 { right: 0; top: 50%; transform: translateY(-50%); animation-delay: 0.6s; }
}

@keyframes twinkle {
  0%, 100% { opacity: 1; transform: translateY(-50%) scale(1); }
  50% { opacity: 0.4; transform: translateY(-50%) scale(0.8); }
}

.star-2 {
  animation-name: twinkle-alt;
  @keyframes twinkle-alt {
    0%, 100% { opacity: 0.4; transform: translateX(-50%) scale(0.8); }
    50% { opacity: 1; transform: translateX(-50%) scale(1.1); }
  }
}

.end-content {
  margin-bottom: 32px;
}

.end-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px 0;
  background: linear-gradient(135deg, #a855f7 0%, #f472b6 50%, #fb923c 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: 0 0 40px rgba(244, 114, 182, 0.3);
}

.end-subtitle {
  font-size: 16px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.end-actions {
  display: flex;
  gap: 16px;
  z-index: 1;
}

.end-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  border: none;
  border-radius: 40px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;

  // 主按钮 - 回到顶部
  &:not(.secondary) {
    background: linear-gradient(135deg, #f472b6 0%, #ec4899 50%, #db2777 100%);
    color: white;
    box-shadow:
      0 4px 20px rgba(244, 114, 182, 0.5),
      0 0 50px rgba(244, 114, 182, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.2);

    &:hover {
      transform: translateY(-3px) scale(1.02);
      box-shadow:
        0 8px 35px rgba(244, 114, 182, 0.6),
        0 0 70px rgba(244, 114, 182, 0.35);
    }

    &:active {
      transform: translateY(-1px) scale(1);
    }
  }

  // 次按钮 - 换一批
  &.secondary {
    background: var(--glass-bg-light);
    color: var(--color-text-primary);
    border: 2px solid var(--glass-border);
    box-shadow: 0 4px 15px rgba(168, 85, 247, 0.15);

    svg {
      color: var(--color-text-secondary);
      transition: transform 0.3s ease;
    }

    &:hover {
      background: var(--glass-bg);
      border-color: var(--color-primary);
      transform: translateY(-2px);
      box-shadow: 0 4px 20px rgba(244, 114, 182, 0.25);

      svg {
        transform: rotate(180deg);
        color: var(--color-primary);
      }
    }
  }
}

.end-glow {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 400px;
  height: 150px;
  background: radial-gradient(ellipse, rgba(244, 114, 182, 0.3) 0%, rgba(251, 146, 60, 0.2) 40%, transparent 70%);
  pointer-events: none;
  filter: blur(30px);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 24px;
  text-align: center;
}

.empty-illustration {
  margin-bottom: 32px;
  opacity: 0.7;
  animation: float-illustration 3s ease-in-out infinite;
  filter: drop-shadow(0 0 20px rgba(244, 114, 182, 0.3));
}

@keyframes float-illustration {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.empty-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 12px 0;
}

.empty-desc {
  font-size: 15px;
  color: var(--color-text-tertiary);
  margin: 0 0 32px 0;
  max-width: 300px;
}

.empty-action {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 32px;
  background: linear-gradient(135deg, #f472b6 0%, #ec4899 50%, #db2777 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 25px rgba(244, 114, 182, 0.45);

  &:hover {
    transform: translateY(-3px) scale(1.02);
    box-shadow: 0 8px 35px rgba(244, 114, 182, 0.55);
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

/* ========== 响应式瀑布流 ========== */
@media (max-width: 1200px) {
  .masonry-wrapper {
    gap: 16px;
  }
  .masonry-column {
    gap: 16px;
  }
}

@media (max-width: 768px) {
  .masonry-wrapper {
    gap: 12px;
  }
  .masonry-column {
    gap: 12px;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .hero-section {
    padding: 40px 16px 100px;
    min-height: auto;
  }

  .brand-logo {
    width: 48px;
    height: 48px;
  }

  .brand-name {
    font-size: 18px;
    letter-spacing: 6px;
  }

  .brand-tagline {
    font-size: 10px;
    letter-spacing: 3px;
  }

  .hero-title {
    gap: 6px 16px;
  }

  .search-capsule {
    padding: 4px 4px 4px 16px;
    max-width: 100%;
  }

  .search-btn {
    padding: 12px 20px;
    font-size: 14px;
  }

  .category-pills {
    gap: 8px;
  }

  .category-pill {
    padding: 8px 16px;
    font-size: 13px;
  }

  .scroll-indicator {
    bottom: 30px;
  }

  .scroll-text {
    font-size: 10px;
  }

  .scroll-arrow {
    width: 40px;
    height: 40px;
  }
}

.gallery-section {
  padding: 40px 0 80px;
}
</style>
