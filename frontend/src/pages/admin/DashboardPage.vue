<template>
  <div class="dashboard-page">
    <AdminPageHeader title="管理仪表盘" subtitle="系统运行状态、内容存量与推荐链路的当日脉搏。">
      <template #icon>
        <BarChart3 :size="19" :stroke-width="1.6" />
      </template>
      <template #extra>
        <div class="header-actions">
          <span class="sync-state admin-mono">{{ syncedLabel }}</span>
          <button class="action-btn" type="button" :disabled="statsLoading" @click="refreshData">
            <RefreshCw :size="14" :stroke-width="1.8" aria-hidden="true" />
            <span>刷新数据</span>
          </button>
        </div>
      </template>
    </AdminPageHeader>

    <!-- ============ 核心账目 ============ -->
    <section class="ledger" aria-labelledby="core-ledger-title">
      <div class="ledger-head">
        <h2 id="core-ledger-title" class="ledger-title">
          <span class="ledger-no admin-mono" aria-hidden="true">01</span>
          核心账目
        </h2>
        <span class="admin-mono">Core ledger</span>
      </div>

      <div class="ledger-grid">
        <article v-for="(metric, index) in coreMetrics" :key="metric.label" class="ledger-cell">
          <span class="cell-no admin-mono" aria-hidden="true">{{
            String(index + 1).padStart(2, '0')
          }}</span>
          <span class="cell-label">
            {{ metric.label }}
            <em>{{ metric.en }}</em>
          </span>
          <strong class="cell-value" :class="{ 'is-loading': statsUnavailable }">
            {{ statsUnavailable ? '—' : metric.value }}
          </strong>
          <span class="cell-desc">{{ metric.desc }}</span>
        </article>
      </div>
    </section>

    <!-- ============ 推荐脉搏 ============ -->
    <section class="ledger ledger--pulse" aria-labelledby="pulse-ledger-title">
      <div class="ledger-head">
        <h2 id="pulse-ledger-title" class="ledger-title">
          <span class="ledger-no admin-mono" aria-hidden="true">02</span>
          今日推荐脉搏
        </h2>
        <span class="admin-mono">Live pulse · {{ todayLabel }}</span>
      </div>

      <div class="ledger-grid">
        <article v-for="(metric, index) in pulseMetrics" :key="metric.label" class="ledger-cell">
          <span class="cell-no admin-mono" aria-hidden="true">{{
            String(index + 1).padStart(2, '0')
          }}</span>
          <span class="cell-label">
            {{ metric.label }}
            <em>{{ metric.en }}</em>
          </span>
          <strong class="cell-value" :class="{ 'is-loading': statsUnavailable }">
            {{ statsUnavailable ? '—' : metric.value }}
          </strong>
          <span class="cell-desc">{{ metric.desc }}</span>
        </article>
      </div>
    </section>

    <!-- ============ 图表 ============ -->
    <div class="charts-grid">
      <section class="panel chart-panel" aria-labelledby="trend-title">
        <header class="panel-head">
          <div class="panel-heading">
            <span class="panel-no admin-mono" aria-hidden="true">03</span>
            <h2 id="trend-title" class="panel-title">用户上传趋势</h2>
          </div>
          <div class="time-selector" role="group" aria-label="时间粒度">
            <button
              v-for="item in timeOptions"
              :key="item.value"
              type="button"
              class="time-btn"
              :class="{ active: timeDimension === item.value }"
              :aria-pressed="timeDimension === item.value"
              @click="timeDimension = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </header>
        <div class="panel-body">
          <div v-if="userTrendLoading" class="chart-loading">
            <span class="loading-line" aria-hidden="true"></span>
            <span class="admin-mono">Reading data…</span>
          </div>
          <p v-else-if="userTrendData.length === 0" class="chart-empty admin-mono">
            No uploads in this period
          </p>
          <v-chart v-else :option="userTrendOptions" autoresize class="chart" />
        </div>
      </section>

      <section class="panel chart-panel" aria-labelledby="category-title">
        <header class="panel-head">
          <div class="panel-heading">
            <span class="panel-no admin-mono" aria-hidden="true">04</span>
            <h2 id="category-title" class="panel-title">空间分类分布</h2>
          </div>
          <span class="panel-meta admin-mono">{{ categoryTotalLabel }}</span>
        </header>
        <div class="panel-body">
          <div v-if="categoryLoading" class="chart-loading">
            <span class="loading-line" aria-hidden="true"></span>
            <span class="admin-mono">Reading data…</span>
          </div>
          <p v-else-if="categoryData.length === 0" class="chart-empty admin-mono">
            No spaces classified yet
          </p>
          <v-chart v-else :option="categoryOptions" autoresize class="chart" />
        </div>
      </section>
    </div>

    <!-- ============ 最近活动 + 快捷入口 ============ -->
    <div class="bottom-grid">
      <section class="panel" aria-labelledby="activity-title">
        <header class="panel-head">
          <div class="panel-heading">
            <span class="panel-no admin-mono" aria-hidden="true">05</span>
            <h2 id="activity-title" class="panel-title">最近上传</h2>
          </div>
          <router-link to="/admin/picture_manage" class="panel-link">查看全部 →</router-link>
        </header>

        <div class="activity-list">
          <p v-if="recentActivities.length === 0" class="panel-empty admin-mono">
            No recent activity
          </p>
          <div
            v-for="(activity, index) in recentActivities"
            :key="`${activity.period}-${index}`"
            class="activity-row"
          >
            <span class="activity-no admin-mono" aria-hidden="true">{{
              String(index + 1).padStart(2, '0')
            }}</span>
            <Upload :size="14" :stroke-width="1.7" class="activity-icon" aria-hidden="true" />
            <span class="activity-text">
              <b>{{ activity.count }}</b> 张图片
            </span>
            <span class="activity-period admin-mono">{{ activity.period }}</span>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="quick-title">
        <header class="panel-head">
          <div class="panel-heading">
            <span class="panel-no admin-mono" aria-hidden="true">06</span>
            <h2 id="quick-title" class="panel-title">快捷入口</h2>
          </div>
          <span class="panel-meta admin-mono">Shortcuts</span>
        </header>

        <div class="quick-grid">
          <router-link
            v-for="(action, index) in quickActions"
            :key="action.path"
            :to="action.path"
            class="quick-row"
          >
            <span class="quick-no admin-mono" aria-hidden="true">{{
              String(index + 1).padStart(2, '0')
            }}</span>
            <component :is="action.icon" :size="16" :stroke-width="1.6" class="quick-icon" />
            <span class="quick-label">{{ action.label }}</span>
            <ArrowUpRight :size="14" :stroke-width="1.7" class="quick-arrow" aria-hidden="true" />
          </router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { computed, onMounted, ref, watch } from 'vue'
