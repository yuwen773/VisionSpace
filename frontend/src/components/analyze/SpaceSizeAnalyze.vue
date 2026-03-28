<template>
  <div class="space-size-analyze">
    <div class="analyze-card">
      <div class="card-header">
        <div class="header-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
        </div>
        <h3 class="card-title">图片大小分布</h3>
      </div>

      <div class="card-content">
        <div v-if="loading" class="loading-state">
          <div class="loader-spinner"></div>
        </div>
        <div v-else-if="!hasData" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
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
import { getSpaceSizeAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const dataList = ref<API.SpaceSizeAnalyzeResponse[]>([])

const hasData = computed(() => dataList.value && dataList.value.length > 0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceSizeAnalyzeUsingPost({
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

const pieColors = ['#60a5fa', '#a855f7', '#f472b6', '#00d4aa', '#fb923c']

const options = computed(() => {
  const pieData = dataList.value?.map((item: any, index: number) => ({
    name: item.sizeRange || '未知',
    value: item.count || 0,
    itemStyle: {
      color: pieColors[index % pieColors.length],
    },
  })) || []

  const total = pieData.reduce((sum, item) => sum + item.value, 0)

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(26, 35, 50, 0.95)',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      textStyle: { color: '#f8fafc' },
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: 'rgba(255, 255, 255, 0.6)' },
      itemWidth: 12,
      itemHeight: 12,
      itemGap: 12,
    },
    series: [
      {
        name: '图片大小',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: 'rgba(26, 35, 50, 0.8)',
          borderWidth: 3,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 18,
            fontWeight: 'bold',
            color: '#fff',
            formatter: () => `{total|${total}}\n{label|张图片}`,
            rich: {
              total: {
                fontSize: 28,
                fontWeight: 800,
                fontFamily: 'var(--font-display)',
                color: '#fff',
              },
              label: {
                fontSize: 12,
                color: 'rgba(255, 255, 255, 0.6)',
                padding: [8, 0, 0, 0],
              },
            },
          },
          itemStyle: {
            shadowBlur: 20,
            shadowColor: 'rgba(168, 85, 247, 0.5)',
          },
        },
        labelLine: {
          show: false,
        },
        data: pieData,
      },
    ],
  }
})
</script>

<style scoped lang="less">
.space-size-analyze {
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
  background: rgba(0, 212, 170, 0.2);
  border-radius: 12px;
  color: #00d4aa;
}

.card-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: white;
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
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #00d4aa;
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
