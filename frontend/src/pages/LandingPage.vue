<template>
  <div id="landingPage">
    <!-- 沉浸式背景层 -->
    <div class="ambient-bg">
      <div class="starfield"></div>
      <div class="starfield starfield-2"></div>
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="hero-glow-center"></div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- Hero 区域 -->
      <section class="hero-section">
        <!-- 品牌标识 -->
        <div class="brand-section">
          <div class="brand-logo">
            <svg class="brand-icon" viewBox="0 0 48 48" fill="none">
              <circle cx="24" cy="24" r="20" stroke="url(#brandGradient)" stroke-width="3"/>
              <circle cx="24" cy="24" r="12" fill="url(#brandGradient)" opacity="0.3"/>
              <circle cx="24" cy="24" r="6" fill="url(#brandGradient)"/>
              <defs>
                <linearGradient id="brandGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#f472b6"/>
                  <stop offset="50%" stop-color="#a855f7"/>
                  <stop offset="100%" stop-color="#9333ea"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div class="brand-text">
            <span class="brand-name">VisionSpace</span>
            <span class="brand-tagline">视觉灵感空间</span>
          </div>
        </div>

        <!-- 主标题 -->
        <h1 class="hero-title">
          <span class="title-word" v-for="(word, i) in titleWords" :key="i"
                :style="{ animationDelay: `${i * 0.15}s` }">
            {{ word }}
          </span>
        </h1>
        <p class="hero-subtitle">在光影之间，寻找共鸣<br/>探索无限视觉灵感</p>

        <!-- 搜索胶囊 -->
        <div class="search-capsule" :class="{ 'search-focused': isSearchFocused }">
          <div class="search-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
          </div>
          <input
            v-model="searchText"
            type="text"
            placeholder="搜索图片、标签或创作者..."
            class="search-input"
            @focus="isSearchFocused = true"
            @blur="isSearchFocused = false"
            @keydown.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">
            <span>探索</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </button>
        </div>

        <!-- 快捷入口 -->
        <div class="quick-entry">
          <div class="quick-entry-inner" @click="router.push('/agent')">
            <div class="quick-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 6v6l4 2"/>
              </svg>
            </div>
            <div class="quick-text">
              <span class="quick-title">SpaceMind</span>
              <span class="quick-desc">描述需求，AI 帮您找图</span>
            </div>
            <div class="quick-arrow">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </div>
          </div>
        </div>

        <!-- 探索入口 -->
        <div class="explore-hint" @click="router.push('/explore')">
          <span>探索精彩图片</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12l7 7 7-7"/>
          </svg>
        </div>
      </section>

      <!-- 特色展示区 -->
      <section class="features-section">
        <div class="features-grid">
          <div class="feature-card" v-for="(feature, i) in features" :key="i"
               :style="{ animationDelay: `${1.2 + i * 0.1}s` }">
            <div class="feature-icon">
              <component :is="feature.icon" />
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </section>
    </div>

    <!-- 底部渐变 -->
    <div class="bottom-fade"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, h } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const searchText = ref('')
const isSearchFocused = ref(false)
const titleWords = ['发现', '视觉', '灵感']

