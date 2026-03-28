<template>
  <div id="spaceAnalyzePage">
    <!-- 沉浸式氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <canvas id="particleCanvas" ref="particleCanvas"></canvas>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航栏 -->
      <header class="page-header">
        <button @click="goBack" class="back-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>

        <div class="header-title">
          <div class="title-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
          </div>
          <h1 class="page-title">空间图库分析</h1>
          <span class="title-divider">/</span>
          <span class="title-sub" v-if="queryAll">全部空间</span>
          <span class="title-sub" v-else-if="queryPublic">公共空间</span>
          <a v-else :href="`/space/${spaceId}`" target="_blank" class="title-link">
            空间 {{ spaceId }}
          </a>
        </div>

        <div class="header-actions">
          <button class="action-btn" @click="refreshData">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"/>
              <path d="M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            <span>刷新</span>
          </button>
        </div>
      </header>

      <!-- 分析概览卡片 -->
      <section class="overview-section">
        <div class="overview-grid">
          <div class="overview-card" :style="{ animationDelay: '0.1s' }">
            <div class="overview-icon storage">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              </svg>
            </div>
            <div class="overview-content">
              <span class="overview-label">存储使用</span>
              <span class="overview-value">{{ formatSize(usageData?.usedSize || 0) }}</span>
              <span class="overview-max">/ {{ formatSize(usageData?.maxSize || 0) }}</span>
            </div>
            <div class="overview-bar">
              <div class="bar-fill" :style="{ width: `${usageData?.sizeUsageRatio || 0}%` }"></div>
            </div>
          </div>

          <div class="overview-card" :style="{ animationDelay: '0.2s' }">
            <div class="overview-icon images">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
            </div>
            <div class="overview-content">
              <span class="overview-label">图片数量</span>
              <span class="overview-value">{{ usageData?.usedCount || 0 }}</span>
              <span class="overview-max">/ {{ usageData?.maxCount || '∞' }}</span>
            </div>
            <div class="overview-bar pink">
              <div class="bar-fill" :style="{ width: `${usageData?.countUsageRatio || 0}%` }"></div>
            </div>
          </div>

          <div class="overview-card" :style="{ animationDelay: '0.3s' }">
            <div class="overview-icon category">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="overview-content">
              <span class="overview-label">分类统计</span>
              <span class="overview-value">{{ categoryCount }}</span>
              <span class="overview-max">个分类</span>
            </div>
          </div>

          <div class="overview-card" :style="{ animationDelay: '0.4s' }">
            <div class="overview-icon tags">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
                <line x1="7" y1="7" x2="7.01" y2="7"/>
              </svg>
            </div>
            <div class="overview-content">
              <span class="overview-label">标签统计</span>
              <span class="overview-value">{{ tagCount }}</span>
              <span class="overview-max">个标签</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 分析图表网格 -->
      <section class="charts-section">
        <div class="charts-grid">
          <!-- 第一行：存储 + 分类 -->
          <div class="chart-cell wide">
            <SpaceUsageAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
              @updateCount="categoryCount = $event"
            />
          </div>
          <div class="chart-cell">
            <SpaceCategoryAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
            />
          </div>

          <!-- 第二行：标签 + 大小 -->
          <div class="chart-cell">
            <SpaceTagAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
              @updateCount="tagCount = $event"
            />
          </div>
          <div class="chart-cell">
            <SpaceSizeAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
            />
          </div>

          <!-- 第三行：用户 + 排行 -->
          <div class="chart-cell">
            <SpaceUserAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
            />
          </div>
          <div class="chart-cell" v-if="isAdmin">
            <SpaceRankAnalyze
              :spaceId="spaceId"
              :queryAll="queryAll"
              :queryPublic="queryPublic"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { getSpaceUsageAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { formatSize } from '@/utils'
import SpaceUsageAnalyze from '@/components/analyze/SpaceUsageAnalyze.vue'
import SpaceCategoryAnalyze from '@/components/analyze/SpaceCategoryAnalyze.vue'
import SpaceTagAnalyze from '@/components/analyze/SpaceTagAnalyze.vue'
import SpaceSizeAnalyze from '@/components/analyze/SpaceSizeAnalyze.vue'
import SpaceUserAnalyze from '@/components/analyze/SpaceUserAnalyze.vue'
import SpaceRankAnalyze from '@/components/analyze/SpaceRankAnalyze.vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// Refs
const particleCanvas = ref<HTMLCanvasElement>()
const categoryCount = ref(0)
const tagCount = ref(0)

// Particle animation
let particleAnimationId: number | null = null

// Computed
const spaceId = computed(() => route.query?.spaceId as string)
const queryAll = computed(() => !!route.query?.queryAll)
const queryPublic = computed(() => !!route.query?.queryPublic)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// Usage data for overview
const usageData = ref<API.SpaceUsageAnalyzeResponse>({})

const fetchUsageData = async () => {
  try {
    const res = await getSpaceUsageAnalyzeUsingPost({
      queryAll: queryAll.value,
      queryPublic: queryPublic.value,
      spaceId: spaceId.value,
    })
    if (res.data.code === 0 && res.data.data) {
      usageData.value = res.data.data
    }
  } catch (error) {
    console.error('获取空间使用数据失败', error)
  }
}

// Navigation
const goBack = () => router.back()

const refreshData = () => {
  window.location.reload()
}

// Init particles
const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const resizeCanvas = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)

  interface Particle {
    x: number
    y: number
    size: number
    speedX: number
    speedY: number
    opacity: number
  }

  const particles: Particle[] = []
  const particleCount = 50

  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      size: Math.random() * 2 + 0.5,
      speedX: (Math.random() - 0.5) * 0.2,
      speedY: (Math.random() - 0.5) * 0.2,
      opacity: Math.random() * 0.4 + 0.1
    })
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    particles.forEach(p => {
      p.x += p.speedX
      p.y += p.speedY

      if (p.x < 0) p.x = canvas.width
      if (p.x > canvas.width) p.x = 0
      if (p.y < 0) p.y = canvas.height
      if (p.y > canvas.height) p.y = 0

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(148, 163, 184, ${p.opacity})`
      ctx.fill()
    })

    particleAnimationId = requestAnimationFrame(animate)
  }

  animate()
}

onMounted(() => {
  fetchUsageData()
  initParticles()
})

onUnmounted(() => {
  if (particleAnimationId) {
    cancelAnimationFrame(particleAnimationId)
  }
})
</script>

<style scoped lang="less">
#spaceAnalyzePage {
  min-height: 100vh;
  background: var(--color-bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
  animation: float-orb 20s ease-in-out infinite;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(34, 104, 245, 0.3) 0%, transparent 70%);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(110, 53, 235, 0.25) 0%, transparent 70%);
  top: 40%;
  left: -150px;
  animation-delay: -7s;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%);
  bottom: -100px;
  right: 20%;
  animation-delay: -14s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -30px) scale(1.05); }
  50% { transform: translate(-20px, 20px) scale(0.95); }
  75% { transform: translate(20px, 10px) scale(1.02); }
}

#particleCanvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 32px 80px;
}

/* ========== 顶部导航栏 ========== */
.page-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 40px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    border-color: rgba(255, 255, 255, 0.2);
    transform: translateX(-4px);
  }
}

.header-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.2);
  border-radius: 14px;
  color: #a855f7;
  animation: pulse-icon 3s ease-in-out infinite;
}

@keyframes pulse-icon {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(168, 85, 247, 0.4); }
  50% { transform: scale(1.05); box-shadow: 0 0 20px 5px rgba(168, 85, 247, 0.2); }
}

.page-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  color: white;
  margin: 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f472b6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.title-divider {
  font-size: 24px;
  color: rgba(255, 255, 255, 0.3);
}

.title-sub {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.6);
}

.title-link {
  font-size: 16px;
  color: #60a5fa;
  text-decoration: none;
  transition: all 0.3s ease;

  &:hover {
    color: #93c5fd;
    text-decoration: underline;
  }
}

.header-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 40px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    border-color: rgba(255, 255, 255, 0.2);
    transform: translateY(-2px);
  }
}

/* ========== 分析概览 ========== */
.overview-section {
  margin-bottom: 40px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.overview-card {
  padding: 24px;
  background: rgba(26, 35, 50, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  animation: card-reveal 0.6s ease-out forwards;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  }
}

@keyframes card-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.overview-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  margin-bottom: 16px;

  &.storage {
    background: rgba(34, 104, 245, 0.2);
    color: #60a5fa;
  }

  &.images {
    background: rgba(168, 85, 247, 0.2);
    color: #c084fc;
  }

  &.category {
    background: rgba(0, 212, 170, 0.2);
    color: #00d4aa;
  }

  &.tags {
    background: rgba(244, 114, 182, 0.2);
    color: #f472b6;
  }
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.overview-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}

.overview-value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: white;
}

.overview-max {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
}

.overview-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;

  .bar-fill {
    height: 100%;
    background: linear-gradient(90deg, #2268f5, #60a5fa);
    border-radius: 3px;
    transition: width 1s ease;
  }

  &.pink .bar-fill {
    background: linear-gradient(90deg, #a855f7, #f472b6);
  }
}

/* ========== 图表网格 ========== */
.charts-section {
  margin-bottom: 40px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.chart-cell {
  &.wide {
    grid-column: span 2;

    @media (max-width: 1024px) {
      grid-column: span 1;
    }
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .main-content {
    padding: 0 16px 60px;
  }

  .page-header {
    flex-wrap: wrap;
    gap: 16px;
  }

  .header-title {
    order: 2;
    width: 100%;
    padding-top: 16px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);
  }

  .page-title {
    font-size: 22px;
  }

  .overview-grid {
    gap: 12px;
  }

  .overview-card {
    padding: 16px;
  }

  .overview-value {
    font-size: 22px;
  }
}
</style>
