<template>
  <div id="addSpacePage">
    <!-- 沉浸式氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <canvas id="particleCanvas" ref="particleCanvas"></canvas>
    </div>

    <!-- 顶部导航栏 -->
    <header class="page-header">
      <button @click="goBack" class="back-btn">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <span>返回</span>
      </button>

      <div class="header-brand">
        <div class="brand-icon">
          <svg width="32" height="32" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="20" stroke="url(#headerGrad)" stroke-width="2"/>
            <circle cx="24" cy="24" r="12" fill="url(#headerGrad)" opacity="0.3"/>
            <circle cx="24" cy="24" r="6" fill="url(#headerGrad)"/>
            <defs>
              <linearGradient id="headerGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#f472b6"/>
                <stop offset="50%" stop-color="#a855f7"/>
                <stop offset="100%" stop-color="#3b82f6"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <span class="brand-text">{{ route.query.id ? '编辑空间' : '创建空间' }}</span>
      </div>

      <div class="header-spacer"></div>
    </header>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 标题区 -->
      <section class="hero-section">
        <h1 class="page-title">
          <span class="title-word" v-for="(word, i) in titleWords" :key="i"
                :style="{ animationDelay: `${i * 0.15}s` }">
            {{ word }}
          </span>
        </h1>
        <p class="page-subtitle">在宇宙中开辟属于你的视觉领地</p>
      </section>

      <!-- 表单与信息区域 -->
      <div class="content-grid">
        <!-- 左侧：创建/编辑表单 -->
        <section class="form-section">
          <div class="form-card">
            <div class="card-glow"></div>
            <div class="card-content">
              <!-- 空间图标 -->
              <div class="space-icon-preview">
                <div class="icon-orb" :class="{ 'orb-active': spaceForm.spaceName }">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                    <circle cx="24" cy="24" r="18" stroke="url(#orbGrad)" stroke-width="2" stroke-dasharray="4 4"/>
                    <circle cx="24" cy="24" r="10" fill="url(#orbGrad)" opacity="0.5"/>
                    <circle cx="24" cy="24" r="4" fill="url(#orbGrad)"/>
                    <defs>
                      <linearGradient id="orbGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="#a855f7"/>
                        <stop offset="100%" stop-color="#3b82f6"/>
                      </linearGradient>
                    </defs>
                  </svg>
                </div>
              </div>

              <a-form
                name="spaceForm"
                layout="vertical"
                :model="spaceForm"
                @finish="handSubmit"
                class="space-form"
              >
                <a-form-item
                  name="spaceName"
                  label="空间名称"
                  :rules="[{ required: true, message: '请输入空间名称' }]"
                >
                  <a-input
                    v-model:value="spaceForm.spaceName"
                    placeholder="给你的空间起个名字..."
                    allow-clear
                    size="large"
                    class="cosmic-input"
                  />
                </a-form-item>

                <a-form-item
                  name="spaceLevel"
                  label="空间级别"
                  :rules="[{ required: true, message: '请选择空间级别' }]"
                >
                  <a-select
                    v-model:value="spaceForm.spaceLevel"
                    :options="SPACE_LEVEL_OPTIONS"
                    placeholder="选择你的空间级别"
                    allow-clear
                    size="large"
                    class="cosmic-select"
                  />
                </a-form-item>

                <a-form-item class="submit-form-item">
                  <button
                    :loading="loading"
                    html-type="submit"
                    class="submit-btn"
                    :class="{ 'btn-active': spaceForm.spaceName && spaceForm.spaceLevel }"
                  >
                    <span class="btn-content">
                      <svg v-if="!loading" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path v-if="!route.query.id" d="M12 5v14M5 12h14"/>
                        <path v-else d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                        <path v-if="route.query.id" d="M17 21v-8H7v8M7 3v5h8"/>
                      </svg>
                      <span>{{ route.query.id ? '保存修改' : '创建空间' }}</span>
                    </span>
                    <span class="btn-glow"></span>
                  </button>
                </a-form-item>
              </a-form>
            </div>
          </div>
        </section>

        <!-- 右侧：空间级别介绍 -->
        <section class="info-section">
          <div class="info-card">
            <div class="card-glow card-glow-purple"></div>
            <div class="card-content">
              <div class="info-header">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="16" x2="12" y2="12"/>
                  <line x1="12" y1="8" x2="12.01" y2="8"/>
                </svg>
                <h3>空间级别</h3>
              </div>

              <p class="info-hint">选择适合你的存储空间</p>

              <div class="planet-list">
                <div
                  v-for="(level, index) in spaceLevelList"
                  :key="level.level"
                  class="planet-card"
                  :class="`planet-${index + 1}`"
                  :style="{ animationDelay: `${0.1 * index + 0.3}s` }"
                  @click="selectLevel(level.level)"
                >
                  <div class="planet-visual">
                    <div class="planet" :class="`planet-size-${index + 1}`">
                      <div class="planet-ring" v-if="index === 2"></div>
                    </div>
                    <div class="planet-orbit"></div>
                  </div>
                  <div class="planet-info">
                    <span class="planet-name">{{ level.text }}</span>
                    <div class="planet-stats">
                      <span class="stat">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                        </svg>
                        {{ formatSize(level.maxSize) }}
                      </span>
                      <span class="stat">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                          <circle cx="8.5" cy="8.5" r="1.5"/>
                          <polyline points="21 15 16 10 5 21"/>
                        </svg>
                        {{ level.maxCount }} 张
                      </span>
                    </div>
                  </div>
                  <div class="planet-selected" v-if="spaceForm.spaceLevel === level.level">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="upgrade-tip">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                <span>升级空间级别请联系管理员</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>

    <!-- 底部装饰 -->
    <div class="bottom-glow"></div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost,
} from '@/api/spaceController.ts'
import { useRouter, useRoute } from 'vue-router'
import {
  SPACE_LEVEL_OPTIONS,
  SPACE_TYPE_ENUM,
  SPACE_LEVEL_ENUM,
} from '@/constants/space.ts'
import { formatSize } from '@/utils'

