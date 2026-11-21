<template>
  <div class="auth-shell">
    <!-- 表单侧 -->
    <section class="auth-pane">
      <header class="auth-brand">
        <img src="@/assets/logo.svg" alt="" class="auth-logo" />
        <span class="auth-wordmark">VisionSpace</span>
        <span class="auth-tagline">视觉灵感空间</span>
        <router-link to="/" class="auth-back">
          <span>返回首页</span>
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M7 17 17 7M9 7h8v8" />
          </svg>
        </router-link>
      </header>

      <div class="auth-body">
        <p class="auth-eyebrow">
          <span class="auth-rule" aria-hidden="true"></span>
          {{ eyebrow }}
        </p>

        <h1 class="auth-title">{{ title }}</h1>
        <p class="auth-desc">{{ description }}</p>

        <div class="auth-slot">
          <slot />
        </div>

        <div class="auth-switch">
          <slot name="footer" />
        </div>
      </div>

      <footer class="auth-meta">
        <span>&copy; 2026 VisionSpace</span>
        <span class="auth-meta-sep" aria-hidden="true">/</span>
        <span>Visual Inspiration Library</span>
      </footer>
    </section>

    <!-- 视觉侧：WebGL 渐变场 + 同心圆环构图 -->
    <aside class="auth-scene" aria-hidden="true">
      <!-- 底：Grainient 流动场，失败时由 .auth-scene 的 CSS 渐变兜底 -->
      <div class="scene-field">
        <Grainient
          :colors="SCENE_COLORS"
          type="radial"
          :angle="120"
          :bands="2"
          :speed="0.18"
          :flow="0.35"
          :scale="1.25"
          :softness="0.95"
          :silk="0.35"
          :grain-amount="0.05"
          :grain-size="1.4"
          :contrast="1.1"
          :saturation="0.95"
          :variation="0.15"
          :interactive="true"
          :stir="0.35"
          :click-swirl="false"
          :intro="true"
          :dpr="1.5"
        />
      </div>

      <!-- 压暗四角，保证竖排引句与角落信息的对比度 -->
      <div class="scene-scrim"></div>

      <div class="scene-glow scene-glow-a"></div>
      <div class="scene-glow scene-glow-b"></div>

      <div class="scene-rings">
        <span class="scene-ring ring-1"></span>
        <span class="scene-ring ring-2"></span>
        <span class="scene-ring ring-3"></span>
      </div>

      <p class="scene-quote">
        {{ quoteFirst }}<br />{{ quoteSecond }}
      </p>

      <div class="scene-meta">
        <span>VisionSpace</span>
        <span>Est. 2026 — 视觉灵感库</span>
      </div>

      <div class="scene-grain"></div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import Grainient from '@/components/backgrounds/GrainientBackground.vue'

/** 与 .auth-scene 的 CSS 兜底渐变同一套紫，画布和兜底之间切换不会跳色 */
const SCENE_COLORS = ['#07030f', '#0d0620', '#1e0b42', '#4c1d95']

withDefaults(
  defineProps<{
    eyebrow: string
    title: string
    description: string
    quoteFirst: string
    quoteSecond: string
  }>(),
  {
    quoteFirst: '在光影之间',
    quoteSecond: '寻找共鸣',
  },
)
</script>

<style scoped lang="less">
/* ============================================================
   认证页外壳 · 杂志跨页（editorial spread）
   左侧是安静的纸面与表单，右侧是唯一的彩色版面。
   ============================================================ */

.auth-shell {
  --auth-accent: #7c3aed;
  --auth-accent-strong: #6d28d9;
  --auth-accent-soft: rgba(124, 58, 237, 0.22);
  --auth-ease: cubic-bezier(0.22, 1, 0.36, 1);

  display: grid;
  grid-template-columns: minmax(0, 0.94fr) minmax(0, 1.06fr);
  min-height: 100vh;
  position: relative;
  background: var(--bg-primary);
  color: var(--text-primary);
  /* clip 不创建滚动容器；窄屏时页面仍可正常滚动 */
  overflow-x: clip;
}

/* ========== 表单侧 ========== */
.auth-pane {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
  padding: clamp(1.5rem, 4vw, 3.25rem) clamp(1.25rem, 5vw, 4.5rem);
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  animation: auth-fade 0.6s var(--auth-ease) both;
}

.auth-logo {
  width: 2rem;
  height: 2rem;
  border-radius: 0.5rem;
  flex-shrink: 0;
}

