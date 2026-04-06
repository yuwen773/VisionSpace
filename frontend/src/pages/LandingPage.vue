<template>
  <div id="landingPage">
    <TargetCursor :spin-duration="2" :hide-default-cursor="true" />

    <div class="ambient-bg">
      <div class="starfield"></div>
      <div class="starfield starfield-2"></div>
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="hero-glow-center"></div>
    </div>

    <div class="main-content">
      <section id="hero" class="hero-section" :class="{ 'is-visible': visibleSections['hero'] }">
        <div class="hero-bg-distortion">
          <GridDistortion
            imageSrc="https://picsum.photos/1920/1080?random=42"
            :grid="12"
            :mouse="0.15"
            :strength="0.28"
            :relaxation="0.85"
          />
        </div>
        <div class="hero-content">
          <div class="brand-section">
            <div class="brand-logo">
              <svg class="brand-icon" viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="20" stroke="url(#brandGradient)" stroke-width="3" />
                <circle cx="24" cy="24" r="12" fill="url(#brandGradient)" opacity="0.3" />
                <circle cx="24" cy="24" r="6" fill="url(#brandGradient)" />
                <defs>
                  <linearGradient id="brandGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#f472b6" />
                    <stop offset="50%" stop-color="#a855f7" />
                    <stop offset="100%" stop-color="#9333ea" />
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="brand-text">
              <span class="brand-name">VisionSpace</span>
              <span class="brand-tagline">视觉灵感宇宙</span>
            </div>
          </div>

          <h1 class="hero-title">
            <span
              class="title-word"
              v-for="(word, i) in titleWords"
              :key="i"
              :style="{ animationDelay: `${0.6 + i * 0.2}s` }"
            >
              {{ word }}
            </span>
          </h1>

          <p class="hero-subtitle">
            在光影之间，寻找共鸣<br />探索无限视觉灵感
          </p>

          <div class="hero-cta-group">
            <button class="cta-btn cta-primary cursor-target" @click="router.push('/explore')">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8" />
                <path d="m21 21-4.35-4.35" />
              </svg>
              <span>进入探索</span>
            </button>
            <button class="cta-btn cta-secondary cursor-target" @click="router.push('/agent')">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 6v6l4 2" />
              </svg>
              <span>唤醒 SpaceMind</span>
            </button>
          </div>
        </div>

      </section>

      <section id="essence" class="brand-essence-section" :class="{ 'is-visible': visibleSections['essence'] }">
        <div class="section-inner editorial-layout">
          <div class="deco-ring deco-ring-1" aria-hidden="true"></div>
          <div class="deco-ring deco-ring-2" aria-hidden="true"></div>
          <div class="deco-arc deco-arc-1" aria-hidden="true"></div>
          <div class="deco-dot deco-dot-1" aria-hidden="true"></div>
          <div class="deco-dot deco-dot-2" aria-hidden="true"></div>
          <div class="deco-dot deco-dot-3" aria-hidden="true"></div>

          <div class="editorial-text-col">
            <div class="glass-text-block">
              <span class="section-label editorial-label">品牌理念</span>
              <div class="quote-mark" aria-hidden="true">"</div>
              <h3 class="editorial-heading">
                <span class="heading-line">AI 与人类</span>
                <span class="heading-line gradient-text">创造力的交汇点</span>
              </h3>
              <div class="editorial-body">
                <p class="editorial-paragraph">
                  VisionSpace 诞生于一个简单的信念：每个人心中都有一个视觉宇宙，等待被唤醒。我们用 AI
                  技术搭建一座桥梁，连接灵感与表达、想象与现实。
                </p>
                <p class="editorial-paragraph">
                  在这里，SpaceMind 不仅理解你的语言，更懂得你的审美。它能在海量视觉资源中精准捕捉你想要的那一抹色彩、那一瞬光影、那一种氛围。
                </p>
                <p class="editorial-paragraph">
                  我们相信协作的力量。无论是个人灵感收集还是团队创意协作，VisionSpace
                  提供流畅的空间体验，让创意在共享中绽放。
                </p>
                <p class="editorial-paragraph editorial-highlight">
                  每一张图片都是一段故事，每一次搜索都是一次发现。欢迎来到你的视觉灵感宇宙。
                </p>
              </div>
              <div class="accent-bar" aria-hidden="true"></div>
            </div>
          </div>

          <div class="editorial-visual-col">
            <div class="gallery-frame">
              <div class="frame-glow" aria-hidden="true"></div>
              <RollingGallery
                :autoplay="true"
                :pause-on-hover="true"
                :images="orbitImages"
                :height="680"
              />
              <div class="surround-ring surround-ring-outer" aria-hidden="true"></div>
              <div class="surround-ring surround-ring-inner" aria-hidden="true"></div>
              <div class="ring-dot ring-dot-1" aria-hidden="true"></div>
              <div class="ring-dot ring-dot-2" aria-hidden="true"></div>
              <div class="ring-dot ring-dot-3" aria-hidden="true"></div>
              <div class="ring-dot ring-dot-4" aria-hidden="true"></div>
            </div>
          </div>
        </div>
      </section>

      <section id="experiences" class="experiences-section" :class="{ 'is-visible': visibleSections['experiences'] }">
        <div class="section-inner">
          <h2 class="section-label">核心体验</h2>
          <h3 class="section-heading">重新定义视觉发现的方式</h3>
          <div class="section-split">
            <div class="section-left">
              <p class="split-eyebrow">每一次灵感</p>
              <h3 class="split-heading">从这里<br />开始探索</h3>
              <p class="split-body">
                VisionSpace 重新定义视觉发现的方式——用颜色找到完美图片，用自然语言描述你的灵感，AI 在秒级为你精准匹配。
              </p>
              <div class="split-stats">
                <div class="stat-item">
                  <span class="stat-num">10x</span>
                  <span class="stat-label">搜索效率提升</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">98%</span>
                  <span class="stat-label">匹配准确率</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">50k+</span>
                  <span class="stat-label">创作者信赖</span>
                </div>
              </div>
            </div>
            <div class="section-right">
              <CardSwap
                :card-distance="70"
                :vertical-distance="60"
                :delay="4000"
                :skew-amount="5"
                easing="elastic"
                :pause-on-hover="true"
              >
                <template #card-0>
                  <div class="swap-card">
                    <div class="swap-card-icon">
                      <component :is="features[1].icon" />
                    </div>
                    <h4 class="swap-card-title">{{ features[1].title }}</h4>
                    <p class="swap-card-desc">{{ features[1].desc }}</p>
                    <p class="swap-card-long">{{ features[1].longDesc }}</p>
                  </div>
                </template>
                <template #card-1>
                  <div class="swap-card">
                    <div class="swap-card-icon">
                      <component :is="features[0].icon" />
                    </div>
                    <h4 class="swap-card-title">{{ features[0].title }}</h4>
                    <p class="swap-card-desc">{{ features[0].desc }}</p>
                    <p class="swap-card-long">{{ features[0].longDesc }}</p>
                  </div>
                </template>
                <template #card-2>
                  <div class="swap-card">
                    <div class="swap-card-icon">
                      <component :is="features[2].icon" />
                    </div>
                    <h4 class="swap-card-title">{{ features[2].title }}</h4>
                    <p class="swap-card-desc">{{ features[2].desc }}</p>
                    <p class="swap-card-long">{{ features[2].longDesc }}</p>
                  </div>
                </template>
              </CardSwap>
            </div>
          </div>
        </div>
      </section>

      <section id="universe" class="visual-universe-section" :class="{ 'is-visible': visibleSections['universe'] }">
        <div class="universe-ambient" aria-hidden="true">
          <div class="universe-dust"></div>
          <div class="universe-glow universe-glow-1"></div>
          <div class="universe-glow universe-glow-2"></div>
          <div class="universe-ring universe-ring-1"></div>
          <div class="universe-ring universe-ring-2"></div>
          <div class="universe-ring universe-ring-3"></div>
          <div class="universe-star universe-star-1"></div>
          <div class="universe-star universe-star-2"></div>
          <div class="universe-star universe-star-3"></div>
          <div class="universe-star universe-star-4"></div>
          <div class="universe-star universe-star-5"></div>
        </div>

        <div class="section-inner universe-content">
          <div class="universe-header">
            <h3 class="section-heading universe-heading">
              来自全球创作者的<br />
              灵感时刻
            </h3>
            <p class="universe-hint">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
              拖拽穹顶，探索无限视觉灵感
            </p>
          </div>

          <div class="universe-dome-wrap">
            <div class="orbit-deco orbit-deco-1" aria-hidden="true"></div>
            <div class="orbit-deco orbit-deco-2" aria-hidden="true"></div>
            <div class="orbit-deco orbit-deco-3" aria-hidden="true"></div>

            <div class="gallery-wrapper">
              <DomeGallery1
                :images="universeImages"
                :fit="0.8"
                fit-basis="auto"
                :min-radius="200"
                :max-radius="Infinity"
                :segments="34"
                :drag-sensitivity="20"
                :enlarge-transition-ms="300"
                :grayscale="true"
                overlay-blur-color="#060010"
                image-border-radius="30px"
                opened-image-width="250px"
                opened-image-height="350px"
              />
            </div>
          </div>
        </div>
      </section>

      <section id="ai-magic" class="ai-magic-section" :class="{ 'is-visible': visibleSections['ai-magic'] }">
        <div class="section-inner">
          <h2 class="section-label">AI 魔力</h2>
          <h3 class="section-heading">感受 AI 带来的视觉革命</h3>
          <div class="ai-grid">
            <div class="ai-chat-preview">
              <div class="chat-header">
                <div class="chat-avatar">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 6v6l4 2" />
                  </svg>
                </div>
                <span class="chat-name">SpaceMind</span>
                <span class="chat-status">在线</span>
              </div>
              <div class="chat-messages">
                <div class="chat-msg chat-user">
                  <div class="msg-bubble msg-user-bubble">
                    帮我找一些黄昏时分、暖色调、有建筑剪影的图片
                  </div>
                </div>
                <div class="chat-msg chat-ai">
                  <div class="msg-bubble msg-ai-bubble">
                    <p>我为你找到了 4 张匹配的图片，融合了黄昏暖色调与建筑剪影的意境：</p>
                    <div class="chat-images">
                      <img
                        v-for="(img, i) in chatExampleImages"
                        :key="i"
                        :src="img.url"
                        :alt="img.name"
                        class="chat-thumb"
                        loading="lazy"
                        decoding="async"
                      />
                    </div>
                    <p class="chat-note">以上图片均来自社区创作者，可直接加入你的空间收藏。</p>
                  </div>
                </div>
              </div>
            </div>

            <div class="ai-outpaint-preview">
              <h4 class="outpaint-label">AI 扩图</h4>
              <p class="outpaint-desc">上传任意图片，AI 智能扩展画面边界，完美衔接原图风格</p>
              <div class="outpaint-compare">
                <div class="compare-item compare-before">
                  <div class="compare-label">Before</div>
                  <div class="compare-image">
                    <img src="https://picsum.photos/400/300?random=77" alt="Before outpainting" loading="lazy" decoding="async" />
                  </div>
                </div>
                <div class="compare-arrow">
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M5 12h14M12 5l7 7-7 7" />
                  </svg>
                </div>
                <div class="compare-item compare-after">
                  <div class="compare-label compare-label-accent">After</div>
                  <div class="compare-image">
                    <img src="https://picsum.photos/600/400?random=78" alt="After outpainting" loading="lazy" decoding="async" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="cta" class="final-cta-section" :class="{ 'is-visible': visibleSections['cta'] }">
        <div class="cta-radial-bg"></div>
        <div class="section-inner cta-content">
          <h2 class="cta-title">准备好开启你的<br />视觉之旅了吗？</h2>
          <p class="cta-subtitle">加入 VisionSpace，在无限灵感中找到属于你的那一束光</p>
          <div class="cta-buttons">
            <button class="cta-btn cta-final-primary cursor-target" @click="router.push('/explore')">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8" />
                <path d="m21 21-4.35-4.35" />
              </svg>
              <span>立即探索</span>
            </button>
            <button class="cta-btn cta-final-secondary cursor-target" @click="router.push('/agent')">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 6v6l4 2" />
              </svg>
              <span>对话 SpaceMind</span>
            </button>
          </div>
        </div>
      </section>

      <section id="footer" class="footer-section-wrap" :class="{ 'is-visible': visibleSections['footer'] }">
        <GlobalFooter />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import GridDistortion from '@/components/vue-bits/animations/GridDistortion.vue'