import {
  ArrowUpRight,
  BarChart3,
  Cloud,
  HardDrive,
  Image,
  MessageSquare,
  RefreshCw,
  Upload,
  Users,
} from 'lucide-vue-next'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import 'echarts'
import {
  getSpaceUserAnalyzeUsingPost,
  getSpaceCategoryAnalyzeUsingPost,
} from '@/api/spaceAnalyzeController'
import { getDashboardStatsUsingPost } from '@/api/adminStatsController'
import { formatSize } from '@/utils'
import { useTheme } from '@/composables/useTheme'

const { currentTheme } = useTheme()

const dashboardStats = ref<API.AdminDashboardStatsVO>({})
const statsLoading = ref(false)
const statsError = ref(false)
const syncedAt = ref<Date | null>(null)

// 加载中或加载失败都显示「—」：把未知渲染成 0 会让人以为系统真的没有数据
const statsUnavailable = computed(() => statsLoading.value || statsError.value)

const userTrendData = ref<API.SpaceUserAnalyzeResponse[]>([])
const userTrendLoading = ref(false)
const timeDimension = ref<'day' | 'week' | 'month'>('day')
const timeOptions = [
  { label: '日', value: 'day' as const },
  { label: '周', value: 'week' as const },
  { label: '月', value: 'month' as const },
]

