<template>
  <div id="landingPage" ref="root" :class="{ 'is-light': isLight }">
    <!-- ============ Atmosphere ============ -->
    <div class="atmosphere" aria-hidden="true">
      <div class="aurora aurora-a"></div>
      <div class="aurora aurora-b"></div>
      <div class="aurora aurora-c"></div>
      <div class="aurora-grid"></div>
      <div class="grain"></div>
      <div class="vignette"></div>
    </div>

    <div class="progress-rail" aria-hidden="true">
      <span :style="{ transform: `scaleX(${progress})` }"></span>
    </div>

    <!-- ============ 00 · Hero ============ -->
    <section
      id="hero"
      class="section hero"
      :class="{ 'is-in': seen.hero }"
      aria-labelledby="hero-title"
    >
      <div class="inner hero-grid">
        <div class="hero-copy">
          <p class="kicker" data-d="0">
            <span class="kicker-dot" aria-hidden="true"></span>
            <span>VISUAL INTELLIGENCE</span>
            <i aria-hidden="true">/</i>
            <span>视觉灵感宇宙</span>
          </p>

          <h1 id="hero-title" class="hero-title">
            <span class="line" data-d="1">让灵感</span>
            <span class="line line-accent" data-d="2">
              有迹可循
              <svg
                class="line-mark"
                viewBox="0 0 220 12"
                aria-hidden="true"
                preserveAspectRatio="none"
              >
                <path d="M2 9C52 3 168 2 218 6" />
              </svg>
            </span>
          </h1>

          <p class="hero-lede" data-d="3">
            VisionSpace 用颜色、语气与记忆理解你的审美，
            把脑海中那一瞬模糊的画面，变成可以被找到的图像。
          </p>

          <div class="actions" data-d="4">
            <button class="btn btn-primary" @click="router.push('/explore')">
              <span>开始探索</span>
              <ArrowRight :size="18" :stroke-width="1.75" />
            </button>
            <button class="btn btn-ghost" @click="router.push('/agent')">
              <span>对话 SpaceMind</span>
              <ArrowUpRight :size="18" :stroke-width="1.75" />
            </button>
          </div>

          <ul class="hero-tags" data-d="5">
            <li>颜色检索</li>
            <li>语义理解</li>
            <li>空间协作</li>
          </ul>
        </div>

        <div
          ref="heroVisual"
          class="hero-visual"
          aria-hidden="true"
          @pointermove="onPointer"
          @pointerleave="onPointerLeave"
        >
          <div class="aperture">
            <span class="aperture-ring"></span>
            <span class="aperture-glow"></span>
          </div>

          <figure class="plate plate-1">
            <img :src="img(heroPlates[0], 900)" alt="" width="900" height="1200" decoding="async" />
          </figure>
          <figure class="plate plate-2">
            <img
              :src="img(heroPlates[1], 1000)"
              alt=""
              width="1000"
              height="1250"
              decoding="async"
            />
          </figure>
          <figure class="plate plate-3">
            <img :src="img(heroPlates[2], 700)" alt="" width="700" height="700" decoding="async" />
          </figure>

          <span class="plate-caption mono">SAMPLE · 001 — 暮色 / 暖调 / 静默</span>
        </div>
      </div>

      <div class="inner hero-foot">
        <span class="scroll-cue mono">
          <span class="scroll-line" aria-hidden="true"></span>
          向下浏览
        </span>
        <ul class="hero-index">
          <li><b>01</b> 找到</li>
          <li><b>02</b> 理解</li>
          <li><b>03</b> 共享</li>
        </ul>
      </div>
    </section>

    <!-- ============ 01 · 理念 ============ -->
    <section
      id="essence"
      class="section essence"
      :class="{ 'is-in': seen.essence }"
      aria-labelledby="essence-title"
    >
      <div class="inner">
        <header class="section-head">
          <span class="index mono"><b>01</b> — 品牌理念</span>
          <p class="head-note mono">A BRIDGE BETWEEN IMAGINATION &amp; IMAGE</p>
        </header>

        <div class="essence-grid">
          <div class="essence-copy">
            <h2 id="essence-title" class="display" data-d="0">
              AI 与人类<br />
              <span class="display-quiet">创造力的交汇点</span>
            </h2>

            <p class="essence-lead" data-d="1">
              VisionSpace 诞生于一个简单的信念：每个人心中都有一个视觉宇宙，等待被唤醒。
            </p>
            <p class="essence-body" data-d="2">
              我们用 AI 搭建一座桥梁，连接灵感与表达、想象与现实。SpaceMind 不只理解你的语言，
              更在意你的审美——它能在一张图里认出你想要的色彩、光影与情绪。
            </p>
            <p class="essence-body" data-d="3">
              无论是独自收藏，还是与团队共同经营一块画布，创意都在共享中变得更清晰。
            </p>

            <blockquote class="pull-quote" data-d="4">
              每一张图片都是一段故事，<br />每一次搜索都是一次发现。
            </blockquote>
          </div>

          <div class="essence-plates" aria-hidden="true">
            <figure class="essence-plate ep-1">
              <img
                :src="img(essenceImages[0], 1000)"
                alt=""
                width="1000"
                height="1250"
                loading="lazy"
                decoding="async"
              />
              <figcaption class="mono">FORM / 光</figcaption>
            </figure>
            <figure class="essence-plate ep-2">
              <img
                :src="img(essenceImages[1], 1000)"
                alt=""
                width="1000"
                height="750"
                loading="lazy"
                decoding="async"
              />
              <figcaption class="mono">TONE / 色</figcaption>
            </figure>
            <figure class="essence-plate ep-3">
              <img
                :src="img(essenceImages[2], 800)"
                alt=""
                width="800"
                height="800"
                loading="lazy"
                decoding="async"
              />
              <figcaption class="mono">MOOD / 绪</figcaption>
            </figure>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 02 · 核心体验 ============ -->
    <section
      id="experiences"
      class="section experiences"
      :class="{ 'is-in': seen.experiences }"
      aria-labelledby="experiences-title"
    >
      <div class="inner">
        <header class="section-head">
          <span class="index mono"><b>02</b> — 核心体验</span>
          <p class="head-note mono">THREE WAYS TO MEET AN IDEA</p>
        </header>

        <h2 id="experiences-title" class="display display-wide" data-d="0">三种方式，与灵感相遇</h2>

        <div class="bento">
          <!-- 色彩检索 -->
          <article class="card card-color" data-d="1">
            <div class="card-top">
              <span class="card-icon"><Palette :size="20" :stroke-width="1.6" /></span>
              <span class="card-no mono">A</span>
            </div>
            <h3 class="card-title">用颜色，找到对的那一张</h3>
            <p class="card-body">
              不必再堆砌关键词。选一个色块、描述一种氛围，AI 会沿着色谱与情绪找到最贴近的画面。
            </p>
            <div class="swatches" aria-hidden="true">
              <span v-for="c in swatches" :key="c" class="swatch" :style="{ background: c }"></span>
            </div>
            <div class="color-strip" aria-hidden="true">
              <img :src="img(heroPlates[1], 520)" alt="" loading="lazy" decoding="async" />
              <img :src="img(essenceImages[1], 520)" alt="" loading="lazy" decoding="async" />
              <img :src="img(universeImages[5], 520)" alt="" loading="lazy" decoding="async" />
            </div>
          </article>

          <!-- SpaceMind -->
          <article class="card card-agent" data-d="2">
            <div class="card-top">
              <span class="card-icon"><Sparkles :size="20" :stroke-width="1.6" /></span>
              <span class="card-no mono">B</span>
            </div>
            <h3 class="card-title">说人话，也能被听懂</h3>
            <p class="card-body">把模糊的感觉交给 SpaceMind，它会追问、修正，然后给出答案。</p>
            <div class="agent-mini" aria-hidden="true">
              <div class="mini-bubble mini-user">想要更安静一点的黄昏</div>
              <div class="mini-bubble mini-ai">
                <span class="mini-dots"><i></i><i></i><i></i></span>
                正在按色温与留白重新排序
              </div>
              <div class="mini-results">
                <img :src="img(universeImages[2], 300)" alt="" loading="lazy" decoding="async" />
                <img :src="img(universeImages[4], 300)" alt="" loading="lazy" decoding="async" />
              </div>
            </div>
          </article>

          <!-- 协作 -->
          <article class="card card-collab" data-d="3">
            <div class="card-top">
              <span class="card-icon"><Users :size="20" :stroke-width="1.6" /></span>
              <span class="card-no mono">C</span>
            </div>
            <h3 class="card-title">一块画布，一起完成</h3>
            <p class="card-body">邀请成员、划分权限，灵感在同一个空间里实时生长。</p>
            <div class="collab" aria-hidden="true">
              <div class="collab-people">
                <span class="avatar av-1">y</span>
                <span class="avatar av-2">周</span>
                <span class="avatar av-3">can</span>
                <span class="people-note mono">3 人在画布中</span>
              </div>
              <div class="collab-board">
                <figure class="board-tile-wrap tile-1">
                  <img :src="img(universeImages[0], 360)" alt="" loading="lazy" decoding="async" />
                </figure>
                <figure class="board-tile-wrap tile-2">
                  <img :src="img(essenceImages[2], 360)" alt="" loading="lazy" decoding="async" />
                </figure>
                <span class="collab-cursor cursor-1">正在整理 · 暮色</span>
                <span class="collab-cursor cursor-2">已加入</span>
              </div>
            </div>
          </article>

          <!-- 扩图 -->
          <article class="card card-outpaint" data-d="4">
            <div class="card-top">
              <span class="card-icon"><Maximize2 :size="20" :stroke-width="1.6" /></span>
              <span class="card-no mono">D</span>
            </div>
            <h3 class="card-title">让画面，继续延伸</h3>
            <p class="card-body">上传任意图片，AI 沿着原有光线与风格扩展边界，接缝不留痕。</p>
            <div class="outpaint-mini" aria-hidden="true">
              <div class="outpaint-frame">
                <img :src="img(outpaintImage, 1100)" alt="" loading="lazy" decoding="async" />
                <span class="outpaint-core"></span>
                <span class="outpaint-tag mono">AI EXPAND +62%</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 03 · 灵感宇宙 ============ -->
    <section
      id="universe"
      class="section universe"
      :class="{ 'is-in': seen.universe }"
      aria-labelledby="universe-title"
    >
      <div class="inner universe-head">
        <span class="index mono"><b>03</b> — 灵感宇宙</span>
        <h2 id="universe-title" class="display" data-d="0">来自全球创作者的<br />灵感时刻</h2>
        <p class="universe-hint mono" data-d="1">
          <MousePointer2 :size="14" :stroke-width="1.6" />
          拖拽穹顶，进入影像的深处
        </p>
      </div>

      <div class="dome-wrap">
        <DomeGallery
          :images="domeImages"
          :fit="0.78"
          fit-basis="auto"
          :min-radius="200"
          :max-radius="Infinity"
          :segments="34"
          :drag-sensitivity="20"
          :enlarge-transition-ms="300"
          :grayscale="true"
          :overlay-blur-color="domeOverlay"
          image-border-radius="3px"
          opened-image-width="260px"
          opened-image-height="360px"
        />
      </div>
    </section>

    <!-- ============ 04 · SpaceMind ============ -->
    <section
      id="spacemind"
      class="section spacemind"
      :class="{ 'is-in': seen.spacemind }"
      aria-labelledby="spacemind-title"
    >
      <div class="inner">
        <header class="section-head">
          <span class="index mono"><b>04</b> — SPACEMIND</span>
          <p class="head-note mono">IT READS THE ROOM, NOT JUST THE PROMPT</p>
        </header>

        <h2 id="spacemind-title" class="display display-wide" data-d="0">它能看懂你的语气</h2>
        <p class="section-lede" data-d="1">
          一段自然语言，就是一次完整的视觉检索。SpaceMind 会拆解你的意图，
          在秒级给出可用的结果，并记得你上一次的偏好。
        </p>

        <div class="stage">
          <!-- Chat -->
          <div class="panel panel-chat" data-d="2">
            <div class="panel-bar">
              <span class="panel-name">
                <span class="live-dot" aria-hidden="true"></span>
                SpaceMind
              </span>
              <span class="panel-meta mono">SESSION 04</span>
            </div>

            <div class="thread">
              <div class="msg msg-user">
                <p>帮我找一些黄昏色调、安静一点的画面，最好有水面</p>
              </div>
              <div class="msg msg-ai">
                <p class="msg-lead">
                  按暖橙色调与低饱和留白筛选，找到 4 张。它们的光线都落在画面下部，情绪更安静：
                </p>
                <div class="msg-grid">
                  <figure v-for="(pic, i) in chatImages" :key="i">
                    <img :src="img(pic.id, 420)" :alt="pic.alt" loading="lazy" decoding="async" />
                    <figcaption class="mono">{{ pic.tag }}</figcaption>
                  </figure>
                </div>
                <p class="msg-note">已按你的空间「暮色收集」归档，可随时继续追问。</p>
              </div>
            </div>

            <div class="composer" aria-hidden="true">
              <span>继续描述你想要的画面…</span>
              <span class="composer-send"><ArrowRight :size="15" :stroke-width="1.8" /></span>
            </div>
          </div>

          <!-- Outpaint -->
          <div class="panel panel-outpaint" data-d="3">
            <div class="panel-bar">
              <span class="panel-name">AI 扩图</span>
              <span class="panel-meta mono">OUTPAINT</span>
            </div>

            <div class="outpaint-stage">
              <img
                :src="img(outpaintImage, 1400)"
                alt="AI 扩图演示：虚线框内为原始画面"
                loading="lazy"
                decoding="async"
              />
              <span class="outpaint-core">
                <span class="core-label mono">原始画面</span>
              </span>
              <span class="outpaint-badge mono">AI EXTEND +62%</span>
            </div>

            <p class="compare-note">
              框内是原始画面，虚线之外为 AI 延展的边界——光线、颗粒与景深保持一致。
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 05 · CTA ============ -->
    <section
      id="cta"
      class="section cta"
      :class="{ 'is-in': seen.cta }"
      aria-labelledby="cta-title"
    >
      <div class="cta-glow" aria-hidden="true"></div>
      <div class="inner cta-inner">
        <span class="index mono" data-d="0"><b>05</b> — 开始</span>
        <h2 id="cta-title" class="cta-title" data-d="1">
          下一张打动你的图，<br />就在一句话之外。
        </h2>
        <p class="cta-sub" data-d="2">加入 VisionSpace，让每一次凝视都有回响。</p>
        <div class="actions actions-center" data-d="3">
          <button class="btn btn-primary" @click="router.push('/explore')">
            <span>立即探索</span>
            <ArrowRight :size="18" :stroke-width="1.75" />
          </button>
          <button class="btn btn-ghost" @click="router.push('/agent')">
            <span>对话 SpaceMind</span>
            <ArrowUpRight :size="18" :stroke-width="1.75" />
          </button>
        </div>
      </div>
    </section>

    <section id="footer" class="section footer-wrap" :class="{ 'is-in': seen.footer }">
      <GlobalFooter />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ArrowUpRight,
  Maximize2,
  MousePointer2,
  Palette,
  Sparkles,
  Users,
} from 'lucide-vue-next'
import DomeGallery from '@/components/vue-bits/components/DomeGallery.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const { currentTheme, applyTheme } = useTheme()
const root = ref<HTMLElement | null>(null)
const heroVisual = ref<HTMLElement | null>(null)