import TargetCursor from '@/components/vue-bits/animations/TargetCursor.vue'
import CardSwap from '@/components/vue-bits/components/CardSwap.vue'
import RollingGallery from '@/components/vue-bits/components/RollingGallery.vue'
import DomeGallery1 from '@/components/vue-bits/components/DomeGallery.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const router = useRouter()

const visibleSections = ref<Record<string, boolean>>({})
let sectionObserver: IntersectionObserver | null = null

onMounted(() => {
  sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        visibleSections.value[entry.target.id] = true
      }
    })
  }, { threshold: 0.2 })

  document.querySelectorAll('section').forEach((section) => {
    sectionObserver!.observe(section)
  })
})

onBeforeUnmount(() => {
  sectionObserver?.disconnect()
})

const titleWords = ['发现', '视觉', '灵感']

const orbitImages = [
  'https://images.unsplash.com/photo-1528181304800-259b08848526?q=80&w=3870&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1506665531195-3566af2b4dfa?q=80&w=3870&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?q=80&w=3456&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1495103033382-fe343886b671?q=80&w=3870&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1506781961370-37a89d6b3095?q=80&w=3264&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1599576838688-8a6c11263108?q=80&w=3870&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1494094892896-7f14a4433b7a?q=80&w=3870&auto=format&fit=crop',
  'https://plus.unsplash.com/premium_photo-1664910706524-e783eed89e71?q=80&w=3869&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1503788311183-fa3bf9c4bc32?q=80&w=3870&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1585970480901-90d6bb2a48b5?q=80&w=3774&auto=format&fit=crop',
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

const CHAT_IMAGE_OFFSET = 60
const chatExampleImages = Array.from({ length: 4 }, (_, i) => ({
  url: `https://picsum.photos/200/200?random=${i + CHAT_IMAGE_OFFSET}`,
  name: `示例${i + 1}`,
}))

const features = [
  {
    title: '智能色彩搜索',
    desc: '用颜色找到完美图片',
    longDesc:
      '不再局限于关键词。描述你想要的氛围或选择颜色，AI 帮你找到最匹配的图片。',
    icon: () =>
      h(
        'svg',
        {
          width: 28,
          height: 28,
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': 2,
        },
        [
          h('circle', { cx: '12', cy: '12', r: '10' }),
          h('path', { d: 'M12 2a10 10 0 0 0 0 20' }),
          h('path', { d: 'M12 2a7 7 0 0 1 0 14' }),
          h('path', { d: 'M12 2a4 4 0 0 1 0 8' }),
        ],
      ),
  },
  {
    title: 'AI Agent',
    desc: 'SpaceMind 读懂你的想法',
    longDesc:
      '用自然语言描述你的需求，SpaceMind 会在秒级时间内为你找到最合适的图片资源。',
    icon: () =>
      h(
        'svg',
        {
          width: 28,
          height: 28,
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': 2,
        },
        [
          h('circle', { cx: '12', cy: '12', r: '10' }),
          h('path', { d: 'M12 6v6l4 2' }),
        ],
      ),
  },
  {
    title: '空间实时协作',
    desc: '与团队共享视觉灵感',
    longDesc:
      '创建私有画布，邀请团队成员，共同管理和整理视觉资源，实时同步更新。',
    icon: () =>
      h(
        'svg',
        {
          width: 28,
          height: 28,
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': 2,
        },
        [
          h('path', {
            d: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z',
          }),
        ],
      ),
  },
  {
    title: 'AI 扩图',
    desc: '无限延展你的创作边界',
    longDesc:
      '上传任意图片，AI 帮你智能扩展画面边界，完美衔接原图风格与内容。',
    icon: () =>
      h(
        'svg',
        {
          width: 28,
          height: 28,
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': 2,
        },
        [
          h('path', { d: 'M15 3h6v6' }),
          h('path', { d: 'M9 21H3v-6' }),
          h('path', { d: 'M21 3l-7 7' }),
          h('path', { d: 'M3 21l7-7' }),
        ],
      ),
  },
]
</script>

<style scoped lang="less">
/* ========== Page root ========== */
#landingPage {
  height: 100vh;
  background: var(--color-bg-primary);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* ========== Ambient background (preserved) ========== */
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
  background-image: radial-gradient(1px 1px at 10% 15%, rgba(255, 255, 255, 0.7) 0%, transparent 100%),
    radial-gradient(1px 1px at 25% 40%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 40% 10%, rgba(255, 255, 255, 0.8) 0%, transparent 100%),
    radial-gradient(1px 1px at 55% 60%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 70% 30%, rgba(255, 255, 255, 0.6) 0%, transparent 100%),
    radial-gradient(1px 1px at 85% 70%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 15% 80%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 92% 55%, rgba(255, 255, 255, 0.6) 0%, transparent 100%),
    radial-gradient(2px 2px at 35% 75%, rgba(168, 85, 247, 0.6) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 60% 85%, rgba(236, 72, 153, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 5% 50%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 78% 8%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 48% 25%, rgba(168, 85, 247, 0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 88% 92%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 22% 65%, rgba(255, 255, 255, 0.3) 0%, transparent 100%);
  animation: starfield-drift 120s linear infinite;
}

.starfield-2 {
  background-image: radial-gradient(1px 1px at 18% 28%, rgba(255, 255, 255, 0.6) 0%, transparent 100%),
    radial-gradient(1px 1px at 33% 72%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 62% 18%, rgba(255, 255, 255, 0.7) 0%, transparent 100%),
    radial-gradient(1px 1px at 80% 48%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 45% 92%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 12% 58%, rgba(168, 85, 247, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 72% 82%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 95% 20%, rgba(255, 255, 255, 0.3) 0%, transparent 100%),
    radial-gradient(2px 2px at 50% 50%, rgba(168, 85, 247, 0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 28% 8%, rgba(255, 255, 255, 0.5) 0%, transparent 100%);
  animation: starfield-drift 180s linear infinite reverse;
  opacity: 0.7;
}

@keyframes starfield-drift {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-200px);
  }
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
  background: radial-gradient(
    circle,
    rgba(168, 85, 247, 0.35) 0%,
    rgba(110, 53, 235, 0.15) 50%,
    transparent 70%
  );
  top: -250px;
  right: -150px;
  animation-name: orb-breathe-1;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(
    circle,
    rgba(236, 72, 153, 0.25) 0%,
    rgba(168, 85, 247, 0.1) 50%,
    transparent 70%
  );
  top: 35%;
  left: -200px;
  animation-name: orb-breathe-2;
  animation-delay: -4s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(
    circle,
    rgba(168, 85, 247, 0.2) 0%,
    rgba(34, 104, 245, 0.1) 50%,
    transparent 70%
  );
  bottom: -150px;
  right: 15%;
  animation-name: orb-breathe-3;
  animation-delay: -8s;
}

@keyframes orb-breathe-1 {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.7;
  }
  33% {
    transform: translate(40px, -40px) scale(1.08);
    opacity: 0.9;
  }
  66% {
    transform: translate(-30px, 30px) scale(0.95);
    opacity: 0.6;
  }
}

@keyframes orb-breathe-2 {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.6;
  }
  40% {
    transform: translate(-50px, 30px) scale(1.1);
    opacity: 0.85;
  }
  70% {
    transform: translate(30px, -20px) scale(0.92);
    opacity: 0.5;
  }
}

