<template>
  <div id="landingPage" ref="pageRef" class="signature-page landing-page">
    <!-- 氛围层：静态光晕 + 极细网格，不做无限循环动画 -->
    <div class="ambient" aria-hidden="true">
      <div class="ambient-glow ambient-glow-a"></div>
      <div class="ambient-glow ambient-glow-b"></div>
      <div class="ambient-grid"></div>
    </div>

    <!-- ================= Hero ================= -->
    <section id="hero" class="hero" :class="{ 'is-visible': visibleSections.hero }">
      <div class="hero-media" aria-hidden="true">
        <img v-if="reduceMotion" :src="heroBackdrop" alt="" class="hero-still" />
        <GridDistortion
          v-else
          :image-src="heroBackdrop"
          :grid="10"
          :mouse="0.12"
          :strength="0.22"
          :relaxation="0.9"
        />
      </div>
      <div class="hero-scrim" aria-hidden="true"></div>

      <div class="shell hero-inner">
        <div class="hero-copy">
          <div class="wordmark reveal">
            <img src="@/assets/logo.svg" alt="" class="wordmark-mark" />
            <span class="wordmark-name">VisionSpace</span>
            <span class="wordmark-rule" aria-hidden="true"></span>
            <span class="wordmark-tag">视觉灵感空间</span>
          </div>

          <h1 class="hero-title reveal">
            <span class="line">在光影之间</span>
            <span class="line accent">发现视觉灵感</span>
          </h1>

          <p class="hero-lede reveal">用颜色、语言或一句话，找到你正在寻找的那幅画面。</p>

          <div class="hero-actions reveal">
            <button class="btn btn-primary" @click="router.push('/explore')">
              <Search :size="18" :stroke-width="2" />
              进入探索
            </button>
            <button class="btn btn-ghost" @click="router.push('/agent')">
              <Sparkles :size="18" :stroke-width="2" />
              对话 SpaceMind
            </button>
          </div>
        </div>

        <div class="hero-collage reveal" aria-hidden="true">
          <figure class="collage-item collage-a">
            <img :src="heroImages[0]" alt="" loading="eager" decoding="async" />
          </figure>
          <figure class="collage-item collage-b">
            <img :src="heroImages[1]" alt="" loading="lazy" decoding="async" />
          </figure>
          <figure class="collage-item collage-c">
            <img :src="heroImages[2]" alt="" loading="lazy" decoding="async" />
          </figure>
        </div>
      </div>
    </section>

    <!-- ================= 品牌理念 ================= -->
    <section id="essence" class="section essence" :class="{ 'is-visible': visibleSections.essence }">
      <div class="shell essence-grid">
        <div class="essence-copy">
          <h2 class="display reveal">
            AI 与人类创造力的<br />
            <span class="accent">交汇点</span>
          </h2>

          <p class="lede reveal">
            VisionSpace 把灵感收集、团队协作与 AI 创作放进同一个空间，让想法从一闪而过变成可以继续使用的素材。
          </p>

          <blockquote class="pull-quote reveal">
            “每一张图片都是一段故事，每一次搜索都是一次发现。”
          </blockquote>

          <p class="body reveal">
            你不用先学会关键词，也不必先整理文件夹。描述氛围、挑一个颜色，剩下的交给 SpaceMind。
          </p>
        </div>

        <div class="essence-visual reveal">
          <RollingGallery
            :autoplay="!reduceMotion"
            :pause-on-hover="true"
            :images="orbitImages"
            :height="560"
          />
          <p class="figure-caption">社区创作者的视觉片段，循环滚动。</p>
        </div>
      </div>
    </section>

    <!-- ================= 核心体验 ================= -->
    <section
      id="experiences"
      class="section experiences"
      :class="{ 'is-visible': visibleSections.experiences }"
    >
      <div class="shell experiences-grid">
        <div class="experiences-copy">
          <h2 class="display reveal">
            三种方式，<br />
            找到同一束光
          </h2>
          <p class="body reveal">
            从颜色到语言，再到一起整理的空间。入口不同，指向的是同一件事：把你脑子里的画面找出来。
          </p>

          <ul class="principle-list">
            <li v-for="feature in features" :key="feature.title" class="reveal">
              <component :is="feature.icon" :size="18" :stroke-width="1.8" class="principle-icon" />
              <div>
                <h3 class="principle-title">{{ feature.title }}</h3>
                <p class="principle-desc">{{ feature.desc }}</p>
              </div>
            </li>
          </ul>
        </div>

        <div class="experiences-visual reveal">
          <CardSwap
            :width="cardSize.width"
            :height="cardSize.height"
            :card-distance="64"
            :vertical-distance="56"
            :delay="reduceMotion ? 2147483000 : 4200"
            :skew-amount="4"
            easing="elastic"
            :pause-on-hover="true"
          >
            <template #card-0>
              <article class="swap-card">
                <component :is="features[0].icon" :size="24" :stroke-width="1.6" class="swap-icon" />
                <h3 class="swap-title">{{ features[0].title }}</h3>
                <p class="swap-desc">{{ features[0].longDesc }}</p>
              </article>
            </template>
            <template #card-1>
              <article class="swap-card">
                <component :is="features[1].icon" :size="24" :stroke-width="1.6" class="swap-icon" />
                <h3 class="swap-title">{{ features[1].title }}</h3>
                <p class="swap-desc">{{ features[1].longDesc }}</p>
              </article>
            </template>
            <template #card-2>
              <article class="swap-card">
                <component :is="features[2].icon" :size="24" :stroke-width="1.6" class="swap-icon" />
                <h3 class="swap-title">{{ features[2].title }}</h3>
                <p class="swap-desc">{{ features[2].longDesc }}</p>
              </article>
            </template>
          </CardSwap>
        </div>
      </div>
    </section>

    <!-- ================= 视觉宇宙 ================= -->
    <section id="universe" class="section universe" :class="{ 'is-visible': visibleSections.universe }">
      <div class="shell universe-head">
        <h2 class="display reveal">
          灵感值得<br />
          被看见
        </h2>
        <p class="body reveal universe-note">
          拖动穹顶，从任何角度浏览一组视觉切片。示例图库只用于展示浏览方式。
        </p>
      </div>

      <div class="universe-stage reveal">
        <DomeGallery1
          :images="universeImages"
          :fit="0.82"
          fit-basis="auto"
          :min-radius="200"
          :max-radius="Infinity"
          :segments="34"
          :drag-sensitivity="20"
          :enlarge-transition-ms="300"
          :grayscale="true"
          :auto-rotate="!reduceMotion"
          overlay-blur-color="var(--color-bg-primary)"
          image-border-radius="18px"
          opened-image-width="250px"
          opened-image-height="350px"
        />
      </div>
    </section>

    <!-- ================= AI 魔力 ================= -->
    <section id="ai-magic" class="section magic" :class="{ 'is-visible': visibleSections['ai-magic'] }">
      <div class="shell magic-grid">
        <div class="magic-copy">
          <h2 class="display reveal">
            一句话，<br />
            得到画面
          </h2>
          <p class="body reveal">
            描述氛围、颜色或情绪，SpaceMind 会把它翻译成检索条件，再给你一组可以直接使用的候选图。
          </p>

          <ul class="principle-list">
            <li v-for="item in magicPoints" :key="item.title" class="reveal">
              <component :is="item.icon" :size="18" :stroke-width="1.8" class="principle-icon" />
              <div>
                <h3 class="principle-title">{{ item.title }}</h3>
                <p class="principle-desc">{{ item.desc }}</p>
              </div>
            </li>
          </ul>
        </div>

        <figure class="magic-visual reveal">
          <div
            class="compare"
            role="group"
            :style="{ '--pos': `${comparePos}%` }"
            :aria-label="`扩图对比，当前显示 ${comparePos}%`"
          >
            <img :src="compareImages.after" alt="AI 扩图后的画面" loading="lazy" decoding="async" />
            <div class="compare-before">
              <img :src="compareImages.before" alt="扩图前的原图" loading="lazy" decoding="async" />
            </div>
            <span class="compare-rule" aria-hidden="true"></span>
            <span class="compare-handle" aria-hidden="true">
              <Move :size="16" :stroke-width="2" />
            </span>
            <input
              v-model.number="comparePos"
              type="range"
              min="0"
              max="100"
              step="0.5"
              class="compare-range"
              aria-label="拖动对比原图与扩图结果"
            />
          </div>
          <figcaption class="compare-caption">
            <span>左侧 原图</span>
            <span class="compare-caption-sep" aria-hidden="true"></span>
            <span>右侧 AI 扩图</span>
          </figcaption>
        </figure>
      </div>
    </section>

    <!-- ================= 结尾 CTA ================= -->
    <section id="cta" class="section final" :class="{ 'is-visible': visibleSections.cta }">
      <div class="final-glow" aria-hidden="true"></div>
      <div class="shell final-inner">
        <h2 class="display final-title reveal">准备好开始了吗</h2>
        <p class="lede reveal">先随手存一张图，剩下的交给灵感。</p>
        <div class="hero-actions center reveal">
          <button class="btn btn-primary" @click="router.push('/explore')">
            <Search :size="18" :stroke-width="2" />
            进入探索
          </button>
          <button class="btn btn-ghost" @click="router.push('/agent')">
            <Sparkles :size="18" :stroke-width="2" />
            对话 SpaceMind
          </button>
        </div>
      </div>
    </section>

    <GlobalFooter />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Maximize2, MessageSquare, Move, Palette, Search, Sparkles, Users } from 'lucide-vue-next'