const router = useRouter()
const route = useRoute()

const loading = ref<boolean>(false)
const space = ref<API.SpaceVO>()
const spaceLevelList = ref<API.SpaceLevel[]>([])

const spaceForm = reactive<API.SpaceAddRequest | API.SpaceEditRequest>({})

const titleWords = ['创建', '你的', '空间']

let particleAnimationId: number | null = null
let resizeHandler: (() => void) | null = null
const particleCanvas = ref<HTMLCanvasElement>()

const handSubmit = async () => {
  loading.value = true
  try {
    const spaceId = space.value?.id
    let res
    if (spaceId) {
      res = await updateSpaceUsingPost({ id: spaceId, ...spaceForm })
    } else {
      res = await addSpaceUsingPost({ ...spaceForm, spaceType: spaceType.value })
    }
    if (res.data.code === 0) {
      message.success(spaceId ? '空间已更新' : '空间创建成功')
      if (spaceId) {
        router.push(`/space/${spaceId}`)
      } else {
        router.push('/my_space')
      }
    } else {
      message.error((spaceId ? '修改失败' : '创建失败') + '：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

const getOldSpace = async () => {
  const id = route.query?.id
  if (id) {
    try {
      const res = await getSpaceVoByIdUsingGet({ id })
      if (res.data.code === 0 && res.data.data) {
        const data = res.data.data
        space.value = data
        spaceForm.spaceName = data.spaceName
        spaceForm.spaceLevel = data.spaceLevel
      }
    } catch (error) {
      message.error('获取空间信息失败')
    }
  }
}

const fetchSpaceLevelList = async () => {
  try {
    const res = await listSpaceLevelUsingGet()
    if (res.data.code === 0 && res.data.data) {
      spaceLevelList.value = res.data.data
    }
  } catch (error) {
    message.error('获取空间级别信息失败')
  }
}

const spaceType = computed(() => {
  if (route.query?.type) {
    return Number(route.query?.type)
  }
  return SPACE_TYPE_ENUM.PRIVATE
})

const selectLevel = (level: number) => {
  spaceForm.spaceLevel = level
}

const goBack = () => {
  router.back()
}

// 粒子背景初始化
const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  resizeHandler = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resizeHandler()
  window.addEventListener('resize', resizeHandler)

  interface Particle {
    x: number
    y: number
    size: number
    speedX: number
    speedY: number
    opacity: number
  }

  const particles: Particle[] = []
  const particleCount = 40

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
      ctx.fillStyle = `rgba(168, 85, 247, ${p.opacity})`
      ctx.fill()
    })

    particleAnimationId = requestAnimationFrame(animate)
  }

  animate()
}