@keyframes orb-breathe-3 {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.55;
  }
  50% {
    transform: translate(20px, -50px) scale(1.12);
    opacity: 0.8;
  }
}

.hero-glow-center {
  position: absolute;
  top: 30%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 800px;
  height: 600px;
  background: radial-gradient(
    ellipse,
    rgba(168, 85, 247, 0.12) 0%,
    rgba(236, 72, 153, 0.08) 30%,
    transparent 70%
  );
  pointer-events: none;
  animation: glow-pulse 4s ease-in-out infinite;
}

@keyframes glow-pulse {
  0%,
  100% {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

/* ========== Main content wrapper ========== */
.main-content {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  scroll-behavior: smooth;
}

/* ========== Shared section layout ========== */
section {
  scroll-snap-align: start;
  scroll-snap-stop: always;
  height: 100vh;
  width: 100%;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
}

.section-inner {
  max-width: var(--container-xl);
  margin: 0 auto;
  width: 100%;
  padding: 0 24px;
}

/* ========== Staggered Entrance Animations ========== */
.section-label,
.section-heading,
.hero-title,
.hero-subtitle,
.hero-cta-group,
.editorial-paragraph,
.split-body,
.split-stats,
.universe-header,
.ai-grid,
.cta-content > * {
  opacity: 0;
  transform: translateY(30px);
  transition: all 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

section.is-visible {
  .section-label {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.2s;
  }
  .section-heading {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.4s;
  }
  .hero-title {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.3s;
  }
  .hero-subtitle {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.5s;
  }
  .hero-cta-group {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.7s;
  }
  .editorial-paragraph {
    opacity: 1;
    transform: translateY(0);
    &:nth-of-type(1) { transition-delay: 0.6s; }
    &:nth-of-type(2) { transition-delay: 0.7s; }
    &:nth-of-type(3) { transition-delay: 0.8s; }
    &:nth-of-type(4) { transition-delay: 0.9s; }
  }
  .split-body {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.5s;
  }
  .split-stats {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.7s;
  }
  .universe-header {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.3s;
  }
  .ai-grid {
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0.5s;
  }
  .cta-content > * {
    opacity: 1;
    transform: translateY(0);
    &:nth-child(1) { transition-delay: 0.2s; }
    &:nth-child(2) { transition-delay: 0.4s; }
    &:nth-child(3) { transition-delay: 0.6s; }
  }
}

/* ========== Footer Section Wrap ========== */
.footer-section-wrap {
  height: auto;
  min-height: auto;
  scroll-snap-align: end;
  background: var(--color-bg-primary);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 3px;
  text-transform: uppercase;
  color: var(--color-accent-purple);
  margin: 0 0 12px;
}

.section-heading {
  font-family: var(--font-display);
  font-size: clamp(28px, 4vw, 42px);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 48px;
  line-height: 1.2;
}

/* ========== Section 1: Hero ========== */
.hero-section {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 60px 24px 80px;
  position: relative;
  overflow: hidden;
}

.hero-bg-distortion {
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.55;
  overflow: hidden;
  width: 100%;
  height: 100%;

  :deep(canvas) {
    width: 100% !important;
    height: 100% !important;
    display: block;
    object-fit: cover;
  }
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
}

/* Removal of old simple hero animation in favor of staggered one below */

.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

section#hero.is-visible .brand-section {
  opacity: 1;
  transform: translateY(0);
  transition-delay: 0.1s;
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
  0%,
  100% {
    filter: drop-shadow(0 0 12px rgba(168, 85, 247, 0.5));
  }
  50% {
    filter: drop-shadow(0 0 28px rgba(168, 85, 247, 0.8));
  }
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

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(56px, 12vw, 120px);
  font-weight: 800;
  line-height: 1.05;
  margin: 0 0 24px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px 32px;
  overflow: hidden; /* For line reveal */
}

.title-word {
  display: inline-block;
  opacity: 0;
  transform: translateY(100%);
  transition: all 0.8s cubic-bezier(0.34, 1.56, 0.64, 1);

  section.is-visible & {
    opacity: 1;
    transform: translateY(0);
  }

  &:nth-child(1) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7c3aed 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    transition-delay: 0.4s;
  }
  &:nth-child(2) {
    background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7e22ce 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    transition-delay: 0.55s;
  }
  &:nth-child(3) {
    background: linear-gradient(135deg, #f472b6 0%, #ec4899 50%, #db2777 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    transition-delay: 0.7s;
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
  margin: 0 0 48px;
  font-weight: 400;
  letter-spacing: 1px;
  line-height: 1.7;
  animation: fade-up 0.8s ease-out 1.2s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* CTA buttons */
.hero-cta-group {
  display: flex;
  gap: 16px;
  animation: fade-up 0.8s ease-out 1.4s forwards;
  opacity: 0;
  transform: translateY(20px);
}

.cta-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 32px;
  border: none;
  border-radius: 60px;
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  letter-spacing: 0.5px;
  pointer-events: auto;
}

.cta-primary {
  background: linear-gradient(135deg, #a855f7 0%, #9333ea 50%, #7e22ce 100%);
  color: #fff;
  box-shadow: 0 4px 24px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-3px) scale(1.04);
    box-shadow: 0 8px 40px rgba(168, 85, 247, 0.55), 0 0 24px rgba(168, 85, 247, 0.3);
  }

  &:active {
    transform: translateY(-1px) scale(0.98);
  }
}

.cta-secondary {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  color: var(--color-text-primary);

  &:hover {
    transform: translateY(-3px) scale(1.04);
    border-color: rgba(168, 85, 247, 0.4);
    box-shadow: 0 8px 30px rgba(168, 85, 247, 0.2);
    background: var(--glass-bg-light);
  }

  &:active {
    transform: translateY(-1px) scale(0.98);
  }
}

/* Scroll hint */
.scroll-hint {
  position: absolute;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fade-up 0.8s ease-out 1.8s forwards;
  opacity: 0;
}

.scroll-line {
  width: 2px;
  height: 40px;
  background: linear-gradient(to bottom, var(--color-accent-purple), transparent);
  animation: scroll-pulse 2s ease-in-out infinite;
}

@keyframes scroll-pulse {
  0%,
  100% {
    opacity: 0.4;
    transform: scaleY(1);
  }
  50% {
    opacity: 1;
    transform: scaleY(1.2);
  }
}

/* ========== Section 2: Brand Essence — Editorial Cosmos ========== */
.brand-essence-section {
  padding: 120px 0;
  background: var(--gradient-deep-space);
  position: relative;
  overflow: hidden;
}

/* ---- Asymmetric two-column editorial layout ---- */
.editorial-layout {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 48px;
  align-items: center;
  position: relative;
}

/* ---- Floating decorative shapes ---- */
.deco-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid;
  pointer-events: none;
}

.deco-ring-1 {
  width: 320px;
  height: 320px;
  border-color: rgba(168, 85, 247, 0.15);
  top: -80px;
  left: -60px;
  animation: float-ring 14s ease-in-out infinite;
}

.deco-ring-2 {
  width: 200px;
  height: 200px;
  border-color: rgba(255, 107, 157, 0.12);
  bottom: -40px;
  right: 38%;
  animation: float-ring 18s ease-in-out infinite reverse;
}

.deco-arc {
  position: absolute;
  pointer-events: none;
}

.deco-arc-1 {
  width: 180px;
  height: 180px;
  border: 2px solid transparent;
  border-top-color: rgba(0, 212, 170, 0.25);
  border-right-color: rgba(0, 212, 170, 0.25);
  top: 20%;
  right: 42%;
  border-radius: 50%;
  transform: rotate(45deg);
  animation: spin-slow 20s linear infinite;
}

.deco-dot {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation: float-dot 8s ease-in-out infinite;
}

.deco-dot-1 {
  width: 8px;
  height: 8px;
  background: var(--color-accent-purple);
  top: 15%;
  left: 25%;
  animation-delay: 0s;
  box-shadow: 0 0 12px rgba(168, 85, 247, 0.6);
}

.deco-dot-2 {
  width: 6px;
  height: 6px;
  background: var(--color-accent-pink);
  top: 70%;
  left: 10%;
  animation-delay: -3s;
  box-shadow: 0 0 10px rgba(255, 107, 157, 0.6);
}

.deco-dot-3 {
  width: 5px;
  height: 5px;
  background: var(--color-accent-cyan);
  top: 40%;
  right: 8%;
  animation-delay: -5s;
  box-shadow: 0 0 8px rgba(0, 212, 170, 0.6);
}

/* ---- Editorial text column ---- */
.editorial-text-col {
  position: relative;
  z-index: 2;
}

/* ---- Glass-morphism text block ---- */
.glass-text-block {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(168, 85, 247, 0.2);
  border-radius: 24px;
  padding: 48px 44px;
  position: relative;
  overflow: hidden;
  transition: border-color 0.4s ease, box-shadow 0.4s ease;
  opacity: 0;
  transform: translateX(-30px);
  transition: all 0.8s cubic-bezier(0.22, 1, 0.36, 1) 0.1s;

  section.is-visible & {
    opacity: 1;
    transform: translateX(0);
  }

  &:hover {
    border-color: rgba(168, 85, 247, 0.4);
    box-shadow: 0 24px 80px rgba(168, 85, 247, 0.12);
  }
}

.glass-text-block::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(168, 85, 247, 0.5) 30%,
    rgba(255, 107, 157, 0.4) 60%,
    transparent 100%
  );
}