import GridDistortion from '@/components/vue-bits/animations/GridDistortion.vue'
import CardSwap from '@/components/vue-bits/components/CardSwap.vue'
import RollingGallery from '@/components/vue-bits/components/RollingGallery.vue'
import DomeGallery1 from '@/components/vue-bits/components/DomeGallery.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const router = useRouter()
const pageRef = ref<HTMLElement>()

const visibleSections = ref<Record<string, boolean>>({})
let sectionObserver: IntersectionObserver | null = null

/* JS 驱动的动效（画布、轮播、穹顶旋转）不受全局 CSS 规则约束，单独降级 */
const reduceMotion = ref(false)
let motionQuery: MediaQueryList | null = null
const syncMotionPreference = () => {
  reduceMotion.value = motionQuery?.matches ?? false
}

/* CardSwap 以像素定位卡片，这里按视口给出不溢出的尺寸 */
const cardSize = reactive({ width: 520, height: 460 })

const syncCardSize = () => {
  const w = window.innerWidth
  if (w <= 480) {
    cardSize.width = 300
    cardSize.height = 300
  } else if (w <= 768) {
    cardSize.width = 360
    cardSize.height = 340
  } else if (w <= 1024) {
    cardSize.width = 440
    cardSize.height = 400
  } else {
    cardSize.width = 520
    cardSize.height = 460
  }
}