onMounted(() => {
  getOldSpace()
  fetchSpaceLevelList()
  initParticles()
})

onUnmounted(() => {
  if (particleAnimationId) {
    cancelAnimationFrame(particleAnimationId)
  }
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
})
</script>

<style scoped lang="less">
#addSpacePage {
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

/* ========== 顶部导航栏 ========== */
.page-header {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 40px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
    border-color: rgba(255, 255, 255, 0.18);
    transform: translateX(-4px);

    svg {
      transform: translateX(-2px);
    }
  }

  svg {
    transition: transform 0.3s ease;
  }
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  animation: pulse-glow 3s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% { filter: drop-shadow(0 0 10px rgba(168, 85, 247, 0.4)); }
  50% { filter: drop-shadow(0 0 20px rgba(168, 85, 247, 0.6)); }
}

.brand-text {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #f8fafc 0%, #f472b6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-spacer {
  width: 140px;
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 40px 80px;
}

/* ========== Hero 区域 ========== */
.hero-section {
  text-align: center;
  padding: 40px 0 60px;
}

.page-title {
  font-family: var(--font-display);
  font-size: clamp(36px, 6vw, 64px);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 16px 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px 20px;
}

.title-word {
  display: inline-block;
  animation: title-reveal 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  opacity: 0;
  transform: translateY(40px);

  &:nth-child(1) {
    background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 50%, #2563eb 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(2) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7e22ce 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(3) {
    background: linear-gradient(135deg, #f472b6 0%, #ec4899 50%, #db2777 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

@keyframes title-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-subtitle {
  font-size: 18px;
  color: var(--color-text-secondary);
  margin: 0;
  font-weight: 400;
  letter-spacing: 1px;
  animation: fade-up 0.8s ease-out 0.5s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 内容网格 ========== */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: start;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}

/* ========== 表单卡片 ========== */
.form-section,
.info-section {
  animation: card-reveal 0.6s ease-out forwards;
  opacity: 0;
}

.form-section {
  animation-delay: 0.2s;
}

.info-section {
  animation-delay: 0.4s;
}

@keyframes card-reveal {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.form-card,
.info-card {
  position: relative;
  background: rgba(26, 35, 50, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  overflow: hidden;
  padding: 40px;
}

.card-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 30%, rgba(59, 130, 246, 0.15) 0%, transparent 50%);
  pointer-events: none;
}

.card-glow-purple {
  background: radial-gradient(circle at 70% 30%, rgba(168, 85, 247, 0.15) 0%, transparent 50%);
}

.card-content {
  position: relative;
  z-index: 1;
}

/* ========== 空间图标预览 ========== */
.space-icon-preview {
  display: flex;
  justify-content: center;
  margin-bottom: 32px;
}

.icon-orb {
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.1);
  border: 2px dashed rgba(168, 85, 247, 0.3);
  border-radius: 50%;
  transition: all 0.5s ease;

  &.orb-active {
    background: rgba(168, 85, 247, 0.2);
    border-color: rgba(168, 85, 247, 0.6);
    box-shadow: 0 0 40px rgba(168, 85, 247, 0.3);
    animation: orb-pulse 2s ease-in-out infinite;
  }
}

@keyframes orb-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

/* ========== 表单样式 ========== */
.space-form {
  :deep(.ant-form-item) {
    margin-bottom: 24px;
  }

  :deep(.ant-form-item-label > label) {
    font-weight: 600;
    color: var(--color-text-primary);
    font-size: 14px;
    margin-bottom: 8px;
  }
}

.cosmic-input,
.cosmic-select {
  :deep(.ant-input),
  :deep(.ant-select-selector) {
    background: rgba(26, 35, 50, 0.8) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    border-radius: 12px !important;
    padding: 12px 16px !important;
    color: var(--color-text-primary) !important;
    font-size: 15px !important;
    transition: all 0.3s ease !important;

    &:hover,
    &:focus {
      border-color: rgba(168, 85, 247, 0.5) !important;
      box-shadow: 0 0 20px rgba(168, 85, 247, 0.15) !important;
    }
  }

  :deep(.ant-input::placeholder) {
    color: rgba(255, 255, 255, 0.3);
  }

  :deep(.ant-select-selection-placeholder) {
    color: rgba(255, 255, 255, 0.3);
  }

  :deep(.ant-select-arrow) {
    color: rgba(255, 255, 255, 0.5);
  }
}

.submit-form-item {
  margin-top: 32px;

  :deep(.ant-form-item-control-input) {
    display: block;
  }
}

.submit-btn {
  position: relative;
  width: 100%;
  height: 56px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.6) 0%, rgba(168, 85, 247, 0.6) 100%);
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 16px;
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 40px rgba(168, 85, 247, 0.35);
  }

  &.btn-active {
    background: linear-gradient(135deg, #3b82f6 0%, #a855f7 100%);
    border-color: rgba(168, 85, 247, 0.5);
    box-shadow: 0 4px 25px rgba(168, 85, 247, 0.4);
  }
}

