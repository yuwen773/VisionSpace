<template>
  <header id="cosmic-header">
    <!-- 背景粒子画布 -->
    <canvas ref="particleCanvas" class="header-particles"></canvas>

    <!-- 渐变光晕层 -->
    <div class="header-aurora">
      <div class="aurora-orb orb-1"></div>
      <div class="aurora-orb orb-2"></div>
      <div class="aurora-orb orb-3"></div>
    </div>

    <!-- 主内容 -->
    <div class="header-content">
      <!-- Logo 区域 -->
      <div class="header-left">
        <router-link to="/" class="cosmic-logo">
          <div class="logo-container">
            <!-- 旋转光环 -->
            <div class="logo-ring">
              <svg viewBox="0 0 60 60" class="ring-svg">
                <defs>
                  <linearGradient id="ringGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#a855f7"/>
                    <stop offset="50%" stop-color="#ec4899"/>
                    <stop offset="100%" stop-color="#f472b6"/>
                  </linearGradient>
                </defs>
                <circle cx="30" cy="30" r="28" fill="none" stroke="url(#ringGradient)" stroke-width="2" stroke-dasharray="8 4"/>
              </svg>
            </div>
            <!-- Logo 图标 -->
            <div class="logo-icon">
              <img src="@/assets/logo.svg" alt="VisionSpace" class="logo-image" />
            </div>
            <!-- 浮动光点 -->
            <div class="logo-particles">
              <span class="particle p1"></span>
              <span class="particle p2"></span>
              <span class="particle p3"></span>
              <span class="particle p4"></span>
            </div>
          </div>
          <div class="logo-text">
            <span class="logo-name">VisionSpace</span>
          </div>
        </router-link>
      </div>

      <!-- 导航菜单 -->
      <nav class="header-center">
        <div class="nav-container">
          <a-menu
            v-model:selectedKeys="current"
            mode="horizontal"
            :items="items"
            :ellipsis="false"
            @click="doMenuClick"
            class="cosmic-nav-menu"
          />
        </div>
      </nav>

      <!-- 用户区域 -->
      <div class="header-right">
        <!-- 主题切换按钮 -->
        <div class="theme-toggle-wrapper" @click="toggleTheme" :title="currentTheme === 'aurora' ? '切换到浅色主题' : '切换到极光主题'">
          <div class="theme-toggle-pill" :class="currentTheme">
            <span class="theme-icon-item sun"><Sun :size="14" /></span>
            <span class="theme-icon-item moon"><Moon :size="14" /></span>
            <div class="theme-toggle-slider"></div>
          </div>
        </div>

        <div v-if="loginUserStore.loginUser.id" class="user-section">
          <a-dropdown :placement="'bottomRight'" class="cosmic-dropdown">
            <div class="user-trigger">
              <div class="user-avatar-wrapper">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar" />
                <span class="avatar-ring"></span>
              </div>
              <div class="user-info">
                <span class="user-name">{{ loginUserStore.loginUser.userAccount ?? 'User' }}</span>
                <span class="user-role">
                  <span class="role-icon"><Crown v-if="isAdmin" :size="12" /><User v-else :size="12" /></span>
                </span>
              </div>
              <span class="user-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="arrow-icon">
                  <path d="M6 9l6 6 6-6"/>
                </svg>
              </span>
            </div>
            <template #overlay>
              <div class="cosmic-dropdown-menu">
                <!-- 用户信息头部 -->
                <div class="dropdown-header">
                  <div class="dropdown-avatar-wrapper">
                    <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="64" class="dropdown-avatar" />
                    <div class="dropdown-avatar-glow"></div>
                  </div>
                  <div class="dropdown-user-info">
                    <div class="dropdown-user-name">{{ loginUserStore.loginUser.userAccount }}</div>
                    <div class="dropdown-user-role">
                      <span class="role-badge" :class="loginUserStore.loginUser.userRole">
                        {{ getRoleText(loginUserStore.loginUser.userRole) }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- 菜单项 -->
                <div class="dropdown-menu-content">
                  <a-menu @click="handleUserMenuClick" class="user-menu">
                    <a-menu-item key="my_space" class="menu-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon"><Home :size="16" /></span>
                        <span class="menu-text">我的空间</span>
                      </div>
                      <div class="menu-item-shine"></div>
                    </a-menu-item>
                    <a-menu-item key="user_center" class="menu-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon"><User :size="16" /></span>
                        <span class="menu-text">用户中心</span>
                      </div>
                      <div class="menu-item-shine"></div>
                    </a-menu-item>
                    <a-menu-item v-if="isAdmin" key="admin" class="menu-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon"><Settings :size="16" /></span>
                        <span class="menu-text">后台管理</span>
                      </div>
                      <div class="menu-item-shine"></div>
                    </a-menu-item>
                    <a-menu-divider class="menu-divider" />
                    <a-menu-item key="logout" class="menu-item logout-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon"><LogOut :size="16" /></span>
                        <span class="menu-text">退出登录</span>
                      </div>
                      <div class="menu-item-shine logout-shine"></div>
                    </a-menu-item>
                  </a-menu>
                </div>
              </div>
            </template>
          </a-dropdown>
        </div>

        <!-- 未登录状态 -->
        <div v-else class="auth-buttons">
          <button @click="goToLogin" class="cosmic-btn secondary">
            <span class="btn-icon"><KeyRound :size="16" /></span>
            <span class="btn-text">登录</span>
            <span class="btn-glow"></span>
          </button>
          <button @click="goToRegister" class="cosmic-btn primary">
            <span class="btn-icon"><Sparkles :size="16" /></span>
            <span class="btn-text">注册</span>
            <span class="btn-glow"></span>
          </button>
        </div>

        <!-- 汉堡菜单按钮（移动端） -->
        <button class="hamburger-btn" @click="toggleMobileMenu" :class="{ active: mobileMenuVisible }">
          <span class="hamburger-line"></span>
          <span class="hamburger-line"></span>
          <span class="hamburger-line"></span>
        </button>
      </div>
    </div>

    <!-- 移动端抽屉菜单 -->
    <a-drawer
      v-model:open="mobileMenuVisible"
      placement="right"
      width="280"
      :drawerStyle="{ background: 'var(--bg-primary)' }"
      class="mobile-drawer"
    >
      <template #title>
        <div class="drawer-header">
          <span class="drawer-title">菜单</span>
        </div>
      </template>
      <div class="mobile-menu-content">
        <a-menu
          v-model:selectedKeys="current"
          mode="vertical"
          :items="items"
          @click="handleMobileMenuClick"
          class="mobile-nav-menu"
        />
      </div>
    </a-drawer>

    <!-- 底部渐变边框 -->
    <div class="header-border">
      <div class="border-gradient"></div>
    </div>
  </header>
</template>

<script lang="ts" setup>
import { ref, computed, watchEffect, onMounted, onUnmounted, h } from 'vue'
import type { MenuProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { Sun, Moon, Crown, User, Home, MessageSquare, Settings, LogOut, KeyRound, Sparkles, Camera, Cloud, Users, Rocket } from 'lucide-vue-next'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { useTheme } from '@/composables/useTheme'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const current = ref<string[]>([])
const particleCanvas = ref<HTMLCanvasElement | null>(null)
const mobileMenuVisible = ref(false)

// 粒子动画
let particleAnimationId: number | null = null

// 主题管理
const { currentTheme, THEMES, setTheme, toggleTheme } = useTheme()

const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const key = String(menu?.key ?? '')
    if (key.startsWith('/admin')) {
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
    label: '主页',
    icon: () => h(Home, { size: 16 }),
  },
  {
    key: '/agent',
    label: '✦ SpaceMind',
  },
  {
    key: '/add_picture',
    label: '创建图片',
    icon: () => h(Camera, { size: 16 }),
  },
  {
    key: '/add_space',
    label: '创建空间',
    icon: () => h(Cloud, { size: 16 }),
    children: [
      {
        key: '/add_space',
        label: '私人空间',
        icon: () => h(Home, { size: 16 }),
      },
      {
        key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
        label: '团队空间',
        icon: () => h(Users, { size: 16 }),
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
    label: item.space?.spaceName || '未知空间',
    icon: () => h(Rocket, { size: 16 }),
  }))

  const teamSpaceGroupMenus = {
    key: '/space',
    label: '我的团队',
    icon: () => h(Users, { size: 16 }),
    children: teamSpaceSubMenus,
  }

  return [...originItems.slice(0, -1), teamSpaceGroupMenus, ...originItems.slice(-1)]
})