/* 落地页两套主题共用一套版式，只换纸面与墨色 */
const isLight = computed(() => currentTheme.value === 'pop')
const domeOverlay = computed(() => (isLight.value ? '#faf7ff' : '#05060c'))

const seen = ref<Record<string, boolean>>({})
const progress = ref(0)

let observer: IntersectionObserver | null = null
let reduceMotion = false
let scrollRaf = 0

const img = (id: string, width = 1200) =>
  `https://images.unsplash.com/${id}?q=80&w=${width}&auto=format&fit=crop`

/* ---------- imagery ---------- */
const heroPlates = [
  'photo-1519681393784-d120267933ba',
  'photo-1475924156734-496f6cac6ec1',
  'photo-1465056836041-7f43ac27dcb5',
]

const essenceImages = [
  'photo-1506905925346-21bda4d32df4',
  'photo-1444927714506-8492d94b4e3d',
  'photo-1483728642387-6c3bdd6c93e5',
]

const universeImages = [
  'photo-1519681393784-d120267933ba',
  'photo-1475924156734-496f6cac6ec1',
  'photo-1506905925346-21bda4d32df4',
  'photo-1469474968028-56623f02e42e',
  'photo-1444927714506-8492d94b4e3d',
  'photo-1464822759023-fed622ff2c3b',
  'photo-1483728642387-6c3bdd6c93e5',
  'photo-1465056836041-7f43ac27dcb5',
]