onMounted(() => {
  syncCardSize()
  window.addEventListener('resize', syncCardSize)

  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncMotionPreference()
  motionQuery.addEventListener('change', syncMotionPreference)

  sectionObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) visibleSections.value[entry.target.id] = true
      })
    },
    { threshold: 0.12 },
  )
  pageRef.value?.querySelectorAll('section').forEach((section) => sectionObserver!.observe(section))
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncCardSize)
  motionQuery?.removeEventListener('change', syncMotionPreference)
  sectionObserver?.disconnect()
})

/* 示例图库：全部来自 Unsplash，页面中已标注为示例内容 */
const heroBackdrop =
  'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?q=80&w=1920&auto=format&fit=crop'

const heroImages = [
  'https://images.unsplash.com/photo-1528181304800-259b08848526?q=80&w=900&h=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1506665531195-3566af2b4dfa?q=80&w=800&h=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?q=80&w=800&h=650&auto=format&fit=crop',
]

const orbitImages = [
  'https://images.unsplash.com/photo-1528181304800-259b08848526?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1506665531195-3566af2b4dfa?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1495103033382-fe343886b671?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1506781961370-37a89d6b3095?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1599576838688-8a6c11263108?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1494094892896-7f14a4433b7a?q=80&w=1200&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1503788311183-fa3bf9c4bc32?q=80&w=1200&auto=format&fit=crop',
]

