<template>
  <header class="admin-header">
    <!-- 面包屑 -->
    <div class="header-left">
      <nav class="breadcrumb" aria-label="面包屑">
        <router-link to="/admin/dashboard" class="crumb-root">Admin</router-link>
        <template v-if="currentLabel">
          <span class="crumb-sep" aria-hidden="true">/</span>
          <span class="crumb-current">{{ currentLabel }}</span>
        </template>
      </nav>
    </div>

    <!-- 右侧 -->
    <div class="header-right">
      <button
        type="button"
        class="theme-btn"
        :aria-label="isLight ? '切换到极光深空主题' : '切换到紫漾浅色主题'"
        :title="isLight ? '切换到极光深空主题' : '切换到紫漾浅色主题'"
        @click="toggleTheme"
      >
        <Moon v-if="isLight" :size="15" :stroke-width="1.7" aria-hidden="true" />
        <Sun v-else :size="15" :stroke-width="1.7" aria-hidden="true" />
      </button>

      <span class="header-rule" aria-hidden="true"></span>

      <a-dropdown :placement="'bottomRight'" :trigger="['click']">
        <button type="button" class="user-trigger" aria-label="账号菜单">
          <a-avatar :src="loginUserStore.loginUser?.userAvatar" :size="30" />
          <span class="user-info">
            <span class="user-name">{{
              loginUserStore.loginUser?.userName || loginUserStore.loginUser?.userAccount || 'Admin'
            }}</span>
            <span class="user-role">Administrator</span>
          </span>
          <svg
            class="dropdown-arrow"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </button>
        <template #overlay>
          <div class="user-dropdown-menu">
            <div class="dropdown-header">
              <a-avatar :src="loginUserStore.loginUser?.userAvatar" :size="40" />
              <div class="dropdown-user-info">
                <div class="dropdown-user-name">
                  {{ loginUserStore.loginUser?.userName || loginUserStore.loginUser?.userAccount }}
                </div>
                <div class="dropdown-user-role">
                  <span class="role-badge">管理员</span>
                </div>
              </div>
            </div>
            <a-menu @click="handleMenuClick" class="user-menu">
              <a-menu-item key="my_space">
                <Home :size="15" :stroke-width="1.7" /> 我的空间
              </a-menu-item>
              <a-menu-item key="home">
                <Sparkles :size="15" :stroke-width="1.7" /> 返回首页
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" class="logout-item">
                <LogOut :size="15" :stroke-width="1.7" /> 退出登录
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
import { useTheme } from '@/composables/useTheme'
import { Home, Sparkles, LogOut, Moon, Sun } from 'lucide-vue-next'

const loginUserStore = useLoginUserStore()
const route = useRoute()
const router = useRouter()
const { currentTheme, toggleTheme } = useTheme()

const isLight = computed(() => currentTheme.value === 'pop')

const pageLabels: Record<string, string> = {
  '/admin/dashboard': '仪表盘',
  '/admin/user_manage': '用户管理',
  '/admin/picture_manage': '图片管理',
  '/admin/space_manage': '空间管理',
  '/admin/storage_config_manage': '存储配置',
  '/admin/feedback_manage': '反馈管理',
}

const currentLabel = computed(() => {
  const match = Object.entries(pageLabels).find(([path]) => route.path.startsWith(path))
  return match && match[0] !== '/admin/dashboard' ? match[1] : ''
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
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 clamp(16px, 2.6vw, 34px);
  background: color-mix(in srgb, var(--admin-bg-secondary) 82%, transparent);
  border-bottom: 1px solid var(--admin-border-default);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-family: var(--admin-font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.crumb-root {
  color: var(--admin-text-tertiary);
  text-decoration: none;
  transition: color var(--admin-transition-fast);

  &:hover {
    color: var(--admin-primary);
  }
}

.crumb-sep {
  color: var(--admin-text-disabled);
}

.crumb-current {
  color: var(--admin-text-primary);
  letter-spacing: 0.14em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-tertiary);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    border-color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    transform var(--admin-transition-fast);

  &:hover {
    color: var(--admin-text-primary);
    border-color: var(--admin-border-default);
    background: var(--admin-bg-hover);
    transform: rotate(-12deg);
  }
}

.header-rule {
  width: 1px;
  height: 20px;
  background: var(--admin-border-default);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 8px 5px 5px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-primary);
  font-family: var(--admin-font);
  cursor: pointer;
  transition:
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast);

  &:hover {
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-default);
  }

  :deep(.ant-avatar) {
    background: var(--admin-bg-active);
  }
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  text-align: left;
}

.user-name {
  max-width: 150px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--admin-text-primary);
}

.user-role {
  font-family: var(--admin-font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
}

.dropdown-arrow {
  width: 14px;
  height: 14px;
  color: var(--admin-text-tertiary);
  transition: transform var(--admin-transition-fast);
}

.user-trigger:hover .dropdown-arrow {
  transform: translateY(1px);
}

/* ============ 下拉 ============ */
.user-dropdown-menu {
  min-width: 250px;
  overflow: hidden;
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  box-shadow: var(--admin-shadow-md);
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--admin-border-subtle);
}

.dropdown-user-info {
  min-width: 0;
}

.dropdown-user-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--admin-serif);
  font-size: 16px;
  font-weight: 600;
  color: var(--admin-text-primary);
}

.dropdown-user-role {
  margin-top: 5px;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid color-mix(in srgb, var(--admin-primary) 30%, transparent);
  border-radius: var(--admin-radius-sm);
  background: var(--admin-primary-bg);
  color: var(--admin-primary);
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.user-menu {
  padding: 6px;
  background: transparent !important;

  :deep(.ant-dropdown-menu-item) {
    gap: 10px;
    color: var(--admin-text-primary) !important;
    border-radius: var(--admin-radius-sm);
    font-size: 13px;

    &:hover {
      background: var(--admin-bg-hover) !important;
    }
  }

  :deep(.ant-dropdown-menu-item-divider) {
    margin: 6px 0;
    background: var(--admin-border-subtle) !important;
  }

  .logout-item {
    color: var(--admin-danger) !important;

    &:hover {
      background: var(--admin-danger-bg) !important;
    }
  }
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .admin-header {
    padding: 0 14px;
  }

  .user-name {
    max-width: 100px;
  }
}

@media (max-width: 640px) {
  .user-info {
    display: none;
  }

  .user-trigger {
    padding: 4px;
  }

  .header-rule {
    display: none;
  }
}
</style>