const items = computed(() => filterMenus(menuItems.value))

const PARENT_MENU_KEYS = new Set(['add_space_submenu', 'team_space_submenu'])

const doMenuClick = (menuInfo: { key: string, keyPath?: string[] }) => {
  // 如果是父菜单项（有子菜单），只展开子菜单，不导航
  if (PARENT_MENU_KEYS.has(menuInfo.key)) {
    return
  }

  const [url, queryString] = menuInfo.key.split('?')
  const query = queryString
    ? Object.fromEntries(new URLSearchParams(queryString).entries())
    : {}

  router.push({
    path: url,
    query: query,
  })
}

// 移动端菜单
const toggleMobileMenu = () => {
  mobileMenuVisible.value = !mobileMenuVisible.value
}

const handleMobileMenuClick = (menuInfo: { key: string, keyPath?: string[] }) => {
  mobileMenuVisible.value = false
  doMenuClick(menuInfo)
}

router.afterEach((to) => {
  if (current.value[0] !== to.fullPath) {
    current.value = [to.fullPath]
  }
})

const handleUserMenuClick = ({ key }: { key: string }) => {
  if (key === 'my_space') {
    router.push('/my_space')
  } else if (key === 'user_center') {
    router.push('/user/center')
  } else if (key === 'admin') {
    router.push('/admin')
  } else if (key === 'logout') {
    doLogout()
  }
}

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

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