const universeImages = [
  'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1519681393784-d120267933ba?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1469474968028-56623f02e42e?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1426604966848-d7adac402bff?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1472214103451-9374bd1c798e?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1501785888041-af3ef285b470?q=80&w=800&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1465056836041-7f43ac27dcb5?q=80&w=800&auto=format&fit=crop',
]

/* 同一张照片的 4:3 裁切与 16:10 宽幅裁切，用于演示扩图 */
const compareImages = {
  before:
    'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=800&h=800&auto=format&fit=crop',
  after:
    'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=1400&h=900&auto=format&fit=crop',
}

const comparePos = ref(52)

const features = [
  {
    title: '颜色搜索',
    desc: '选一个颜色，找到同一种情绪的画面。',
    longDesc: '不必先想清楚关键词。从色板里挑一种颜色，就能沿着同一套色彩关系继续找下去。',
    icon: Palette,
  },
  {
    title: 'AI Agent',
    desc: '用一句话描述画面，SpaceMind 负责翻译。',
    longDesc: '描述氛围、光线或构图，SpaceMind 会拆解成可检索的条件，并给出可以直接使用的候选。',
    icon: MessageSquare,
  },
  {
    title: '空间协作',
    desc: '把灵感放进共享空间，一起整理。',
    longDesc: '创建私有或团队空间，邀请成员共同维护素材与标签，改动实时同步。',
    icon: Users,
  },
]

const magicPoints = [
  {
    title: '自然语言检索',
    desc: '“黄昏、暖色、有建筑剪影”，一句话就能成为检索条件。',
    icon: MessageSquare,
  },
  {
    title: '风格延续',
    desc: '扩图会沿用原图的光线、色调与笔触，而不是另起一张。',
    icon: Maximize2,
  },
  {
    title: '结果可继续编辑',
    desc: '候选图可以直接收藏、加入空间，或作为下一轮对话的输入。',
    icon: Sparkles,
  },
]
</script>

<style scoped lang="less">
/* ============================================================
   Signature system
   - 圆角规则：卡片/媒体 20px，输入类控件 12px，按钮胶囊 999px
   - 单一强调色：--color-primary（极光蓝 / 紫漾紫），pink 只做渐变伙伴
   - 动效只在有信息意义时出现：入场层级、悬停反馈、交互结果
   ============================================================ */

#landingPage {
  position: relative;
  overflow-x: hidden;
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-family: var(--font-body);
}

/* ---------- 氛围层 ---------- */
.ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.ambient-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(140px);
  opacity: 0.55;
}

.ambient-glow-a {
  width: 70vw;
  height: 70vw;
  top: -34vw;
  right: -22vw;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--color-primary) 30%, transparent) 0%,
    transparent 66%
  );
}

.ambient-glow-b {
  width: 60vw;
  height: 60vw;
  bottom: -30vw;
  left: -24vw;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--color-secondary) 20%, transparent) 0%,
    transparent 68%
  );
}

