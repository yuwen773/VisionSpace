# VisionSpace LandingPage 全新品牌首页实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 LandingPage 从现有基础版本重设计为 6 大板块的品牌介绍首页，集成 5 个 Vue Bits 动画/交互组件。

**Architecture:** 采用 Vue Bits copy-paste 组件封装策略，每个组件独立文件放置于 `src/components/vue-bits/`，通过 CSS Variables 实现双主题自动适配。LandingPage 作为主容器，按顺序组织 6 大板块。

**Tech Stack:** Vue 3.5 + TypeScript + Vite 6 + Less + motion-v（Vue Bits 依赖）

---

## 前置依赖检查

在开始之前，运行以下命令确认开发环境正常：

```bash
cd frontend && npm run dev
```

确认 localhost:5173 可访问后再继续。

---

## 实施阶段概览

| 阶段 | 内容 | 组件/板块 |
|------|------|-----------|
| 阶段 1 | Vue Bits 组件目录搭建 | 基础架构 |
| 阶段 2 | Hero Section 实现 | Floating Lines + Splash Cursor + Image Trail |
| 阶段 3 | Brand Essence 板块 | Orbit Images |
| 阶段 4 | Signature Experiences 板块 | Card Swap × 4 |
| 阶段 5 | Visual Universe 板块 | Circular Gallery |
| 阶段 6 | AI Magic 板块 | 对话示例 + 扩图对比 |
| 阶段 7 | Final CTA 板块 | 渐变背景 + 粒子 |
| 阶段 8 | LandingPage 组装 + 双主题适配 | 全局 |

---

## 阶段 1：Vue Bits 组件目录搭建

### Task 1: 创建组件目录结构

**Files:**
- Create: `frontend/src/components/vue-bits/animations/SplashCursor.vue`
- Create: `frontend/src/components/vue-bits/animations/ImageTrail.vue`
- Create: `frontend/src/components/vue-bits/animations/FloatingLines.vue`
- Create: `frontend/src/components/vue-bits/components/CardSwap.vue`
- Create: `frontend/src/components/vue-bits/components/CircularGallery.vue`
- Create: `frontend/src/components/vue-bits/OrbitImages.vue`

**Step 1: 创建目录结构**

```bash
mkdir -p frontend/src/components/vue-bits/animations
mkdir -p frontend/src/components/vue-bits/components
```

**Step 2: 创建占位文件（便于 Git 追踪）**

每个文件初始内容为：

```vue
<!-- Placeholder for Vue Bits component -->
<template>
  <div class="vue-bits-placeholder">Component will be integrated here</div>
</template>
```

**Step 3: Commit**

```bash
git add src/components/vue-bits/
git commit -m "feat(landing): create vue-bits component directory structure"
```

---

### Task 2: 安装 motion-v 依赖（Vue Bits 需要）

**Files:**
- Modify: `frontend/package.json`

**Step 1: 检查是否已安装**

```bash
cd frontend && grep -l "motion-v" package.json
```

如果未找到，执行：

```bash
cd frontend && npm install motion-v
```

**Step 2: Commit**

```bash
git add package.json package-lock.json
git commit -m "deps(landing): install motion-v for vue-bits animations"
```

---

## 阶段 2：Hero Section 实现

### Task 3: 实现 FloatingLines 背景组件

**Files:**
- Modify: `frontend/src/components/vue-bits/animations/FloatingLines.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/backgrounds/floating-lines
点击 "Copy Code"，获取完整组件代码。

**Step 2: 粘贴并调整**

将代码粘贴到 `FloatingLines.vue`，做以下调整：
- 颜色变量化：将硬编码颜色替换为 CSS Variables（`--color-primary`、`--color-secondary`）
- 确保 `<script setup lang="ts">` 语法
- 添加 `name: 'FloatingLines'` 便于调试

**Step 3: 验证组件可编译**

```bash
cd frontend && npm run type-check
```

如有问题，调整类型声明。

**Step 4: Commit**

```bash
git add src/components/vue-bits/animations/FloatingLines.vue
git commit -m "feat(landing): implement FloatingLines background component"
```

---

### Task 4: 实现 SplashCursor 动画组件

**Files:**
- Modify: `frontend/src/components/vue-bits/animations/SplashCursor.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/animations/splash-cursor
点击 "Copy Code"。

**Step 2: 粘贴并调整**