.auth-wordmark {
  font-family: var(--font-brand);
  font-size: 1.125rem;
  line-height: 1;
  letter-spacing: 0.04em;
  color: var(--text-primary);
}

.auth-tagline {
  padding-left: 0.75rem;
  border-left: 1px solid var(--color-border-default);
  font-family: var(--font-mono);
  font-size: 0.625rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.auth-back {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0;
  font-family: var(--font-mono);
  font-size: 0.625rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.25s ease;

  &:hover {
    color: var(--color-text-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--auth-accent);
    outline-offset: 3px;
    border-radius: 2px;
  }
}

.auth-body {
  width: 100%;
  max-width: 25rem;
  margin: auto;
  padding: 2.5rem 0;
}.auth-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0 0 1rem;
  font-family: var(--font-mono);
  font-size: 0.625rem;
  font-weight: 500;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  animation: auth-rise 0.7s var(--auth-ease) 0.05s both;
}

.auth-rule {
  width: 1.75rem;
  height: 1px;
  background: var(--auth-accent);
  flex-shrink: 0;
}

.auth-title {
  margin: 0 0 0.75rem;
  font-family: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: clamp(2rem, 3.6vw, 2.875rem);
  font-weight: 900;
  line-height: 1.12;
  letter-spacing: 0.01em;
  color: var(--color-text-primary);
  animation: auth-rise 0.7s var(--auth-ease) 0.12s both;
}

.auth-desc {
  margin: 0 0 2.25rem;
  font-size: 0.9375rem;
  line-height: 1.75;
  color: var(--color-text-secondary);
  text-wrap: pretty;
  animation: auth-rise 0.7s var(--auth-ease) 0.18s both;
}

.auth-slot {
  animation: auth-rise 0.7s var(--auth-ease) 0.24s both;
}

.auth-switch {
  margin-top: 1.375rem;
  font-size: 0.8125rem;
  color: var(--color-text-tertiary);
  animation: auth-fade 0.7s var(--auth-ease) 0.34s both;
}

.auth-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.auth-meta-sep {
  opacity: 0.5;
}

