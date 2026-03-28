<template>
  <div class="dashboard-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <span>📊</span>
          <span>管理仪表盘</span>
        </h1>
        <p class="page-subtitle">系统运行状态总览</p>
      </div>
      <div class="header-actions">
        <button class="action-btn" @click="refreshData">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M23 4v6h-6"/>
            <path d="M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          <span>刷新数据</span>
        </button>
      </div>
    </div>

    <!-- 统计卡片区域 -->
    <div class="stats-grid">
      <div class="stat-card" :style="{ animationDelay: '0.1s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon users">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">用户总数</span>
            <span class="stat-value">{{ dashboardStats.userCount || 0 }}</span>
            <span class="stat-desc">注册用户</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.2s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon pictures">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">图片总数</span>
            <span class="stat-value">{{ dashboardStats.pictureCount || 0 }}</span>
            <span class="stat-desc">已上传图片</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.3s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon spaces">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">空间总数</span>
            <span class="stat-value">{{ dashboardStats.spaceCount || 0 }}</span>
            <span class="stat-desc">创建的空间</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.4s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon storage">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">存储使用</span>
            <span class="stat-value">{{ formatSize(dashboardStats?.usedSize || 0) }}</span>
          </div>
        </template>
      </div>

      <!-- 推荐统计卡片 -->
      <div class="stat-card" :style="{ animationDelay: '0.5s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon recommend">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">今日曝光</span>
            <span class="stat-value">{{ dashboardStats.todayImpressionCount || 0 }}</span>
            <span class="stat-desc">推荐曝光</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.6s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon click">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">今日点击</span>
            <span class="stat-value">{{ dashboardStats.todayClickCount || 0 }}</span>
            <span class="stat-desc">推荐点击量</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.65s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon ctr">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">点击率 CTR</span>
            <span class="stat-value">{{ dashboardStats.todayCtr || 0 }}%</span>
            <span class="stat-desc">点击/曝光</span>
          </div>
        </template>
      </div>

      <div class="stat-card" :style="{ animationDelay: '0.7s' }">
        <div v-if="statsLoading" class="stat-loading">
          <div class="stat-loader"></div>
        </div>
        <template v-else>
          <div class="stat-icon collect">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-label">今日收藏</span>
            <span class="stat-value">{{ dashboardStats.todayCollectCount || 0 }}</span>
            <span class="stat-desc">推荐收藏</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="charts-grid">
      <!-- 用户上传趋势 -->
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <h3 class="chart-title">用户上传趋势</h3>
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
        <div class="chart-content">
          <div v-if="userTrendLoading" class="loading-state">
            <div class="loader-spinner"></div>
          </div>
          <v-chart
            v-else
            :option="userTrendOptions"
            style="height: 280px; width: 100%"
          />
        </div>
      </div>

      <!-- 分类分布 -->
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-icon category">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <h3 class="chart-title">空间分类分布</h3>
        </div>
        <div class="chart-content">
          <div v-if="categoryLoading" class="loading-state">
            <div class="loader-spinner"></div>
          </div>
          <v-chart
            v-else
            :option="categoryOptions"
            style="height: 280px; width: 100%"
          />
        </div>
      </div>
    </div>

    <!-- 下方区域：最近活动 + 快速操作 -->
    <div class="bottom-grid">
      <!-- 最近活动 -->
      <div class="activity-card">
        <div class="card-header">
          <div class="header-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <h3 class="card-title">上传趋势</h3>
          <router-link to="/admin/picture_manage" class="view-more">查看全部</router-link>
        </div>
        <div class="activity-list">
          <div v-if="recentActivities.length === 0" class="empty-state">
            <span>暂无最近活动</span>
          </div>
          <div
            v-for="(activity, index) in recentActivities"
            :key="index"
            class="activity-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div class="activity-icon" :class="activity.type">
              <span v-if="activity.type === 'upload'">📤</span>
              <span v-else-if="activity.type === 'create'">✨</span>
              <span v-else>📊</span>
            </div>
            <div class="activity-content">
              <span class="activity-text">{{ activity.text }}</span>
              <span class="activity-time">{{ activity.time }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 快速操作 -->
      <div class="quick-actions-card">
        <div class="card-header">
          <div class="header-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
          <h3 class="card-title">快速操作</h3>
        </div>
        <div class="quick-actions">
          <router-link to="/admin/user_manage" class="quick-action-item">
            <div class="action-icon users">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="8.5" cy="7" r="4"/>
                <line x1="20" y1="8" x2="20" y2="14"/>
                <line x1="23" y1="11" x2="17" y2="11"/>
              </svg>
            </div>
            <span class="action-label">用户管理</span>
          </router-link>

          <router-link to="/admin/space_manage" class="quick-action-item">
            <div class="action-icon spaces">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              </svg>
            </div>
            <span class="action-label">空间管理</span>
          </router-link>

          <router-link to="/admin/picture_manage" class="quick-action-item">
            <div class="action-icon pictures">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
            </div>
            <span class="action-label">图片管理</span>
          </router-link>

          <router-link to="/admin/storage_config_manage" class="quick-action-item">
            <div class="action-icon storage">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <ellipse cx="12" cy="5" rx="9" ry="3"/>
                <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/>
                <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
              </svg>
            </div>
            <span class="action-label">存储配置</span>
          </router-link>

          <router-link to="/add_space" class="quick-action-item">
            <div class="action-icon create">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </div>
            <span class="action-label">创建空间</span>
          </router-link>

          <router-link to="/add_picture" class="quick-action-item">
            <div class="action-icon upload">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
            </div>
            <span class="action-label">上传图片</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import 'echarts'
import { getSpaceUserAnalyzeUsingPost, getSpaceCategoryAnalyzeUsingPost } from '@/api/spaceAnalyzeController'
import { getDashboardStatsUsingPost } from '@/api/adminStatsController'
import { formatSize } from '@/utils'

// 统计数据
const dashboardStats = ref<API.AdminDashboardStatsVO>({})

// 统计数据加载状态
const statsLoading = ref(false)

// 用户上传趋势数据
const userTrendData = ref<API.SpaceUserAnalyzeResponse[]>([])
const userTrendLoading = ref(false)
const timeDimension = ref<'day' | 'week' | 'month'>('day')
const timeOptions = [
  { label: '日', value: 'day' as const },
  { label: '周', value: 'week' as const },
  { label: '月', value: 'month' as const },
]

// 分类数据
const categoryData = ref<API.SpaceCategoryAnalyzeResponse[]>([])
const categoryLoading = ref(false)

// 最近活动（从用户上传趋势中提取）
const recentActivities = computed(() => {
  return userTrendData.value.slice(0, 5).map((item: API.SpaceUserAnalyzeResponse) => ({
    type: 'upload',
    text: `${item.period} 上传 ${item.count} 张图片`,
    time: '',
  }))
})

// 获取仪表盘聚合统计
const fetchDashboardStats = async () => {
  statsLoading.value = true
  try {
    const res = await getDashboardStatsUsingPost()
    if (res.data.code === 0 && res.data.data) {
      dashboardStats.value = res.data.data
    }
  } catch (error) {
    message.error('获取仪表盘统计失败')
  } finally {
    statsLoading.value = false
  }
}

// 获取用户上传趋势
const fetchUserTrendData = async () => {
  userTrendLoading.value = true
  try {
    const res = await getSpaceUserAnalyzeUsingPost({
      queryAll: true,
      timeDimension: timeDimension.value,
    })
    if (res.data.code === 0 && res.data.data) {
      userTrendData.value = res.data.data ?? []
    }
  } catch (error) {
    message.error('获取用户上传趋势失败')
  } finally {
    userTrendLoading.value = false
  }
}

// 获取分类数据
const fetchCategoryData = async () => {
  categoryLoading.value = true
  try {
    const res = await getSpaceCategoryAnalyzeUsingPost({
      queryAll: true,
    })
    if (res.data.code === 0 && res.data.data) {
      categoryData.value = res.data.data ?? []
    }
  } catch (error) {
    message.error('获取分类数据失败')
  } finally {
    categoryLoading.value = false
  }
}

// 刷新所有数据
const refreshData = () => {
  fetchDashboardStats()
  fetchUserTrendData()
  fetchCategoryData()
}

// 监听时间维度变化
watch(timeDimension, () => {
  fetchUserTrendData()
})

// 用户上传趋势图表配置
const userTrendOptions = computed(() => {
  const periods = userTrendData.value?.map((item: API.SpaceUserAnalyzeResponse) => item.period) || []
  const counts = userTrendData.value?.map((item: API.SpaceUserAnalyzeResponse) => item.count) || []

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#d0d7de',
      textStyle: { color: '#1f2328' },
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
      axisLine: { lineStyle: { color: '#d0d7de' } },
      axisLabel: { color: '#656d76', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      name: '上传数量',
      axisLine: { show: true, lineStyle: { color: '#0969da' } },
      axisLabel: { color: '#656d76' },
      splitLine: { lineStyle: { color: '#e8eaed' } },
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
          color: '#0969da',
          width: 3,
        },
        itemStyle: {
          color: '#0969da',
          borderColor: '#fff',
          borderWidth: 2,
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(9, 105, 218, 0.15)' },
              { offset: 1, color: 'rgba(9, 105, 218, 0)' },
            ],
          },
        },
      },
    ],
  }
})