const getRoleText = (role?: string) => {
  const roleMap: Record<string, string> = {
    admin: '管理员',
    user: '用户',
  }
  return roleMap[role ?? ''] || '用户'
}

const goToLogin = () => router.push('/user/login')
const goToRegister = () => router.push('/user/register')

// 粒子动画
let resizeCanvas: (() => void) | null = null

const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  resizeCanvas = () => {
    canvas.width = canvas.offsetWidth
    canvas.height = canvas.offsetHeight
  }
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)

  interface Particle {
    x: number
    y: number
    size: number
    speedX: number
    speedY: number
    opacity: number
    twinkle: number
  }

  const particles: Particle[] = []
  const particleCount = 25

  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      size: Math.random() * 2 + 0.5,
      speedX: (Math.random() - 0.5) * 0.3,
      speedY: (Math.random() - 0.5) * 0.3,
      opacity: Math.random() * 0.5 + 0.2,
      twinkle: Math.random() * Math.PI * 2,
    })
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    particles.forEach((p) => {
      p.x += p.speedX
      p.y += p.speedY
      p.twinkle += 0.02

      if (p.x < 0) p.x = canvas.width
      if (p.x > canvas.width) p.x = 0
      if (p.y < 0) p.y = canvas.height
      if (p.y > canvas.height) p.y = 0

      const currentOpacity = p.opacity * (0.5 + 0.5 * Math.sin(p.twinkle))

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(168, 85, 247, ${currentOpacity})`
      ctx.fill()

      // 光晕效果
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(168, 85, 247, ${currentOpacity * 0.2})`
      ctx.fill()
    })

    particleAnimationId = requestAnimationFrame(animate)
  }

  animate()
}

onMounted(() => {
  initParticles()
})

onUnmounted(() => {
  if (particleAnimationId) {
    cancelAnimationFrame(particleAnimationId)
  }
  if (resizeCanvas) {
    window.removeEventListener('resize', resizeCanvas)
  }
})
</script>

<style lang="less" scoped>
/* ========== 头部容器 ========== */
#cosmic-header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  height: 72px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--glass-border);
}

/* ========== 粒子画布 ========== */
.header-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  opacity: 0.7;
}

/* ========== 极光光晕层 ========== */
.header-aurora {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.aurora-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.15;
  animation: auroraFloat 20s ease-in-out infinite;
}