@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translateY(0.75rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes auth-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ========== 表单细节（插槽内是 ant-design 组件） ========== */
.auth-slot {
  :deep(.ant-form-item) {
    margin-bottom: 1.375rem;
  }

  :deep(.ant-form-item:last-child) {
    margin-bottom: 0;
  }

  :deep(.field-label) {
    display: block;
    margin-bottom: 0.5rem;
    font-size: 0.75rem;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: var(--color-text-secondary);
  }

  :deep(.ant-input),
  :deep(.ant-input-affix-wrapper) {
    min-height: 3rem;
    padding: 0.5rem 1rem;
    border: 1px solid var(--color-border-strong);
    border-radius: 0.5rem;
    background: transparent;
    box-shadow: none;
    font-family: inherit;
    font-size: 0.9375rem;
    color: var(--color-text-primary);
    transition:
      border-color 0.25s ease,
      box-shadow 0.25s ease,
      background 0.25s ease;
  }

  :deep(.ant-input) {
    padding: 0.5rem 1rem;
  }

  :deep(.ant-input-affix-wrapper .ant-input) {
    padding: 0;
    border: none;
    min-height: auto;
    background: transparent;
  }

  :deep(.ant-input::placeholder) {
    color: var(--color-text-tertiary);
  }

  :deep(.ant-input-prefix) {
    margin-right: 0.625rem;
    color: var(--color-text-tertiary);
    transition: color 0.25s ease;
  }

  :deep(.ant-input-suffix) {
    color: var(--color-text-tertiary);
  }

  /* 自绘的密码可见性切换：antd 自带图标 tabindex=-1，键盘无法操作 */
  :deep(.field-toggle) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    margin-right: -0.25rem;
    padding: 0;
    border: none;
    border-radius: 0.25rem;
    background: transparent;
    color: var(--color-text-tertiary);
    cursor: pointer;
    transition: color 0.2s ease;
  }

  :deep(.field-toggle:hover) {
    color: var(--color-text-primary);
  }

  :deep(.field-toggle:focus-visible) {
    outline: 2px solid var(--auth-accent);
    outline-offset: 2px;
  }

  :deep(.ant-input:focus),
  :deep(.ant-input-affix-wrapper:focus),
  :deep(.ant-input-affix-wrapper-focused) {
    border-color: var(--auth-accent);
    box-shadow: 0 0 0 3px var(--auth-accent-soft);
    background: transparent;
  }

  :deep(.ant-input-affix-wrapper-focused .ant-input-prefix),
  :deep(.ant-input-affix-wrapper:focus-within .ant-input-prefix) {
    color: var(--auth-accent);
  }

  /* antd v4 对纯 input 与 affix wrapper 用两套 class，错误态要各写一遍 */
  :deep(.ant-input-status-error),
  :deep(.ant-input-affix-wrapper-status-error),
  :deep(.ant-form-item-has-error .ant-input),
  :deep(.ant-form-item-has-error .ant-input-affix-wrapper) {
    border-color: var(--color-error) !important;
    background: transparent !important;
    box-shadow: none !important;
  }

  :deep(.ant-form-item-has-error .ant-input:focus),
  :deep(.ant-form-item-has-error .ant-input-affix-wrapper-focused) {
    box-shadow: 0 0 0 3px var(--color-error-bg) !important;
  }

  :deep(.ant-form-item-with-help) {
    margin-bottom: 1.5rem;
  }

  :deep(.ant-form-item-explain-error) {
    margin-top: 0.375rem;
    font-size: 0.75rem;
    line-height: 1.5;
    color: var(--color-error);
  }

  /* 提交按钮 */
  :deep(.auth-submit) {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    width: 100%;
    min-height: 3.125rem;
    padding: 0.75rem 1.25rem;
    border: none;
    border-radius: 0.5rem;
    background: var(--auth-accent);
    color: #fff;
    font-family: inherit;
    font-size: 0.9375rem;
    font-weight: 600;
    letter-spacing: 0.02em;
    cursor: pointer;
    transition:
      background 0.25s ease,
      transform 0.25s var(--auth-ease);
  }

  :deep(.auth-submit:hover:not(:disabled)) {
    background: var(--auth-accent-strong);
  }

  :deep(.auth-submit:active:not(:disabled)) {
    transform: translateY(1px);
  }

  :deep(.auth-submit:disabled) {
    opacity: 0.65;
    cursor: progress;
  }

  :deep(.auth-submit .submit-arrow) {
    display: inline-flex;
    transition: transform 0.3s var(--auth-ease);
  }

  :deep(.auth-submit:hover:not(:disabled) .submit-arrow) {
    transform: translateX(3px);
  }

  :deep(.auth-submit:focus-visible) {
    outline: 2px solid var(--auth-accent);
    outline-offset: 3px;
  }

  :deep(.spin) {
    animation: auth-spin 0.9s linear infinite;
  }
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 文案链接 */
.auth-slot :deep(.auth-link),
.auth-switch :deep(.auth-link) {
  position: relative;
  color: var(--auth-accent);
  font-weight: 600;
  text-decoration: none;
  transition: color 0.25s ease;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: -0.125rem;
    height: 1px;
    background: currentColor;
    transform: scaleX(0);
    transform-origin: left;
    transition: transform 0.35s var(--auth-ease);
  }

  &:hover::after,
  &:focus-visible::after {
    transform: scaleX(1);
  }
}

.auth-slot :deep(.auth-link:focus-visible),
.auth-switch :deep(.auth-link:focus-visible) {
  outline: 2px solid var(--auth-accent);
  outline-offset: 3px;
  border-radius: 2px;
}

.auth-slot :deep(.auth-legal),
.auth-switch :deep(.auth-legal) {
  margin: 0 0 0.5rem;
  font-size: 0.6875rem;
  line-height: 1.7;
  color: var(--color-text-tertiary);
}

.auth-switch :deep(.auth-switch-line) {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.7;
  color: var(--color-text-tertiary);
}

.auth-switch :deep(.auth-switch-line + .auth-switch-line) {
  margin-top: 0.5rem;
}

/* ========== 视觉侧 ========== */
.auth-scene {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  background:
    radial-gradient(120% 90% at 78% 8%, rgba(124, 58, 237, 0.42) 0%, transparent 58%),
    radial-gradient(90% 80% at 18% 92%, rgba(236, 72, 153, 0.28) 0%, transparent 62%),
    linear-gradient(158deg, #0a0616 0%, #120a24 52%, #170a2b 100%);
  animation: auth-fade 1s var(--auth-ease) both;
}

.scene-field {
  position: absolute;
  inset: 0;
  /* 压低整体亮度：画布只提供流动和颗粒，明度仍由下面的深紫渐变决定 */
  opacity: 0.72;
}

.scene-scrim {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(
      100deg,
      rgba(8, 4, 18, 0.52) 0%,
      rgba(8, 4, 18, 0) 38%,
      rgba(8, 4, 18, 0) 60%,
      rgba(8, 4, 18, 0.46) 100%
    ),
    linear-gradient(to top, rgba(8, 4, 18, 0.55) 0%, rgba(8, 4, 18, 0) 36%);
}

.scene-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.4;
}

