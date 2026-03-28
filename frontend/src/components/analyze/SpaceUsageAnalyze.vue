<template>
  <div class="space-usage-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          </svg>
        </div>
        <h3 class="card-title">空间存储分析</h3>
      </div>

      <div class="card-content">
        <div class="gauge-container">
          <!-- 存储空间仪表 -->
          <div class="gauge-item">
            <div class="gauge-ring">
              <svg viewBox="0 0 200 200" class="gauge-svg">
                <defs>
                  <linearGradient id="storageGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#2268f5"/>
                    <stop offset="100%" stop-color="#60a5fa"/>
                  </linearGradient>
                </defs>
                <circle cx="100" cy="100" r="85" class="gauge-bg"/>
                <circle
                  cx="100"
                  cy="100"
                  r="85"
                  class="gauge-progress"
                  :stroke-dasharray="`${getStoragePercent()} 270 100`"
                />
              </svg>
              <div class="gauge-center">
                <span class="gauge-value">{{ data?.sizeUsageRatio?.toFixed(0) || 0 }}%</span>
                <span class="gauge-label">存储</span>
              </div>
            </div>
            <div class="gauge-info">
              <span class="info-used">{{ formatSize(data?.usedSize || 0) }}</span>
              <span class="info-sep">/</span>
              <span class="info-max">{{ formatSize(data?.maxSize || 0) }}</span>
            </div>
          </div>

          <!-- 图片数量仪表 -->
          <div class="gauge-item">
            <div class="gauge-ring pink">
              <svg viewBox="0 0 200 200" class="gauge-svg">
                <defs>
                  <linearGradient id="countGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#a855f7"/>
                    <stop offset="100%" stop-color="#f472b6"/>
                  </linearGradient>
                </defs>
                <circle cx="100" cy="100" r="85" class="gauge-bg"/>
                <circle
                  cx="100"
                  cy="100"
                  r="85"
                  class="gauge-progress"
                  :stroke-dasharray="`${getCountPercent()} 270 100`"
                />
              </svg>
              <div class="gauge-center">
                <span class="gauge-value">{{ data?.countUsageRatio?.toFixed(0) || 0 }}%</span>
                <span class="gauge-label">图片</span>
              </div>
            </div>
            <div class="gauge-info">
              <span class="info-used">{{ data?.usedCount || 0 }}</span>
              <span class="info-sep">/</span>
              <span class="info-max">{{ data?.maxCount || '∞' }}</span>
            </div>
          </div>
        </div>

        <!-- 详细数据 -->
        <div class="detail-stats">
          <div class="stat-row">
            <span class="stat-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
              图片数量
            </span>
            <span class="stat-value">{{ data?.usedCount || 0 }} 张</span>
          </div>
          <div class="stat-row">
            <span class="stat-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              </svg>
              已用空间
            </span>
            <span class="stat-value">{{ formatSize(data?.usedSize || 0) }}</span>
          </div>
          <div class="stat-row">
            <span class="stat-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              平均每张
            </span>
            <span class="stat-value">{{ getAvgSize() }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceUsageAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
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

const emit = defineEmits(['updateCount'])

const loading = ref(false)
const data = ref<API.SpaceUsageAnalyzeResponse>({})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceUsageAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
    })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data
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

const getStoragePercent = () => {
  return Math.min(100, (data.value?.sizeUsageRatio || 0) * 2.7)
}

const getCountPercent = () => {
  return Math.min(100, (data.value?.countUsageRatio || 0) * 2.7)
}

const getAvgSize = () => {
  if (!data.value?.usedSize || !data.value?.usedCount) return '0 KB'
  const avg = data.value.usedSize / data.value.usedCount
  return formatSize(avg)
}
</script>

<style scoped lang="less">
.space-usage-analyze {
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
  margin-bottom: 24px;
}

.header-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(34, 104, 245, 0.2);
  border-radius: 12px;
  color: #60a5fa;
}

.card-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: white;
  margin: 0;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.gauge-container {
  display: flex;
  justify-content: space-around;
  gap: 32px;
}

.gauge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.gauge-ring {
  position: relative;
  width: 140px;
  height: 140px;

  .gauge-svg {
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
  }

  .gauge-bg {
    fill: none;
    stroke: rgba(255, 255, 255, 0.08);
    stroke-width: 12;
  }

  .gauge-progress {
    fill: none;
    stroke: url(#storageGrad);
    stroke-width: 12;
    stroke-linecap: round;
    transition: stroke-dasharray 1s ease;
    filter: drop-shadow(0 0 8px rgba(34, 104, 245, 0.5));
  }

  &.pink .gauge-progress {
    stroke: url(#countGrad);
    filter: drop-shadow(0 0 8px rgba(168, 85, 247, 0.5));
  }
}

.gauge-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.gauge-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 800;
  color: white;
  line-height: 1;
}

.gauge-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 4px;
}

.gauge-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-used {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: white;
}

.info-sep {
  color: rgba(255, 255, 255, 0.3);
}

.info-max {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
}

.detail-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);

  svg {
    opacity: 0.6;
  }
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: white;
}
</style>
