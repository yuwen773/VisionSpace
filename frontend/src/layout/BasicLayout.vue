<template>
  <div id="basicLayout" :class="{ 'full-screen-layout': $route.meta.fullScreen }">
    <ThemeTransitionEffect />
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
        <a-layout-content class="layout-content" :class="{ 'full-screen-content': $route.meta.fullScreen }">
          <div class="content-wrapper" :class="{ 'full-screen-wrapper': $route.meta.fullScreen }">
            <router-view v-slot="{ Component }">
              <transition name="page" mode="out-in">
                <component :is="Component" :key="$route.fullPath" />
              </transition>
            </router-view>
          </div>
        </a-layout-content>
      </a-layout>

      <!-- 底部 -->
      <a-layout-footer v-if="$route.meta.showFooter" class="layout-footer">
        <!-- 链接网格 -->
        <div class="footer-grid">
          <!-- Logo 列 -->
          <div class="footer-brand">
            <div class="footer-logo">
              <img src="@/assets/logo.svg" alt="VisionSpace" />
              <span class="footer-logo-text text-gradient">VisionSpace</span>
            </div>
            <p class="footer-description">企业级智能协同图片空间</p>
          </div>

          <!-- 产品列 -->
          <div class="footer-section">
            <h4 class="footer-title">产品</h4>
            <ul class="footer-links">
              <li><a href="/">主页</a></li>
              <li><a href="/add_space">创建空间</a></li>
              <li><a href="/add_picture">上传图片</a></li>
            </ul>
          </div>

          <!-- 支持列 -->
          <div class="footer-section">
            <h4 class="footer-title">支持</h4>
            <ul class="footer-links">
              <li><a href="#">帮助中心</a></li>
              <li><a href="#">API 文档</a></li>
              <li><a href="#">联系我们</a></li>
            </ul>
          </div>

          <!-- 关于列 -->
          <div class="footer-section">
            <h4 class="footer-title">关于</h4>
            <ul class="footer-links">
              <li><a href="#">关于我们</a></li>
              <li><a href="#">隐私政策</a></li>
              <li><a href="#">服务条款</a></li>
            </ul>
          </div>
        </div>

        <div class="footer-bottom">
          <p>&copy; 2026 VisionSpace. Made with ❤️ by 周can & yuwen</p>
        </div>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import ThemeTransitionEffect from '@/components/ThemeTransitionEffect.vue'
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

  &.full-screen-content {
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

  &.full-screen-wrapper {
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

/* ========== 底部 ========== */
.layout-footer {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border-top: 1px solid var(--color-border-subtle);
  padding: var(--space-12) var(--space-6) 0;
  margin-top: var(--space-16);
}

/* ========== Logo 区域 ========== */
.footer-brand {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  margin-left: 100px;
}

.footer-grid {
  max-width: var(--container-2xl);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.8fr 1fr 1fr 1fr;
  gap: var(--space-6) var(--space-5);
  margin-bottom: var(--space-10);
}

.footer-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.footer-logo img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.footer-logo-text {
  font-family: var(--font-brand);
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
}

.footer-description {
  font-size: var(--text-lg);
  color: var(--color-text-tertiary);
  line-height: var(--leading-relaxed);
}

.footer-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.footer-links {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.footer-links li a {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  transition: all var(--transition-fast);
  display: inline-block;

  &:hover {
    color: var(--color-primary-400);
    transform: translateX(4px);
  }
}

.footer-bottom {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border-subtle);
  text-align: center;

  p {
    font-size: var(--text-sm);
    color: var(--color-text-disabled);
    margin: 0;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .footer-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-6);
  }

  .footer-brand {
    grid-column: span 3;
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    margin-bottom: var(--space-4);
    margin-left: 100px;
  }

  .footer-logo {
    margin-right: var(--space-4);
  }

  .footer-logo img {
    width: 44px;
    height: 44px;
  }

  .footer-logo-text {
    font-size: var(--text-xl);
  }

  .footer-description {
    font-size: var(--text-base);
  }

  .footer-section {
    gap: var(--space-5);
  }

  .footer-title {
    font-size: var(--text-base);
    margin-bottom: var(--space-3);
  }

  .footer-links {
    gap: var(--space-3);
  }

  .footer-links li a {
    font-size: var(--text-sm);
  }
}

@media (max-width: 640px) {
  .layout-content {
    padding: var(--space-4);
  }

  .footer-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }

  .footer-brand {
    grid-column: span 3;
    flex-direction: column;
    align-items: center;
    text-align: center;
    margin-bottom: var(--space-4);
    margin-left: 0;
    gap: var(--space-1);
  }

  .footer-logo {
    margin-right: 0;
  }

  .footer-description {
    font-size: var(--text-xs);
    text-align: center;
  }

  .footer-section {
    align-items: center;
  }

  .footer-title {
    font-size: var(--text-xs);
    margin-bottom: var(--space-1);
  }

  .footer-links {
    align-items: center;
    gap: var(--space-1);
  }

  .footer-links li a {
    font-size: var(--text-xs);
    transform: none;

    &:hover {
      transform: none;
    }
  }

  .layout-footer {
    padding: var(--space-6) var(--space-4) 0;
    margin-top: var(--space-10);
  }

  .footer-bottom {
    padding-top: var(--space-4);

    p {
      font-size: var(--text-xs);
    }
  }
}
</style>