.orb-1 {
  width: 300px;
  height: 150px;
  background: linear-gradient(135deg, #a855f7, #ec4899);
  top: -50px;
  left: 10%;
  animation-delay: 0s;
}

.orb-2 {
  width: 250px;
  height: 120px;
  background: linear-gradient(135deg, #ec4899, #f472b6);
  top: -30px;
  right: 20%;
  animation-delay: -7s;
}

.orb-3 {
  width: 200px;
  height: 100px;
  background: linear-gradient(135deg, #a855f7, #f472b6);
  top: -40px;
  left: 50%;
  animation-delay: -14s;
}

@keyframes auroraFloat {
  0%, 100% {
    transform: translateX(0) translateY(0) scale(1);
  }
  33% {
    transform: translateX(30px) translateY(10px) scale(1.05);
  }
  66% {
    transform: translateX(-20px) translateY(5px) scale(0.95);
  }
}

/* ========== 主内容布局 ========== */
.header-content {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-5);
  height: 100%;
}

/* ========== Logo 区域 ========== */
.header-left {
  flex-shrink: 0;
}

.cosmic-logo {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  text-decoration: none;
  transition: transform var(--transition-bounce);

  &:hover {
    transform: scale(1.03);

    .logo-ring svg {
      transform: rotate(180deg);
    }

    .particle {
      animation-play-state: running;
    }
  }
}

.logo-container {
  position: relative;
  width: 52px;
  height: 52px;
}

.logo-ring {
  position: absolute;
  inset: -4px;

  .ring-svg {
    width: 100%;
    height: 100%;
    animation: ringRotate 20s linear infinite;
  }
}

.logo-icon {
  position: absolute;
  inset: 6px;

  .logo-image {
    width: 40px;
    height: 40px;
    border-radius: var(--radius-lg);
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);
    transition: all var(--transition-bounce);
  }
}

.logo-particles {
  position: absolute;
  inset: 0;

  .particle {
    position: absolute;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    animation: particleFloat 3s ease-in-out infinite;

    &.p1 {
      background: #a855f7;
      top: 2px;
      left: 2px;
      animation-delay: 0s;
      box-shadow: 0 0 10px #a855f7;
    }

    &.p2 {
      background: #6e35eb;
      top: 2px;
      right: 2px;
      animation-delay: 0.75s;
      box-shadow: 0 0 10px #6e35eb;
    }

    &.p3 {
      background: #a855f7;
      bottom: 2px;
      left: 50%;
      transform: translateX(-50%);
      animation-delay: 1.5s;
      box-shadow: 0 0 10px #a855f7;
    }

    &.p4 {
      background: #ff6b9d;
      bottom: 2px;
      right: 2px;
      animation-delay: 2.25s;
      box-shadow: 0 0 10px #ff6b9d;
    }
  }
}

@keyframes ringRotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes particleFloat {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
  50% {
    transform: translateY(-6px) scale(1.2);
    opacity: 0.7;
  }
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-name {
  font-family: var(--font-brand);
  font-size: 1.25rem;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 50%, #f472b6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;


  &::before {
    content: '';
    width: 16px;
    height: 2px;
    background: linear-gradient(90deg, var(--color-primary-500), var(--color-accent-purple));
    border-radius: 2px;
  }
}

/* ========== 导航菜单 ========== */
.header-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 var(--space-6);
}

.nav-container {
  position: relative;
  z-index: 100;
  flex: 1;
  width: 100%;
  display: flex;
  justify-content: center;
}

.cosmic-nav-menu {
  background: transparent;
  border: none;
  font-family: var(--font-display);
  display: flex;
  justify-content: center;
  flex-wrap: nowrap;
  min-width: 0;
  flex: 1;

  :deep(.ant-menu-overflow) {
    justify-content: center;
  }

  :deep(.ant-menu-overflow-item) {
    flex-shrink: 0;
  }

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    color: var(--text-secondary);
    font-weight: 600;
    font-size: 15px;
    padding: 10px 20px;
    margin: 0 6px;
    border-radius: var(--radius-full);
    transition: all var(--transition-bounce);
    position: relative;
    overflow: visible !important;
    white-space: nowrap;
    flex-shrink: 0;

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(168, 85, 247, 0.15), rgba(236, 72, 153, 0.15));
      opacity: 0;
      transition: opacity var(--transition-base);
      border-radius: inherit;
      pointer-events: none;
    }

    &:hover {
      color: var(--text-primary);
      transform: translateY(-2px);

      &::before {
        opacity: 1;
      }

      .menu-icon {
        transform: scale(1.1);
      }
    }

    &.ant-menu-item-selected {
      color: var(--text-primary);
      background: linear-gradient(135deg, rgba(168, 85, 247, 0.25), rgba(236, 72, 153, 0.25));
      border: 1px solid rgba(168, 85, 247, 0.4);
      box-shadow: 0 0 20px rgba(168, 85, 247, 0.2), inset 0 0 20px rgba(168, 85, 247, 0.1);

      &::before {
        opacity: 1;
        background: linear-gradient(135deg, rgba(168, 85, 247, 0.3), rgba(236, 72, 153, 0.3));
      }

      &::after {
        content: '';
        position: absolute;
        bottom: 4px;
        left: 50%;
        transform: translateX(-50%);
        width: 20px;
        height: 3px;
        background: linear-gradient(90deg, var(--color-primary-500), var(--color-accent-purple));
        border-radius: 2px;
        box-shadow: 0 0 10px var(--color-primary-500);
      }
    }
  }

  :deep(.ant-menu-submenu-arrow) {
    color: var(--text-tertiary);
  }
}