- 颜色变量化
- 添加移动端禁用逻辑（检测 `window.matchMedia('(pointer: coarse)')`）
- 添加 `v-if` 控制（可通过 prop `enabled` 开启/关闭）

**Step 3: Commit**

```bash
git add src/components/vue-bits/animations/SplashCursor.vue
git commit -m "feat(landing): implement SplashCursor animation component"
```

---

### Task 5: 实现 ImageTrail 动画组件

**Files:**
- Modify: `frontend/src/components/vue-bits/animations/ImageTrail.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/animations/image-trail
点击 "Copy Code"。

**Step 2: 粘贴并调整**

- 颜色变量化
- 移动端降级为纯 CSS hover 效果
- 添加 `v-if` 控制

**Step 3: Commit**

```bash
git add src/components/vue-bits/animations/ImageTrail.vue
git commit -m "feat(landing): implement ImageTrail animation component"
```

---

### Task 6: 实现 Hero Section 主体

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 编写 Hero Section 代码**

```vue
<!-- Hero Section -->
<section class="hero-section">
  <!-- 背景层 -->
  <VB-FloatingLines class="hero-bg" />

  <!-- 鼠标粒子（全局开启） -->
  <VB-SplashCursor :enabled="!isMobile" />

  <!-- 主内容 -->
  <div class="hero-content">
    <!-- 品牌标识 -->
    <div class="brand-section">
      <div class="brand-logo">
        <svg class="brand-icon" viewBox="0 0 48 48" fill="none">
          <!-- 现有 SVG 不变 -->
        </svg>
      </div>
      <div class="brand-text">
        <span class="brand-name">VisionSpace</span>
        <span class="brand-tagline">视觉灵感宇宙</span>
      </div>
    </div>

    <!-- 主标题 -->
    <h1 class="hero-title">
      <span class="title-word" v-for="(word, i) in ['发现', '视觉', '灵感']" :key="i"
            :style="{ animationDelay: `${i * 0.15}s` }">
        {{ word }}
      </span>
    </h1>
    <p class="hero-subtitle">在光影之间，寻找共鸣<br/>探索无限视觉灵感</p>

    <!-- Image Trail 标题增强 -->
    <VB-ImageTrail class="hero-image-trail">
      <div class="cta-buttons">
        <button class="cta-primary" @click="router.push('/explore')">
          进入探索
        </button>
        <button class="cta-secondary" @click="router.push('/agent')">
          唤醒 SpaceMind
        </button>
      </div>
    </VB-ImageTrail>
  </div>
</section>
```

**Step 2: 添加 Hero Section 样式**

在 `<style scoped lang="less">` 中添加：

```less
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 60px 24px;
}

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
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  // ... 其他渐变
}

.cta-buttons {
  display: flex;
  gap: 24px;
  justify-content: center;
  flex-wrap: wrap;
}

.cta-primary {
  padding: 16px 40px;
  background: linear-gradient(135deg, var(--color-accent-pink) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 40px;
  color: white;
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-3px) scale(1.03);
    box-shadow: 0 8px 35px rgba(244, 114, 182, 0.55);
  }
}

.cta-secondary {
  padding: 16px 40px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 40px;
  color: var(--color-text-primary);
  font-weight: 600;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--color-primary);
    box-shadow: 0 0 30px rgba(168, 85, 247, 0.3);
  }
}

.hero-image-trail {
  display: inline-block;
}
```

**Step 3: 添加响应式**

```less
@media (max-width: 768px) {
  .hero-title {
    font-size: clamp(40px, 10vw, 80px);
  }
  .cta-buttons {
    flex-direction: column;
    gap: 16px;
  }
  .cta-primary, .cta-secondary {
    width: 100%;
    max-width: 280px;
  }
}
```

**Step 4: 验证**

启动开发服务器 `npm run dev`，手动检查 Hero Section。

**Step 5: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement Hero Section with FloatingLines, SplashCursor, ImageTrail"
```

---

## 阶段 3：Brand Essence 板块

### Task 7: 实现 OrbitImages 组件

