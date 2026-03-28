<template>
  <header class="admin-header">
    <!-- 面包屑导航 -->
    <div class="header-left">
      <nav class="breadcrumb">
        <router-link to="/admin/dashboard" class="breadcrumb-item">
          <span class="breadcrumb-icon">🏠</span>
          <span class="breadcrumb-text">首页</span>
        </router-link>
        <template v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
          <span class="breadcrumb-separator">/</span>
          <router-link
            :to="crumb.path"
            class="breadcrumb-item"
            :class="{ active: index === breadcrumbs.length - 1 }"
          >
            <span class="breadcrumb-text">{{ crumb.label }}</span>
          </router-link>
        </template>
      </nav>
    </div>

    <!-- 右侧用户信息 -->
    <div class="header-right">
      <!-- 搜索框 -->
      <div class="search-box">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8" />
          <path d="M21 21l-4.35-4.35" />
        </svg>
        <input type="text" placeholder="搜索..." class="search-input" />
        <span class="search-shortcut">⌘K</span>
      </div>

      <!-- 通知图标 -->
      <button class="header-icon-btn">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        <span class="notification-badge">3</span>
      </button>

      <!-- 用户下拉菜单 -->
      <a-dropdown :placement="'bottomRight'">
        <div class="user-trigger">
          <a-avatar :src="loginUserStore.loginUser?.userAvatar" :size="32" />
          <div class="user-info">
            <span class="user-name">{{ loginUserStore.loginUser?.userAccount || 'Admin' }}</span>
            <span class="user-role">管理员</span>
          </div>
          <svg class="dropdown-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 9l6 6 6-6" />
          </svg>
        </div>
        <template #overlay>
          <div class="user-dropdown-menu">
            <div class="dropdown-header">
              <div class="dropdown-avatar-wrapper">
                <a-avatar :src="loginUserStore.loginUser?.userAvatar" :size="48" />
              </div>
              <div class="dropdown-user-info">
                <div class="dropdown-user-name">{{ loginUserStore.loginUser?.userAccount }}</div>
                <div class="dropdown-user-role">
                  <span class="role-badge">管理员</span>
                </div>
              </div>
            </div>
            <a-menu @click="handleMenuClick" class="user-menu">
              <a-menu-item key="my_space">
                <span>🏠</span> 我的空间
              </a-menu-item>
              <a-menu-item key="home">
                <span>✨</span> 返回首页
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" class="logout-item">
                <span>👋</span> 退出登录
              </a-menu-item>
            </a-menu>
          </div>
        </template>
      </a-dropdown>
    </div>
  </header>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/userLogin'
import { userLogoutUsingPost } from '@/api/userController'

const loginUserStore = useLoginUserStore()
const route = useRoute()
const router = useRouter()

const menuLabels: Record<string, string> = {
  '/admin/dashboard': '仪表盘',
  '/admin/user_manage': '用户管理',
  '/admin/picture_manage': '图片管理',
  '/admin/space_manage': '空间管理',
  '/admin/storage_config_manage': '存储配置',
}

const breadcrumbs = computed(() => {
  const path = route.path
  const crumbs = []

  for (const [menuPath, label] of Object.entries(menuLabels)) {
    if (path.startsWith(menuPath) && menuPath !== '/admin/dashboard') {
      crumbs.push({ path: menuPath, label })
      break
    }
  }

  return crumbs
})

const handleMenuClick = async ({ key }: { key: string }) => {
  if (key === 'my_space') {
    router.push('/my_space')
  } else if (key === 'home') {
    router.push('/')
  } else if (key === 'logout') {
    await doLogout()
  }
}

const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setUserLogin({ userName: '未登录' })
    message.success('再见！')
    await router.push('/user/login')
  } else {
    message.error(res.data.message)
  }
}
</script>

<style lang="less" scoped>
.admin-header {
  height: 64px;
  background: #0d1117;
  border-bottom: 1px solid #30363d;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8b949e;
  text-decoration: none;
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.15s ease;

  &:hover {
    background: #21262d;
    color: #c9d1d9;
  }

  &.active {
    color: #c9d1d9;
    background: transparent;
  }

  .breadcrumb-icon {
    font-size: 14px;
  }
}

.breadcrumb-separator {
  color: #484f58;
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 8px 12px;
  transition: all 0.15s ease;

  &:focus-within {
    border-color: #58a6ff;
    box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
  }

  .search-icon {
    width: 16px;
    height: 16px;
    color: #8b949e;
    flex-shrink: 0;
  }

  .search-input {
    background: transparent;
    border: none;
    outline: none;
    color: #c9d1d9;
    font-size: 14px;
    width: 180px;

    &::placeholder {
      color: #484f58;
    }
  }

  .search-shortcut {
    font-size: 11px;
    color: #484f58;
    background: #21262d;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid #30363d;
  }
}

.header-icon-btn {
  position: relative;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: #21262d;
    color: #c9d1d9;
    border-color: #30363d;
  }

  svg {
    width: 20px;
    height: 20px;
  }

  .notification-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    min-width: 16px;
    height: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f85149;
    color: white;
    font-size: 10px;
    font-weight: 600;
    border-radius: 8px;
    padding: 0 4px;
  }
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: #21262d;
  }

  .user-info {
    display: flex;
    flex-direction: column;

    .user-name {
      font-size: 14px;
      font-weight: 600;
      color: #c9d1d9;
      line-height: 1.2;
    }

    .user-role {
      font-size: 12px;
      color: #8b949e;
    }
  }

  .dropdown-arrow {
    width: 16px;
    height: 16px;
    color: #8b949e;
  }
}

.user-dropdown-menu {
  min-width: 240px;
  background: #161b22 !important;
  border: 1px solid #30363d;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #0d1117;
  border-bottom: 1px solid #30363d;

  .dropdown-avatar-wrapper {
    flex-shrink: 0;
  }

  .dropdown-user-info {
    min-width: 0;
  }

  .dropdown-user-name {
    font-size: 16px;
    font-weight: 600;
    color: #c9d1d9;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .dropdown-user-role {
    margin-top: 4px;
  }

  .role-badge {
    display: inline-flex;
    align-items: center;
    font-size: 12px;
    font-weight: 500;
    padding: 2px 8px;
    background: rgba(35, 134, 54, 0.2);
    color: #3fb950;
    border-radius: 12px;
    border: 1px solid rgba(35, 134, 54, 0.3);
  }
}

.user-menu {
  background: transparent !important;
  padding: 8px !important;

  :deep(.ant-dropdown-menu-item) {
    color: #c9d1d9 !important;
    padding: 10px 12px;
    border-radius: 6px;
    font-size: 14px;

    &:hover {
      background: #21262d !important;
    }

    span {
      margin-right: 8px;
    }
  }

  :deep(.ant-dropdown-menu-item-divider) {
    background: #30363d !important;
    margin: 8px 0;
  }

  .logout-item {
    color: #f85149 !important;

    &:hover {
      background: rgba(248, 81, 73, 0.1) !important;
    }
  }
}
</style>
