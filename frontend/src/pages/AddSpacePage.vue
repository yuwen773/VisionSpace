<template>
  <div id="addSpacePage">
    <!-- 紫漾梦幻氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="floating-shapes">
        <div class="shape-dot dot-1"></div>
        <div class="shape-dot dot-2"></div>
        <div class="shape-dot dot-3"></div>
        <div class="shape-dot dot-4"></div>
        <div class="shape-dot dot-5"></div>
      </div>
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
            <circle cx="24" cy="24" r="20" stroke="url(#headerGradZiyan)" stroke-width="2"/>
            <circle cx="24" cy="24" r="12" fill="url(#headerGradZiyan)" opacity="0.3"/>
            <circle cx="24" cy="24" r="6" fill="url(#headerGradZiyan)"/>
            <defs>
              <linearGradient id="headerGradZiyan" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#a855f7"/>
                <stop offset="50%" stop-color="#ec4899"/>
                <stop offset="100%" stop-color="#f472b6"/>
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
        <p class="page-subtitle">在梦幻星空中开辟属于你的视觉领地 ✨</p>
      </section>

      <!-- 表单与信息区域 -->
      <div class="content-grid">
        <!-- 左侧：创建/编辑表单 -->
        <section class="form-section">
          <div class="form-card">
            <div class="card-shine"></div>
            <div class="card-content">
              <!-- 空间图标 -->
              <div class="space-icon-preview">
                <div class="icon-orb" :class="{ 'orb-active': spaceForm.spaceName }">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                    <circle cx="24" cy="24" r="18" stroke="url(#orbGradZiyan)" stroke-width="2" stroke-dasharray="4 4"/>
                    <circle cx="24" cy="24" r="10" fill="url(#orbGradZiyan)" opacity="0.5"/>
                    <circle cx="24" cy="24" r="4" fill="url(#orbGradZiyan)"/>
                    <defs>
                      <linearGradient id="orbGradZiyan" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="#a855f7"/>
                        <stop offset="100%" stop-color="#ec4899"/>
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
                        <path v-if="route.query.id" d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
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
            <div class="card-shine card-shine-purple"></div>
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
    <div class="bottom-decoration">
      <svg viewBox="0 0 1440 100" preserveAspectRatio="none" style="width: 100%; height: 60px;">
        <path d="M0,50 C200,100 400,0 600,50 C800,100 1000,0 1200,50 C1400,100 1440,50 1440,50 L1440,100 L0,100 Z" fill="var(--color-primary)" opacity="0.1"/>
        <path d="M0,60 C300,100 500,20 720,60 C940,100 1140,20 1440,60 L1440,100 L0,100 Z" fill="var(--color-secondary)" opacity="0.08"/>
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
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
} from '@/constants/space.ts'
import { formatSize } from '@/utils'

const router = useRouter()
const route = useRoute()

const loading = ref<boolean>(false)
const space = ref<API.SpaceVO>()
const spaceLevelList = ref<API.SpaceLevel[]>([])

const spaceForm = reactive<API.SpaceAddRequest | API.SpaceEditRequest>({})

const titleWords = ['创建', '你的', '空间']

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

onMounted(() => {
  getOldSpace()
  fetchSpaceLevelList()
})
</script>

<style scoped lang="less">
#addSpacePage {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 紫漾氛围背景 ========== */
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
  filter: blur(100px);
  opacity: 0.5;
  animation: float-orb 25s ease-in-out infinite;
}

.orb-1 {
  width: 700px;
  height: 700px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.25) 0%, transparent 70%);
  top: -250px;
  right: -150px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%);
  top: 30%;
  left: -200px;
  animation-delay: -8s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
  bottom: -100px;
  right: 10%;
  animation-delay: -16s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(40px, -40px) scale(1.05); }
  50% { transform: translate(-30px, 30px) scale(0.95); }
  75% { transform: translate(25px, 15px) scale(1.03); }
}

/* 浮动装饰点 */
.floating-shapes {
  position: absolute;
  inset: 0;
}

.shape-dot {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
  animation: float-dot 15s ease-in-out infinite;
}

.dot-1 {
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  top: 15%;
  left: 10%;
  animation-delay: 0s;
}

.dot-2 {
  width: 8px;
  height: 8px;
  background: var(--color-secondary);
  top: 25%;
  right: 20%;
  animation-delay: -3s;
}

.dot-3 {
  width: 16px;
  height: 16px;
  background: var(--color-violet);
  bottom: 30%;
  left: 15%;
  animation-delay: -6s;
}

.dot-4 {
  width: 10px;
  height: 10px;
  background: var(--color-pink);
  top: 60%;
  right: 25%;
  animation-delay: -9s;
}