const domeImages = universeImages.map((id) => img(id, 800))

const chatImages = [
  { id: 'photo-1475924156734-496f6cac6ec1', alt: '暖色黄昏的水面', tag: 'WARM · 01' },
  { id: 'photo-1469474968028-56623f02e42e', alt: '山间的低饱和光线', tag: 'CALM · 02' },
  { id: 'photo-1506905925346-21bda4d32df4', alt: '安静的远山', tag: 'QUIET · 03' },
  { id: 'photo-1519681393784-d120267933ba', alt: '夜色中的雪峰', tag: 'DUSK · 04' },
]

const outpaintImage = 'photo-1469474968028-56623f02e42e'

const swatches = ['#e8a06a', '#c96f4a', '#8c5c7e', '#4b5f8a', '#2f3a4a']

/* ---------- scroll / reveal ---------- */
const onScroll = () => {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    const max = document.documentElement.scrollHeight - window.innerHeight
    progress.value = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0
  })
}

const onPointer = (event: PointerEvent) => {
  if (reduceMotion || !heroVisual.value) return
  const rect = heroVisual.value.getBoundingClientRect()
  const mx = (event.clientX - rect.left) / rect.width - 0.5
  const my = (event.clientY - rect.top) / rect.height - 0.5
  heroVisual.value.style.setProperty('--mx', mx.toFixed(4))
  heroVisual.value.style.setProperty('--my', my.toFixed(4))
}

const onPointerLeave = () => {
  heroVisual.value?.style.setProperty('--mx', '0')
  heroVisual.value?.style.setProperty('--my', '0')
}

onMounted(async () => {
  // 没手动选过主题的访客，落地页以深空极光开场；选过就尊重其选择（含页头开关的实时切换）
  if (!localStorage.getItem('ycloud-theme')) {
    applyTheme('aurora', false)
  }

  reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  await nextTick()
  seen.value.hero = true

  if (root.value) {
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            seen.value[entry.target.id] = true
            observer?.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.15, rootMargin: '0px 0px -8% 0px' },
    )
    root.value.querySelectorAll('section[id]').forEach((section) => observer?.observe(section))
  }

  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll)
  onScroll()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
})
</script>

<style scoped lang="less">
/* ===================================================================
   VisionSpace — Landing (award tier)
   深空极光 × 编辑式排版：衬线大标题 + 单色系统 + 克制动效
   =================================================================== */

#landingPage {
  /* 页面自有调色，不依赖全局主题，保证任何主题下都成立 */
  --lp-bg: #05060c;
  --lp-bg-raised: #090b15;
  --lp-ink: #f4f1ea;
  --lp-ink-2: rgba(244, 241, 234, 0.72);
  --lp-ink-3: rgba(244, 241, 234, 0.52);
  --lp-line: rgba(244, 241, 234, 0.1);
  --lp-line-2: rgba(244, 241, 234, 0.2);
  --lp-violet: #b7a2ff;
  --lp-violet-deep: #6f4bd8;
  --lp-mint: #86e3c8;

  --lp-serif: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --lp-sans: 'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --lp-mono: 'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace;
  --lp-ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  min-height: 100vh;
  background: var(--lp-bg);
  color: var(--lp-ink);
  font-family: var(--lp-sans);
  font-weight: 400;
  overflow-x: clip;
  isolation: isolate;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

