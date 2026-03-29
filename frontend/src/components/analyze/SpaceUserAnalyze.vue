<template>
  <div class="space-user-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <h3 class="card-title">用户上传趋势</h3>

        <div class="header-controls">
          <div class="time-selector">
            <button
              v-for="item in timeOptions"
              :key="item.value"
              class="time-btn"
              :class="{ active: timeDimension === item.value }"
              @click="timeDimension = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </div>

      <div class="card-content">
        <div v-if="loading" class="loading-state">
          <div class="loader-spinner"></div>
        </div>
        <div v-else-if="!hasData" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
          </svg>
          <span>暂无数据</span>
        </div>
        <div v-else class="chart-container">
          <v-chart
            :option="options"
            style="height: 280px; width: 100%"
          />
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
import { getSpaceUserAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const timeDimension = ref<'day' | 'week' | 'month'>('day')
const dataList = ref<API.SpaceUserAnalyzeResponse[]>([])

const timeOptions = [
  { label: '日', value: 'day' as const },
  { label: '周', value: 'week' as const },
  { label: '月', value: 'month' as const },
]

const hasData = computed(() => dataList.value && dataList.value.length > 0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceUserAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
      timeDimension: timeDimension.value,
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

const options = computed(() => {
  const periods = dataList.value?.map((item: any) => item.period) || []
  const counts = dataList.value?.map((item: any) => item.count) || []

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(250, 247, 255, 0.95)',
      borderColor: 'rgba(168, 85, 247, 0.2)',
      textStyle: { color: '#1e1b4b' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: '10%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: periods,
      boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(168, 85, 247, 0.2)' } },
      axisLabel: { color: '#6b7280', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      name: '上传数量',
      axisLine: { show: true, lineStyle: { color: '#a855f7' } },
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: 'rgba(168, 85, 247, 0.08)' } },
    },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          color: '#a855f7',
          width: 3,
          shadowColor: 'rgba(168, 85, 247, 0.3)',
          shadowBlur: 10,
        },
        itemStyle: {
          color: '#a855f7',
          borderColor: '#faf7ff',
          borderWidth: 2,
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(168, 85, 247, 0.2)' },
              { offset: 1, color: 'rgba(168, 85, 247, 0)' },
            ],
          },
        },
      },
    ],
  }
})
</script>

<style scoped lang="less">
.space-user-analyze {
  height: 100%;
}

.analyze-card {
  height: 100%;
  padding: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 20px;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-card);

  &:hover {
    transform: translateY(-4px);
    border-color: var(--color-primary-light);
    box-shadow: var(--shadow-card-hover);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.header-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.15);
  border-radius: 12px;
  color: var(--color-primary);
}

.card-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  flex: 1;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-selector {
  display: flex;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: 4px;
}

.time-btn {
  padding: 6px 16px;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    color: var(--text-primary);
  }

  &.active {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    color: white;
    box-shadow: 0 4px 15px rgba(168, 85, 247, 0.3);
  }
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
  border: 3px solid var(--bg-tertiary);
  border-top-color: var(--color-primary);
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
  color: var(--text-tertiary);

  svg {
    opacity: 0.4;
  }
}

.chart-container {
  animation: fade-in 0.6s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