/* ========== 用户区域 ========== */
.header-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.user-section {
  display: flex;
  align-items: center;
}

.cosmic-dropdown {
  :deep(.ant-dropdown-trigger) {
    outline: none;
  }
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-bounce);
  background: var(--bg-secondary);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border-default);

  &:hover {
    border-color: var(--color-primary);
    transform: translateY(-2px);
    box-shadow: var(--shadow-card);

    .avatar-ring {
      transform: scale(1.1) rotate(45deg);
      opacity: 1;
    }

    .user-avatar {
      transform: scale(1.05);
    }
  }
}

.user-avatar-wrapper {
  position: relative;
  width: 40px;
  height: 40px;

  .user-avatar {
    width: 36px;
    height: 36px;
    border-radius: var(--radius-lg);
    border: 2px solid var(--border-default);
    transition: all var(--transition-bounce);
    box-shadow: var(--shadow-sm);
  }

  .avatar-ring {
    position: absolute;
    inset: -2px;
    border-radius: var(--radius-lg);
    border: 2px solid transparent;
    background: linear-gradient(135deg, var(--color-primary), var(--color-secondary)) border-box;
    mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
    mask-composite: exclude;
    opacity: 0.5;
    transition: all var(--transition-bounce);
  }
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 700;
  color: var(--text-primary);
  font-size: 14px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: var(--text-tertiary);
}

.user-arrow {
  color: var(--text-tertiary);
  transition: all var(--transition-base);

  .arrow-icon {
    width: 16px;
    height: 16px;
  }
}

/* ========== 下拉菜单 ========== */
.cosmic-dropdown-menu {
  min-width: 280px;
  background: var(--bg-card) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);

  /* 确保 Ant Design 菜单文字可见 */
  :deep(.ant-dropdown-menu) {
    background: transparent !important;
    padding: 8px;
  }

  :deep(.ant-dropdown-menu-item) {
    color: var(--text-primary) !important;
    border-radius: 8px;
    padding: 10px 14px;

    &:hover {
      background: var(--bg-hover) !important;
    }
  }

  :deep(.ant-dropdown-menu-item-divider) {
    background: var(--border-subtle) !important;
  }
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-5);
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-subtle);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--color-primary), var(--color-secondary), transparent);
  }
}

.dropdown-avatar-wrapper {
  position: relative;

  .dropdown-avatar {
    border: 2px solid var(--border-default);
    box-shadow: var(--shadow-sm);
  }

  .dropdown-avatar-glow {
    position: absolute;
    inset: -8px;
    background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%);
    filter: blur(10px);
    z-index: -1;
  }
}

.dropdown-user-info {
  flex: 1;
}

.dropdown-user-name {
  font-weight: 700;
  color: var(--text-primary) !important;
  font-size: 16px;
  font-family: var(--font-display);
}

.dropdown-user-role {
  margin-top: 4px;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: rgba(168, 85, 247, 0.1);
  color: var(--color-primary);
  border: 1px solid rgba(168, 85, 247, 0.2);

  &.admin {
    background: rgba(168, 85, 247, 0.15);
    color: var(--color-accent-purple);
    border-color: rgba(168, 85, 247, 0.3);
  }
}

.dropdown-menu-content {
  padding: var(--space-3);
}

.user-menu {
  background: transparent;
  border: none;

  :deep(.ant-menu-item) {
    padding: 0;
    background: transparent;
  }
}

.menu-item {
  border-radius: var(--radius-lg);
  margin: 4px 0;
  height: auto;
  line-height: 1.5;

  &:hover {
    background: var(--bg-hover) !important;
    transform: none !important;
  }
}

.menu-item-inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  position: relative;
  overflow: hidden;
  transition: all var(--transition-base);

  .menu-icon {
    font-size: 16px;
    transition: transform var(--transition-bounce);
  }

  .menu-text {
    font-weight: 600;
    color: var(--text-primary) !important;
    font-size: 14px;
  }
}

