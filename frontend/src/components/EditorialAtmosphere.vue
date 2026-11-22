<script setup lang="ts">
/**
 * 编辑版面共用的一层环境：固定底层 + 模糊光斑 + 细网格 + 噪点 + 暗角。
 *
 * 此前四个页面各写一份：`LandingPage` 是 `.atmosphere/.aurora-{a,b,c}/.aurora-grid`
 * 配 `#landingPage.is-light` 的 8 条覆盖，`ExplorePage` 是 `.ambient-bg/.ex-orb-{1,2,3}`
 * 配 `#homePage.is-light` 的 8 条覆盖，`SpaceDetailPage`/`AddPicturePage` 用
 * `award-page.css` 里的 `.aw-atmosphere/.aw-aurora` 再各写一遍标记。同一层东西三套类名、
 * 三份 `@keyframes`、三处浅色覆盖，改一处得记得改三处。
 *
 * 这里把它收成一个组件：标记由 `variant` 决定，颜色与几何都在组件样式里，
 * 主题不再是「祖先页面上有个 is-light」这种隐式约定，而是显式的 `light` 属性。
 * 页面只负责把自己的 `isLight` 传进来。
 */
withDefaults(
  defineProps<{
    /** award：空间展厅/显影台；landing：落地页；explore：探索页 */
    variant: 'award' | 'landing' | 'explore'
    /** 紫漾浅色纸面 */
    light?: boolean
  }>(),
  { light: false },
)
</script>

<template>
  <div class="aw-atmosphere" :class="[`is-${variant}`, { 'is-light': light }]" aria-hidden="true">
    <div class="aw-aurora aw-aurora-1"></div>
    <div class="aw-aurora aw-aurora-2"></div>
    <div v-if="variant !== 'award'" class="aw-aurora aw-aurora-3"></div>
    <div v-if="variant !== 'award'" class="aw-vignette"></div>
  </div>
</template>

<style scoped>
.aw-atmosphere {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

/* 细网格 */
.aw-atmosphere::before {
  content: '';
  position: absolute;
  inset: 0;
  background-size: 96px 96px;
}

/* 噪点 */
.aw-atmosphere::after {
  content: '';
  position: absolute;
  inset: -40%;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.aw-aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.42;
  animation: aw-drift 28s ease-in-out infinite alternate;
}

.aw-vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(3, 4, 10, 0.7) 100%
  );
}

@keyframes aw-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-2.5vw, 2.5vh, 0) scale(1.07);
  }
}

@keyframes aw-grain-shift {
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

/* ============ award：空间展厅 / 显影台 ============ */
.is-award {
  background: radial-gradient(
    120% 70% at 50% -10%,
    #0b0a18 0%,
    var(--aw-bg, #06050b) 55%,
    #030208 100%
  );
}

.is-award::before {
  background-image:
    linear-gradient(to right, rgba(242, 238, 230, 0.026) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(242, 238, 230, 0.026) 1px, transparent 1px);
  mask-image: radial-gradient(ellipse 72% 58% at 50% 24%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 72% 58% at 50% 24%, #000 0%, transparent 78%);
}

/* 展厅的噪点是静态的，没有帧动画 */
.is-award::after {
  opacity: 0.045;
}

.is-award .aw-aurora-1 {
  width: 56vw;
  height: 40vw;
  top: -16vw;
  right: -10vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.32) 0%, transparent 68%);
}

.is-award .aw-aurora-2 {
  width: 40vw;
  height: 36vw;
  bottom: 2vh;
  left: -14vw;
  background: radial-gradient(circle, rgba(74, 122, 168, 0.18) 0%, transparent 70%);
  animation-delay: -11s;
}

.is-award.is-light {
  background: radial-gradient(
    120% 70% at 50% -10%,
    #ffffff 0%,
    var(--aw-bg, #faf7ff) 58%,
    #efe9ff 100%
  );
}

.is-award.is-light::before {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.06) 1px, transparent 1px);
}

.is-award.is-light::after {
  opacity: 0.025;
}

.is-award.is-light .aw-aurora {
  opacity: 0.26;
}

.is-award.is-light .aw-aurora-1 {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.28) 0%, transparent 68%);
}

.is-award.is-light .aw-aurora-2 {
  background: radial-gradient(circle, rgba(236, 72, 153, 0.16) 0%, transparent 70%);
}

/* ============ landing：落地页 ============ */
.is-landing {
  background: radial-gradient(
    120% 80% at 50% -10%,
    #0b0d1c 0%,
    var(--lp-bg, #06050b) 55%,
    #03040a 100%
  );
}

.is-landing::before {
  background-image:
    linear-gradient(to right, rgba(244, 241, 234, 0.028) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(244, 241, 234, 0.028) 1px, transparent 1px);
  mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 0%, transparent 78%);
}