.dot-5 {
  width: 6px;
  height: 6px;
  background: var(--color-primary-light);
  bottom: 20%;
  right: 40%;
  animation-delay: -12s;
}

@keyframes float-dot {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  25% { transform: translate(10px, -15px) rotate(90deg); }
  50% { transform: translate(-5px, 10px) rotate(180deg); }
  75% { transform: translate(15px, 5px) rotate(270deg); }
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
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateX(-4px);
    box-shadow: var(--shadow-glow-purple);

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
  0%, 100% { filter: drop-shadow(0 0 8px rgba(168, 85, 247, 0.4)); }
  50% { filter: drop-shadow(0 0 16px rgba(236, 72, 153, 0.5)); }
}

.brand-text {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 50%, #f472b6 100%);
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
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7c3aed 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(2) {
    background: linear-gradient(135deg, #ec4899 0%, #db2777 50%, #be185d 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  &:nth-child(3) {
    background: linear-gradient(135deg, #f472b6 0%, #e879f9 50%, #d946ef 100%);
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
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
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
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 24px;
  overflow: hidden;
  padding: 40px;
  box-shadow: var(--shadow-card);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-4px);
  }
}

.card-shine {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 30%, rgba(168, 85, 247, 0.08) 0%, transparent 50%);
  pointer-events: none;
}

.card-shine-purple {
  background: radial-gradient(circle at 70% 30%, rgba(236, 72, 153, 0.08) 0%, transparent 50%);
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
  background: rgba(168, 85, 247, 0.08);
  border: 2px dashed var(--color-primary-light);
  border-radius: 50%;
  transition: all 0.5s ease;

  &.orb-active {
    background: rgba(168, 85, 247, 0.15);
    border-color: var(--color-primary);
    border-style: solid;
    box-shadow: 0 0 40px rgba(168, 85, 247, 0.2);
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
    color: var(--text-primary);
    font-size: 14px;
    margin-bottom: 8px;
  }
}

.cosmic-input,
.cosmic-select {
  :deep(.ant-input),
  :deep(.ant-select-selector) {
    background: var(--bg-card) !important;
    border: 2px solid var(--border-default) !important;
    border-radius: 12px !important;
    padding: 12px 16px !important;
    color: var(--text-primary) !important;
    font-size: 15px !important;
    transition: all 0.3s ease !important;

    &:hover,
    &:focus {
      border-color: var(--color-primary) !important;
      box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.1) !important;
    }
  }

  :deep(.ant-input::placeholder) {
    color: var(--text-tertiary);
  }

  :deep(.ant-select-selection-placeholder) {
    color: var(--text-tertiary);
  }

  :deep(.ant-select-arrow) {
    color: var(--text-secondary);
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
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 16px;
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);
  }

  &.btn-active {
    background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-secondary-dark) 100%);
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
  background: linear-gradient(135deg, var(--color-secondary) 0%, var(--color-pink) 100%);
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
  color: var(--text-primary);

  svg {
    color: var(--color-primary);
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
  color: var(--text-tertiary);
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
  background: var(--bg-tertiary);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  animation: planet-reveal 0.5s ease-out forwards;
  opacity: 0;

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary-light);
    transform: translateX(8px);
    box-shadow: var(--shadow-glow-purple);
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
    background: linear-gradient(135deg, var(--color-violet) 0%, var(--color-primary) 100%);
    box-shadow: 0 0 15px rgba(168, 85, 247, 0.4);
  }

  &.planet-size-2 {
    width: 32px;
    height: 32px;
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    box-shadow: 0 0 20px rgba(236, 72, 153, 0.4);
  }

  &.planet-size-3 {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, var(--color-secondary) 0%, var(--color-pink) 100%);
    box-shadow: 0 0 25px rgba(244, 114, 182, 0.4);
  }
}

.planet-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotateX(70deg);
  width: 60px;
  height: 60px;
  border: 2px solid rgba(244, 114, 182, 0.5);
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
  border: 1px dashed var(--border-default);
  border-radius: 50%;
  animation: orbit-pulse 3s ease-in-out infinite;
}

@keyframes orbit-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}

.planet-info {
  flex: 1;
  min-width: 0;
}

.planet-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
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
  color: var(--text-tertiary);

  svg {
    opacity: 0.7;
  }
}

.planet-selected {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, var(--color-mint) 0%, var(--color-mint-dark) 100%);
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
  background: rgba(168, 85, 247, 0.08);
  border: 1px solid var(--border-accent);
  border-radius: 12px;
  font-size: 13px;
  color: var(--color-primary-dark);
}

/* ========== 底部装饰 ========== */
.bottom-decoration {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
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