/* ---- Large decorative quote mark ---- */
.quote-mark {
  font-family: var(--font-brand);
  font-size: 120px;
  line-height: 0.8;
  color: rgba(168, 85, 247, 0.15);
  position: absolute;
  top: 24px;
  left: 28px;
  user-select: none;
  pointer-events: none;
}

/* ---- Editorial label ---- */
.editorial-label {
  display: block;
  margin-bottom: 20px;
}

/* ---- Dramatic display heading ---- */
.editorial-heading {
  font-family: var(--font-display);
  font-size: clamp(28px, 3.8vw, 46px);
  font-weight: 800;
  line-height: 1.15;
  margin: 0 0 28px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.heading-line {
  display: block;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.8s cubic-bezier(0.22, 1, 0.36, 1);

  section.is-visible & {
    opacity: 1;
    transform: translateY(0);
  }

  &:nth-child(1) {
    transition-delay: 0.35s;
    color: var(--color-text-primary);
  }

  &:nth-child(2) {
    transition-delay: 0.5s;
  }
}

.gradient-text {
  background: linear-gradient(
    135deg,
    var(--color-accent-purple) 0%,
    var(--color-accent-pink) 50%,
    var(--color-accent-cyan) 100%
  );
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ---- Editorial body text ---- */
.editorial-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editorial-paragraph {
  font-family: var(--font-body);
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--color-text-secondary);
  margin: 0;
}

.editorial-highlight {
  color: var(--color-accent-pink);
  font-style: italic;
  font-weight: 600;
  font-size: 15px;
}

/* ---- Accent bar ---- */
.accent-bar {
  position: absolute;
  bottom: 0;
  left: 44px;
  right: 44px;
  height: 2px;
  background: linear-gradient(
    90deg,
    var(--color-accent-purple),
    var(--color-accent-pink),
    var(--color-accent-cyan)
  );
  border-radius: 2px;
  opacity: 0.6;
  animation: accent-pulse 3s ease-in-out infinite;
}

/* ---- Editorial visual column ---- */
.editorial-visual-col {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 740px;
  animation: fade-slide-left 1s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  opacity: 0;
  animation-delay: 0.35s;
}

/* ---- Gallery frame ---- */
.gallery-frame {
  position: relative;
  width: 100%;
  max-width: 720px;
  min-height: 720px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---- Frame glow ---- */
.frame-glow {
  position: absolute;
  width: 600px;
  height: 600px;
  background: radial-gradient(
    circle,
    rgba(168, 85, 247, 0.2) 0%,
    rgba(255, 107, 157, 0.1) 40%,
    transparent 70%
  );
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: glow-breathe 5s ease-in-out infinite;
}

/* ---- Surrounding decorative rings ---- */
.surround-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid;
  pointer-events: none;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.surround-ring-outer {
  width: 700px;
  height: 700px;
  border-color: rgba(168, 85, 247, 0.12);
  animation: spin-slow 30s linear infinite;
}

.surround-ring-outer::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: 50%;
  border: 1px dashed rgba(168, 85, 247, 0.2);
  animation: spin-slow 20s linear infinite reverse;
}

.surround-ring-inner {
  width: 600px;
  height: 600px;
  border-color: rgba(255, 107, 157, 0.1);
  animation: spin-slow 22s linear infinite reverse;
}

/* ---- Ring dots ---- */
.ring-dot {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.ring-dot-1 {
  width: 10px;
  height: 10px;
  background: var(--color-accent-purple);
  top: 50%;
  left: 50%;
  margin-top: -230px;
  margin-left: -5px;
  box-shadow: 0 0 16px rgba(168, 85, 247, 0.8);
  animation: orbit-dot 12s linear infinite;
}

.ring-dot-2 {
  width: 7px;
  height: 7px;
  background: var(--color-accent-cyan);
  top: 50%;
  left: 50%;
  margin-top: 220px;
  margin-left: -3.5px;
  box-shadow: 0 0 12px rgba(0, 212, 170, 0.8);
  animation: orbit-dot 18s linear infinite reverse;
}

.ring-dot-3 {
  width: 6px;
  height: 6px;
  background: var(--color-accent-pink);
  top: 50%;
  left: 50%;
  margin-left: 225px;
  margin-top: -3px;
  box-shadow: 0 0 10px rgba(255, 107, 157, 0.8);
  animation: orbit-dot 15s linear infinite;
}

.ring-dot-4 {
  width: 5px;
  height: 5px;
  background: rgba(248, 250, 252, 0.6);
  top: 50%;
  left: 50%;
  margin-left: -228px;
  margin-top: -2.5px;
  box-shadow: 0 0 8px rgba(248, 250, 252, 0.4);
  animation: orbit-dot 20s linear infinite reverse;
}

/* ---- Keyframe animations ---- */
@keyframes float-ring {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
    opacity: 0.6;
  }
  33% {
    transform: translate(12px, -18px) rotate(5deg);
    opacity: 1;
  }
  66% {
    transform: translate(-8px, 12px) rotate(-3deg);
    opacity: 0.8;
  }
}

@keyframes float-dot {
  0%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  50% {
    transform: translateY(-12px);
    opacity: 1;
  }
}

@keyframes spin-slow {
  from {
    transform: translate(-50%, -50%) rotate(0deg);
  }
  to {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

@keyframes orbit-dot {
  from {
    transform: rotate(0deg) translateX(0);
  }
  to {
    transform: rotate(360deg) translateX(0);
  }
}

@keyframes glow-breathe {
  0%, 100% {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.08);
  }
}

@keyframes accent-pulse {
  0%, 100% {
    opacity: 0.4;
  }
  50% {
    opacity: 0.8;
  }
}

@keyframes fade-slide-right {
  from {
    opacity: 0;
    transform: translateX(-40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes fade-slide-left {
  from {
    opacity: 0;
    transform: translateX(40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== Section 3: Signature Experiences ========== */
.experiences-section {
  padding: 120px 0;
  position: relative;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.feature-card {
  padding: 32px 24px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur-light);
  -webkit-backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  text-align: center;
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 50% 0%, rgba(168, 85, 247, 0.08) 0%, transparent 70%);
    opacity: 0;
    transition: opacity 0.35s ease;
  }

  &:hover {
    transform: translateY(-8px) scale(1.02);
    border-color: rgba(168, 85, 247, 0.3);
    box-shadow: 0 20px 60px rgba(168, 85, 247, 0.15), 0 0 40px rgba(168, 85, 247, 0.08);

    &::before {
      opacity: 1;
    }
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
  transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.35s ease;

  .feature-card:hover & {
    transform: scale(1.1);
    box-shadow: 0 8px 24px rgba(168, 85, 247, 0.4);
  }
}

.feature-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px;
}

.feature-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0 0 12px;
  font-weight: 500;
}

.feature-long-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0;
  line-height: 1.7;
  opacity: 0.8;
}

.section-split {
  display: flex;
  align-items: center;
  gap: 80px;
  padding: 40px 0;
}

.section-left {
  flex: 0 0 360px;
}

.split-eyebrow {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent, #a855f7);
  margin: 0 0 16px;
  opacity: 0.8;
}

.split-heading {
  font-family: var(--font-display);
  font-size: 48px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--color-text-primary);
  margin: 0 0 24px;
  letter-spacing: -0.02em;
}

.split-body {
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text-secondary);
  margin: 0 0 40px;
}

.split-stats {
  display: flex;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  background: var(--gradient-aurora);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.section-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.swap-card {
  width: 100%;
  height: 100%;
  padding: 40px 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur-light);
  -webkit-backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  box-sizing: border-box;
}

.swap-card-icon {
  width: 72px;
  height: 72px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-aurora);
  border-radius: 18px;
  color: white;
  box-shadow: 0 8px 32px rgba(168, 85, 247, 0.25);
}

.swap-card-title {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 10px;
}

.swap-card-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin: 0 0 14px;
  font-weight: 600;
}

