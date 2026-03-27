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
            <img
              :src="picture.thumbnailUrl || picture.url"
              :alt="picture.name"
              class="picture-image"
              @load="onImageLoad(picture.id)"
              v-if="loadedImages.has(picture.id)"
            />
            <div class="picture-skeleton" v-else />
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
import { useRouter } from 'vue-router'

const router = useRouter()

// 瀑布流配置
const COLUMN_COUNT = 4
const COLUMN_GAP = 16
const columnCount = ref(COLUMN_COUNT)

// 数据状态
const currentType = ref('hot')
const pictureList = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const hasMore = ref(true)

// 图片加载状态
const loadedImages = ref<Set<number>>(new Set())

// 列分配映射：记录每张图片属于哪一列
const columnAssignment = ref<Map<number, number>>(new Map())
// 列高度追踪
const columnHeights = ref<number[]>([0, 0, 0, 0])

// 曝光追踪
const impressionRefs = ref<Map<number, HTMLElement>>(new Map())
const observedPictures = ref<Set<number>>(new Set())

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
const onImageLoad = (pictureId: number) => {
  loadedImages.value.add(pictureId)
}

/**
 * Tab 切换
 */
const handleTypeChange = () => {
  page.value = 1
  pictureList.value = []
  columnAssignment.value.clear()
  columnHeights.value = [0, 0, 0, 0]
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
    const data = res?.data
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
const handleClick = (picture: any) => {
  console.log('click:', picture.id)
  router.push(`/picture/${picture.id}`)
}

/**
 * 设置曝光追踪 ref
 */
const setImpressionRef = (el: HTMLElement | null, pictureId: number) => {
  if (!el) return
  impressionRefs.value.set(pictureId, el)

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
          const pictureId = Array.from(impressionRefs.value.entries()).find(
            ([_, el]) => el === entry.target
          )?.[0]

          if (pictureId && !observedPictures.value.has(pictureId)) {
            observedPictures.value.add(pictureId)
            console.log('impression:', pictureId)
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
  initImpressionObserver()
  initScrollObserver()
  loadPictures()
})

onUnmounted(() => {
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
}

/* 骨架屏 */
.picture-skeleton {
  width: 100%;
  padding-bottom: 75%;
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