::selection {
  background: rgba(183, 162, 255, 0.28);
  color: #fff;
}

/* ============ Atmosphere ============ */
.atmosphere {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(120% 80% at 50% -10%, #0b0d1c 0%, var(--lp-bg) 55%, #03040a 100%);
}

.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  animation: aurora-drift 26s ease-in-out infinite alternate;
}

.aurora-a {
  width: 62vw;
  height: 46vw;
  top: -18vw;
  right: -12vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.34) 0%, transparent 68%);
}

.aurora-b {
  width: 46vw;
  height: 42vw;
  bottom: 6vh;
  left: -16vw;
  background: radial-gradient(circle, rgba(74, 122, 168, 0.2) 0%, transparent 70%);
  animation-delay: -9s;
}

.aurora-c {
  width: 30vw;
  height: 30vw;
  top: 48%;
  right: 6%;
  background: radial-gradient(circle, rgba(134, 227, 200, 0.1) 0%, transparent 70%);
  animation-delay: -17s;
}

@keyframes aurora-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-3vw, 3vh, 0) scale(1.08);
  }
}

.aurora-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(244, 241, 234, 0.028) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(244, 241, 234, 0.028) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 0%, transparent 78%);
}

.grain {
  position: absolute;
  inset: -40%;
  opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  animation: grain-shift 8s steps(6) infinite;
}

@keyframes grain-shift {
  0%,
  100% {
    transform: translate(0, 0);
  }
  20% {
    transform: translate(-3%, 2%);
  }
  40% {
    transform: translate(2%, -3%);
  }
  60% {
    transform: translate(-2%, -2%);
  }
  80% {
    transform: translate(3%, 1%);
  }
}

.vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(3, 4, 10, 0.7) 100%
  );
}

/* ============ Progress ============ */
/* 页头吸顶后占据顶部 72px，进度线贴着页头下沿走 */
.progress-rail {
  position: fixed;
  top: 72px;
  left: 0;
  right: 0;
  height: 1px;
  z-index: 40;
  background: transparent;

  span {
    display: block;
    height: 100%;
    transform-origin: 0 50%;
    background: linear-gradient(90deg, transparent, var(--lp-violet));
  }
}

/* ============ Editorial image treatment ============ */
/* 所有照片统一压暗、降饱和并加一层冷色，避免与深空底色打架 */
.plate img,
.essence-plate img,
.color-strip img,
.mini-results img,
.msg-grid img,
.outpaint-frame img,
.outpaint-stage > img,
.board-tile-wrap img {
  filter: saturate(0.36) contrast(1.08) brightness(0.8);
  transition: filter 0.9s var(--lp-ease);
}

.plate::after,
.essence-plate::after,
.msg-grid figure::after,
.outpaint-frame::after,
.board-tile-wrap::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(96, 74, 168, 0.22) 0%, rgba(5, 6, 12, 0.32) 100%);
}

.msg-grid figure {
  position: relative;
}

/* ============ Shared layout ============ */
.section {
  position: relative;
  z-index: 1;
  padding: clamp(88px, 12vh, 156px) clamp(20px, 5vw, 64px);
}

.inner {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
}

.mono {
  font-family: var(--lp-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--lp-ink-3);
}

.index {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: var(--lp-mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--lp-ink-3);

  b {
    font-weight: 400;
    color: var(--lp-violet);
  }
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  margin-bottom: clamp(36px, 6vh, 64px);
  border-bottom: 1px solid var(--lp-line);
}

.head-note {
  text-align: right;
  letter-spacing: 0.14em;
}

.display {
  margin: 0;
  font-family: var(--lp-serif);
  font-size: clamp(30px, 3.9vw, 54px);
  font-weight: 600;
  line-height: 1.24;
  letter-spacing: -0.01em;
  color: var(--lp-ink);
}

.display-wide {
  margin-bottom: clamp(40px, 6vh, 72px);
}

.display-quiet {
  color: var(--lp-ink-2);
}

.section-lede {
  max-width: 52ch;
  margin: -24px 0 clamp(40px, 6vh, 64px);
  font-size: clamp(15px, 1.25vw, 18px);
  line-height: 1.95;
  color: var(--lp-ink-2);
}

/* 中文断行避免末行只剩一个孤字 */
.hero-lede,
.section-lede,
.essence-lead,
.essence-body,
.card-body,
.msg-lead,
.compare-note,
.cta-sub {
  text-wrap: pretty;
}

/* ============ Reveal ============ */
.section [data-d] {
  opacity: 0;
  transform: translateY(22px);
  transition:
    opacity 0.9s var(--lp-ease),
    transform 0.9s var(--lp-ease);
  transition-delay: calc(var(--d, 0) * 80ms);
}

.section.is-in [data-d] {
  opacity: 1;
  transform: none;
}

.section [data-d] {
  --d: 0;
}
.section [data-d='1'] {
  --d: 1;
}
.section [data-d='2'] {
  --d: 2;
}
.section [data-d='3'] {
  --d: 3;
}
.section [data-d='4'] {
  --d: 4;
}
.section [data-d='5'] {
  --d: 5;
}
.section [data-d='6'] {
  --d: 6;
}

/* ============ Buttons ============ */
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 6px;
}

.actions-center {
  justify-content: center;
}

.btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 15px 28px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: none;
  font-family: var(--lp-sans);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
  cursor: pointer;
  transition:
    transform 0.4s var(--lp-ease),
    background-color 0.4s var(--lp-ease),
    border-color 0.4s var(--lp-ease),
    color 0.4s var(--lp-ease),
    box-shadow 0.6s var(--lp-ease);

  svg {
    transition: transform 0.45s var(--lp-ease);
  }

  &:hover svg {
    transform: translateX(4px);
  }

  &:active {
    transform: scale(0.985);
  }

  &:focus-visible {
    outline: 2px solid var(--lp-violet);
    outline-offset: 3px;
  }
}

.btn-primary {
  color: #0b0714;
  background: linear-gradient(120deg, #efe9ff 0%, #c9b6ff 55%, #a98bff 100%);
  box-shadow: 0 10px 40px -12px rgba(167, 139, 250, 0.55);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 18px 52px -14px rgba(167, 139, 250, 0.7);
  }
}

.btn-ghost {
  color: var(--lp-ink);
  border-color: var(--lp-line-2);
  background: rgba(244, 241, 234, 0.02);

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(244, 241, 234, 0.42);
    background: rgba(244, 241, 234, 0.05);
  }
}

/* ============ Hero ============ */
.hero {
  min-height: calc(100svh - 72px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-top: clamp(48px, 8vh, 96px);
  padding-bottom: clamp(32px, 5vh, 64px);
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  gap: clamp(32px, 5vw, 80px);
  align-items: center;
  flex: 1;
}

.hero-copy {
  position: relative;
  z-index: 2;
  max-width: 640px;
}

.kicker {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 clamp(24px, 4vh, 40px);
  font-family: var(--lp-mono);
  font-size: 10.5px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--lp-ink-3);

  i {
    font-style: normal;
    color: var(--lp-line-2);
  }
}