const categoryData = ref<API.SpaceCategoryAnalyzeResponse[]>([])
const categoryLoading = ref(false)

const todayLabel = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`
})

const syncedLabel = computed(() => {
  if (statsError.value) return '未同步 · 数据不可用'
  if (!syncedAt.value) return '待同步'
  const time = syncedAt.value
  return `Synced ${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
})

// 后端把 Long 序列化成字符串（避免 JS 精度丢失），展示前统一转成数字
const toNumber = (value?: number | string | null) => {
  const num = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(num) ? num : 0
}

const numberText = (value?: number | string | null) => toNumber(value).toLocaleString('zh-CN')

const coreMetrics = computed(() => [
  {
    label: '用户总数',
    en: 'Users',
    value: numberText(dashboardStats.value.userCount),
    desc: '注册用户',
  },
  {
    label: '图片总数',
    en: 'Images',
    value: numberText(dashboardStats.value.pictureCount),
    desc: '已上传图片',
  },
  {
    label: '空间总数',
    en: 'Spaces',
    value: numberText(dashboardStats.value.spaceCount),
    desc: '创建的空间',
  },
  {
    label: '存储使用',
    en: 'Storage',
    value: formatSize(dashboardStats.value?.usedSize || 0),
    desc: '对象存储占用',
  },
])

const pulseMetrics = computed(() => [
  {
    label: '今日曝光',
    en: 'Impressions',
    value: numberText(dashboardStats.value.todayImpressionCount),
    desc: '推荐曝光',
  },
  {
    label: '今日点击',
    en: 'Clicks',
    value: numberText(dashboardStats.value.todayClickCount),
    desc: '推荐点击',
  },
  {
    label: '点击率',
    en: 'CTR',
    value: `${dashboardStats.value.todayCtr || 0}%`,
    desc: '点击 / 曝光',
  },
  {
    label: '今日收藏',
    en: 'Collects',
    value: numberText(dashboardStats.value.todayCollectCount),
    desc: '推荐收藏',
  },
])

const recentActivities = computed(() =>
  (userTrendData.value ?? []).slice(0, 5).map((item: API.SpaceUserAnalyzeResponse) => ({
    period: item.period ?? '',
    count: item.count ?? 0,
  })),
)

const categoryTotalLabel = computed(() => {
  const total = (categoryData.value ?? []).reduce(
    (sum: number, item: API.SpaceCategoryAnalyzeResponse) => sum + toNumber(item.count),
    0,
  )
  return `${total.toLocaleString('zh-CN')} images`
})

const quickActions = [
  { path: '/admin/user_manage', label: '用户管理', icon: Users },
  { path: '/admin/picture_manage', label: '图片管理', icon: Image },
  { path: '/admin/space_manage', label: '空间管理', icon: Cloud },
  { path: '/admin/feedback_manage', label: '反馈管理', icon: MessageSquare },
  { path: '/admin/storage_config_manage', label: '存储配置', icon: HardDrive },
]

const fetchDashboardStats = async () => {
  statsLoading.value = true
  try {
    const res = await getDashboardStatsUsingPost()
    if (res.data.code === 0 && res.data.data) {
      dashboardStats.value = res.data.data
      syncedAt.value = new Date()
      statsError.value = false
    } else {
      statsError.value = true
      message.error(res.data.message || '获取仪表盘统计失败')
    }
  } catch {
    // 失败时保持「—」，不把未知渲染成 0
    statsError.value = true
    message.error('获取仪表盘统计失败')
  } finally {
    statsLoading.value = false
  }
}

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
  } catch {
    message.error('获取用户上传趋势失败')
  } finally {
    userTrendLoading.value = false
  }
}

const fetchCategoryData = async () => {
  categoryLoading.value = true
  try {
    const res = await getSpaceCategoryAnalyzeUsingPost({ queryAll: true })
    if (res.data.code === 0 && res.data.data) {
      categoryData.value = res.data.data ?? []
    }
  } catch {
    message.error('获取分类数据失败')
  } finally {
    categoryLoading.value = false
  }
}