.menu-item-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(168, 85, 247, 0.1), transparent);
  transition: left 0.5s ease;

  .menu-item:hover & {
    left: 100%;
  }
}

.menu-divider {
  border-color: var(--glass-border);
  margin: var(--space-2) 0;
}

/* 主题子菜单 */
.theme-submenu {
  :deep(.ant-menu-submenu-title) {
    padding: 0 !important;
    margin: 0;

    &:hover {
      background: transparent !important;
    }
  }

  :deep(.ant-menu-sub) {
    background: var(--bg-card) !important;
    border-radius: var(--radius-lg);
    padding: var(--space-2);
    margin-top: 4px;
    border: 1px solid var(--border-default);
  }
}

.theme-options {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.theme-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 8px 12px;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);

  .theme-icon {
    font-size: 16px;
    filter: drop-shadow(0 0 3px rgba(255, 255, 255, 0.3));
  }

  .theme-name {
    flex: 1;
    font-weight: 600;
    color: var(--text-primary) !important;
    font-size: 13px;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  }

  .theme-check {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--color-primary-500), var(--color-accent-purple));
    border-radius: 50%;
    color: white;

    svg {
      width: 12px;
      height: 12px;
    }
  }

  &.theme-active {
    background: rgba(168, 85, 247, 0.2) !important;
  }

  &:hover:not(.theme-active) {
    background: var(--bg-hover);
  }
}

.logout-item {
  &:hover {
    background: rgba(244, 63, 94, 0.1) !important;

    .menu-text {
      color: var(--color-rose);
    }

    .logout-shine {
      background: linear-gradient(90deg, transparent, rgba(244, 63, 94, 0.15), transparent);
    }
  }

  .logout-shine {
    background: linear-gradient(90deg, transparent, rgba(244, 63, 94, 0.1), transparent);
  }
}

/* ========== 主题切换按钮 ========== */
.theme-toggle-wrapper {
  margin-right: var(--space-5);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-full);
  background: rgba(0, 0, 0, 0.05);
  border: 1px solid var(--glass-border);
  transition: all var(--transition-base);
  height: 38px;
  width: 72px;
  display: flex;
  align-items: center;
  position: relative;
  flex-shrink: 0;

  &:hover {
    border-color: var(--color-primary-400);
    box-shadow: 0 0 10px rgba(168, 85, 247, 0.1);
  }
}

.theme-toggle-pill {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
}

.theme-icon-item {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  opacity: 0.4;
  filter: grayscale(1);

  &.sun {
    color: var(--color-sunshine, #f59e0b);
  }

  &.moon {
    color: var(--color-primary);
  }
}

.theme-toggle-slider {
  position: absolute;
  top: 0;
  left: 0;
  width: 30px;
  height: 30px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  z-index: 1;
}

/* 状态样式 */
.theme-toggle-pill:not(.aurora) {
  .sun {
    opacity: 1;
    filter: none;
    transform: scale(1.1);
  }
  .theme-toggle-slider {
    left: 0;
  }
}

.theme-toggle-pill.aurora {
  .moon {
    opacity: 1;
    filter: none;
    transform: scale(1.1);
  }
  .theme-toggle-slider {
    left: calc(100% - 30px);
    background: rgba(255, 255, 255, 0.1);
    box-shadow: 0 0 15px rgba(168, 85, 247, 0.4);
  }
}

/* ========== 认证按钮 ========== */
.auth-buttons {
  display: flex;
  gap: var(--space-3);
}

.cosmic-btn {
  position: relative;
  height: 44px;
  padding: 0 var(--space-5);
  border-radius: var(--radius-full);
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-bounce);
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  outline: none;

  .btn-icon {
    font-size: 16px;
    transition: transform var(--transition-bounce);
  }

  .btn-text {
    position: relative;
    z-index: 1;
  }

  .btn-glow {
    position: absolute;
    inset: 0;
    opacity: 0;
    transition: opacity var(--transition-base);
    border-radius: inherit;
  }

  &:hover {
    transform: translateY(-3px);

    .btn-icon {
      transform: scale(1.15) rotate(10deg);
    }

    .btn-glow {
      opacity: 1;
    }
  }

  &.secondary {
    background: var(--glass-bg);
    color: var(--text-primary);
    border: 1px solid var(--glass-border);
    backdrop-filter: var(--glass-blur);

    .btn-glow {
      background: linear-gradient(135deg, rgba(168, 85, 247, 0.2), rgba(236, 72, 153, 0.2));
    }

    &:hover {
      border-color: rgba(168, 85, 247, 0.5);
      box-shadow: 0 4px 20px rgba(168, 85, 247, 0.2);
    }
  }

  &.primary {
    background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-secondary-500) 50%, var(--color-accent-purple) 100%);
    color: white;
    border: none;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

    .btn-glow {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.05));
    }

    &:hover {
      box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4), 0 0 40px rgba(236, 72, 153, 0.2);
    }
  }
}