**Files:**
- Modify: `frontend/src/components/vue-bits/OrbitImages.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/animations/orbit-images
点击 "Copy Code"。

**Step 2: 粘贴并调整**

- 颜色变量化
- 图片数量改为 3-5 张（可通过 prop `images` 传入）
- 动画速度可通过 CSS Variable `--orbit-speed` 控制

**Step 3: Commit**

```bash
git add src/components/vue-bits/OrbitImages.vue
git commit -m "feat(landing): implement OrbitImages component"
```

---

### Task 8: 实现 Brand Essence Section

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 添加 Brand Essence Section**

在 Hero Section 下方添加：

```vue
<!-- Brand Essence Section -->
<section class="brand-essence-section">
  <div class="brand-essence-inner">
    <!-- 左侧文案 -->
    <div class="brand-story">
      <h2 class="section-title">VisionSpace<br/>不只是图片空间</h2>
      <p class="brand-desc">
        在这里，AI 不是工具，而是你的视觉伙伴。
        无论是寻找完美配色，还是探索灵感边界，
        SpaceMind 都能读懂你的想法，帮你发现从未想象过的视觉可能。
      </p>
      <p class="brand-desc">
        与全球创作者同行，在无限协作中沉淀你的视觉灵感宇宙。
      </p>
    </div>
    <!-- 右侧 Orbit Images -->
    <div class="brand-visual">
      <VB-OrbitImages :images="orbitImages" />
    </div>
  </div>
</section>
```

**Step 2: 添加样式**

```less
.brand-essence-section {
  padding: 120px 24px;
  background: var(--gradient-deep-space);
}

.brand-essence-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 80px;
  align-items: center;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 48px;
  }
}

.section-title {
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 48px);
  font-weight: 800;
  line-height: 1.2;
  margin: 0 0 24px 0;
  background: linear-gradient(135deg, var(--color-text-primary) 0%, var(--color-secondary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-desc {
  font-size: 16px;
  color: var(--color-text-secondary);
  line-height: 1.8;
  margin: 0 0 16px 0;
}

.brand-visual {
  display: flex;
  justify-content: center;
  align-items: center;
}
```

**Step 3: 添加 orbitImages 数据**

```ts
const orbitImages = ref([
  { url: 'https://picsum.photos/400/400?random=1', alt: '示例图片1' },
  { url: 'https://picsum.photos/400/400?random=2', alt: '示例图片2' },
  { url: 'https://picsum.photos/400/400?random=3', alt: '示例图片3' },
])
```

**Step 4: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement Brand Essence section with OrbitImages"
```

---

## 阶段 4：Signature Experiences 板块

### Task 9: 实现 CardSwap 组件

**Files:**
- Modify: `frontend/src/components/vue-bits/components/CardSwap.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/components/card-swap
点击 "Copy Code"。

**Step 2: 粘贴并调整**

- 颜色变量化
- 添加 TypeScript 类型声明
- 确保 CSS 适配深空黑主题

**Step 3: Commit**

```bash
git add src/components/vue-bits/components/CardSwap.vue
git commit -m "feat(landing): implement CardSwap component"
```

---

### Task 10: 实现 Signature Experiences Section

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 添加 Signature Experiences Section**

```vue
<!-- Signature Experiences Section -->
<section class="signature-section">
  <h2 class="section-title text-center">在这里，你能体验到前所未有的视觉力量</h2>
  <div class="features-grid">
    <VB-CardSwap v-for="feature in features" :key="feature.title" class="feature-card-wrapper">
      <template #front>
        <div class="feature-card">
          <div class="feature-icon">
            <component :is="feature.icon" />
          </div>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.shortDesc }}</p>
        </div>
      </template>
      <template #back>
        <div class="feature-card-back">
          <p>{{ feature.longDesc }}</p>
        </div>
      </template>
    </VB-CardSwap>
  </div>
