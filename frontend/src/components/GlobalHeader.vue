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
                    <stop offset="0%" stop-color="#2268f5"/>
                    <stop offset="50%" stop-color="#6e35eb"/>
                    <stop offset="100%" stop-color="#a855f7"/>
                  </linearGradient>
                </defs>
                <circle cx="30" cy="30" r="28" fill="none" stroke="url(#ringGradient)" stroke-width="2" stroke-dasharray="8 4"/>
              </svg>
            </div>
            <!-- Logo 图标 -->
            <div class="logo-icon">
              <img src="@/assets/logo.png" alt="VisionSpace" class="logo-image" />
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
            <span class="logo-tag">
              <span class="tag-text">探索宇宙</span>
            </span>
          </div>
        </router-link>
      </div>

      <!-- 导航菜单 -->
      <nav class="header-center">
        <div class="nav-container">
          <a-menu
            v-model:selectedKeys="current"
            mode="horizontal"
            :items="menuItems"
            :ellipsis="false"
            @click="doMenuClick"
            class="cosmic-nav-menu"
          />
        </div>
      </nav>

      <!-- 用户区域 -->
      <div class="header-right">
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
                  <span class="role-icon">{{ isAdmin ? '👑' : '👤' }}</span>
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
                        <span class="menu-icon">🏠</span>
                        <span class="menu-text">我的空间</span>
                      </div>
                      <div class="menu-item-shine"></div>
                    </a-menu-item>
                    <a-menu-item v-if="isAdmin" key="admin" class="menu-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon">⚙️</span>
                        <span class="menu-text">后台管理</span>
                      </div>
                      <div class="menu-item-shine"></div>
                    </a-menu-item>
                    <a-menu-divider class="menu-divider" />
                    <a-sub-menu key="theme" class="theme-submenu">
                      <template #title>
                        <div class="menu-item-inner">
                          <span class="menu-icon">{{ THEMES[currentTheme].icon }}</span>
                          <span class="menu-text">主题切换</span>
                        </div>
                        <div class="menu-item-shine"></div>
                      </template>
                      <div class="theme-options">
                        <a-menu-item
                          v-for="(themeInfo, key) in THEMES"
                          :key="key"
                          @click="setTheme(key)"
                          class="theme-item"
                          :class="{ 'theme-active': currentTheme === key }"
                        >
                          <span class="theme-icon">{{ themeInfo.icon }}</span>
                          <span class="theme-name">{{ themeInfo.name }}</span>
                          <span v-if="currentTheme === key" class="theme-check">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                              <path d="M20 6L9 17l-5-5"/>
                            </svg>
                          </span>
                        </a-menu-item>
                      </div>
                    </a-sub-menu>
                    <a-menu-divider class="menu-divider" />
                    <a-menu-item key="logout" class="menu-item logout-item">
                      <div class="menu-item-inner">
                        <span class="menu-icon">👋</span>
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
            <span class="btn-icon">🔑</span>
            <span class="btn-text">登录</span>
            <span class="btn-glow"></span>
          </button>
          <button @click="goToRegister" class="cosmic-btn primary">
            <span class="btn-icon">✨</span>
            <span class="btn-text">注册</span>
            <span class="btn-glow"></span>
          </button>
        </div>
      </div>
    </div>

    <!-- 底部渐变边框 -->
    <div class="header-border">
      <div class="border-gradient"></div>
    </div>
  </header>
</template>

