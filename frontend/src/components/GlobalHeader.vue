<template>
  <div id="globalHeader">
    <div class="header-content">
      <!-- Logo 区域 -->
      <div class="header-left">
        <router-link to="/" class="logo-link animate-wiggle">
          <div class="logo-wrapper">
            <img src="@/assets/logo.png" alt="VisionSpace" class="logo-image" />
            <div class="logo-shapes">
              <span class="logo-shape shape-1"></span>
              <span class="logo-shape shape-2"></span>
              <span class="logo-shape shape-3"></span>
            </div>
          </div>
          <div class="logo-text">
            <span class="logo-name">VisionSpace</span>
            <span class="logo-tag bg-sunshine">Space</span>
          </div>
        </router-link>
      </div>

      <!-- 导航菜单 -->
      <div class="header-center">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          :items="menuItems"
          :ellipsis="false"
          @click="doMenuClick"
          class="nav-menu"
        />
      </div>

      <!-- 用户区域 -->
      <div class="header-right">
        <div v-if="loginUserStore.loginUser.id" class="user-section">
          <a-dropdown :placement="'bottomRight'" class="user-dropdown">
            <div class="user-trigger pop-card animate-wiggle">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar" />
              <span class="user-name">{{ loginUserStore.loginUser.userAccount ?? 'User' }}</span>
              <span class="user-arrow">▼</span>
            </div>
            <template #overlay>
              <div class="user-dropdown-menu pop-card">
                <div class="user-dropdown-header">
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="56" />
                  <div class="user-dropdown-info">
                    <div class="user-dropdown-name">{{ loginUserStore.loginUser.userAccount }}</div>
                    <div class="user-dropdown-role">{{ getRoleText(loginUserStore.loginUser.userRole) }}</div>
                  </div>
                </div>
                <a-menu @click="handleUserMenuClick" class="user-menu">
                  <a-menu-item key="my_space">
                    <span>🏠</span>
                    <span>我的空间</span>
                  </a-menu-item>
                  <a-menu-divider />
                  <a-sub-menu key="theme" class="theme-submenu">
                    <template #title>
                      <span>{{ THEMES[currentTheme].icon }}</span>
                      <span>主题切换</span>
                    </template>
                    <a-menu-item
                      v-for="(themeInfo, key) in THEMES"
                      :key="key"
                      @click="setTheme(key)"
                      :class="{ 'theme-active': currentTheme === key }"
                    >
                      <span>{{ themeInfo.icon }}</span>
                      <span>{{ themeInfo.name }}</span>
                      <span v-if="currentTheme === key" class="theme-check">✓</span>
                    </a-menu-item>
                  </a-sub-menu>
                  <a-menu-divider />
                  <a-menu-item key="logout" class="logout-item">
                    <span>👋</span>
                    <span>退出登录</span>
                  </a-menu-item>
                </a-menu>
              </div>
            </template>
          </a-dropdown>
        </div>

        <div v-else class="auth-buttons">
          <a-button @click="goToLogin" class="pop-btn secondary">
            <span>🔑</span>
            <span>登录</span>
          </a-button>
          <a-button @click="goToRegister" class="pop-btn primary">
            <span>✨</span>
            <span>注册</span>
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { h, ref, computed, watchEffect } from 'vue'
import {
  HomeOutlined,
  AppstoreOutlined,
  CloudOutlined,
  LogoutOutlined,
  PictureOutlined,
  UserOutlined,
  TeamOutlined,
  CloudServerOutlined,
  SettingOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons-vue'
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { useTheme } from '@/composables/useTheme'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const current = ref<string[]>([])

// 主题管理
const { currentTheme, THEMES, toggleTheme, setTheme } = useTheme()

const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    if (menu?.key?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

const originItems = [
  {
    key: '/',
    label: '🏠 主页',
  },
  {
    key: '/add_picture',
    label: '📸 创建图片',
  },
  {
    key: '/add_space',
    label: '☁️ 创建空间',
    children: [
      {
        key: '/add_space',
        label: '🏠 私人空间',
      },
      {
        key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
        label: '👥 团队空间',
      },
    ],
  },
  {
    key: '/my_space',
    label: '💾 我的空间',
  },
  {
    key: '/admin',
    label: '⚙️ 管理',
    children: [
      {
        key: '/admin/user_manage',
        label: '👥 用户管理',
      },
      {
        key: '/admin/picture_manage',
        label: '🖼️ 图片管理',
      },
      {
        key: '/admin/space_manage',
        label: '☁️ 空间管理',
      },
      {
        key: '/admin/storage_config_manage',
        label: '☁️ 存储配置',
      },
    ],
  },
]

const teamSpaceList = ref<API.SpaceUserVO[]>([])

const fetchTeamSpaceList = async () => {
  try {
    const res = await listMyTeamSpaceUsingPost()
    teamSpaceList.value = res.data.data ?? []
  } catch (error) {
    console.error('获取团队空间列表失败', error)
  }
}

watchEffect(() => {
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

const menuItems = computed(() => {
  if (teamSpaceList.value.length < 1) {
    return originItems
  }

  const teamSpaceSubMenus = teamSpaceList.value.map((item) => ({
    key: '/space/' + item.spaceId,
    label: '🚀 ' + (item.space?.spaceName || '未知空间'),
  }))

  const teamSpaceGroupMenus = {
    key: '/space',
    label: '👥 我的团队',
    children: teamSpaceSubMenus,
  }

  return [...originItems.slice(0, -1), teamSpaceGroupMenus, ...originItems.slice(-1)]
})

const items = computed(() => filterMenus(menuItems.value))

const doMenuClick = (menuInfo) => {
  const url = menuInfo.key.split('?')[0]
  const queryString = menuInfo.key.split('?')[1]
  const searchParams = new URLSearchParams(queryString)
  const query = Object.fromEntries(searchParams.entries())

  router.push({
    path: url,
    query: query,
  })
}

router.afterEach((to, from) => {
  current.value = [to.fullPath]
})

const handleUserMenuClick = ({ key }) => {
  if (key === 'my_space') {
    router.push('/my_space')
  } else if (key === 'logout') {
    doLogout()
  }
}

const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setUserLogin({ userName: '未登录' })
    message.success('👋 再见！')
    await router.push('/user/login')
  } else {
    message.error(res.data.message)
  }
}

const getRoleText = (role: string) => {
  const roleMap: Record<string, string> = {
    admin: '👑 管理员',
    user: '👤 用户',
  }
  return roleMap[role] || '👤 用户'
}

const goToLogin = () => router.push('/user/login')
const goToRegister = () => router.push('/user/register')
</script>

<style lang="less" scoped>
#globalHeader {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--glass-border);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--space-3) var(--space-5);
  height: 70px;
}

/* ========== Logo 区域 ========== */
.header-left {
  flex-shrink: 0;
}

.logo-link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  text-decoration: none;
  transition: transform var(--transition-bounce);

  &:hover {
    transform: scale(1.05);
  }
}