// 分类分布图表配置
const categoryOptions = computed(() => {
  const categories = categoryData.value?.map((item: API.SpaceCategoryAnalyzeResponse) => item.category) || []
  const countData = categoryData.value?.map((item: API.SpaceCategoryAnalyzeResponse) => item.count) || []

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#d0d7de',
      textStyle: { color: '#1f2328' },
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
      data: categories,
      axisLine: { lineStyle: { color: '#d0d7de' } },
      axisLabel: { color: '#656d76', fontSize: 11 },
    },
    yAxis: [
      {
        type: 'value',
        name: '图片数量',
        axisLine: { show: true, lineStyle: { color: '#0969da' } },
        axisLabel: { color: '#656d76' },
        splitLine: { lineStyle: { color: '#e8eaed' } },
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
              { offset: 0, color: '#0969da' },
              { offset: 1, color: '#0550ae' },
            ],
          },
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: '50%',
      },
    ],
  }
})

onMounted(() => {
  refreshData()
})
</script>

<style lang="less" scoped>
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-content {
  .page-title {
    display: flex;
    align-items: center;
    gap: 12px;
    font-family: var(--admin-font);
    font-size: 28px;
    font-weight: 700;
    color: var(--admin-text-primary);
    margin: 0 0 8px 0;

    span:first-child {
      font-size: 28px;
    }
  }

  .page-subtitle {
    font-size: 14px;
    color: var(--admin-text-secondary);
    margin: 0;
  }
}