const refreshData = () => {
  fetchDashboardStats()
  fetchUserTrendData()
  fetchCategoryData()
}

watch(timeDimension, () => {
  fetchUserTrendData()
})

/** ECharts 不读 CSS 变量，主题色在这里按当前主题取一份。 */
const chartPalette = computed(() =>
  currentTheme.value === 'aurora'
    ? {
        ink: '#f2eee6',
        inkSoft: 'rgba(242, 238, 230, 0.55)',
        line: 'rgba(242, 238, 230, 0.14)',
        split: 'rgba(242, 238, 230, 0.07)',
        accent: '#b9a6ff',
        accentDeep: '#6f4bd8',
        accentArea: 'rgba(185, 166, 255, 0.18)',
        tooltipBg: 'rgba(10, 8, 19, 0.94)',
        tooltipBorder: 'rgba(242, 238, 230, 0.2)',
      }
    : {
        ink: '#1e1b4b',
        inkSoft: 'rgba(30, 27, 75, 0.56)',
        line: 'rgba(76, 29, 149, 0.2)',
        split: 'rgba(76, 29, 149, 0.09)',
        accent: '#7c3aed',
        accentDeep: '#5b21b6',
        accentArea: 'rgba(124, 58, 237, 0.14)',
        tooltipBg: 'rgba(251, 248, 255, 0.97)',
        tooltipBorder: 'rgba(76, 29, 149, 0.24)',
      },
)

const axisLabelStyle = computed(() => ({
  color: chartPalette.value.inkSoft,
  fontSize: 10,
  fontFamily: 'JetBrains Mono, SFMono-Regular, Consolas, monospace',
}))

const tooltipStyle = computed(() => ({
  trigger: 'axis' as const,
  backgroundColor: chartPalette.value.tooltipBg,
  borderColor: chartPalette.value.tooltipBorder,
  borderWidth: 1,
  padding: [8, 12],
  textStyle: {
    color: chartPalette.value.ink,
    fontSize: 12,
    fontFamily: 'Nunito, PingFang SC, Microsoft YaHei, sans-serif',
  },
  extraCssText: 'border-radius:2px;box-shadow:0 12px 32px -18px rgba(0,0,0,.4);',
}))

const userTrendOptions = computed(() => {
  const palette = chartPalette.value
  const periods = userTrendData.value?.map((item) => item.period) || []
  const counts = userTrendData.value?.map((item) => toNumber(item.count)) || []

  return {
    tooltip: tooltipStyle.value,
    grid: { left: 8, right: 18, bottom: 8, top: 22, containLabel: true },
    xAxis: {
      type: 'category',
      data: periods,
      boundaryGap: false,
      axisLine: { lineStyle: { color: palette.line } },
      axisTick: { show: false },
      axisLabel: axisLabelStyle.value,
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: axisLabelStyle.value,
      splitLine: { lineStyle: { color: palette.split, type: 'dashed' } },
    },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: palette.accent, width: 1.6 },
        itemStyle: {
          color: palette.accent,
          borderColor: palette.tooltipBg,
          borderWidth: 2,
        },
        emphasis: { focus: 'series' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: palette.accentArea },
              { offset: 1, color: 'rgba(0,0,0,0)' },
            ],
          },
        },
      },
    ],
  }
})

const categoryOptions = computed(() => {
  const palette = chartPalette.value
  const categories = categoryData.value?.map((item) => item.category) || []
  const countData = categoryData.value?.map((item) => toNumber(item.count)) || []

  return {
    tooltip: tooltipStyle.value,
    grid: { left: 8, right: 18, bottom: 8, top: 22, containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: palette.line } },
      axisTick: { show: false },
      axisLabel: {
        ...axisLabelStyle.value,
        fontFamily: 'Nunito, PingFang SC, Microsoft YaHei, sans-serif',
        fontSize: 11,
      },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: axisLabelStyle.value,
      splitLine: { lineStyle: { color: palette.split, type: 'dashed' } },
    },
    series: [
      {
        name: '图片数量',
        type: 'bar',
        data: countData,
        barWidth: '42%',
        itemStyle: {
          borderRadius: [1, 1, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: palette.accent },
              { offset: 1, color: palette.accentDeep },
            ],
          },
        },
        emphasis: {
          itemStyle: { color: palette.accent },
        },
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
  max-width: 1440px;
  margin: 0 auto;
}