<script lang="ts" setup>
import { ref, computed, watchEffect, onMounted, onUnmounted } from 'vue'
import type { MenuProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { useTheme } from '@/composables/useTheme'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const current = ref<string[]>([])
const particleCanvas = ref<HTMLCanvasElement | null>(null)

// 粒子动画
let particleAnimationId: number | null = null

// 主题管理
const { currentTheme, THEMES, setTheme } = useTheme()

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

const doMenuClick = (menuInfo: { key: string }) => {
  const url = menuInfo.key.split('?')[0]
  const queryString = menuInfo.key.split('?')[1]
  const searchParams = new URLSearchParams(queryString)
  const query = Object.fromEntries(searchParams.entries())

  router.push({
    path: url,
    query: query,
  })
}

router.afterEach((to) => {
  current.value = [to.fullPath]
})

const handleUserMenuClick = ({ key }: { key: string }) => {
  if (key === 'my_space') {
    router.push('/my_space')
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
      ctx.fillStyle = `rgba(148, 163, 184, ${currentOpacity})`
      ctx.fill()

      // 光晕效果
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(34, 104, 245, ${currentOpacity * 0.2})`
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
  overflow: hidden;
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
  background: linear-gradient(135deg, #2268f5, #6e35eb);
  top: -50px;
  left: 10%;
  animation-delay: 0s;
}

.orb-2 {
  width: 250px;
  height: 120px;
  background: linear-gradient(135deg, #6e35eb, #a855f7);
  top: -30px;
  right: 20%;
  animation-delay: -7s;
}

.orb-3 {
  width: 200px;
  height: 100px;
  background: linear-gradient(135deg, #a855f7, #ff6b9d);
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
    box-shadow: 0 4px 20px rgba(34, 104, 245, 0.4);
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
      background: #2268f5;
      top: 2px;
      left: 2px;
      animation-delay: 0s;
      box-shadow: 0 0 10px #2268f5;
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
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.2;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--text-primary) 0%, var(--color-primary-500) 50%, var(--color-accent-purple) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;

  .tag-text {
    font-size: 10px;
    font-weight: 600;
    color: var(--color-accent-purple);
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

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
  z-index: 2;
}

.cosmic-nav-menu {
  background: transparent;
  border: none;
  font-family: var(--font-display);
  display: flex;
  justify-content: center;

  :deep(.ant-menu-overflow) {
    justify-content: center;
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
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(34, 104, 245, 0.15), rgba(110, 53, 235, 0.15));
      opacity: 0;
      transition: opacity var(--transition-base);
      border-radius: inherit;
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
      background: linear-gradient(135deg, rgba(34, 104, 245, 0.25), rgba(110, 53, 235, 0.25));
      border: 1px solid rgba(34, 104, 245, 0.4);
      box-shadow: 0 0 20px rgba(34, 104, 245, 0.2), inset 0 0 20px rgba(34, 104, 245, 0.1);

      &::before {
        opacity: 1;
        background: linear-gradient(135deg, rgba(34, 104, 245, 0.3), rgba(168, 85, 247, 0.3));
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
  background: rgba(26, 35, 50, 0.6);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);

  &:hover {
    border-color: rgba(34, 104, 245, 0.5);
    transform: translateY(-2px);
    box-shadow: 0 4px 20px rgba(34, 104, 245, 0.2), 0 0 30px rgba(110, 53, 235, 0.1);

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
    border: 2px solid rgba(34, 104, 245, 0.3);
    transition: all var(--transition-bounce);
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
  }

  .avatar-ring {
    position: absolute;
    inset: -2px;
    border-radius: var(--radius-lg);
    border: 2px solid transparent;
    background: linear-gradient(135deg, var(--color-primary-500), var(--color-accent-purple)) border-box;
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
  background: rgba(26, 35, 50, 0.98) !important;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5), 0 0 30px rgba(34, 104, 245, 0.1);

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
      background: rgba(34, 104, 245, 0.15) !important;
    }
  }

  :deep(.ant-dropdown-menu-item-divider) {
    background: rgba(148, 163, 184, 0.15) !important;
  }
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-5);
  background: linear-gradient(135deg, rgba(34, 104, 245, 0.15) 0%, rgba(110, 53, 235, 0.15) 100%);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--color-primary-500), var(--color-accent-purple), transparent);
  }
}

.dropdown-avatar-wrapper {
  position: relative;

  .dropdown-avatar {
    border: 3px solid rgba(34, 104, 245, 0.4);
    box-shadow: 0 4px 20px rgba(34, 104, 245, 0.3);
  }

  .dropdown-avatar-glow {
    position: absolute;
    inset: -8px;
    background: radial-gradient(circle, rgba(168, 85, 247, 0.3) 0%, transparent 70%);
    filter: blur(10px);
    z-index: -1;
  }
}

.dropdown-user-info {
  flex: 1;
}

.dropdown-user-name {
  font-weight: 700;
  color: #f8fafc !important;
  font-size: 16px;
  font-family: var(--font-display);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
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
  background: rgba(34, 104, 245, 0.2);
  color: var(--color-primary-500);
  border: 1px solid rgba(34, 104, 245, 0.3);

  &.admin {
    background: rgba(168, 85, 247, 0.2);
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
    background: rgba(34, 104, 245, 0.1) !important;
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
    filter: drop-shadow(0 0 4px rgba(255, 255, 255, 0.3));
  }

  .menu-text {
    font-weight: 600;
    color: #f8fafc !important;
    font-size: 14px;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  }
}

.menu-item-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
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
    background: rgba(26, 35, 50, 0.95) !important;
    border-radius: var(--radius-lg);
    padding: var(--space-2);
    margin-top: 4px;
    border: 1px solid rgba(148, 163, 184, 0.2);
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
    color: #f8fafc !important;
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
    background: rgba(34, 104, 245, 0.2) !important;
  }

  &:hover:not(.theme-active) {
    background: rgba(255, 255, 255, 0.05);
  }
}

.logout-item {
  &:hover {
    background: rgba(255, 107, 157, 0.1) !important;

    .menu-text {
      color: var(--color-accent-pink);
    }

    .logout-shine {
      background: linear-gradient(90deg, transparent, rgba(255, 107, 157, 0.15), transparent);
    }
  }

  .logout-shine {
    background: linear-gradient(90deg, transparent, rgba(255, 107, 157, 0.1), transparent);
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
    background: rgba(26, 35, 50, 0.8);
    color: var(--text-primary);
    border: 1px solid var(--glass-border);
    backdrop-filter: var(--glass-blur);

    .btn-glow {
      background: linear-gradient(135deg, rgba(34, 104, 245, 0.2), rgba(110, 53, 235, 0.2));
    }

    &:hover {
      border-color: rgba(34, 104, 245, 0.5);
      box-shadow: 0 4px 20px rgba(34, 104, 245, 0.2);
    }
  }

  &.primary {
    background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-secondary-500) 50%, var(--color-accent-purple) 100%);
    color: white;
    border: none;
    box-shadow: 0 4px 20px rgba(34, 104, 245, 0.3);

    .btn-glow {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.05));
    }

    &:hover {
      box-shadow: 0 8px 30px rgba(34, 104, 245, 0.4), 0 0 40px rgba(110, 53, 235, 0.2);
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
    rgba(34, 104, 245, 0.6) 20%,
    rgba(110, 53, 235, 0.8) 40%,
    rgba(168, 85, 247, 0.6) 60%,
    rgba(255, 107, 157, 0.4) 80%,
    transparent 100%
  );
  animation: borderGlow 4s ease-in-out infinite;
  box-shadow: 0 0 20px rgba(34, 104, 245, 0.5), 0 0 40px rgba(110, 53, 235, 0.3);
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

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .header-center {
    display: none;
  }

  .logo-text {
    display: none;
  }

  .user-info {
    display: none;
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