.ambient-grid {
  position: absolute;
  inset: 0;
  background-image: var(--pattern-grid);
  background-size: 72px 72px;
  opacity: 0.5;
  -webkit-mask-image: radial-gradient(ellipse at 50% 0%, #000 0%, transparent 68%);
  mask-image: radial-gradient(ellipse at 50% 0%, #000 0%, transparent 68%);
}

/* ---------- 版式基础 ---------- */
.section {
  position: relative;
  z-index: 1;
  padding: clamp(84px, 9vw, 144px) 0;
}

.shell {
  width: 100%;
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: 0 clamp(20px, 4vw, 32px);
}

.display {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(2rem, 4.4vw, 3.5rem);
  line-height: 1.1;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  text-wrap: balance;
}

.display .accent,
.accent {
  color: var(--color-primary);
}

.lede {
  margin: 1.25rem 0 0;
  max-width: 34em;
  font-size: clamp(1rem, 1.35vw, 1.125rem);
  line-height: 1.75;
  color: var(--color-text-secondary);
}

.body {
  margin: 1.25rem 0 0;
  max-width: 38em;
  font-size: 0.95rem;
  line-height: 1.85;
  color: var(--color-text-secondary);
}

.figure-caption {
  margin: 1rem 0 0;
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}

/* ---------- 入场（层级提示，不是装饰） ---------- */
.reveal {
  opacity: 0;
  transform: translateY(22px);
  transition:
    opacity 0.7s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}

section.is-visible .reveal {
  opacity: 1;
  transform: none;
}

section.is-visible .reveal:nth-child(2) {
  transition-delay: 0.07s;
}
section.is-visible .reveal:nth-child(3) {
  transition-delay: 0.14s;
}
section.is-visible .reveal:nth-child(4) {
  transition-delay: 0.21s;
}
section.is-visible .reveal:nth-child(5) {
  transition-delay: 0.28s;
}

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  padding: 0.9rem 1.6rem;
  border-radius: 999px;
  border: 1px solid transparent;
  font-family: var(--font-body);
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  cursor: pointer;
  white-space: nowrap;
  transition:
    transform 0.25s var(--ease-spring, cubic-bezier(0.34, 1.56, 0.64, 1)),
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.25s ease,
    color 0.2s ease;
}

.btn:active {
  transform: translateY(1px) scale(0.98);
}

.btn-primary {
  background: var(--color-primary-600);
  color: #ffffff;
  box-shadow: 0 10px 30px color-mix(in srgb, var(--color-primary) 26%, transparent);

  &:hover {
    background: var(--color-primary-700);
    transform: translateY(-2px);
  }
}

.btn-ghost {
  background: transparent;
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);

  &:hover {
    border-color: var(--color-primary);
    color: var(--color-primary);
    transform: translateY(-2px);
  }
}

/* ============================================================
   Hero
   ============================================================ */
.hero {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  min-height: calc(100dvh - 72px);
  padding: clamp(72px, 10vh, 128px) 0 clamp(56px, 8vh, 96px);
  overflow: hidden;
}

.hero-media {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  z-index: 0;

  :deep(canvas) {
    width: 100% !important;
    height: 100% !important;
    display: block;
  }
}

.hero-still {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

[data-theme='pop'] .hero-media {
  opacity: 0.28;
}

.hero-scrim {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    linear-gradient(
      90deg,
      var(--color-bg-primary) 6%,
      color-mix(in srgb, var(--color-bg-primary) 78%, transparent) 40%,
      transparent 78%
    ),
    linear-gradient(0deg, var(--color-bg-primary) 0%, transparent 42%);
}

[data-theme='pop'] .hero-scrim {
  background:
    linear-gradient(
      90deg,
      var(--color-bg-primary) 4%,
      color-mix(in srgb, var(--color-bg-primary) 86%, transparent) 38%,
      transparent 74%
    ),
    linear-gradient(0deg, var(--color-bg-primary) 0%, transparent 46%);
}

.hero-inner {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr);
  align-items: center;
  gap: clamp(32px, 5vw, 72px);
}

.hero-copy {
  max-width: 34rem;
}