.swap-card-long {
  font-size: 13px;
  color: var(--color-text-tertiary);
  line-height: 1.7;
  margin: 0;
  opacity: 0.8;
}

/* ========== Section 4: Visual Universe — Cosmic Observatory ========== */
.visual-universe-section {
  padding: 30px 0;
  background: var(--gradient-deep-space);
  position: relative;
  overflow: hidden;
}

/* ---- Ambient background ---- */
.universe-ambient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.universe-dust {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 8% 12%, rgba(255, 255, 255, 0.5) 0%, transparent 100%),
    radial-gradient(1px 1px at 22% 38%, rgba(255, 255, 255, 0.3) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 45% 8%, rgba(168, 85, 247, 0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 62% 55%, rgba(255, 255, 255, 0.35) 0%, transparent 100%),
    radial-gradient(1px 1px at 78% 22%, rgba(255, 255, 255, 0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 91% 70%, rgba(236, 72, 153, 0.35) 0%, transparent 100%),
    radial-gradient(1px 1px at 15% 85%, rgba(255, 255, 255, 0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 55% 92%, rgba(168, 85, 247, 0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 73% 45%, rgba(255, 255, 255, 0.25) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 33% 65%, rgba(0, 212, 170, 0.3) 0%, transparent 100%);
  animation: dust-drift 80s linear infinite;
}

@keyframes dust-drift {
  from { transform: translateY(0) translateX(0); }
  to { transform: translateY(-150px) translateX(30px); }
}

.universe-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  pointer-events: none;
}

.universe-glow-1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.18) 0%, transparent 70%);
  top: -100px;
  left: 10%;
  animation: glow-breathe 8s ease-in-out infinite;
}