/* ========== 底部渐变边框 ========== */
.header-border {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  overflow: hidden;
}

.border-gradient {
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(168, 85, 247, 0.6) 20%,
    rgba(236, 72, 153, 0.8) 40%,
    rgba(168, 85, 247, 0.6) 60%,
    rgba(244, 114, 182, 0.4) 80%,
    transparent 100%
  );
  animation: borderGlow 4s ease-in-out infinite;
  box-shadow: 0 0 20px rgba(168, 85, 247, 0.5), 0 0 40px rgba(236, 72, 153, 0.3);
}

@keyframes borderGlow {
  0%, 100% {
    opacity: 0.6;
    transform: scaleX(0.98);
  }
  50% {
    opacity: 1;
    transform: scaleX(1);
  }
}

/* ========== 汉堡菜单按钮 ========== */
.hamburger-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5px;
  width: 44px;
  height: 44px;
  padding: 10px;
  margin-left: auto;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-base);

  &:hover {
    border-color: var(--color-primary);
    box-shadow: var(--shadow-glow-purple);
  }

  &.active {
    .hamburger-line:nth-child(1) {
      transform: translateY(8px) rotate(45deg);
    }

    .hamburger-line:nth-child(2) {
      opacity: 0;
    }

    .hamburger-line:nth-child(3) {
      transform: translateY(-8px) rotate(-45deg);
    }
  }
}

.hamburger-line {
  display: block;
  width: 20px;
  height: 2px;
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 50%, #f472b6 100%);
  border-radius: 2px;
  transition: all var(--transition-base);
}

/* ========== 移动端抽屉 ========== */
.mobile-drawer {
  :deep(.ant-drawer-content) {
    background: var(--bg-primary);
  }

  :deep(.ant-drawer-header) {
    background: var(--bg-secondary);
    border-bottom: 1px solid var(--border-subtle);

    .ant-drawer-title {
      color: var(--text-primary);
    }
  }

  :deep(.ant-drawer-close) {
    color: var(--text-secondary);
  }
}

.drawer-header {
  display: flex;
  align-items: center;
}

.drawer-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 50%, #f472b6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.mobile-menu-content {
  padding: var(--space-2);
}

.mobile-nav-menu {
  background: transparent;
  border: none;

  :deep(.ant-menu-item) {
    color: var(--text-primary);
    font-weight: 600;
    padding: 12px 16px;
    margin: 4px 0;
    border-radius: var(--radius-lg);
    border: 1px solid transparent;

    &:hover {
      background: var(--bg-hover);
      color: var(--color-primary);
    }

    &.ant-menu-item-selected {
      background: linear-gradient(135deg, rgba(168, 85, 247, 0.15), rgba(236, 72, 153, 0.15));
      border-color: rgba(168, 85, 247, 0.3);
      color: var(--color-primary);
    }
  }

  :deep(.ant-menu-submenu-title) {
    color: var(--text-primary);
    font-weight: 600;
    padding: 12px 16px;
    margin: 4px 0;
    border-radius: var(--radius-lg);

    &:hover {
      background: var(--bg-hover);
      color: var(--color-primary);
    }
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .header-center {
    display: none;
  }

  .hamburger-btn {
    display: flex;
  }

  .logo-text {
    display: none;
  }

  .user-info {
    display: none;
  }

  .theme-toggle-wrapper {
    margin-right: var(--space-3);
  }
}

@media (max-width: 640px) {
  .header-content {
    padding: 0 var(--space-4);
  }

  .auth-buttons {
    .btn-text {
      display: none;
    }

    .cosmic-btn {
      padding: 0 var(--space-3);
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

<style lang="less">
/* 全局覆盖 Ant Design Menu 弹出层样式 */
.ant-menu-submenu-popup .ant-menu-item {
  display: flex !important;
  align-items: center !important;
  height: 40px !important;
}
</style>