.kicker-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lp-mint);
  box-shadow: 0 0 12px rgba(134, 227, 200, 0.8);
  animation: dot-pulse 3.2s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.hero-title {
  margin: 0;
  font-family: var(--lp-serif);
  font-size: clamp(52px, 7.6vw, 112px);
  font-weight: 600;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--lp-ink);
}

.line {
  display: block;
}

.line-accent {
  position: relative;
  display: inline-block;
  color: var(--lp-violet);
  padding-bottom: 0.08em;
}

.line-mark {
  position: absolute;
  left: 0;
  bottom: -0.02em;
  width: 100%;
  height: 10px;
  overflow: visible;

  path {
    fill: none;
    stroke: var(--lp-mint);
    stroke-width: 1.2;
    stroke-linecap: round;
    opacity: 0.6;
    stroke-dasharray: 240;
    stroke-dashoffset: 240;
    transition: stroke-dashoffset 1.4s var(--lp-ease) 0.55s;
  }
}

.is-in .line-mark path {
  stroke-dashoffset: 0;
}

.hero-lede {
  max-width: 44ch;
  margin: clamp(24px, 4vh, 36px) 0 0;
  font-size: clamp(15px, 1.35vw, 18px);
  line-height: 1.95;
  color: var(--lp-ink-2);
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: clamp(28px, 4vh, 40px) 0 0;
  padding: 0;
  list-style: none;

  li {
    padding: 7px 14px;
    border: 1px solid var(--lp-line);
    border-radius: 999px;
    font-family: var(--lp-mono);
    font-size: 10.5px;
    letter-spacing: 0.1em;
    color: var(--lp-ink-3);
    transition:
      border-color 0.4s var(--lp-ease),
      color 0.4s var(--lp-ease);

    &:hover {
      border-color: var(--lp-line-2);
      color: var(--lp-ink-2);
    }
  }
}

/* ---- Hero visual ---- */
.hero-visual {
  --mx: 0;
  --my: 0;
  position: relative;
  align-self: center;
  aspect-ratio: 1 / 1.06;
  width: 100%;
  max-height: 620px;
}

.aperture {
  position: absolute;
  inset: 8%;
  border-radius: 50%;
  border: 1px solid rgba(183, 162, 255, 0.16);
  overflow: hidden;
  box-shadow: inset 0 0 80px rgba(111, 75, 216, 0.12);
}

.aperture-ring {
  position: absolute;
  inset: -1px;
  border-radius: 50%;
  border: 1px dashed rgba(183, 162, 255, 0.3);
  animation: spin 90s linear infinite;
}

.aperture-glow {
  position: absolute;
  inset: 26%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.35) 0%, transparent 70%);
  filter: blur(24px);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.plate {
  position: absolute;
  margin: 0;
  overflow: hidden;
  border-radius: 3px;
  background: var(--lp-bg-raised);
  box-shadow:
    0 40px 90px -30px rgba(0, 0, 0, 0.9),
    0 0 0 1px rgba(244, 241, 234, 0.06);
  transition: transform 0.9s var(--lp-ease);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 1.2s var(--lp-ease);
  }

  &:hover img {
    transform: scale(1.04);
  }
}

.plate-1 {
  width: 46%;
  aspect-ratio: 3 / 4;
  top: 7%;
  left: 2%;
  transform: translate3d(calc(var(--mx) * 16px), calc(var(--my) * 16px), 0);
}

.plate-2 {
  width: 50%;
  aspect-ratio: 4 / 5;
  top: 24%;
  right: 0;
  transform: translate3d(calc(var(--mx) * -22px), calc(var(--my) * -22px), 0);
}

.plate-3 {
  width: 32%;
  aspect-ratio: 1;
  bottom: 3%;
  left: 27%;
  transform: translate3d(calc(var(--mx) * 28px), calc(var(--my) * 28px), 0);
}

.plate-caption {
  position: absolute;
  right: 0;
  bottom: -2px;
  font-size: 9.5px;
  letter-spacing: 0.18em;
  color: var(--lp-ink-3);
}

/* ---- Hero foot ---- */
.hero-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: clamp(40px, 7vh, 80px);
  padding-top: 20px;
  border-top: 1px solid var(--lp-line);
}

.scroll-cue {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--lp-ink-3);
}

.scroll-line {
  display: block;
  width: 44px;
  height: 1px;
  background: linear-gradient(90deg, var(--lp-violet), transparent);
  transform-origin: 0 50%;
  animation: scroll-pulse 2.6s var(--lp-ease) infinite;
}

@keyframes scroll-pulse {
  0%,
  100% {
    transform: scaleX(0.35);
    opacity: 0.4;
  }
  50% {
    transform: scaleX(1);
    opacity: 1;
  }
}

.hero-index {
  display: flex;
  gap: clamp(16px, 3vw, 40px);
  margin: 0;
  padding: 0;
  list-style: none;
  font-family: var(--lp-mono);
  font-size: 10.5px;
  letter-spacing: 0.14em;
  color: var(--lp-ink-3);

  b {
    margin-right: 6px;
    font-weight: 400;
    color: var(--lp-violet);
  }
}

/* ============ Essence ============ */
.essence-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr);
  gap: clamp(40px, 7vw, 104px);
  align-items: center;
}

.essence-copy > * {
  max-width: 46ch;
}

.essence-lead {
  margin: clamp(28px, 4vh, 40px) 0 22px;
  font-size: clamp(17px, 1.45vw, 20px);
  line-height: 1.85;
  color: var(--lp-ink);
}

.essence-body {
  margin: 0 0 18px;
  font-size: 15px;
  line-height: 1.95;
  color: var(--lp-ink-2);
}

.pull-quote {
  margin: clamp(32px, 5vh, 52px) 0 0;
  padding-left: 22px;
  border-left: 1px solid var(--lp-violet);
  font-family: var(--lp-serif);
  font-size: clamp(17px, 1.5vw, 21px);
  line-height: 1.9;
  color: var(--lp-ink);
}

.essence-plates {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.essence-plate {
  position: relative;
  margin: 0;
  overflow: hidden;
  border-radius: 2px;
  background: var(--lp-bg-raised);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 1.4s var(--lp-ease);
  }

  &:hover img {
    transform: scale(1.045);
    filter: saturate(0.62) contrast(1.04) brightness(0.9);
  }

  figcaption {
    position: absolute;
    left: 12px;
    bottom: 10px;
    padding: 3px 7px;
    border: 1px solid rgba(244, 241, 234, 0.14);
    border-radius: 2px;
    background: rgba(5, 6, 12, 0.48);
    backdrop-filter: blur(6px);
    font-size: 9px;
    letter-spacing: 0.2em;
    color: rgba(244, 241, 234, 0.9);
  }
}