.universe-glow-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.15) 0%, transparent 70%);
  bottom: -80px;
  right: 15%;
  animation: glow-breathe 10s ease-in-out infinite reverse;
  animation-delay: -3s;
}

.universe-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid;
  pointer-events: none;
}

.universe-ring-1 {
  width: 600px;
  height: 600px;
  border-color: rgba(168, 85, 247, 0.08);
  top: -60px;
  left: 5%;
  animation: spin-slow 60s linear infinite;
}

.universe-ring-2 {
  width: 400px;
  height: 400px;
  border-color: rgba(0, 212, 170, 0.06);
  top: 30%;
  right: 8%;
  animation: spin-slow 45s linear infinite reverse;
}

.universe-ring-3 {
  width: 300px;
  height: 300px;
  border-color: rgba(255, 107, 157, 0.07);
  bottom: -40px;
  left: 50%;
  transform: translateX(-50%);
  animation: spin-slow 35s linear infinite;
}

.universe-star {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation: star-twinkle 3s ease-in-out infinite;
}

.universe-star-1 {
  width: 4px;
  height: 4px;
  background: #fff;
  top: 18%;
  left: 15%;
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.8);
  animation-delay: 0s;
}

.universe-star-2 {
  width: 3px;
  height: 3px;
  background: rgba(168, 85, 247, 0.9);
  top: 25%;
  right: 22%;
  box-shadow: 0 0 8px rgba(168, 85, 247, 0.8);
  animation-delay: -1s;
}