// 功能特色
const features = [
  {
    title: 'AI 智能推荐',
    desc: '描述需求，SpaceMind 秒级匹配',
    icon: () => h('svg', { width: 28, height: 28, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('circle', { cx: 12, cy: 12, r: 10 }),
      h('path', { d: 'M12 6v6l4 2' })
    ])
  },
  {
    title: '海量视觉灵感',
    desc: '探索千万级高质量图片',
    icon: () => h('svg', { width: 28, height: 28, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('rect', { x: 3, y: 3, width: 18, height: 18, rx: 2 }),
      h('circle', { cx: 8.5, cy: 8.5, r: 1.5 }),
      h('path', { d: 'M21 15l-5-5L5 21' })
    ])
  },
  {
    title: '私人空间管理',
    desc: '创建画布，沉淀你的灵感库',
    icon: () => h('svg', { width: 28, height: 28, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('path', { d: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z' })
    ])
  }
]

const doSearch = () => {
  if (searchText.value.trim()) {
    router.push({ path: '/search_picture', query: { keyword: searchText.value } })
  }
}
</script>

<style scoped lang="less">
#landingPage {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

/* ========== 氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.starfield {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 10% 15%, rgba(255,255,255,0.7) 0%, transparent 100%),
    radial-gradient(1px 1px at 25% 40%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 40% 10%, rgba(255,255,255,0.8) 0%, transparent 100%),
    radial-gradient(1px 1px at 55% 60%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 70% 30%, rgba(255,255,255,0.6) 0%, transparent 100%),
    radial-gradient(1px 1px at 85% 70%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 15% 80%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 92% 55%, rgba(255,255,255,0.6) 0%, transparent 100%),
    radial-gradient(2px 2px at 35% 75%, rgba(168,85,247,0.6) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 60% 85%, rgba(236,72,153,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 5% 50%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 78% 8%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 48% 25%, rgba(168,85,247,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 88% 92%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 22% 65%, rgba(255,255,255,0.3) 0%, transparent 100%);
  animation: starfield-drift 120s linear infinite;
}

.starfield-2 {
  background-image:
    radial-gradient(1px 1px at 18% 28%, rgba(255,255,255,0.6) 0%, transparent 100%),
    radial-gradient(1px 1px at 33% 72%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 62% 18%, rgba(255,255,255,0.7) 0%, transparent 100%),
    radial-gradient(1px 1px at 80% 48%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 45% 92%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 12% 58%, rgba(168,85,247,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 72% 82%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 95% 20%, rgba(255,255,255,0.3) 0%, transparent 100%),
    radial-gradient(2px 2px at 50% 50%, rgba(168,85,247,0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 28% 8%, rgba(255,255,255,0.5) 0%, transparent 100%);
  animation: starfield-drift 180s linear infinite reverse;
  opacity: 0.7;
}

@keyframes starfield-drift {
  from { transform: translateY(0); }
  to { transform: translateY(-200px); }
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  animation: orb-breathe 12s ease-in-out infinite;
}

.orb-1 {
  width: 700px;
  height: 700px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.35) 0%, rgba(110, 53, 235, 0.15) 50%, transparent 70%);
  top: -250px;
  right: -150px;
  animation-name: orb-breathe-1;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.25) 0%, rgba(168, 85, 247, 0.1) 50%, transparent 70%);
  top: 35%;
  left: -200px;
  animation-name: orb-breathe-2;
  animation-delay: -4s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, rgba(34, 104, 245, 0.1) 50%, transparent 70%);
  bottom: -150px;
  right: 15%;
  animation-name: orb-breathe-3;
  animation-delay: -8s;
}

@keyframes orb-breathe-1 {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.7; }
  33% { transform: translate(40px, -40px) scale(1.08); opacity: 0.9; }
  66% { transform: translate(-30px, 30px) scale(0.95); opacity: 0.6; }
}

@keyframes orb-breathe-2 {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.6; }
  40% { transform: translate(-50px, 30px) scale(1.1); opacity: 0.85; }
  70% { transform: translate(30px, -20px) scale(0.92); opacity: 0.5; }
}

@keyframes orb-breathe-3 {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.55; }
  50% { transform: translate(20px, -50px) scale(1.12); opacity: 0.8; }
}

.hero-glow-center {
  position: absolute;
  top: 30%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 800px;
  height: 600px;
  background: radial-gradient(ellipse, rgba(168, 85, 247, 0.12) 0%, rgba(236, 72, 153, 0.08) 30%, transparent 70%);
  pointer-events: none;
  animation: glow-pulse 4s ease-in-out infinite;
}

@keyframes glow-pulse {
  0%, 100% { opacity: 0.6; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 1; transform: translate(-50%, -50%) scale(1.1); }
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.hero-section {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 60px 24px 80px;
}

/* 品牌标识 */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  animation: fade-up 0.8s ease-out forwards;
  opacity: 0;
}

.brand-logo {
  width: 72px;
  height: 72px;
  animation: logo-breathe 4s ease-in-out infinite;
}

.brand-icon {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 0 12px rgba(168, 85, 247, 0.5));
  transition: filter 0.4s ease;
}

@keyframes logo-breathe {
  0%, 100% { filter: drop-shadow(0 0 12px rgba(168, 85, 247, 0.5)); }
  50% { filter: drop-shadow(0 0 28px rgba(168, 85, 247, 0.8)); }
}