.wordmark {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: clamp(24px, 4vh, 40px);
}

.wordmark-mark {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.wordmark-name {
  font-family: var(--font-display);
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-primary);
}

.wordmark-rule {
  width: 1px;
  height: 14px;
  background: var(--color-border-strong);
}

.wordmark-tag {
  font-size: 0.78rem;
  letter-spacing: 0.18em;
  color: var(--color-text-tertiary);
}

.hero-title {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(2.6rem, 6.4vw, 5rem);
  line-height: 1.04;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.hero-title .line {
  display: block;
}

.hero-title .accent {
  color: var(--color-primary);
}

.hero-lede {
  margin: clamp(20px, 3vh, 28px) 0 0;
  max-width: 28em;
  font-size: clamp(1rem, 1.5vw, 1.2rem);
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: clamp(28px, 4vh, 40px);

  &.center {
    justify-content: center;
  }
}

/* 拼贴：三张真实照片，尺寸刻意不对称 */
.hero-collage {
  position: relative;
  height: clamp(380px, 58vh, 600px);
}

.collage-item {
  position: absolute;
  margin: 0;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-elevated);
  box-shadow: var(--shadow-2xl);
  transition:
    transform 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.5s ease;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    transition: transform 0.7s cubic-bezier(0.22, 1, 0.36, 1);
  }

  &:hover {
    transform: translateY(-6px);
    box-shadow: var(--shadow-card-hover);

    img {
      transform: scale(1.04);
    }
  }
}

.collage-a {
  width: 58%;
  height: 82%;
  top: 0;
  right: 0;
}

.collage-b {
  width: 44%;
  height: 48%;
  bottom: 0;
  left: 0;
}

.collage-c {
  width: 34%;
  height: 36%;
  top: 12%;
  left: 4%;
}

/* ============================================================
   品牌理念
   ============================================================ */
.essence-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  gap: clamp(40px, 6vw, 96px);
}

.pull-quote {
  margin: clamp(28px, 4vw, 40px) 0 0;
  padding-left: 1.25rem;
  border-left: 2px solid var(--color-primary);
  font-family: var(--font-display);
  font-size: clamp(1.15rem, 2vw, 1.5rem);
  line-height: 1.55;
  color: var(--color-text-primary);
}

.essence-visual {
  min-height: 560px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* ============================================================
   核心体验
   ============================================================ */
.experiences-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: center;
  gap: clamp(40px, 6vw, 88px);
}

.principle-list {
  list-style: none;
  margin: clamp(28px, 4vw, 40px) 0 0;
  padding: 0;
}

.principle-list li {
  display: flex;
  gap: 0.9rem;
  padding: 1.05rem 0;
  border-top: 1px solid var(--color-border-subtle);

  &:last-child {
    border-bottom: 1px solid var(--color-border-subtle);
  }
}

.principle-icon {
  flex-shrink: 0;
  margin-top: 3px;
  color: var(--color-primary);
}

.principle-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.principle-desc {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--color-text-tertiary);
}

.experiences-visual {
  display: flex;
  justify-content: center;
}

.swap-card {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: clamp(24px, 3vw, 40px);
  border-radius: 16px;
  border: 1px solid var(--color-border-default);
  background: var(--gradient-card);
  box-shadow: var(--shadow-lg);
}

.swap-icon {
  margin-bottom: auto;
  color: var(--color-primary);
}

.swap-title {
  margin: 0 0 0.6rem;
  font-family: var(--font-display);
  font-size: clamp(1.25rem, 2vw, 1.6rem);
  font-weight: 600;
  color: var(--color-text-primary);
}

.swap-desc {
  margin: 0;
  font-size: 0.925rem;
  line-height: 1.75;
  color: var(--color-text-secondary);
}

/* ============================================================
   视觉宇宙
   ============================================================ */
.universe {
  padding-bottom: clamp(40px, 5vw, 72px);
  border-top: 1px solid var(--color-border-subtle);
}