.universe-star-3 {
  width: 2px;
  height: 2px;
  background: rgba(0, 212, 170, 0.8);
  top: 65%;
  left: 8%;
  box-shadow: 0 0 6px rgba(0, 212, 170, 0.6);
  animation-delay: -2s;
}

.universe-star-4 {
  width: 3px;
  height: 3px;
  background: #fff;
  top: 72%;
  right: 12%;
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.6);
  animation-delay: -0.5s;
}

.universe-star-5 {
  width: 2px;
  height: 2px;
  background: rgba(236, 72, 153, 0.7);
  top: 45%;
  left: 3%;
  box-shadow: 0 0 6px rgba(236, 72, 153, 0.5);
  animation-delay: -1.5s;
}

@keyframes star-twinkle {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.3); }
}

/* ---- Content layout ---- */
.universe-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* ---- Header ---- */
.universe-header {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 10px;
  animation: fade-up 0.7s ease-out 0.2s forwards;
  opacity: 0;
  max-width: 600px;
  margin: 0 auto;
  text-align: center;
  align-items: center;
}

.universe-heading {
  margin: 0;
  animation: none;
  opacity: 1;
}

.universe-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-tertiary);
  animation: fade-up 0.7s ease-out 0.5s forwards;
  opacity: 0;
  margin: 0;

  svg {
    color: var(--color-accent-purple);
    flex-shrink: 0;
  }
}

/* ---- Dome wrap ---- */
.universe-dome-wrap {
  position: relative;
  height: 600px; /* EXACTLY like demo: h-[600px] */
  animation: fade-up 0.9s ease-out 0.2s forwards;
  opacity: 0;
}

/* Orbit decorations */
.orbit-deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.orbit-deco-1 {
  width: 640px;
  height: 640px;
  border: 1px solid rgba(168, 85, 247, 0.1);
  animation: spin-slow 40s linear infinite;

  &::before {
    content: '';
    position: absolute;
    inset: -1px;
    border-radius: 50%;
    border: 1px dashed rgba(168, 85, 247, 0.15);
    animation: spin-slow 30s linear infinite reverse;
  }
}

.orbit-deco-2 {
  width: 520px;
  height: 520px;
  border: 1px solid rgba(0, 212, 170, 0.08);
  animation: spin-slow 55s linear infinite reverse;
}

.orbit-deco-3 {
  width: 700px;
  height: 700px;
  border: 1px solid rgba(255, 107, 157, 0.06);
  animation: spin-slow 70s linear infinite;
}

/* CRITICAL: fixed height matching demo exactly */
.gallery-wrapper {
  position: relative;
  width: 100%;
  height: 600px; /* MUST be fixed px like demo: h-[600px] */
  border-radius: 20px;
  overflow: hidden;
  z-index: 1;
}

/* ========== Section 5: AI Magic ========== */
.ai-magic-section {
  padding: 120px 0;
  position: relative;
}

.ai-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  align-items: start;
}

/* Chat preview */
.ai-chat-preview {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--glass-border);
}

.chat-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gradient-aurora);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.chat-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
}

.chat-status {
  font-size: 11px;
  color: var(--color-success-light);
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-success-light);
  }
}

.chat-messages {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-msg {
  display: flex;

  &.chat-user {
    justify-content: flex-end;
  }
  &.chat-ai {
    justify-content: flex-start;
  }
}

.msg-bubble {
  max-width: 85%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
}

.msg-user-bubble {
  background: linear-gradient(135deg, #a855f7, #9333ea);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-ai-bubble {
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid var(--glass-border);
  color: var(--color-text-secondary);
  border-bottom-left-radius: 4px;

  p {
    margin: 0 0 8px;
  }
}

.chat-images {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 12px 0;
}

.chat-thumb {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 8px;
  object-fit: cover;
}

.chat-note {
  font-size: 12px !important;
  color: var(--color-text-tertiary) !important;
  opacity: 0.7;
}

/* Outpainting preview */
.ai-outpaint-preview {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  padding: 32px 24px;
}

.outpaint-label {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px;
}

.outpaint-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0 0 24px;
  line-height: 1.6;
}

.outpaint-compare {
  display: flex;
  align-items: center;
  gap: 16px;
}

.compare-item {
  flex: 1;
}

.compare-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  margin-bottom: 8px;
}

