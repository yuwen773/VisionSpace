<template>
  <div id="basicLayout" :class="{ 'full-screen-layout': $route.meta.fullScreen }">
    <!-- 背景装饰 -->
    <div class="background-decoration">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
    </div>

    <a-layout class="main-layout">
      <!-- 头部导航 -->
      <a-layout-header class="layout-header">
        <GlobalHeader />
      </a-layout-header>

      <!-- 主要内容区域 -->
      <a-layout>
        <a-layout-content
          class="layout-content"
          :class="{ 'full-width-content': $route.meta.fullWidth || $route.meta.fullScreen }"
        >
          <div
            class="content-wrapper"
            :class="{ 'full-width-wrapper': $route.meta.fullWidth || $route.meta.fullScreen }"
          >
            <router-view v-slot="{ Component }">
              <transition name="page" mode="out-in">
                <component :is="Component" :key="$route.fullPath" />
              </transition>
            </router-view>
          </div>
        </a-layout-content>
      </a-layout>

      <!-- 底部 -->
      <GlobalFooter v-if="$route.meta.showFooter" />
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style lang="less" scoped>
#basicLayout {
  min-height: 100vh;
  position: relative;
  background: var(--color-bg-primary);
  overflow-x: hidden;
  width: 100%;

  &.full-screen-layout {
    height: 100vh;
    overflow: hidden;

    .main-layout {
      height: 100%;
    }

    // 内部 a-layout 也需要约束
    .ant-layout {
      flex: 1;
      min-height: 0;
    }

    .layout-content {
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }
  }
}

/* ========== 背景装饰 ========== */
.background-decoration {
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
  opacity: 0.15;
  animation: orbFloat 30s ease-in-out infinite;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, var(--color-primary-500) 0%, transparent 70%);
  top: -200px;
  right: -200px;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, var(--color-secondary-500) 0%, transparent 70%);
  bottom: -150px;
  left: -150px;
  animation-delay: -10s;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--color-accent-purple) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -20s;
}

@keyframes orbFloat {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
  }
  66% {
    transform: translate(-30px, 30px) scale(0.9);
  }
}

/* ========== 主布局 ========== */
.main-layout {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* ========== 头部 ========== */
.layout-header {
  background: transparent;
  padding: 0;
  line-height: normal;
  height: auto;
  flex-shrink: 0;
}

/* ========== 内容区域 ========== */
.layout-content {
  background: transparent;
  padding: var(--space-6);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  &.full-width-content {
    padding: 0;
  }
}

.content-wrapper {
  max-width: var(--container-2xl);
  margin: 0 auto;
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;

  &.full-width-wrapper {
    max-width: none;
    margin: 0;
  }
}

/* ========== 页面切换动画 ========== */
.page-enter-active,
.page-leave-active {
  transition: all var(--transition-slow);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

@media (max-width: 640px) {
  .layout-content {
    padding: var(--space-4);
  }
}
</style>