.is-landing::after {
  opacity: 0.05;
  animation: aw-grain-shift 8s steps(6) infinite;
}

.is-landing .aw-aurora-1 {
  width: 62vw;
  height: 46vw;
  top: -18vw;
  right: -12vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.34) 0%, transparent 68%);
}

.is-landing .aw-aurora-2 {
  width: 46vw;
  height: 42vw;
  bottom: 6vh;
  left: -16vw;
  background: radial-gradient(circle, rgba(74, 122, 168, 0.2) 0%, transparent 70%);
  animation-delay: -9s;
}

.is-landing .aw-aurora-3 {
  width: 30vw;
  height: 30vw;
  top: 48%;
  right: 6%;
  background: radial-gradient(circle, rgba(134, 227, 200, 0.1) 0%, transparent 70%);
  animation-delay: -17s;
}

.is-landing.is-light {
  background: radial-gradient(
    120% 80% at 50% -10%,
    #ffffff 0%,
    var(--lp-bg, #faf7ff) 55%,
    #efe9ff 100%
  );
}

.is-landing.is-light::before {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.07) 1px, transparent 1px);
}

.is-landing.is-light::after {
  opacity: 0.028;
}

.is-landing.is-light .aw-aurora {
  opacity: 0.3;
}

.is-landing.is-light .aw-aurora-1 {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 68%);
}

.is-landing.is-light .aw-aurora-2 {
  background: radial-gradient(circle, rgba(236, 72, 153, 0.18) 0%, transparent 70%);
}

.is-landing.is-light .aw-aurora-3 {
  background: radial-gradient(circle, rgba(34, 211, 238, 0.14) 0%, transparent 70%);
}

.is-landing.is-light .aw-vignette {
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(237, 233, 254, 0.55) 100%
  );
}

/* ============ explore：探索页 ============ */
.is-explore {
  background: radial-gradient(
    120% 80% at 60% -10%,
    #0b0d1c 0%,
    var(--ex-bg, #06050b) 55%,
    #03040a 100%
  );
}

.is-explore::before {
  background-image:
    linear-gradient(to right, rgba(244, 241, 234, 0.03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(244, 241, 234, 0.03) 1px, transparent 1px);
  mask-image: radial-gradient(ellipse 75% 60% at 55% 18%, #000 0%, transparent 76%);
  -webkit-mask-image: radial-gradient(ellipse 75% 60% at 55% 18%, #000 0%, transparent 76%);
}

.is-explore::after {
  opacity: 0.05;
  animation: aw-grain-shift 8s steps(6) infinite;
}

.is-explore .aw-aurora-1 {
  width: 60vw;
  height: 44vw;
  top: -20vw;
  right: -12vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.34) 0%, transparent 68%);
}

.is-explore .aw-aurora-2 {
  width: 46vw;
  height: 40vw;
  top: 30%;
  left: -18vw;
  background: radial-gradient(circle, rgba(134, 227, 200, 0.1) 0%, transparent 70%);
  animation-delay: -9s;
}

.is-explore .aw-aurora-3 {
  width: 34vw;
  height: 34vw;
  bottom: -10vw;
  right: 8%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.12) 0%, transparent 70%);
  animation-delay: -16s;
}

.is-explore.is-light {
  background: radial-gradient(
    120% 80% at 50% -10%,
    #ffffff 0%,
    var(--ex-bg, #faf7ff) 55%,
    #efe9ff 100%
  );
}

.is-explore.is-light::before {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.07) 1px, transparent 1px);
}

.is-explore.is-light::after {
  opacity: 0.028;
}

.is-explore.is-light .aw-aurora {
  opacity: 0.3;
}

.is-explore.is-light .aw-aurora-1 {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 68%);
}

.is-explore.is-light .aw-aurora-2 {
  background: radial-gradient(circle, rgba(34, 211, 238, 0.14) 0%, transparent 70%);
}

.is-explore.is-light .aw-aurora-3 {
  background: radial-gradient(circle, rgba(236, 72, 153, 0.16) 0%, transparent 70%);
}

.is-explore.is-light .aw-vignette {
  background: radial-gradient(
    ellipse 90% 70% at 50% 40%,
    transparent 55%,
    rgba(237, 233, 254, 0.55) 100%
  );
}

@media (prefers-reduced-motion: reduce) {
  .aw-aurora,
  .aw-atmosphere::after {
    animation: none !important;
  }
}
</style>