.brand-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.brand-name {
  font-family: var(--font-brand);
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 8px;
  background: linear-gradient(135deg, #f8fafc 0%, #f472b6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-transform: uppercase;
}

.brand-tagline {
  font-size: 12px;
  color: var(--color-text-tertiary);
  letter-spacing: 4px;
}

/* 主标题 */
.hero-title {
  font-family: var(--font-display);
  font-size: clamp(56px, 12vw, 120px);
  font-weight: 800;
  line-height: 1.05;
  margin: 0 0 24px 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px 32px;
}

.title-word {
  display: inline-block;
  animation: title-reveal 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  opacity: 0;
  transform: translateY(50px);

  &:nth-child(1) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7c3aed 100%);
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

.hero-subtitle {
  font-size: clamp(16px, 2.5vw, 22px);
  color: var(--color-text-secondary);
  margin: 0 0 40px 0;
  font-weight: 400;
  letter-spacing: 1px;
  line-height: 1.7;
  animation: fade-up 0.8s ease-out 0.4s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 搜索胶囊 */
.search-capsule {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 6px 6px 20px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 60px;
  width: 100%;
  max-width: 720px;
  margin-bottom: 24px;
  transition: all 0.3s ease;
  animation: fade-up 0.8s ease-out 0.6s forwards;
  opacity: 0;
  transform: translateY(20px);

  &:hover {
    background: var(--glass-bg-light);
    border-color: rgba(148, 163, 184, 0.2);
  }

  &.search-focused {
    background: var(--glass-bg-light);
    border-color: var(--color-primary);
    box-shadow: 0 0 40px rgba(168, 85, 247, 0.3);
  }
}

.search-icon {
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: color 0.3s ease;
}

.search-capsule.search-focused .search-icon {
  color: var(--color-primary);
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 16px;
  color: var(--text-primary);
  min-width: 0;
  letter-spacing: 0.5px;

  &::placeholder {
    color: var(--text-tertiary);
  }
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: linear-gradient(135deg, #f472b6 0%, #a855f7 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  flex-shrink: 0;
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-3px) scale(1.03);
    box-shadow: 0 8px 35px rgba(244, 114, 182, 0.55), 0 0 20px rgba(168, 85, 247, 0.3);
  }

  &:active {
    transform: translateY(-1px) scale(0.98);
    box-shadow: 0 2px 12px rgba(168, 85, 247, 0.4);
  }
}

/* 快捷入口 */
.quick-entry {
  margin-top: 8px;
  animation: fade-up 0.8s ease-out 0.75s forwards;
  opacity: 0;
  transform: translateY(20px);
}

.quick-entry-inner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: var(--gradient-glass);
  border: 1px solid var(--color-border-subtle);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.25s ease;
  max-width: 480px;
  width: 100%;

  &:hover {
    background: var(--color-bg-hover);
    border-color: var(--color-border-accent);
    transform: translateY(-2px);
    box-shadow: 0 8px 30px rgba(168, 85, 247, 0.2);
  }
}

.quick-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--gradient-aurora);
  color: white;
  flex-shrink: 0;
}

.quick-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  text-align: left;
}

.quick-title {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.quick-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.quick-arrow {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  transition: transform 0.3s ease;
}

.quick-entry-inner:hover .quick-arrow {
  transform: translateX(4px);
  color: var(--color-primary);
}

/* 探索入口 */
.explore-hint {
  margin-top: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-tertiary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  animation: fade-up 0.8s ease-out 0.9s forwards, float-hint 3s ease-in-out infinite 1.5s;
  opacity: 0;

  &:hover {
    color: var(--color-primary);
    gap: 12px;
  }
}

@keyframes float-hint {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(6px); }
}

/* ========== 特色展示区 ========== */
.features-section {
  padding: 80px 24px 120px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.feature-card {
  padding: 32px 24px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur-light);
  -webkit-backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  text-align: center;
  animation: fade-up 0.8s ease-out forwards;
  opacity: 0;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-8px);
    border-color: var(--color-border-accent);
    box-shadow: 0 20px 60px rgba(168, 85, 247, 0.2);
  }
}

.feature-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-aurora);
  border-radius: 16px;
  color: white;
  opacity: 0.9;
}

.feature-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}

.feature-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
  line-height: 1.6;
}

/* ========== 底部渐变 ========== */
.bottom-fade {
  height: 200px;
  background: linear-gradient(to bottom, transparent 0%, var(--bg-primary) 100%);
  pointer-events: none;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .hero-section {
    padding: 40px 16px 60px;
  }

  .brand-logo {
    width: 56px;
    height: 56px;
  }

  .brand-name {
    font-size: 18px;
    letter-spacing: 6px;
  }

  .search-capsule {
    padding: 4px 4px 4px 16px;
    max-width: 100%;
  }

  .search-btn {
    padding: 12px 20px;
    font-size: 14px;
  }

  .features-section {
    padding: 40px 16px 80px;
  }

  .feature-card {
    padding: 24px 20px;
  }
}
</style>
