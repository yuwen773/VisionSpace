<template>
  <div class="home-recommend">
    <!-- 推荐类型切换 -->
    <div class="recommend-tabs">
      <a-radio-group v-model:value="currentType" @change="handleTypeChange">
        <a-radio-button value="hot">热门</a-radio-button>
        <a-radio-button value="latest">最新</a-radio-button>
        <a-radio-button value="quality">优质</a-radio-button>
        <a-radio-button value="random">发现</a-radio-button>
      </a-radio-group>
    </div>

    <!-- 瀑布流容器 -->
    <div class="waterfall-container" ref="containerRef">
      <div class="waterfall-column" v-for="col in columnCount" :key="col">
        <div
          v-for="picture in getColumnPictures(col - 1)"
          :key="picture.id"
          class="picture-card"
          @click="handleClick(picture)"
        >
          <div class="picture-image-wrapper" :style="{ paddingBottom: getAspectRatio(picture) }">
            <div class="picture-skeleton" v-if="!loadedImages.has(picture.id)" />
            <img
              :src="picture.thumbnailUrl || picture.url"
              :alt="picture.name"
              class="picture-image"
              :class="{ 'image-loaded': loadedImages.has(picture.id) }"
              @load="onImageLoad(picture.id)"
            />
          </div>
          <div class="picture-info">
            <div class="picture-name">{{ picture.name }}</div>
            <div class="picture-meta">
              <span>{{ picture.picWidth }}×{{ picture.picHeight }}</span>
              <span>{{ picture.picFormat?.toUpperCase() }}</span>
            </div>
          </div>
          <!-- 曝光埋点追踪器 -->
          <div
            class="impression-tracker"
            :data-picture-id="picture.id"
            :ref="el => setImpressionRef(el, picture.id)"
          />
        </div>
      </div>
    </div>

    <!-- 加载更多指示器 -->
    <div class="load-more" ref="loadMoreRef">
      <a-spin v-if="loading" />
      <span v-else-if="!hasMore && pictureList.length > 0" class="no-more">
        没有更多了
      </span>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && pictureList.length === 0" class="empty-state">
      <span>暂无推荐内容</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getRecommendListUsingGet } from '@/api/pictureRecommend'
import { reportPictureAction, ActionTypeEnum, ActionSourceEnum } from '@/api/pictureAction'
import { useRouter } from 'vue-router'

const router = useRouter()

// 瀑布流配置
const COLUMN_GAP = 16
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

// 数据状态
const currentType = ref('hot')
const pictureList = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const hasMore = ref(true)

// 图片加载状态
const loadedImages = ref<Set<string>>(new Set())

// 列分配映射：记录每张图片属于哪一列
const columnAssignment = ref<Map<string, number>>(new Map())
// 列高度追踪
const columnHeights = ref<number[]>(new Array(columnCount.value).fill(0))

// 曝光追踪
const observedPictures = ref<Set<string>>(new Set())

// Refs
const containerRef = ref<HTMLElement>()
const loadMoreRef = ref<HTMLElement>()

// 曝光 Observer
let impressionObserver: IntersectionObserver | null = null
// 滚动 Observer
let scrollObserver: IntersectionObserver | null = null

/**
 * 计算图片宽高比，用于 padding-bottom 占位
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
    return 280 * 0.75 + 60 // 默认 4:3 比例，加上 info 部分高度
  }
  const estimatedCardWidth = 280
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
    columnHeights.value[shortestCol] += estimatedHeight + COLUMN_GAP
  })
}

/**
 * 图片加载完成
 */
const onImageLoad = (pictureId: string) => {
  loadedImages.value.add(pictureId)
}

/**
 * Tab 切换
 */
const handleTypeChange = () => {
  page.value = 1
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = new Array(columnCount.value).fill(0)
  observedPictures.value.clear()
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
 * 点击图片
 */
const handleClick = async (picture: any) => {
  await reportClick(picture.id)
  router.push(`/picture/${picture.id}`)
}

/**
 * 上报曝光
 */
const reportImpression = async (pictureId: string) => {
  try {
    await reportPictureAction({
      pictureId: Number(pictureId),
      actionType: ActionTypeEnum.IMPRESSION,
      source: ActionSourceEnum.HOME_RECOMMEND
    })
  } catch (error) {
    console.error('上报曝光失败:', error)
  }
}

/**
 * 上报点击
 */
const reportClick = async (pictureId: string) => {
  try {
    await reportPictureAction({
      pictureId: Number(pictureId),
      actionType: ActionTypeEnum.CLICK,
      source: ActionSourceEnum.HOME_RECOMMEND
    })
  } catch (error) {
    console.error('上报点击失败:', error)
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
  if (!loadMoreRef.value) return

  scrollObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && hasMore.value && !loading.value) {
        loadMore()
      }
    },
    {
      root: null,
      rootMargin: '0px',
      threshold: 0.1
    }
  )

  scrollObserver.observe(loadMoreRef.value)
}

onMounted(() => {
  updateColumnCount()
  window.addEventListener('resize', updateColumnCount)
  initImpressionObserver()
  initScrollObserver()
  loadPictures()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateColumnCount)
  if (impressionObserver) impressionObserver.disconnect()
  if (scrollObserver) scrollObserver.disconnect()
})
</script>

<style scoped>
.home-recommend {
  padding: 16px;
}

.recommend-tabs {
  margin-bottom: 16px;
}

/* 瀑布流容器 */
.waterfall-container {
  display: flex;
  gap: 16px;
  width: 100%;
}

.waterfall-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 图片卡片 */
.picture-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  break-inside: avoid;
}

.picture-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 图片容器 - 用 padding-bottom 占位 */
.picture-image-wrapper {
  position: relative;
  width: 100%;
  overflow: hidden;
}

.picture-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.3s;
}

.picture-image.image-loaded {
  opacity: 1;
}

/* 骨架屏 */
.picture-skeleton {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 图片信息 */
.picture-info {
  padding: 8px;
}

.picture-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.picture-meta {
  font-size: 12px;
  color: #999;
  display: flex;
  gap: 8px;
}

/* 曝光追踪器 - 不可见 */
.impression-tracker {
  position: absolute;
  top: 0;
  left: 0;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

/* 加载更多 */
.load-more {
  text-align: center;
  padding: 20px 0;
  color: #999;
}

.no-more {
  font-size: 14px;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 14px;
}
</style>