.ep-1 {
  aspect-ratio: 3 / 4;
  grid-row: span 2;
}

.ep-2 {
  aspect-ratio: 4 / 3;
}

.ep-3 {
  aspect-ratio: 1;
  margin-top: 4px;
}

/* ============ Experiences / Bento ============ */
.bento {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 18px;
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: clamp(24px, 3vw, 34px);
  border: 1px solid var(--lp-line);
  border-radius: 4px;
  background: linear-gradient(180deg, rgba(244, 241, 234, 0.045), rgba(244, 241, 234, 0.012));
  overflow: hidden;
  transition:
    border-color 0.5s var(--lp-ease),
    background-color 0.5s var(--lp-ease),
    transform 0.6s var(--lp-ease);

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(120% 90% at 50% 0%, rgba(183, 162, 255, 0.09), transparent 60%);
    opacity: 0;
    transition: opacity 0.6s var(--lp-ease);
    pointer-events: none;
  }

  &:hover {
    border-color: var(--lp-line-2);
    transform: translateY(-4px);

    &::after {
      opacity: 1;
    }
  }
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--lp-line);
  border-radius: 50%;
  color: var(--lp-violet);
}

.card-no {
  font-size: 10px;
  color: var(--lp-ink-3);
}

.card-title {
  margin: 0 0 10px;
  font-family: var(--lp-serif);
  font-size: clamp(19px, 1.7vw, 24px);
  font-weight: 600;
  line-height: 1.4;
  color: var(--lp-ink);
}

.card-body {
  margin: 0 0 24px;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--lp-ink-2);
}

.card-color {
  grid-column: span 4;
}

.card-agent {
  grid-column: span 2;
}

.card-collab {
  grid-column: span 2;
}

.card-outpaint {
  grid-column: span 4;
}

/* swatches + strip */
.swatches {
  display: flex;
  gap: 8px;
  margin-top: auto;
  margin-bottom: 18px;
}

.swatch {
  height: 34px;
  flex: 1;
  border-radius: 2px;
  transition:
    transform 0.45s var(--lp-ease),
    flex 0.55s var(--lp-ease);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);

  &:hover {
    transform: translateY(-4px);
    flex: 1.7;
  }
}

.color-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;

  img {
    width: 100%;
    aspect-ratio: 4 / 3;
    object-fit: cover;
    border-radius: 2px;
    transition: filter 0.5s var(--lp-ease);
  }
}

.card-color:hover .color-strip img {
  filter: saturate(0.66) contrast(1.05) brightness(0.9);
}

/* agent mini */
.agent-mini {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: auto;
}

.mini-bubble {
  max-width: 92%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
}

.mini-user {
  align-self: flex-end;
  color: #120a22;
  background: linear-gradient(120deg, #e6dcff, #bfa9ff);
  border-bottom-right-radius: 3px;
}

.mini-ai {
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lp-ink-2);
  background: rgba(244, 241, 234, 0.05);
  border: 1px solid var(--lp-line);
  border-bottom-left-radius: 3px;
}

.mini-dots {
  display: inline-flex;
  gap: 3px;

  i {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--lp-mint);
    animation: dot-bounce 1.2s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }
    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes dot-bounce {
  0%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.mini-results {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;

  img {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: 2px;
  }
}

/* collaboration */
.collab {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
  margin-top: 20px;
}

.collab-people {
  display: flex;
  align-items: center;
  padding-left: 8px;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-left: -8px;
  border-radius: 50%;
  border: 1px solid rgba(244, 241, 234, 0.24);
  background: #14101f;
  font-size: 12px;
  color: var(--lp-ink);

  &:first-child {
    margin-left: 0;
  }
}

.av-2 {
  background: #1a1428;
}

.av-3 {
  font-size: 9px;
}

.people-note {
  margin-left: 12px;
  font-size: 9.5px;
  letter-spacing: 0.1em;
}

.collab-board {
  position: relative;
  flex: 1;
  min-height: 190px;
  border: 1px solid var(--lp-line);
  border-radius: 3px;
  background-color: rgba(244, 241, 234, 0.015);
  background-image: radial-gradient(circle at 1px 1px, rgba(244, 241, 234, 0.1) 1px, transparent 0);
  background-size: 16px 16px;
  overflow: hidden;
}

.board-tile-wrap {
  position: absolute;
  margin: 0;
  border-radius: 2px;
  overflow: hidden;
  box-shadow: 0 18px 40px -18px rgba(0, 0, 0, 0.85);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.tile-1 {
  left: 6%;
  top: 12%;
  width: 46%;
  aspect-ratio: 4 / 3;
  transform: rotate(-2deg);
}

.tile-2 {
  right: 6%;
  bottom: 14%;
  width: 38%;
  aspect-ratio: 1;
  transform: rotate(1.6deg);
}

.collab-cursor {
  position: absolute;
  padding: 4px 9px;
  border-radius: 999px;
  font-family: var(--lp-mono);
  font-size: 9.5px;
  letter-spacing: 0.06em;
  white-space: nowrap;

  &::before {
    content: '';
    position: absolute;
    top: -4px;
    left: -4px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }
}

.cursor-1 {
  right: 8%;
  top: 8%;
  color: var(--lp-violet);
  background: rgba(183, 162, 255, 0.14);
}

.cursor-2 {
  left: 8%;
  bottom: 10%;
  color: var(--lp-mint);
  background: rgba(134, 227, 200, 0.12);
}

/* outpaint mini */
.outpaint-mini {
  position: relative;
  margin-top: auto;
}

.outpaint-frame {
  position: relative;
  overflow: hidden;
  border-radius: 2px;
  aspect-ratio: 16 / 7;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.outpaint-core {
  position: absolute;
  inset: 16% 26%;
  border: 1px dashed rgba(134, 227, 200, 0.85);
  box-shadow: 0 0 0 9999px rgba(4, 5, 11, 0.52);
}

.outpaint-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  padding: 4px 9px;
  border: 1px solid rgba(134, 227, 200, 0.35);
  border-radius: 999px;
  background: rgba(5, 6, 12, 0.58);
  backdrop-filter: blur(6px);
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--lp-mint);
  pointer-events: none;
}

/* ============ Universe ============ */
.universe {
  padding-left: 0;
  padding-right: 0;
}

.universe-head {
  padding: 0 clamp(20px, 5vw, 64px);
  margin-bottom: clamp(28px, 4vh, 44px);
}

.universe-head .display {
  margin-top: 18px;
}

.universe-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 18px 0 0;
  color: var(--lp-ink-3);
  letter-spacing: 0.1em;
}

