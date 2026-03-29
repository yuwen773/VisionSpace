<template>
  <div class="space-category-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h3 class="card-title">分类分布分析</h3>
      </div>

      <div class="card-content">
        <div v-if="loading" class="loading-state">
          <div class="loader-spinner"></div>
        </div>
        <div v-else-if="!hasData" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
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
import { getSpaceCategoryAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const dataList = ref<API.SpaceCategoryAnalyzeResponse[]>([])

const hasData = computed(() => dataList.value && dataList.value.length > 0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceCategoryAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
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
  const categories = dataList.value?.map((item: any) => item.category) || []
  const countData = dataList.value?.map((item: any) => item.count) || []
  const sizeData = dataList.value?.map((item: any) => Number((item.totalSize / (1024 * 1024)).toFixed(2))) || []

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(250, 247, 255, 0.95)',
      borderColor: 'rgba(168, 85, 247, 0.2)',
      textStyle: { color: '#1e1b4b' },
    },
    legend: {
      data: ['图片数量', '总大小 (MB)'],
      bottom: 0,
      textStyle: { color: '#6b7280' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '5%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: 'rgba(168, 85, 247, 0.2)' } },
      axisLabel: { color: '#6b7280' },
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        position: 'left',
        axisLine: { show: true, lineStyle: { color: '#a855f7' } },
        axisLabel: { color: '#6b7280' },
        splitLine: { lineStyle: { color: 'rgba(168, 85, 247, 0.08)' } },
      },
      {
        type: 'value',
        name: '大小 (MB)',
        position: 'right',
        axisLine: { show: true, lineStyle: { color: '#ec4899' } },
        axisLabel: { color: '#6b7280' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '图片数量',
        type: 'bar',
        data: countData,
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#a855f7' },
              { offset: 1, color: '#7c3aed' },
            ],
          },
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: '35%',
      },
      {
        name: '总大小 (MB)',
        type: 'bar',
        data: sizeData,
        yAxisIndex: 1,
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#ec4899' },
              { offset: 1, color: '#db2777' },
            ],
          },
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: '35%',
      },
    ],
  }
})
</script>

<style scoped lang="less">
.space-category-analyze {
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