.logo-wrapper {
  position: relative;
}

.logo-image {
  width: 44px;
  height: 44px;
  border: 3px solid var(--border-bold);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pop);
  transition: all var(--transition-bounce);

  &:hover {
    transform: rotate(-5deg);
  }
}

.logo-shapes {
  position: absolute;
  inset: -6px;
  pointer-events: none;
}

.logo-shape {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;

  &.shape-1 {
    background: var(--color-coral);
    top: 0;
    left: 0;
    animation: float 3s ease-in-out infinite;
  }

  &.shape-2 {
    background: var(--color-sunshine);
    top: 0;
    right: 0;
    animation: float 3s ease-in-out infinite 0.5s;
  }

  &.shape-3 {
    background: var(--color-mint);
    bottom: 0;
    left: 50%;
    transform: translateX(-50%);
    animation: float 3s ease-in-out infinite 1s;
  }
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-name {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.logo-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  border: 2px solid var(--border-bold);
  display: inline-block;
}

/* ========== 导航菜单 ========== */
.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 0 var(--space-6);
}

.nav-menu {
  background: transparent;
  border: none;
  font-family: var(--font-display);
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;

  :deep(.ant-menu-overflow) {
    justify-content: center;
    width: 100%;
  }
  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    color: var(--text-primary);
    font-weight: 700;
    font-size: var(--text-base);
    border-radius: var(--radius-full);
    padding: 10px 18px;
    margin: 0 var(--space-2);
    transition: all var(--transition-bounce);

    &:hover {
      background: var(--bg-tertiary);
      color: var(--color-coral);
      transform: translateY(-2px);
    }

    &.ant-menu-item-selected {
      background: var(--color-sky);
      border: 3px solid var(--border-bold);
      color: white;
      transform: translateY(-2px);
    }
  }

  :deep(.ant-menu-submenu-arrow) {
    color: var(--text-tertiary);
  }
}

