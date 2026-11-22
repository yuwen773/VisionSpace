<template>
  <div class="admin-layout">
    <a class="admin-skip" href="#admin-content">跳到主要内容</a>

    <!-- 环境层：静态网格与两团极淡的光，不参与滚动 -->
    <div class="admin-atmosphere" aria-hidden="true">
      <div class="admin-atmosphere__grid"></div>
      <div class="admin-atmosphere__glow admin-atmosphere__glow--a"></div>
      <div class="admin-atmosphere__glow admin-atmosphere__glow--b"></div>
    </div>

    <!-- 侧边栏 -->
    <AdminSidebar ref="sidebarRef" />

    <!-- 主内容区 -->
    <div class="admin-main" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <!-- 顶部导航 -->
      <AdminHeader />

      <!-- 页面内容 -->
      <main id="admin-content" ref="contentRef" class="admin-content" tabindex="-1">
        <router-view v-slot="{ Component, route }">
          <Transition name="admin-route" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'

// 引入管理端主题
import '@/styles/admin-theme.css'

const sidebarRef = ref<InstanceType<typeof AdminSidebar> | null>(null)
const contentRef = ref<HTMLElement | null>(null)
// 窄屏默认收成图标栏：240px 的展开态会盖住大部分表格
const isNarrowViewport = () => window.matchMedia('(max-width: 1024px)').matches
const sidebarCollapsed = ref(isNarrowViewport())

// 可以在子组件中注入侧边栏折叠状态
provide('sidebarCollapsed', sidebarCollapsed)

const route = useRoute()

// 窄屏下跳转后收起侧栏，避免浮层一直挡在内容上；同时回到内容顶部
watch(
  () => route.path,
  () => {
    if (isNarrowViewport()) sidebarCollapsed.value = true
    contentRef.value?.scrollTo({ top: 0 })
  },
)

// body 上的类让 Modal / Dropdown / Popconfirm 这些挂到 body 的浮层
// 也拿到管理端变量；离开后台时移除，避免污染前台。
onMounted(() => document.body.classList.add('admin-mode'))
onBeforeUnmount(() => document.body.classList.remove('admin-mode'))
</script>

<style lang="less" scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  position: relative;
  isolation: isolate;
  overflow-x: clip;
  background: var(--admin-bg-secondary);
}

/* ============ 跳转链接 ============ */
.admin-skip {
  position: absolute;
  top: -60px;
  left: 24px;
  z-index: 200;
  padding: 10px 16px;
  background: var(--admin-text-primary);
  color: var(--admin-bg-primary);
  font-family: var(--admin-font);
  font-size: 13px;
  text-decoration: none;
  transition: top var(--admin-transition-fast);
}

.admin-skip:focus {
  top: 12px;
}

/* ============ 环境层 ============ */
.admin-atmosphere {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(120% 90% at 50% -20%, var(--admin-bg-primary) 0%, transparent 60%);
}

.admin-atmosphere__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, var(--admin-grid-line) 1px, transparent 1px),
    linear-gradient(to bottom, var(--admin-grid-line) 1px, transparent 1px);
  background-size: 88px 88px;
  mask-image: radial-gradient(ellipse 80% 65% at 50% 18%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 80% 65% at 50% 18%, #000 0%, transparent 78%);
}

.admin-atmosphere__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
}

.admin-atmosphere__glow--a {
  width: 52vw;
  height: 38vw;
  top: -18vw;
  right: -12vw;
  background: radial-gradient(circle, var(--admin-glow-a) 0%, transparent 70%);
}

.admin-atmosphere__glow--b {
  width: 34vw;
  height: 30vw;
  bottom: -14vw;
  left: 8vw;
  background: radial-gradient(circle, var(--admin-glow-b) 0%, transparent 70%);
}

/* ============ 主区 ============ */
.admin-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  margin-left: 252px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left var(--admin-transition-base);

  &.sidebar-collapsed {
    margin-left: 76px;
  }
}

.admin-content {
  flex: 1;
  padding: clamp(24px, 3.4vw, 44px) clamp(20px, 3.4vw, 44px) clamp(40px, 5vw, 72px);
  background: transparent;
  overflow-y: auto;
  outline: none;
}

@media (max-width: 1024px) {
  /* 侧栏固定成 76px 图标栏，展开时浮在内容之上 */
  .admin-main,
  .admin-main.sidebar-collapsed {
    margin-left: 76px;
  }
}

@media (max-width: 640px) {
  .admin-main,
  .admin-main.sidebar-collapsed {
    margin-left: 60px;
  }

  .admin-content {
    padding: 20px 14px 48px;
  }
}

/* ============ 页面切换 ============ */
.admin-route-enter-active {
  transition:
    opacity 0.36s var(--admin-ease),
    transform 0.36s var(--admin-ease);
}

.admin-route-leave-active {
  transition: opacity 0.12s ease;
}

.admin-route-enter-from {
  opacity: 0;
  transform: translateY(14px);
}

.admin-route-leave-to {
  opacity: 0;
}
</style>
