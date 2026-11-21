<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <AdminSidebar ref="sidebarRef" />

    <!-- 主内容区 -->
    <div class="admin-main" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <!-- 顶部导航 -->
      <AdminHeader />

      <!-- 页面内容 -->
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, provide, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'

// 引入管理端主题
import '@/styles/admin-theme.css'

const sidebarRef = ref<InstanceType<typeof AdminSidebar> | null>(null)
// 窄屏默认收成图标栏：240px 的展开态会盖住大部分表格
const isNarrowViewport = () => window.matchMedia('(max-width: 1024px)').matches
const sidebarCollapsed = ref(isNarrowViewport())

// 可以在子组件中注入侧边栏折叠状态
provide('sidebarCollapsed', sidebarCollapsed)

// 窄屏下跳转后收起侧栏，避免浮层一直挡在内容上
const route = useRoute()
watch(
  () => route.path,
  () => {
    if (isNarrowViewport()) sidebarCollapsed.value = true
  },
)
</script>

<style lang="less" scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--admin-bg-primary);
}

.admin-main {
  flex: 1;
  margin-left: 240px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.2s ease;

  &.sidebar-collapsed {
    margin-left: 64px;
  }
}

@media (max-width: 1024px) {
  /* 侧栏固定 64px 图标栏，展开时浮在内容之上 */
  .admin-main,
  .admin-main.sidebar-collapsed {
    margin-left: 64px;
  }

  .admin-content {
    padding: var(--admin-space-4);
  }
}

@media (max-width: 640px) {
  .admin-content {
    padding: var(--admin-space-3);
  }
}

.admin-content {
  flex: 1;
  padding: var(--admin-space-6);
  background: var(--admin-bg-secondary);
  overflow-y: auto;
}
</style>
