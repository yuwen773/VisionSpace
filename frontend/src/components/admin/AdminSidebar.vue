<template>
  <aside class="admin-sidebar" :class="{ collapsed: isCollapsed }">
    <!-- Logo 区域 -->
    <div class="sidebar-header">
      <router-link to="/" class="sidebar-logo">
        <img src="@/assets/logo.svg" alt="VisionSpace" class="logo-image" />
        <span v-show="!isCollapsed" class="logo-text">VisionSpace</span>
      </router-link>
      <button class="collapse-btn" @click="toggleCollapse">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path v-if="isCollapsed" d="M9 18l6-6-6-6" />
          <path v-else d="M15 18l-6-6 6-6" />
        </svg>
      </button>
    </div>

    <!-- 导航菜单 -->
    <nav class="sidebar-nav">
      <div class="nav-section">
        <span v-show="!isCollapsed" class="nav-section-title">管理</span>
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span v-show="!isCollapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </div>
    </nav>

    <!-- 底部用户信息 -->
    <div class="sidebar-footer">
      <div class="user-info" @click="handleUserClick">
        <a-avatar :src="loginUserStore.loginUser?.userAvatar" :size="36" />
        <div v-show="!isCollapsed" class="user-details">
          <span class="user-name">{{ loginUserStore.loginUser?.userAccount || 'Admin' }}</span>
          <span class="user-role">管理员</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import { ref, inject, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'

const loginUserStore = useLoginUserStore()
const route = useRoute()
const router = useRouter()

const sidebarCollapsed = inject<Ref<boolean>>('sidebarCollapsed', ref(false))
const isCollapsed = sidebarCollapsed

const menuItems = [
  { path: '/admin/dashboard', label: '仪表盘', icon: '📊' },
  { path: '/admin/user_manage', label: '用户管理', icon: '👥' },
  { path: '/admin/picture_manage', label: '图片管理', icon: '🖼️' },
  { path: '/admin/space_manage', label: '空间管理', icon: '☁️' },
  { path: '/admin/storage_config_manage', label: '存储配置', icon: '💾' },
]

const isActive = (path: string) => {
  return route.path.startsWith(path)
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const handleUserClick = () => {
  router.push('/my_space')
}
</script>

<style lang="less" scoped>
.admin-sidebar {
  width: 240px;
  height: 100vh;
  background: #0d1117;
  border-right: 1px solid #30363d;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;

  &.collapsed {
    width: 64px;

    .sidebar-header {
      padding: 16px 12px;
      justify-content: center;
    }

    .collapse-btn {
      position: static;
      margin-left: 0;
    }

    .nav-section-title {
      display: none;
    }

    .nav-item {
      justify-content: center;
      padding: 12px;
    }

    .user-info {
      justify-content: center;
      padding: 12px;
    }
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #30363d;
  min-height: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;

  .logo-image {
    width: 32px;
    height: 32px;
    border-radius: 8px;
  }

  .logo-text {
    font-family: var(--font-brand);
    font-size: 16px;
    font-weight: 700;
    color: #c9d1d9;
    white-space: nowrap;
  }
}

.collapse-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid #30363d;
  border-radius: 6px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;

  &:hover {
    background: #21262d;
    color: #c9d1d9;
    border-color: #8b949e;
  }

  svg {
    width: 16px;
    height: 16px;
  }
}

.sidebar-nav {
  flex: 1;
  padding: 16px 8px;
  overflow-y: auto;
}

.nav-section {
  margin-bottom: 24px;
}

.nav-section-title {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0 12px;
  margin-bottom: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  margin: 2px 0;
  border-radius: 6px;
  color: #c9d1d9;
  text-decoration: none;
  transition: all 0.15s ease;
  font-size: 14px;

  &:hover {
    background: #21262d;
    color: #ffffff;
  }

  &.active {
    background: #238636;
    color: #ffffff;

    .nav-icon {
      filter: grayscale(0);
    }
  }

  .nav-icon {
    font-size: 16px;
    flex-shrink: 0;
    filter: grayscale(0.3);
    transition: filter 0.15s ease;
  }

  .nav-label {
    white-space: nowrap;
  }
}

.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid #30363d;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: #21262d;
  }

  .user-details {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .user-name {
    font-size: 14px;
    font-weight: 600;
    color: #c9d1d9;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-role {
    font-size: 12px;
    color: #8b949e;
  }
}
</style>