.dome-wrap {
  position: relative;
  height: clamp(420px, 62vh, 660px);
  width: 100%;
  -webkit-mask-image: linear-gradient(
    180deg,
    transparent 0,
    #000 84px,
    #000 calc(100% - 48px),
    transparent 100%
  );
  mask-image: linear-gradient(
    180deg,
    transparent 0,
    #000 84px,
    #000 calc(100% - 48px),
    transparent 100%
  );
}

/* ============ SpaceMind stage ============ */
.stage {
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(0, 0.96fr);
  gap: 18px;
  align-items: stretch;
}

.panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--lp-line);
  border-radius: 4px;
  background: linear-gradient(180deg, rgba(244, 241, 234, 0.04), rgba(244, 241, 234, 0.01));
  overflow: hidden;
}

.panel-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--lp-line);
}

.panel-name {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  font-weight: 500;
  color: var(--lp-ink);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lp-mint);
  box-shadow: 0 0 10px rgba(134, 227, 200, 0.8);
}

.panel-meta {
  font-size: 9.5px;
  letter-spacing: 0.18em;
  color: var(--lp-ink-3);
}

.thread {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: clamp(18px, 2.4vw, 28px);
  flex: 1;
}

.msg {
  max-width: 88%;
  font-size: 13.5px;
  line-height: 1.75;

  p {
    margin: 0;
  }
}

.msg-user {
  align-self: flex-end;
  padding: 12px 16px;
  border-radius: 12px 12px 3px 12px;
  color: #120a22;
  background: linear-gradient(120deg, #e9e0ff, #bda6ff);
}

.msg-ai {
  align-self: flex-start;
  padding: 16px 18px;
  border: 1px solid var(--lp-line);
  border-radius: 12px 12px 12px 3px;
  background: rgba(244, 241, 234, 0.035);
  color: var(--lp-ink-2);
}

.msg-lead {
  margin-bottom: 14px !important;
}

.msg-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 7px;
  margin-bottom: 12px;

  figure {
    margin: 0;
  }

  img {
    display: block;
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: 2px;
    transition: filter 0.45s var(--lp-ease);
  }

  figure:hover img {
    filter: saturate(0.66) contrast(1.05) brightness(0.9);
  }

  figcaption {
    margin-top: 5px;
    font-size: 8px;
    letter-spacing: 0.12em;
    color: var(--lp-ink-3);
  }
}

.msg-note {
  font-size: 11.5px !important;
  color: var(--lp-ink-3) !important;
}

.composer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 16px 16px;
  padding: 12px 12px 12px 16px;
  border: 1px solid var(--lp-line);
  border-radius: 999px;
  font-size: 12.5px;
  color: var(--lp-ink-3);
}

.composer-send {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: #0b0714;
  background: var(--lp-violet);
}

/* outpaint panel */
.panel-outpaint {
  justify-content: space-between;
}

.outpaint-stage {
  position: relative;
  margin: clamp(18px, 2.4vw, 28px) clamp(18px, 2.4vw, 28px) 0;
  overflow: hidden;
  border-radius: 2px;
  border: 1px solid var(--lp-line);

  > img {
    display: block;
    width: 100%;
    aspect-ratio: 16 / 10;
    object-fit: cover;
    object-position: 50% 48%;
  }
}

.outpaint-stage .outpaint-core {
  inset: 12% 24%;
  z-index: 2;
}

.core-label {
  position: absolute;
  left: 10px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 2px;
  background: rgba(5, 6, 12, 0.55);
  backdrop-filter: blur(4px);
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--lp-mint);
}

.outpaint-badge {
  position: absolute;
  top: 12px;
  right: 14px;
  z-index: 3;
  padding: 5px 10px;
  border: 1px solid rgba(134, 227, 200, 0.35);
  border-radius: 999px;
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--lp-mint);
  background: rgba(5, 6, 12, 0.55);
  backdrop-filter: blur(6px);
}

.compare-note {
  margin: 0;
  padding: 16px clamp(18px, 2.4vw, 28px) clamp(20px, 2.6vw, 30px);
  font-size: 11.5px;
  line-height: 1.8;
  color: var(--lp-ink-3);
}

/* ============ CTA ============ */
.cta {
  overflow: hidden;
  padding-top: clamp(84px, 13vh, 176px);
  padding-bottom: clamp(110px, 18vh, 220px);
  text-align: center;
}

.cta-glow {
  position: absolute;
  left: 50%;
  top: 50%;
  width: min(900px, 110vw);
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(111, 75, 216, 0.24) 0%, transparent 62%);
  filter: blur(20px);
  pointer-events: none;
}

.cta-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cta-title {
  margin: clamp(22px, 3vh, 32px) 0 22px;
  font-family: var(--lp-serif);
  font-size: clamp(32px, 5vw, 68px);
  font-weight: 600;
  line-height: 1.28;
  letter-spacing: -0.01em;
  color: var(--lp-ink);
}

.cta-sub {
  margin: 0 0 clamp(32px, 5vh, 48px);
  font-size: clamp(14px, 1.2vw, 16px);
  letter-spacing: 0.04em;
  color: var(--lp-ink-2);
}

/* ============ Footer ============ */
.footer-wrap {
  padding: 0;
  border-top: 1px solid var(--lp-line);
}

/* ===================================================================
   Light theme（theme-pop）：同一套版式，换成暖纸与墨字
   =================================================================== */
#landingPage.is-light {
  --lp-bg: #faf7ff;
  --lp-bg-raised: #f5f3ff;
  --lp-ink: #1e1b4b;
  --lp-ink-2: rgba(30, 27, 75, 0.74);
  /* 0.64 ≈ 4.8:1 —— 小号等宽标签在浅纸上仍可读 */
  --lp-ink-3: rgba(30, 27, 75, 0.64);
  --lp-line: rgba(124, 58, 237, 0.16);
  --lp-line-2: rgba(124, 58, 237, 0.34);
  --lp-violet: #9333ea;
  --lp-violet-deep: #7c3aed;
  /* 亮纸上的薄荷需压深，浅绿（#0f9d6e）仅 3.3:1 */
  --lp-mint: #047857;
}

#landingPage.is-light .atmosphere {
  background: radial-gradient(120% 80% at 50% -10%, #ffffff 0%, var(--lp-bg) 55%, #efe9ff 100%);
}

#landingPage.is-light .aurora {
  opacity: 0.3;
}

#landingPage.is-light .aurora-a {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 68%);
}

#landingPage.is-light .aurora-b {
  background: radial-gradient(circle, rgba(236, 72, 153, 0.18) 0%, transparent 70%);
}

#landingPage.is-light .aurora-c {
  background: radial-gradient(circle, rgba(34, 211, 238, 0.14) 0%, transparent 70%);
}

#landingPage.is-light .aurora-grid {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.07) 1px, transparent 1px);
}

