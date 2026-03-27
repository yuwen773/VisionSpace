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

    <!-- 图片列表 -->
    <div class="picture-grid">
      <div
        v-for="picture in pictureList"
        :key="picture.id"
        class="picture-item"
        @click="goToDetail(picture.id)"
      >
        <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name" />
        <div class="picture-info">
          <div class="picture-name">{{ picture.name }}</div>
          <div class="picture-meta">
            <span>{{ picture.picWidth }}×{{ picture.picHeight }}</span>
            <span>{{ picture.picFormat?.toUpperCase() }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载更多 -->
    <div class="load-more" v-if="hasMore">
      <a-button @click="loadMore" :loading="loading">加载更多</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRecommendListUsingGet } from '@/api/pictureRecommend'
import { useRouter } from 'vue-router'

const router = useRouter()

const currentType = ref('hot')
const pictureList = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const hasMore = ref(true)

const handleTypeChange = () => {
  page.value = 1
  pictureList.value = []
  hasMore.value = true
  loadPictures()
}

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

const loadMore = () => {
  page.value++
  loadPictures()
}

const goToDetail = (id: number) => {
  router.push(`/picture/${id}`)
}

onMounted(() => {
  loadPictures()
})
</script>

<style scoped>
.home-recommend {
  padding: 16px;
}

.recommend-tabs {
  margin-bottom: 16px;
}

.picture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.picture-item {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: transform 0.2s;
}

.picture-item:hover {
  transform: translateY(-4px);
}

.picture-item img {
  width: 100%;
  height: 200px;
  object-fit: cover;
}

.picture-info {
  padding: 8px;
}

.picture-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picture-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: flex;
  gap: 8px;
}

.load-more {
  text-align: center;
  margin-top: 16px;
}
</style>
