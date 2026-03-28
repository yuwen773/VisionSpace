<template>
  <div class="space-tag-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
            <line x1="7" y1="7" x2="7.01" y2="7"/>
          </svg>
        </div>
        <h3 class="card-title">标签云分析</h3>
        <span class="tag-count" v-if="totalCount > 0">{{ totalCount }} 个标签</span>
      </div>

      <div class="card-content">
        <div v-if="loading" class="loading-state">
          <div class="loader-spinner"></div>
        </div>
        <div v-else-if="!hasData" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
            <line x1="7" y1="7" x2="7.01" y2="7"/>
          </svg>
          <span>暂无标签</span>
        </div>
        <div v-else class="cloud-container">
          <div class="word-cloud">
            <span
              v-for="item in tagData"
              :key="item.tag"
              class="cloud-word"
              :style="{
                fontSize: `${getFontSize(item.count)}px`,
                opacity: getOpacity(item.count),
                color: getColor(item.count),
              }"
            >
              {{ item.tag }}
              <em class="word-count">{{ item.count }}</em>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceTagAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

const emit = defineEmits(['updateCount'])

const loading = ref(false)
const dataList = ref<API.SpaceTagAnalyzeResponse[]>([])

const hasData = computed(() => dataList.value && dataList.value.length > 0)
const totalCount = computed(() => dataList.value?.length || 0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceTagAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
      emit('updateCount', dataList.value.length)
    } else {
      message.error('获取数据失败')
    }
  } catch (error) {
    message.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

watchEffect(() => {
  fetchData()
})

const tagData = computed(() => {
  if (!dataList.value) return []
  return dataList.value
    .filter((item: any) => item && item.tag && item.count)
    .map((item: any) => ({
      tag: item.tag?.toString() || '',
      count: Number(item.count) || 0,
    }))
    .filter((item) => item.count > 0)
    .sort((a, b) => b.count - a.count)
})

const maxCount = computed(() => {
  if (!tagData.value.length) return 1
  return Math.max(...tagData.value.map((t) => t.count))
})

const getFontSize = (count: number) => {
  const minSize = 12
  const maxSize = 32
  const ratio = count / maxCount.value
  return minSize + ratio * (maxSize - minSize)
}

const getOpacity = (count: number) => {
  const minOpacity = 0.5
  const maxOpacity = 1
  const ratio = count / maxCount.value
  return minOpacity + ratio * (maxOpacity - minOpacity)
}

const getColor = (count: number) => {
  const ratio = count / maxCount.value
  if (ratio > 0.7) return '#f472b6'
  if (ratio > 0.4) return '#a855f7'
  return '#60a5fa'
}
</script>

<style scoped lang="less">
.space-tag-analyze {
  height: 100%;
}

.analyze-card {
  height: 100%;
  padding: 24px;
  background: rgba(26, 35, 50, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.header-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(244, 114, 182, 0.2);
  border-radius: 12px;
  color: #f472b6;
}

.card-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: white;
  margin: 0;
  flex: 1;
}

.tag-count {
  padding: 4px 12px;
  background: rgba(244, 114, 182, 0.15);
  border: 1px solid rgba(244, 114, 182, 0.3);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #f472b6;
}

.card-content {
  min-height: 280px;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 280px;
}

.loader-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #f472b6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 280px;
  gap: 12px;
  color: rgba(255, 255, 255, 0.4);

  svg {
    opacity: 0.3;
  }
}

.cloud-container {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.word-cloud {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 12px 20px;
  max-width: 100%;
  padding: 20px;
}

.cloud-word {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  font-weight: 600;
  transition: all 0.3s ease;
  cursor: default;

  &:hover {
    transform: scale(1.1);
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
    z-index: 1;

    .word-count {
      opacity: 1;
    }
  }
}

.word-count {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 10px;
  opacity: 0.6;
  transition: opacity 0.3s ease;
}
</style>