.compare-label-accent {
  color: var(--color-accent-pink);
}

.compare-image {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--glass-border);

  img {
    width: 100%;
    height: auto;
    display: block;
  }
}

.compare-arrow {
  flex-shrink: 0;
  color: var(--color-accent-purple);
  animation: arrow-pulse 2s ease-in-out infinite;
}

@keyframes arrow-pulse {
  0%,
  100% {
    opacity: 0.5;
    transform: translateX(0);
  }
  50% {
    opacity: 1;
    transform: translateX(4px);
  }
}

/* ========== Section 6: Final CTA ========== */
.final-cta-section {
  padding: 160px 0;
  position: relative;
  overflow: hidden;
  text-align: center;
}

.cta-radial-bg {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at 50% 50%, rgba(147, 51, 234, 0.15) 0%, transparent 50%),
    radial-gradient(ellipse at 30% 60%, rgba(244, 114, 182, 0.1) 0%, transparent 40%),
    radial-gradient(ellipse at 70% 40%, rgba(168, 85, 247, 0.08) 0%, transparent 40%);
  pointer-events: none;
}

.cta-content {
  position: relative;
  z-index: 1;
}

.cta-title {
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 56px);
  font-weight: 800;
  color: var(--color-text-primary);
  margin: 0 0 20px;
  line-height: 1.2;
  background: linear-gradient(135deg, #f8fafc 0%, #f472b6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.cta-subtitle {
  font-size: clamp(16px, 2vw, 20px);
  color: var(--color-text-secondary);
  margin: 0 0 48px;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

.cta-buttons {
  display: flex;
  gap: 20px;
  justify-content: center;
}

.cta-final-primary {
  padding: 20px 44px;
  font-size: 18px;
  background: linear-gradient(135deg, #f472b6 0%, #a855f7 50%, #9333ea 100%);
  color: #fff;
  box-shadow: 0 4px 32px rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-4px) scale(1.05);
    box-shadow: 0 12px 48px rgba(244, 114, 182, 0.5), 0 0 32px rgba(168, 85, 247, 0.3);
  }
}

.cta-final-secondary {
  padding: 20px 44px;
  font-size: 18px;
  background: transparent;
  color: var(--color-text-primary);
  border: 2px solid rgba(168, 85, 247, 0.4);

  &:hover {
    transform: translateY(-4px) scale(1.05);
    border-color: rgba(168, 85, 247, 0.7);
    box-shadow: 0 8px 40px rgba(168, 85, 247, 0.2);
    background: rgba(168, 85, 247, 0.08);
  }
}

/* ========== Responsive ========== */
@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .editorial-layout {
    grid-template-columns: 1fr;
    gap: 48px;
  }

  .editorial-text-col {
    animation-name: fade-up;
    animation-delay: 0.1s;
  }

  .editorial-visual-col {
    animation-name: fade-up;
    animation-delay: 0.2s;
  }

  .glass-text-block {
    padding: 36px 28px;
  }

  .quote-mark {
    font-size: 80px;
    top: 16px;
    left: 20px;
  }

  .editorial-heading {
    font-size: clamp(24px, 5vw, 36px);
  }

  .surround-ring-outer {
    width: 340px;
    height: 340px;
  }

  .surround-ring-inner {
    width: 280px;
    height: 280px;
  }

  .ring-dot-1 { margin-top: -170px; }
  .ring-dot-2 { margin-top: 165px; }
  .ring-dot-3 { margin-left: 165px; }
  .ring-dot-4 { margin-left: -168px; }

  .deco-ring-1 {
    width: 200px;
    height: 200px;
  }

  .deco-arc-1 {
    width: 120px;
    height: 120px;
    right: 15%;
  }

  .ai-grid {
    grid-template-columns: 1fr;
  }

  /* Section 4 responsive */
  .universe-content {
    gap: 24px;
  }

  .universe-header {
    padding-top: 20px;
  }

  .universe-dome-wrap {
    height: 500px;
  }

  .gallery-wrapper {
    height: 500px;
  }

  .orbit-deco-1 { width: 480px; height: 480px; }
  .orbit-deco-2 { width: 400px; height: 400px; }
  .orbit-deco-3 { width: 560px; height: 560px; }
}

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

  .hero-cta-group {
    flex-direction: column;
    width: 100%;
    max-width: 320px;
  }

  .cta-btn {
    justify-content: center;
    width: 100%;
  }

  .hero-subtitle {
    margin-bottom: 32px;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .brand-essence-section,
  .experiences-section,
  .visual-universe-section,
  .ai-magic-section,
  .final-cta-section {
    padding: 80px 0;
  }

  .section-split {
    flex-direction: column;
    gap: 40px;
  }

  .section-left {
    flex: none;
    width: 100%;
    text-align: center;
  }

  .split-heading {
    font-size: 36px;
  }

  .split-stats {
    justify-content: center;
  }

  .section-right {
    width: 100%;
  }

  .universe-dome-wrap {
    height: 420px;
  }

  .gallery-wrapper {
    height: 420px;
  }

  .orbit-deco-1 { width: 380px; height: 380px; }
  .orbit-deco-2 { width: 320px; height: 320px; }
  .orbit-deco-3 { width: 460px; height: 460px; }

  .outpaint-compare {
    flex-direction: column;

    .compare-arrow {
      transform: rotate(90deg);
    }
  }

  .cta-buttons {
    flex-direction: column;
    align-items: center;
    max-width: 360px;
    margin: 0 auto;

    .cta-btn {
      width: 100%;
      justify-content: center;
    }
  }

  .scroll-hint {
    bottom: 24px;
  }

  .section-inner {
    padding: 0 16px;
  }
}

@media (max-width: 480px) {
  .hero-title {
    gap: 8px 16px;
  }

  .brand-section {
    margin-bottom: 24px;
  }

  .feature-card {
    padding: 24px 20px;
  }

  .universe-dome-wrap {
    height: 340px;
  }

  .gallery-wrapper {
    height: 340px;
  }

  .orbit-deco-1 { width: 280px; height: 280px; }
  .orbit-deco-2 { width: 240px; height: 240px; }
  .orbit-deco-3 { width: 360px; height: 360px; }

  .chat-images {
    grid-template-columns: repeat(2, 1fr);
  }

  .final-cta-section {
    padding: 80px 0;
  }
}
</style>