.header-actions {
  .action-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 20px;
    background: var(--admin-bg-primary);
    border: 1px solid var(--admin-border-default);
    border-radius: 8px;
    color: var(--admin-text-primary);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s ease;

    &:hover {
      background: var(--admin-bg-hover);
      border-color: var(--admin-primary);
      color: var(--admin-primary);
    }

    svg {
      flex-shrink: 0;
    }
  }
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: 12px;
  animation: card-reveal 0.5s ease-out forwards;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--admin-primary);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }
}

@keyframes card-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  flex-shrink: 0;

  &.users {
    background: var(--admin-success-bg);
    color: var(--admin-success);
  }

  &.pictures {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
  }

  &.spaces {
    background: #f3e8ff;
    color: #9333ea;
  }

  &.storage {
    background: var(--admin-danger-bg);
    color: var(--admin-danger);
  }

  &.recommend {
    background: #fef3c7;
    color: #f59e0b;
  }

  &.click {
    background: #dbeafe;
    color: #3b82f6;
  }

  &.ctr {
    background: #dcfce7;
    color: #22c55e;
  }

  &.collect {
    background: #fce7f3;
    color: #ec4899;
  }
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--admin-text-secondary);
}

.stat-value {
  font-family: var(--admin-font);
  font-size: 28px;
  font-weight: 700;
  color: var(--admin-text-primary);
  line-height: 1.2;
}

.stat-desc {
  font-size: 12px;
  color: var(--admin-text-disabled);
}

.stat-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.stat-loader {
  width: 24px;
  height: 24px;
  border: 2px solid var(--admin-border-default);
  border-top-color: var(--admin-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* 图表网格 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: 12px;
  padding: 20px;
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  }
}

.chart-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.chart-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--admin-success-bg);
  border-radius: 10px;
  color: var(--admin-success);

  &.category {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
  }
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--admin-text-primary);
  margin: 0;
  flex: 1;
}

.time-selector {
  display: flex;
  background: var(--admin-bg-tertiary);
  border-radius: 8px;
  padding: 4px;
}

.time-btn {
  padding: 6px 14px;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--admin-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: var(--admin-text-primary);
  }

  &.active {
    background: var(--admin-primary);
    color: white;
  }
}

.chart-content {
  min-height: 280px;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 280px;
}

.loader-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--admin-border-default);
  border-top-color: var(--admin-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 下方区域 */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.activity-card,
.quick-actions-card {
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: 12px;
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.header-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--admin-danger-bg);
  border-radius: 10px;
  color: var(--admin-danger);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--admin-text-primary);
  margin: 0;
  flex: 1;
}

.view-more {
  font-size: 13px;
  color: var(--admin-primary);
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

/* 活动列表 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: var(--admin-text-disabled);
  font-size: 14px;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--admin-bg-tertiary);
  border-radius: 8px;
  animation: item-reveal 0.4s ease-out forwards;
  opacity: 0;
  transform: translateX(-10px);
}

@keyframes item-reveal {
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.activity-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 16px;

  &.upload {
    background: var(--admin-success-bg);
  }

  &.create {
    background: var(--admin-primary-bg);
  }
}

.activity-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.activity-text {
  font-size: 14px;
  color: var(--admin-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.activity-time {
  font-size: 12px;
  color: var(--admin-text-disabled);
}

/* 快速操作 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.quick-action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  background: var(--admin-bg-tertiary);
  border: 1px solid var(--admin-border-default);
  border-radius: 10px;
  text-decoration: none;
  transition: all 0.2s ease;

  &:hover {
    background: var(--admin-primary-bg);
    border-color: var(--admin-primary);
    transform: translateY(-2px);

    .action-icon {
      transform: scale(1.1);
    }
  }
}

.action-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  transition: transform 0.2s ease;

  &.users {
    background: var(--admin-success-bg);
    color: var(--admin-success);
  }

  &.spaces {
    background: #f3e8ff;
    color: #9333ea;
  }

  &.pictures {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
  }

  &.storage {
    background: var(--admin-danger-bg);
    color: var(--admin-danger);
  }

  &.create {
    background: var(--admin-success-bg);
    color: var(--admin-success);
  }

  &.upload {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
  }
}

.action-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--admin-text-primary);
  text-align: center;
}
</style>