#landingPage.is-light .grain {
  opacity: 0.028;
}

#landingPage.is-light .vignette {
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(237, 233, 254, 0.55) 100%
  );
}

/* 照片在亮底上不再压暗，只保留一层轻冷调 */
#landingPage.is-light .plate img,
#landingPage.is-light .essence-plate img,
#landingPage.is-light .color-strip img,
#landingPage.is-light .mini-results img,
#landingPage.is-light .msg-grid img,
#landingPage.is-light .outpaint-frame img,
#landingPage.is-light .outpaint-stage > img,
#landingPage.is-light .board-tile-wrap img {
  filter: saturate(0.58) contrast(1.02) brightness(1);
}

#landingPage.is-light .plate::after,
#landingPage.is-light .essence-plate::after,
#landingPage.is-light .msg-grid figure::after,
#landingPage.is-light .outpaint-frame::after,
#landingPage.is-light .board-tile-wrap::after {
  background: linear-gradient(180deg, rgba(168, 85, 247, 0.08) 0%, rgba(250, 247, 255, 0.12) 100%);
}

#landingPage.is-light .plate {
  box-shadow:
    0 30px 60px -34px rgba(76, 29, 149, 0.32),
    0 0 0 1px rgba(124, 58, 237, 0.08);
}

#landingPage.is-light .btn-primary {
  color: #fff;
  background: linear-gradient(120deg, #a855f7 0%, #c026d3 55%, #ec4899 100%);
  box-shadow: 0 10px 30px -12px rgba(168, 85, 247, 0.5);

  &:hover {
    box-shadow: 0 16px 40px -14px rgba(236, 72, 153, 0.55);
  }
}

#landingPage.is-light .btn-ghost {
  background: rgba(255, 255, 255, 0.7);

  &:hover {
    background: #fff;
    border-color: rgba(124, 58, 237, 0.5);
  }
}

#landingPage.is-light .swatch {
  box-shadow: inset 0 0 0 1px rgba(76, 29, 149, 0.16);
}

#landingPage.is-light .card {
  background: linear-gradient(180deg, #ffffff 0%, rgba(255, 255, 255, 0.72) 100%);
}

#landingPage.is-light .card::after {
  background: radial-gradient(120% 90% at 50% 0%, rgba(168, 85, 247, 0.14), transparent 60%);
}

#landingPage.is-light .panel {
  background: linear-gradient(180deg, #ffffff 0%, rgba(255, 255, 255, 0.76) 100%);
}

#landingPage.is-light .mini-ai,
#landingPage.is-light .msg-ai {
  background: #fff;
}

#landingPage.is-light .collab-board {
  background-color: rgba(255, 255, 255, 0.75);
  background-image: radial-gradient(circle at 1px 1px, rgba(124, 58, 237, 0.18) 1px, transparent 0);
}

#landingPage.is-light .avatar {
  background: #fff;
  border-color: rgba(124, 58, 237, 0.22);
}

#landingPage.is-light .av-2 {
  background: #f5f3ff;
}

#landingPage.is-light .outpaint-core {
  border-color: rgba(236, 72, 153, 0.9);
  box-shadow: 0 0 0 9999px rgba(124, 58, 237, 0.3);
}

/* 深色小药丸上的文字在亮主题里换回亮薄荷，保证对比 */
#landingPage.is-light .outpaint-badge,
#landingPage.is-light .outpaint-tag,
#landingPage.is-light .core-label {
  color: #34d399;
}

#landingPage.is-light .composer-send {
  color: #fff;
}

#landingPage.is-light .cta-glow {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.24) 0%, transparent 62%);
}

#landingPage.is-light .progress-rail span {
  background: linear-gradient(90deg, transparent, #a855f7 55%, #ec4899);
}

/* ===================================================================
   Responsive
   =================================================================== */
@media (max-width: 1080px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1.12fr) minmax(0, 0.88fr);
    gap: 32px;
  }

  .essence-grid {
    grid-template-columns: 1fr;
    gap: 56px;
  }

  .ep-1 {
    grid-row: auto;
    aspect-ratio: 4 / 3;
  }

  .ep-2 {
    aspect-ratio: 4 / 3;
  }

  .ep-3 {
    grid-column: span 2;
    aspect-ratio: 16 / 9;
    margin-top: 0;
  }

  .stage {
    grid-template-columns: 1fr;
  }

  .bento {
    grid-template-columns: repeat(2, 1fr);
  }

  .card-color,
  .card-agent,
  .card-collab,
  .card-outpaint {
    grid-column: span 1;
  }

  .card-color,
  .card-outpaint {
    grid-column: span 2;
  }
}

@media (max-width: 820px) {
  .hero {
    min-height: auto;
    padding-top: 56px;
  }

  .hero-grid {
    grid-template-columns: 1fr;
    gap: 48px;
  }

  .hero-copy {
    max-width: none;
  }

  .hero-visual {
    max-width: 520px;
    margin: 0 auto;
    aspect-ratio: 1 / 0.92;
  }

  .hero-foot {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .head-note {
    text-align: left;
  }
}

@media (max-width: 640px) {
  .section {
    padding-top: 72px;
    padding-bottom: 72px;
  }

  .hero {
    padding-top: 40px;
    padding-bottom: 32px;
  }

  .hero-title {
    font-size: clamp(42px, 13vw, 64px);
  }

  .hero-lede {
    font-size: 14.5px;
  }

  .actions {
    width: 100%;
    gap: 10px;

    .btn {
      flex: 1 1 auto;
      justify-content: center;
      padding: 14px 20px;
      font-size: 14px;
    }
  }

  .hero-tags li {
    font-size: 9.5px;
  }

  .display {
    font-size: clamp(27px, 7.4vw, 36px);
  }

  .display-wide {
    max-width: none;
  }

  .bento {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .card-color,
  .card-agent,
  .card-collab,
  .card-outpaint {
    grid-column: span 1;
  }

  .essence-plates {
    gap: 10px;
  }

  .universe-head {
    padding: 0 20px;
  }

  .dome-wrap {
    height: 400px;
  }

  .msg {
    max-width: 100%;
  }

  .msg-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .cta-title {
    font-size: clamp(28px, 8.4vw, 40px);
  }
}

@media (max-width: 560px) {
  .actions {
    flex-direction: column;
    align-items: stretch;

    .btn {
      width: 100%;
    }
  }
}

/* ============ Reduced motion ============ */
@media (prefers-reduced-motion: reduce) {
  #landingPage {
    *,
    *::before,
    *::after {
      animation-duration: 0.001ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.001ms !important;
      scroll-behavior: auto !important;
    }
  }

  .section [data-d] {
    opacity: 1;
    transform: none;
  }

  .line-mark path {
    stroke-dashoffset: 0;
  }
}
</style>