.universe-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: clamp(24px, 4vw, 64px);
  margin-bottom: clamp(24px, 4vw, 40px);
}

.universe-note {
  margin: 0;
  max-width: 30em;
  text-align: right;
}

.universe-stage {
  position: relative;
  height: clamp(420px, 62vh, 620px);
  width: 100%;
}

/* ============================================================
   AI 魔力
   ============================================================ */
.magic-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  align-items: center;
  gap: clamp(40px, 6vw, 88px);
}

.magic-visual {
  margin: 0;
}

.compare {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-elevated);
  box-shadow: var(--shadow-xl);
  touch-action: pan-y;

  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    user-select: none;
    pointer-events: none;
  }
}

.compare-before {
  position: absolute;
  inset: 0;
  clip-path: inset(0 calc(100% - var(--pos)) 0 0);
}

.compare-rule {
  position: absolute;
  top: 0;
  bottom: 0;
  left: var(--pos);
  width: 2px;
  margin-left: -1px;
  background: #ffffff;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.18);
}

.compare-handle {
  position: absolute;
  top: 50%;
  left: var(--pos);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin: -20px 0 0 -20px;
  border-radius: 50%;
  background: #ffffff;
  color: #111827;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
  pointer-events: none;
}

.compare:focus-within .compare-handle {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}

.compare-range {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: ew-resize;
}

.compare-range:focus-visible {
  outline: none;
}

.compare-caption {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 1rem 0 0;
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}

.compare-caption-sep {
  width: 18px;
  height: 1px;
  background: var(--color-border-strong);
}

/* ============================================================
   结尾 CTA
   ============================================================ */
.final {
  position: relative;
  overflow: hidden;
  border-top: 1px solid var(--color-border-subtle);
  text-align: center;
}

.final-glow {
  position: absolute;
  inset: auto 0 -12% 0;
  height: 60%;
  pointer-events: none;
  background: radial-gradient(
    ellipse at 50% 100%,
    color-mix(in srgb, var(--color-primary) 18%, transparent) 0%,
    transparent 70%
  );
}

.final-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.final-title {
  font-size: clamp(2.2rem, 5vw, 4rem);
}

.final .lede {
  margin-top: 1rem;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1024px) {
  .hero {
    min-height: auto;
    padding-top: clamp(88px, 10vh, 120px);
  }

  .hero-inner,
  .essence-grid,
  .experiences-grid,
  .magic-grid {
    grid-template-columns: 1fr;
  }

  .hero-collage {
    height: clamp(260px, 42vh, 380px);
    max-width: 640px;
    width: 100%;
    margin: 0 auto;
  }

  .essence-visual {
    min-height: 0;
  }

  .experiences-visual {
    order: -1;
  }

  .universe-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .universe-note {
    text-align: left;
  }
}

@media (max-width: 768px) {
  .section {
    padding: 64px 0;
  }

  .universe {
    padding-bottom: 40px;
  }

  .hero-title {
    font-size: clamp(2.25rem, 10vw, 3rem);
  }

  .hero-actions {
    width: 100%;

    .btn {
      flex: 1 1 100%;
    }
  }

  .hero-collage {
    height: 300px;
  }

  .collage-a {
    width: 62%;
    height: 84%;
  }

  .collage-b {
    width: 46%;
    height: 50%;
  }

  .collage-c {
    width: 32%;
    height: 34%;
  }

  .compare {
    aspect-ratio: 1 / 1;
  }

  .universe-stage {
    height: 380px;
  }
}

@media (max-width: 480px) {
  .shell {
    padding: 0 16px;
  }

  .wordmark-tag,
  .wordmark-rule {
    display: none;
  }

  .hero-collage {
    height: 240px;
  }

  .compare-handle {
    width: 34px;
    height: 34px;
    margin: -17px 0 0 -17px;
  }
}
</style>
