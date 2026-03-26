<template>
  <div id="basicLayout">
    <!-- 背景装饰 -->
    <div class="background-decoration">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
    </div>

    <a-layout class="main-layout">
      <!-- 头部导航 -->
      <a-layout-header class="layout-header" v-if="!isLoginOrRegister">
        <GlobalHeader />
      </a-layout-header>

      <!-- 主要内容区域 -->
      <a-layout>
        <a-layout-content class="layout-content">
          <div class="content-wrapper">
            <router-view v-slot="{ Component }">
              <transition name="page" mode="out-in">
                <component :is="Component" :key="$route.fullPath" />
              </transition>
            </router-view>
          </div>
        </a-layout-content>
      </a-layout>

      <!-- 底部 -->
      <a-layout-footer class="layout-footer" v-if="!isLoginOrRegister">
        <!-- 浮动主题切换按钮 -->
        <ThemeToggleButton />
        <div class="footer-content">
          <div class="footer-section">
            <div class="footer-logo">
              <img src="@/assets/logo.png" alt="VisionSpace" />
              <span class="footer-logo-text text-gradient">VisionSpace</span>
            </div>
            <p class="footer-description">企业级智能协同图片空间</p>
          </div>

          <div class="footer-section">
            <h4 class="footer-title">产品</h4>
            <ul class="footer-links">
              <li><a href="/">主页</a></li>
              <li><a href="/add_space">创建空间</a></li>
              <li><a href="/add_picture">上传图片</a></li>
            </ul>
          </div>

          <div class="footer-section">
            <h4 class="footer-title">支持</h4>
            <ul class="footer-links">
              <li><a href="#">帮助中心</a></li>
              <li><a href="#">API 文档</a></li>
              <li><a href="#">联系我们</a></li>
            </ul>
          </div>

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
          <p>&copy; 2024 VisionSpace. Made with ❤️ by 周can & yuwen</p>
        </div>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import ThemeToggleButton from '@/components/ThemeToggleButton.vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isLoginOrRegister = ref<boolean>(false)

watch(
  route,
  () => {
    isLoginOrRegister.value = route.name === 'login' || route.name === 'register'
  },
  { immediate: true }
)
</script>

<style lang="less" scoped>
#basicLayout {
  min-height: 100vh;
  position: relative;
  background: var(--color-bg-primary);
  overflow-x: hidden;
  width: 100%;
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
  background: transparent;
}

/* ========== 头部 ========== */
.layout-header {
  background: transparent;
  padding: 0;
  line-height: normal;
  height: auto;
}

/* ========== 内容区域 ========== */
.layout-content {
  background: transparent;
  padding: var(--space-6);
  min-height: calc(100vh - 64px - 200px);
}

.content-wrapper {
  max-width: var(--container-2xl);
  margin: 0 auto;
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

.footer-content {
  max-width: var(--container-2xl);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 2fr repeat(3, 1fr);
  gap: var(--space-10);
  margin-bottom: var(--space-10);
}

.footer-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.footer-logo img {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.footer-logo-text {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: var(--font-bold);
}

.footer-description {
  font-size: var(--text-sm);
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
  .footer-content {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-8);
  }
}

@media (max-width: 640px) {
  .layout-content {
    padding: var(--space-4);
  }

  .footer-content {
    grid-template-columns: 1fr;
    gap: var(--space-6);
  }

  .layout-footer {
    padding: var(--space-8) var(--space-4) 0;
  }
}
</style>