/* ========== 用户区域 ========== */
.header-right {
  flex-shrink: 0;
}

.user-section {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-base);
  background: var(--glass-bg-light);
  backdrop-filter: var(--glass-blur-light);
  border: 1px solid var(--glass-border);

  &:hover {
    border-color: var(--color-primary-500);
    transform: translate(-2px, -2px);
    box-shadow: var(--shadow-glow-sm);
  }
}

.user-avatar {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border-bold);
}

.user-name {
  font-weight: 700;
  color: var(--text-primary);
  max-width: 100px;
}

.user-arrow {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ========== 用户下拉菜单 ========== */
.user-dropdown-menu {
  min-width: 240px;
  padding: var(--space-2);
}

.user-dropdown-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--gradient-aurora-subtle);
  border-bottom: 1px solid var(--glass-border);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

.user-dropdown-info {
  flex: 1;
}

.user-dropdown-name {
  font-weight: 700;
  color: var(--text-primary);
  font-size: var(--text-base);
}

.user-dropdown-role {
  font-size: var(--text-sm);
  color: white;
  font-weight: 600;
}

.user-menu {
  background: transparent;
  border: none;
  padding: var(--space-2);

  :deep(.ant-menu-item) {
    color: var(--text-primary);
    font-weight: 600;
  padding: 10px 16px;
  border-radius: var(--radius-lg);
  margin: var(--space-1) 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);

    &:hover {
      background: var(--bg-hover);
      color: var(--color-sky);
      transform: translateX(4px);
    }
  }

  .logout-item:hover {
    background: var(--color-coral-light);
    color: var(--color-coral-dark);
  }
}

/* ========== 主题切换菜单 ========== */
.theme-submenu {
  :deep(.ant-menu-submenu-title) {
    color: var(--text-primary);
    font-weight: 600;
    padding: 10px 16px;
    border-radius: var(--radius-lg);
    margin: var(--space-1) 0;
    display: flex;
    align-items: center;
    gap: var(--space-3);

    &:hover {
      background: var(--bg-hover);
      color: var(--color-sky);
      transform: translateX(4px);
    }
  }

  :deep(.ant-menu-sub) {
    background: var(--bg-secondary);
    border-radius: var(--radius-lg);
    padding: var(--space-2);
    margin-top: var(--space-2);
  }

  :deep(.ant-menu-item) {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    position: relative;

    &.theme-active {
      background: var(--color-sky);
      color: white;
      font-weight: 700;
    }

    .theme-check {
      margin-left: auto;
      font-weight: bold;
    }
  }
}

/* 主题切换动画 */
:deep(.ant-menu-item),
:deep(.ant-menu-submenu-title) {
  transition: all var(--transition-bounce);
}

/* ========== 认证按钮 ========== */
.auth-buttons {
  display: flex;
  gap: var(--space-3);
}

.pop-btn {
  height: 48px;
  padding: 0 var(--space-4);

  span:first-child {
    font-size: var(--text-lg);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .header-center {
    display: none;
  }

  .logo-text {
    display: none;
  }
}

@media (max-width: 640px) {
  .header-content {
    padding: var(--space-2) var(--space-4);
  }

  .auth-buttons {
    .pop-btn {
      padding: 0 var(--space-3);

      span:last-child {
        display: none;
      }
    }
  }
}

/* ========== 动画 ========== */
@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}
</style>