</section>
```

**Step 2: 添加 features 数据**

```ts
const features = [
  {
    title: '智能色彩搜索',
    shortDesc: '用颜色找到完美图片',
    longDesc: '不再局限于关键词。描述你想要的氛围或选择颜色，AI 帮你找到最匹配的图片。',
    icon: () => h('svg', { width: 32, height: 32, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('circle', { cx: 13.5, cy: 6.5, r: 2.5 }),
      h('circle', { cx: 17.5, cy: 10.5, r: 2.5 }),
      h('circle', { cx: 8.5, cy: 7.5, r: 2.5 }),
      h('circle', { cx: 6.5, cy: 12.5, r: 2.5 }),
    ])
  },
  {
    title: 'AI Agent',
    shortDesc: 'SpaceMind 读懂你的想法',
    longDesc: '用自然语言描述你的需求，SpaceMind 会在秒级时间内为你找到最合适的图片资源。',
    icon: () => h('svg', { width: 32, height: 32, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('circle', { cx: 12, cy: 12, r: 10 }),
      h('path', { d: 'M12 6v6l4 2' })
    ])
  },
  {
    title: '空间实时协作',
    shortDesc: '与团队共享视觉灵感',
    longDesc: '创建私有画布，邀请团队成员，共同管理和整理视觉资源，实时同步更新。',
    icon: () => h('svg', { width: 32, height: 32, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }),
      h('circle', { cx: 9, cy: 7, r: 4 }),
      h('path', { d: 'M23 21v-2a4 4 0 0 0-3-3.87' }),
      h('path', { d: 'M16 3.13a4 4 0 0 1 0 7.75' })
    ])
  },
  {
    title: 'AI 扩图',
    shortDesc: '无限延展你的创作边界',
    longDesc: '上传任意图片，AI 帮你智能扩展画面边界，完美衔接原图风格与内容。',
    icon: () => h('svg', { width: 32, height: 32, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [
      h('rect', { x: 3, y: 3, width: 18, height: 18, rx: 2 }),
      h('path', { d: 'M12 8v8M8 12h8' })
    ])
  },
]
```

**Step 3: 添加样式**

```less
.signature-section {
  padding: 120px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.text-center {
  text-align: center;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-top: 60px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.feature-card-wrapper {
  height: 280px;
}

.feature-card, .feature-card-back {
  height: 100%;
  padding: 32px 24px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
}

.feature-icon {
  width: 64px;
  height: 64px;
  background: var(--gradient-aurora);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 20px;
}

.feature-card h3 {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--color-text-primary);
}

.feature-card p {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}
```

**Step 4: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement Signature Experiences section with CardSwap"
```

---

## 阶段 5：Visual Universe 板块

### Task 11: 实现 CircularGallery 组件

**Files:**
- Modify: `frontend/src/components/vue-bits/components/CircularGallery.vue`

**Step 1: 从 Vue Bits 官网获取代码**

访问 https://vue-bits.dev/components/circular-gallery
点击 "Copy Code"。

**Step 2: 粘贴并调整**

- 颜色变量化
- 添加 `images` prop（Array）接受图片列表
- 移动端降级逻辑：当 `window.innerWidth < 768` 时显示静态网格而非旋转
- 自动旋转速度可通过 CSS Variable `--gallery-rotate-duration` 控制

**Step 3: Commit**

```bash
git add src/components/vue-bits/components/CircularGallery.vue
git commit -m "feat(landing): implement CircularGallery component"
```

---

### Task 12: 实现 Visual Universe Section

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 添加 Visual Universe Section**

```vue
<!-- Visual Universe Section -->
<section class="visual-universe-section">
  <h2 class="section-title text-center">来自全球创作者的灵感时刻</h2>
  <div class="gallery-container">
    <VB-CircularGallery :images="galleryImages" />
  </div>
</section>
```

**Step 2: 添加 galleryImages 数据**

```ts
// 从后端 API 获取热门图片
const galleryImages = ref<{ url: string; id: number }[]>([])

onMounted(async () => {
  try {
    // TODO: 替换为真实 API
    const res = await fetch('/api/picture/list?pageSize=12&sortField=viewCount&sortOrder=desc')
    const data = await res.json()
    galleryImages.value = data.data.records.map((p: any) => ({ url: p.url, id: p.id }))
  } catch {
    // Fallback to placeholder images
    galleryImages.value = Array.from({ length: 12 }, (_, i) => ({
      url: `https://picsum.photos/400/400?random=${i + 100}`,
      id: i
    }))
  }
})
```

**Step 3: 添加样式**

```less
.visual-universe-section {
  padding: 120px 24px;
  background: var(--gradient-deep-space);
}

.gallery-container {
  max-width: 1200px;
  margin: 60px auto 0;
  display: flex;
  justify-content: center;
  min-height: 500px;
}
```

**Step 4: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement Visual Universe section with CircularGallery"
```

---

## 阶段 6：AI Magic 板块

### Task 13: 实现 AI Magic Section

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 添加 AI Magic Section**

