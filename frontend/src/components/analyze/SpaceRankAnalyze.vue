<template>
  <div class="space-rank-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
        </div>
        <h3 class="card-title">空间使用排行</h3>
        <span class="rank-badge">TOP 10</span>
      </div>

      <div class="card-content">
        <div v-if="loading" class="loading-state">
          <div class="loader-spinner"></div>
        </div>
        <div v-else-if="!hasData" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          <span>暂无数据</span>
        </div>
        <div v-else class="rank-list">
          <div
            v-for="(space, index) in dataList"
            :key="space.spaceId"
            class="rank-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div class="rank-position" :class="getPositionClass(index)">
              {{ index + 1 }}
            </div>
            <div class="rank-info">
              <span class="rank-name">{{ space.spaceName }}</span>
              <div class="rank-bar">
                <div
                  class="rank-bar-fill"
                  :style="{ width: `${getPercent(space.totalSize)}%` }"
                ></div>
              </div>
            </div>
            <div class="rank-value">
              {{ formatSize(space.totalSize || 0) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import 'echarts'
import { getSpaceRankAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { formatSize } from '@/utils'

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

const loading = ref(false)
const dataList = ref<API.Space[]>([])

const hasData = computed(() => dataList.value && dataList.value.length > 0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceRankAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
      topN: 10,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
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

const maxSize = computed(() => {
  if (!dataList.value.length) return 0
  return Math.max(...dataList.value.map((s) => s.totalSize || 0))
})

const getPercent = (size: number) => {
  if (!maxSize.value) return 0
  return Math.min(100, ((size || 0) / maxSize.value) * 100)
}

const getPositionClass = (index: number) => {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}
</script>

<style scoped lang="less">
.space-rank-analyze {
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
  background: rgba(251, 146, 60, 0.2);
  border-radius: 12px;
  color: #fb923c;
}

.card-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: white;
  margin: 0;
  flex: 1;
}

.rank-badge {
  padding: 4px 12px;
  background: linear-gradient(135deg, #fb923c 0%, #f472b6 100%);
  border-radius: 20px;
  font-size: 11px;
  font-weight: 800;
  color: white;
  letter-spacing: 1px;
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
  border-top-color: #fb923c;
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

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
  animation: item-reveal 0.4s ease forwards;
  opacity: 0;
  transform: translateX(-20px);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.3);
    transform: translateX(4px);
  }
}

@keyframes item-reveal {
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.rank-position {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.6);

  &.gold {
    background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
    color: #1a1a1a;
    box-shadow: 0 4px 15px rgba(251, 191, 36, 0.4);
  }

  &.silver {
    background: linear-gradient(135deg, #e2e8f0 0%, #94a3b8 100%);
    color: #1a1a1a;
    box-shadow: 0 4px 15px rgba(148, 163, 184, 0.4);
  }

  &.bronze {
    background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
    color: white;
    box-shadow: 0 4px 15px rgba(249, 115, 22, 0.4);
  }
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: white;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;
}

.rank-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #fb923c, #f472b6);
  border-radius: 3px;
  transition: width 1s ease;
}

.rank-value {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: #fb923c;
  white-space: nowrap;
}
</style>