/* ============ 页头操作 ============ */
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sync-state {
  font-size: 9px;
  color: var(--admin-text-tertiary);
  white-space: nowrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-primary);
  font-family: var(--admin-font);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    border-color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    opacity var(--admin-transition-fast);

  &:hover:not(:disabled) {
    color: var(--admin-primary);
    border-color: var(--admin-primary);
    background: var(--admin-primary-bg);
  }

  &:disabled {
    opacity: 0.45;
    cursor: wait;
  }
}

/* ============ 账目 ============ */
.ledger {
  margin-bottom: clamp(20px, 2.6vw, 30px);
  animation: admin-rise 0.55s var(--admin-ease) both;
}

.ledger--pulse {
  animation-delay: 0.06s;
}

@keyframes admin-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.ledger-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--admin-border-strong);
}

.ledger-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0;
  font-family: var(--admin-serif);
  font-size: clamp(19px, 1.8vw, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--admin-text-primary);
}

.ledger-no {
  font-size: 9px;
  color: var(--admin-primary);
}

.ledger-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-left: 1px solid var(--admin-border-default);
}

.ledger-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: clamp(18px, 2vw, 26px) clamp(16px, 1.8vw, 24px) clamp(20px, 2.2vw, 28px);
  border-right: 1px solid var(--admin-border-default);
  border-bottom: 1px solid var(--admin-border-default);
  background: color-mix(in srgb, var(--admin-bg-primary) 72%, transparent);
  transition:
    background var(--admin-transition-base),
    transform var(--admin-transition-base);

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: -1px;
    width: 0;
    height: 1px;
    background: var(--admin-primary);
    transition: width var(--admin-transition-slow);
  }

  &:hover {
    background: var(--admin-bg-primary);

    &::after {
      width: 100%;
    }

    .cell-no {
      color: var(--admin-primary);
    }
  }
}

.cell-no {
  font-size: 9px;
  color: var(--admin-text-disabled);
  transition: color var(--admin-transition-fast);
}

.cell-label {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin-top: 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--admin-text-secondary);

  em {
    font-family: var(--admin-font-mono);
    font-size: 8px;
    font-style: normal;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    color: var(--admin-text-tertiary);
  }
}

.cell-value {
  display: block;
  margin-top: 10px;
  font-family: var(--admin-serif);
  font-size: clamp(30px, 3.2vw, 44px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.01em;
  color: var(--admin-text-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &.is-loading {
    color: var(--admin-text-disabled);
  }
}

.cell-desc {
  margin-top: 8px;
  font-size: 11px;
  color: var(--admin-text-tertiary);
}

/* ============ 面板 ============ */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(16px, 2vw, 24px);
  margin-bottom: clamp(16px, 2vw, 24px);
}

.panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  animation: admin-rise 0.6s var(--admin-ease) both;
}

.chart-panel:nth-child(2) {
  animation-delay: 0.06s;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 14px;
  border-bottom: 1px solid var(--admin-border-default);
}

.panel-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.panel-no {
  font-size: 9px;
  color: var(--admin-primary);
}

.panel-title {
  margin: 0;
  font-family: var(--admin-serif);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--admin-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.panel-meta {
  font-size: 9px;
  color: var(--admin-text-tertiary);
  white-space: nowrap;
}

.panel-link {
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  text-decoration: none;
  transition: color var(--admin-transition-fast);

  &:hover {
    color: var(--admin-primary);
  }
}

.panel-body {
  flex: 1;
  padding: 14px 10px 8px;
}

.chart {
  width: 100%;
  height: 272px;
}

.chart-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  height: 272px;
  color: var(--admin-text-tertiary);
}