```vue
<!-- AI Magic Section -->
<section class="ai-magic-section">
  <div class="ai-magic-inner">
    <!-- 左侧：AI Agent 对话示例 -->
    <div class="ai-chat-preview">
      <h2 class="section-title">让 AI 成为你的视觉伙伴</h2>
      <div class="chat-example">
        <div class="chat-message user">帮我找一组清新自然风格的风景图</div>
        <div class="chat-message assistant">
          <div class="chat-images">
            <img v-for="img in chatExampleImages" :key="img.id" :src="img.url" :alt="img.name" />
          </div>
          <p>为你找到了 4 张符合清新自然风格的风景图，点击查看详情~</p>
        </div>
      </div>
    </div>
    <!-- 右侧：扩图前后对比 -->
    <div class="outpainting-preview">
      <VB-ImageTrail class="outpainting-trail">
        <div class="outpainting-comparison">
          <div class="comparison-label">AI 扩图</div>
          <div class="comparison-images">
            <div class="img-original">
              <img src="https://picsum.photos/300/200?random=50" alt="原图" />
              <span class="img-label">原图</span>
            </div>
            <div class="comparison-arrow">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </div>
            <div class="img-expanded">
              <img src="https://picsum.photos/600/400?random=51" alt="扩图后" />
              <span class="img-label">AI 扩图后</span>
            </div>
          </div>
        </div>
      </VB-ImageTrail>
    </div>
  </div>
</section>
```

**Step 2: 添加样式**

```less
.ai-magic-section {
  padding: 120px 24px;
  background: linear-gradient(to bottom, var(--color-bg-primary) 0%, var(--color-bg-secondary) 100%);
}

.ai-magic-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 80px;
  align-items: center;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 48px;
  }
}

.chat-example {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message {
  padding: 16px 20px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  max-width: 85%;
}

.chat-message.user {
  background: var(--gradient-aurora);
  color: white;
  align-self: flex-end;
}

.chat-message.assistant {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  color: var(--color-text-primary);
  align-self: flex-start;
}

.chat-images {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.chat-images img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 8px;
}

.outpainting-comparison {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  padding: 32px;
}

.comparison-label {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 24px;
}

.comparison-images {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.img-original, .img-expanded {
  flex: 1;
  min-width: 200px;
  text-align: center;
}

.img-original img, .img-expanded img {
  width: 100%;
  max-width: 300px;
  border-radius: 12px;
  object-fit: cover;
}

.img-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 8px;
}

.comparison-arrow {
  color: var(--color-primary);
  flex-shrink: 0;
}
```

**Step 3: 添加 chatExampleImages 数据**

```ts
const chatExampleImages = ref([
  { url: 'https://picsum.photos/200/200?random=60', id: 60, name: '示例1' },
  { url: 'https://picsum.photos/200/200?random=61', id: 61, name: '示例2' },
  { url: 'https://picsum.photos/200/200?random=62', id: 62, name: '示例3' },
  { url: 'https://picsum.photos/200/200?random=63', id: 63, name: '示例4' },
])
```

**Step 4: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement AI Magic section with chat preview and outpainting comparison"
```

---

## 阶段 7：Final CTA 板块

### Task 14: 实现 Final CTA Section

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 添加 Final CTA Section**

在页面最后添加：

```vue
<!-- Final CTA Section -->
<section class="final-cta-section">
  <div class="cta-bg-gradient"></div>
  <div class="cta-content">
    <h2 class="cta-title">准备好开启你的视觉之旅了吗？</h2>
    <div class="cta-buttons">
      <button class="cta-primary-large" @click="router.push('/explore')">
        立即进入探索
      </button>
      <button class="cta-secondary-large" @click="router.push('/agent')">
        免费体验 SpaceMind
      </button>
    </div>
  </div>
</section>
```

**Step 2: 添加样式**

```less
.final-cta-section {
  position: relative;
  padding: 160px 24px;
  overflow: hidden;
  text-align: center;
}

.cta-bg-gradient {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 30% 50%, rgba(168, 85, 247, 0.15) 0%, transparent 50%),
    radial-gradient(ellipse at 70% 50%, rgba(236, 72, 153, 0.15) 0%, transparent 50%),
    var(--color-bg-primary);
  z-index: 0;
}

.cta-content {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
}

