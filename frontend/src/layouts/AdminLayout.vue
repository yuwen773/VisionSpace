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
import { ref, provide } from 'vue'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'

// 引入管理端主题
import '@/styles/admin-theme.css'

const sidebarRef = ref<InstanceType<typeof AdminSidebar> | null>(null)
const sidebarCollapsed = ref(false)

// 可以通过 provide 向子组件传递侧边栏折叠状态
provide('sidebarCollapsed', sidebarCollapsed)
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

.admin-content {
  flex: 1;
  padding: var(--admin-space-6);
  background: var(--admin-bg-secondary);
  overflow-y: auto;
}
</style>