.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 272px;
  margin: 0;
  font-size: 9px;
  color: var(--admin-text-tertiary);
}

.loading-line {
  position: relative;
  width: 120px;
  height: 1px;
  overflow: hidden;
  background: var(--admin-border-default);

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: var(--admin-primary);
    transform-origin: left;
    animation: admin-scan 1.4s var(--admin-ease) infinite;
  }
}

@keyframes admin-scan {
  0% {
    transform: scaleX(0);
    opacity: 0.9;
  }
  60%,
  100% {
    transform: scaleX(1);
    opacity: 0;
  }
}

/* ============ 时间选择 ============ */
.time-selector {
  display: flex;
  align-items: center;
  gap: 2px;
}

.time-btn {
  min-width: 34px;
  height: 28px;
  padding: 0 10px;
  background: transparent;
  border: none;
  border-bottom: 1px solid transparent;
  color: var(--admin-text-tertiary);
  font-family: var(--admin-font);
  font-size: 12px;
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    border-color var(--admin-transition-fast);

  &:hover {
    color: var(--admin-text-primary);
  }

  &.active {
    color: var(--admin-primary);
    border-bottom-color: var(--admin-primary);
    font-weight: 600;
  }
}

/* ============ 底部 ============ */
.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: clamp(16px, 2vw, 24px);
}

.bottom-grid .panel:nth-child(2) {
  animation-delay: 0.08s;
}

.activity-list {
  padding: 4px 20px 12px;
}

.panel-empty {
  margin: 0;
  padding: 26px 0;
  text-align: center;
  font-size: 9px;
  color: var(--admin-text-tertiary);
}

.activity-row {
  display: grid;
  grid-template-columns: 26px 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--admin-border-subtle);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    .activity-no {
      color: var(--admin-primary);
    }

    .activity-icon {
      transform: translateY(-1px);
      color: var(--admin-primary);
    }
  }
}

.activity-no {
  font-size: 9px;
  color: var(--admin-text-disabled);
  transition: color var(--admin-transition-fast);
}

.activity-icon {
  color: var(--admin-text-tertiary);
  transition:
    color var(--admin-transition-fast),
    transform var(--admin-transition-fast);
}

.activity-text {
  min-width: 0;
  font-size: 13px;
  color: var(--admin-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  b {
    font-weight: 600;
    color: var(--admin-text-primary);
  }
}

.activity-period {
  font-size: 9px;
  color: var(--admin-text-tertiary);
  white-space: nowrap;
}

/* ============ 快捷入口 ============ */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 6px 12px 14px;
}

.quick-row {
  display: grid;
  grid-template-columns: 22px 18px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  padding: 12px 10px;
  border-bottom: 1px solid var(--admin-border-subtle);
  color: var(--admin-text-secondary);
  text-decoration: none;
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast);

  &:hover {
    color: var(--admin-text-primary);
    background: var(--admin-bg-tertiary);

    .quick-no,
    .quick-icon {
      color: var(--admin-primary);
    }

    .quick-arrow {
      opacity: 1;
      transform: translate(1px, -1px);
    }
  }
}

.quick-no {
  font-size: 9px;
  color: var(--admin-text-disabled);
  transition: color var(--admin-transition-fast);
}

.quick-icon {
  color: var(--admin-text-tertiary);
  transition: color var(--admin-transition-fast);
}

.quick-label {
  min-width: 0;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-arrow {
  color: var(--admin-text-tertiary);
  opacity: 0;
  transition:
    opacity var(--admin-transition-fast),
    transform var(--admin-transition-fast);
}

/* ============ 响应式 ============ */
@media (max-width: 1080px) {
  .ledger-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts-grid,
  .bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .ledger-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .ledger-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .quick-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel-heading {
    min-width: 0;
  }

  .panel-title {
    font-size: 17px;
  }

  .panel-head {
    padding: 14px 16px 12px;
  }

  .activity-list {
    padding: 4px 16px 10px;
  }
}
</style>