.cta-title {
  font-family: var(--font-display);
  font-size: clamp(28px, 5vw, 48px);
  font-weight: 800;
  line-height: 1.2;
  margin: 0 0 48px 0;
  background: linear-gradient(135deg, var(--color-text-primary) 0%, var(--color-secondary) 50%, var(--color-accent-pink) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.cta-buttons {
  display: flex;
  gap: 24px;
  justify-content: center;
  flex-wrap: wrap;
}

.cta-primary-large {
  padding: 20px 48px;
  background: linear-gradient(135deg, var(--color-accent-pink) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 50px;
  color: white;
  font-weight: 700;
  font-size: 18px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-4px) scale(1.03);
    box-shadow: 0 12px 40px rgba(244, 114, 182, 0.5);
  }
}

.cta-secondary-large {
  padding: 20px 48px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border: 2px solid var(--color-secondary);
  border-radius: 50px;
  color: var(--color-text-primary);
  font-weight: 700;
  font-size: 18px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(168, 85, 247, 0.1);
    border-color: var(--color-accent-pink);
    box-shadow: 0 0 40px rgba(168, 85, 247, 0.3);
  }
}
```

**Step 3: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): implement Final CTA section"
```

---

## 阶段 8：LandingPage 组装 + 双主题适配

### Task 15: 清理并组装 LandingPage

**Files:**
- Modify: `frontend/src/pages/LandingPage.vue`

**Step 1: 整合所有板块**

确保 LandingPage.vue 包含所有 6 个板块：

1. Hero Section（已完成）
2. Brand Essence Section（已完成）
3. Signature Experiences Section（已完成）
4. Visual Universe Section（已完成）
5. AI Magic Section（已完成）
6. Final CTA Section（已完成）

**Step 2: 清理旧代码**

删除原有的：
- `features-section` 旧样式
- 不再使用的变量和导入

**Step 3: 添加整体布局容器**

```vue
<template>
  <div id="landingPage">
    <!-- 氛围背景层 -->
    <div class="ambient-bg">
      <div class="starfield"></div>
      <div class="starfield starfield-2"></div>
    </div>

    <!-- 主内容 -->
    <main class="main-content">
      <!-- Hero Section -->
      ...
      <!-- Brand Essence Section -->
      ...
      <!-- Signature Experiences Section -->
      ...
      <!-- Visual Universe Section -->
      ...
      <!-- AI Magic Section -->
      ...
      <!-- Final CTA Section -->
      ...
    </main>

    <!-- 底部渐变 -->
    <div class="bottom-fade"></div>
  </div>
</template>
```

**Step 4: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): assemble all sections into complete LandingPage"
```

---

### Task 16: 双主题适配验证

**Step 1: 切换主题测试**

1. 启动开发服务器
2. 切换到紫漾（Pop Art）主题
3. 检查以下组件是否正确适配：
   - Floating Lines 颜色变化
   - Circular Gallery 旋转速度
   - 整体渐变色彩

**Step 2: 移动端适配验证**

使用 Chrome DevTools 移动端模式测试：
1. Hero Section 响应式布局
2. Features Grid 单列显示
3. Circular Gallery 降级为静态网格

**Step 3: Commit**

```bash
git add src/pages/LandingPage.vue
git commit -m "feat(landing): verify dual-theme adaptation and mobile responsiveness"
```

---

## 实施完成检查清单

- [ ] 所有 5 个 Vue Bits 组件已集成并可运行
- [ ] Hero Section 正确显示 Floating Lines + Splash Cursor + Image Trail
- [ ] Brand Essence Section 显示 Orbit Images
- [ ] Signature Experiences 显示 4 个 Card Swap 卡片
- [ ] Visual Universe 显示 Circular Gallery 旋转画廊
- [ ] AI Magic Section 显示对话示例和扩图对比
- [ ] Final CTA Section 正常显示
- [ ] 双主题切换正常
- [ ] 移动端响应式正常
- [ ] `npm run type-check` 通过
- [ ] `npm run build` 通过

---

## 文件变更总览

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/components/vue-bits/animations/SplashCursor.vue` | Create | 鼠标粒子溅射 |
| `frontend/src/components/vue-bits/animations/ImageTrail.vue` | Create | 鼠标轨迹 |
| `frontend/src/components/vue-bits/animations/FloatingLines.vue` | Create | 背景线条 |
| `frontend/src/components/vue-bits/components/CardSwap.vue` | Create | 卡片翻转 |
| `frontend/src/components/vue-bits/components/CircularGallery.vue` | Create | 圆形画廊 |
| `frontend/src/components/vue-bits/OrbitImages.vue` | Create | 环绕图片 |
| `frontend/src/pages/LandingPage.vue` | Modify | 重写所有板块 |
| `frontend/package.json` | Modify | 添加 motion-v 依赖 |