.btn-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.btn-glow {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #a855f7 0%, #f472b6 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.submit-btn:hover .btn-glow {
  opacity: 1;
}

/* ========== 信息卡片 ========== */
.info-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--color-text-primary);

  svg {
    color: #a855f7;
  }

  h3 {
    font-family: var(--font-display);
    font-size: 20px;
    font-weight: 700;
    margin: 0;
  }
}

.info-hint {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0 0 28px 0;
}

/* ========== 行星列表 ========== */
.planet-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.planet-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(26, 35, 50, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.04);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  animation: planet-reveal 0.5s ease-out forwards;
  opacity: 0;

  &:hover {
    background: rgba(26, 35, 50, 0.7);
    border-color: rgba(168, 85, 247, 0.3);
    transform: translateX(8px);
  }

  &.planet-1 { animation-delay: 0.3s; }
  &.planet-2 { animation-delay: 0.4s; }
  &.planet-3 { animation-delay: 0.5s; }
}

@keyframes planet-reveal {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.planet-visual {
  position: relative;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
}

.planet {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  transition: all 0.3s ease;

  &.planet-size-1 {
    width: 24px;
    height: 24px;
    background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
    box-shadow: 0 0 15px rgba(59, 130, 246, 0.5);
  }

  &.planet-size-2 {
    width: 32px;
    height: 32px;
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 100%);
    box-shadow: 0 0 20px rgba(168, 85, 247, 0.5);
  }

  &.planet-size-3 {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, #f472b6 0%, #ec4899 100%);
    box-shadow: 0 0 25px rgba(244, 114, 182, 0.5);
  }
}

.planet-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotateX(70deg);
  width: 60px;
  height: 60px;
  border: 2px solid rgba(244, 114, 182, 0.4);
  border-radius: 50%;
  animation: ring-rotate 10s linear infinite;
}

@keyframes ring-rotate {
  from { transform: translate(-50%, -50%) rotateX(70deg) rotateZ(0deg); }
  to { transform: translate(-50%, -50%) rotateX(70deg) rotateZ(360deg); }
}

.planet-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 48px;
  height: 48px;
  border: 1px dashed rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  animation: orbit-pulse 3s ease-in-out infinite;
}

@keyframes orbit-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}

.planet-info {
  flex: 1;
  min-width: 0;
}

.planet-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.planet-stats {
  display: flex;
  gap: 16px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-tertiary);

  svg {
    opacity: 0.6;
  }
}

.planet-selected {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  animation: check-pop 0.3s ease;
}

@keyframes check-pop {
  0% { transform: translateY(-50%) scale(0); }
  50% { transform: translateY(-50%) scale(1.2); }
  100% { transform: translateY(-50%) scale(1); }
}

/* ========== 升级提示 ========== */
.upgrade-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
  padding: 12px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 12px;
  font-size: 13px;
  color: #fbbf24;
}

/* ========== 底部装饰 ========== */
.bottom-glow {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 600px;
  height: 200px;
  background: radial-gradient(ellipse at center bottom, rgba(168, 85, 247, 0.2) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .page-header {
    padding: 16px 20px;
  }

  .back-btn span {
    display: none;
  }

  .header-brand {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
  }

  .header-spacer {
    display: none;
  }

  .main-content {
    padding: 20px 20px 60px;
  }

  .hero-section {
    padding: 20px 0 40px;
  }

  .form-card,
  .info-card {
    padding: 28px 24px;
  }

  .page-title {
    gap: 6px 16px;
  }

  .brand-text {
    font-size: 16px;
  }
}
</style>