.scene-glow-a {
  width: 30rem;
  height: 30rem;
  top: -8rem;
  right: -8rem;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.55) 0%, transparent 68%);
}

.scene-glow-b {
  width: 26rem;
  height: 26rem;
  bottom: -10rem;
  left: -6rem;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.4) 0%, transparent 70%);
}

.scene-rings {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}

.scene-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.ring-1 {
  width: min(46vw, 22rem);
  aspect-ratio: 1;
}

.ring-2 {
  width: min(62vw, 34rem);
  aspect-ratio: 1;
  border-color: rgba(168, 85, 247, 0.28);
  border-top-color: rgba(244, 114, 182, 0.75);
  animation: scene-rotate 64s linear infinite;
}

.ring-3 {
  width: min(82vw, 46rem);
  aspect-ratio: 1;
  border-color: rgba(255, 255, 255, 0.06);
}

@keyframes scene-rotate {
  to {
    transform: rotate(360deg);
  }
}

.scene-quote {
  position: absolute;
  top: 50%;
  right: clamp(2rem, 5vw, 4.75rem);
  transform: translateY(-50%);
  margin: 0;
  writing-mode: vertical-rl;
  font-family: 'Noto Serif SC', 'Songti SC', 'STSong', serif;
  font-size: clamp(1.375rem, 2.2vw, 2rem);
  font-weight: 600;
  letter-spacing: 0.26em;
  line-height: 2;
  color: rgba(255, 255, 255, 0.86);
  text-shadow: 0 0 32px rgba(10, 6, 22, 0.6);
}

.scene-meta {
  position: absolute;
  left: clamp(2rem, 4vw, 3.25rem);
  bottom: clamp(2rem, 4vw, 3rem);
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.42);
}

.scene-grain {
  position: absolute;
  inset: 0;
  opacity: 0.06;
  mix-blend-mode: overlay;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 140px 140px;
}

/* ========== 响应式 ========== */
@media (min-width: 768px) and (max-width: 1023px) {
  .auth-pane {
    padding: 1.75rem clamp(1.25rem, 3vw, 2rem);
  }

  .auth-body {
    padding: 1.5rem 0;
  }

  .auth-title {
    font-size: clamp(1.75rem, 3.2vw, 2.25rem);
  }

  .scene-quote {
    right: 1.5rem;
    font-size: 1.25rem;
    letter-spacing: 0.2em;
  }

  .scene-meta {
    left: 1.5rem;
    bottom: 1.5rem;
    font-size: 0.5rem;
  }
}

@media (max-width: 767px) {
  .auth-shell {
    grid-template-columns: 1fr;
    min-height: 100dvh;
  }

  /* 窄屏去掉整块视觉版，保留表单与品牌行 */
  .auth-scene {
    display: none;
  }

  .auth-pane {
    gap: 1rem;
    min-height: 100dvh;
    padding: 1.5rem 1.25rem 1.75rem;
  }

  .auth-body {
    padding: 1.5rem 0;
  }

  .auth-title {
    font-size: clamp(1.875rem, 8vw, 2.25rem);
  }

  .auth-desc {
    margin-bottom: 1.75rem;
  }
}

@media (max-width: 420px) {
  .auth-tagline {
    display: none;
  }

  .auth-meta {
    font-size: 0.5rem;
    letter-spacing: 0.12em;
  }
}

/* ========== 触屏目标 ========== */
@media (pointer: coarse) {
  .auth-slot :deep(.ant-input),
  .auth-slot :deep(.ant-input-affix-wrapper),
  .auth-slot :deep(.auth-submit) {
    min-height: 3rem;
  }
}

/* ========== 减弱动效 ========== */
@media (prefers-reduced-motion: reduce) {
  .auth-brand,
  .auth-eyebrow,
  .auth-title,
  .auth-desc,
  .auth-slot,
  .auth-switch,
  .auth-scene,
  .ring-2 {
    animation: none;
  }
}
</style>
