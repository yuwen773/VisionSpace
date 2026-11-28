<template>
  <header id="cosmic-header" class="vs-header" :class="{ 'is-scrolled': isScrolled }">
    <div class="vs-header__inner">
      <!-- 品牌 -->
      <router-link to="/" class="vs-brand" aria-label="VisionSpace 首页">
        <img src="@/assets/logo.svg" alt="" class="vs-brand__mark" />
        <span class="vs-brand__word">VisionSpace</span>
      </router-link>

      <!-- 主导航：一条基线，选中项下方有会移动的高亮段 -->
      <nav class="vs-nav" aria-label="主导航">
        <div ref="navTrack" class="vs-nav__track">
          <span class="vs-nav__marker" aria-hidden="true" :style="markerStyle"></span>
          <a-menu
            v-model:selectedKeys="current"
            mode="horizontal"
            :items="desktopItems"
            :disabled-overflow="true"
            class="vs-nav__menu"
            @click="doMenuClick"
          />
        </div>
      </nav>

      <!-- 右侧动作区 -->
      <div class="vs-actions">
        <button
          type="button"
          class="vs-mode"
          :class="currentTheme"
          @click="toggleTheme"
          :aria-label="currentTheme === 'aurora' ? '切换到浅色主题' : '切换到极光主题'"
          :title="currentTheme === 'aurora' ? '切换到浅色主题' : '切换到极光主题'"
        >
          <span class="vs-mode__slot sun"><Sun :size="12" /></span>
          <span class="vs-mode__slot moon"><Moon :size="12" /></span>
          <span class="vs-mode__thumb" aria-hidden="true"></span>
        </button>

        <a-dropdown
          v-if="loginUserStore.loginUser.id"
          v-model:open="userMenuOpen"
          placement="bottomRight"
          :trigger="['click']"
        >
          <button
            type="button"
            class="vs-user"
            aria-label="用户菜单"
            aria-haspopup="menu"
            :aria-expanded="userMenuOpen"
          >
            <a-avatar
              :src="loginUserStore.loginUser.userAvatar"
              :size="26"
              class="vs-user__avatar"
            />
            <span class="vs-user__name">{{ loginUserStore.loginUser.userAccount ?? 'User' }}</span>
            <Crown v-if="isAdmin" :size="12" class="vs-user__crown" aria-hidden="true" />
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              class="vs-user__caret"
              aria-hidden="true"
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
          <template #overlay>
            <div class="vs-menu" role="menu" aria-label="账号">
              <div class="vs-menu__id">
                <a-avatar
                  :src="loginUserStore.loginUser.userAvatar"
                  :size="40"
                  class="vs-menu__avatar"
                />
                <div class="vs-menu__id-text">
                  <p class="vs-menu__name">{{ loginUserStore.loginUser.userAccount }}</p>
                  <p class="vs-menu__role">{{ getRoleText(loginUserStore.loginUser.userRole) }}</p>
                </div>
              </div>
              <button
                v-for="entry in userMenuEntries"
                :key="entry.key"
                type="button"
                role="menuitem"
                class="vs-menu__row"
                :class="{ 'is-danger': entry.key === 'logout' }"
                @click="handleUserMenuClick(entry.key)"
              >
                <span class="vs-menu__icon"><component :is="entry.icon" :size="15" /></span>
                <span>{{ entry.label }}</span>
              </button>
            </div>
          </template>
        </a-dropdown>

        <div v-else class="vs-auth">
          <button type="button" class="vs-auth__quiet" @click="goToLogin">登录</button>
          <button type="button" class="vs-auth__solid" @click="goToRegister">注册</button>
        </div>

        <button
          type="button"
          class="vs-burger"
          @click="toggleMobileMenu"
          :class="{ active: mobileMenuVisible }"
          :aria-label="mobileMenuVisible ? '关闭菜单' : '打开菜单'"
          :aria-expanded="mobileMenuVisible"
        >
          <span class="vs-burger__line"></span>
          <span class="vs-burger__line"></span>
        </button>
      </div>
    </div>
  </header>

  <!-- 移动端抽屉 -->
  <a-drawer
    v-model:open="mobileMenuVisible"
    placement="right"
    width="300"
    root-class-name="vs-drawer"
    :closable="true"
  >
    <template #title>
      <span class="vs-drawer__word">VisionSpace</span>
    </template>
    <a-menu
      v-model:selectedKeys="current"
      mode="vertical"
      :items="items"
      @click="handleMobileMenuClick"
      class="vs-drawer__menu"
    />
  </a-drawer>
</template>

<script lang="ts" setup>
import { ref, computed, watch, watchEffect, nextTick, onMounted, onUnmounted, h } from 'vue'
import type { MenuProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Sun,
  Moon,
  Crown,
  User,
  Home,
  Settings,
  LogOut,
  Camera,
  Cloud,
  Users,
  Rocket,
  Compass,
  ShieldCheck,
} from 'lucide-vue-next'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { useTheme } from '@/composables/useTheme'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()
const current = ref<string[]>([route.fullPath])
const mobileMenuVisible = ref(false)
const userMenuOpen = ref(false)

// 主题管理
const { currentTheme, toggleTheme } = useTheme()

/* ========== 吸顶状态 ========== */
// 页头自身始终是纸面；滚动后只加重底线，不改变高度（其他页面按 72px 定位）
const isScrolled = ref(false)
const onScroll = () => {
  isScrolled.value = window.scrollY > 4
}

/* ========== 导航高亮段 ==========
 * 选中项下方那截紫色线，用 transform 在页面之间移动。
 * 测不到元素时保持隐藏，纯装饰，不影响导航本身。
 */
const navTrack = ref<HTMLElement | null>(null)
const markerStyle = ref<Record<string, string>>({
  opacity: '0',
  width: '0px',
  transform: 'translateX(0)',
})

const updateMarker = () => {
  nextTick(() => {
    const track = navTrack.value
    if (!track) return
    const active = track.querySelector<HTMLElement>(
      '.ant-menu-item-selected, .ant-menu-submenu-selected > .ant-menu-submenu-title',
    )
    if (!active) {
      markerStyle.value = { ...markerStyle.value, opacity: '0' }
      return
    }
    const trackBox = track.getBoundingClientRect()
    const itemBox = active.getBoundingClientRect()
    markerStyle.value = {
      opacity: '1',
      width: `${Math.round(itemBox.width)}px`,
      transform: `translateX(${Math.round(itemBox.left - trackBox.left)}px)`,
    }
  })
}

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
    label: '首页',
    icon: () => h(Home, { size: 16 }),
  },
  {
    key: '/explore',
    label: '探索',
    icon: () => h(Compass, { size: 16 }),
  },
  {
    key: '/agent',
    label: 'SpaceMind',
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
    popupClassName: 'vs-popup',
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
    popupClassName: 'vs-popup',
    children: teamSpaceSubMenus,
  }

  return [...originItems.slice(0, -1), teamSpaceGroupMenus, ...originItems.slice(-1)]
})

const items = computed(() => filterMenus(menuItems.value))

/* 桌面端导航只留文字：图标交给抽屉与下拉菜单，顶栏保持一条基线 */
const desktopItems = computed(() => {
  const entries = filterMenus(menuItems.value) ?? []
  return entries.map((entry) => {
    const rest = { ...(entry as unknown as Record<string, unknown>) }
    delete rest.icon
    return rest
  }) as unknown as MenuProps['items']
})

const PARENT_MENU_KEYS = new Set(['add_space_submenu', 'team_space_submenu'])

const doMenuClick = (menuInfo: { key: string; keyPath?: string[] }) => {
  // 如果是父菜单项（有子菜单），只展开子菜单，不导航
  if (PARENT_MENU_KEYS.has(menuInfo.key)) {
    return
  }

  const [url, queryString] = menuInfo.key.split('?')
  const query = queryString ? Object.fromEntries(new URLSearchParams(queryString).entries()) : {}

  router.push({
    path: url,
    query: query,
  })
}

// 移动端菜单
const toggleMobileMenu = () => {
  mobileMenuVisible.value = !mobileMenuVisible.value
}

const handleMobileMenuClick = (menuInfo: { key: string; keyPath?: string[] }) => {
  mobileMenuVisible.value = false
  doMenuClick(menuInfo)
}

// 直接进入页面时 afterEach 不会触发，选中项由路由监听驱动
watch(
  () => route.fullPath,
  (path) => {
    if (current.value[0] !== path) {
      current.value = [path]
    }
  },
)

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
// 处置角色独立于平台管理员：管理身份本身不构成对私人内容的处置权
const isModerator = computed(() => loginUserStore.loginUser.userRole === 'moderator')

const userMenuEntries = computed(() => {
  const entries = [
    { key: 'my_space', label: '我的空间', icon: Home },
    { key: 'user_center', label: '用户中心', icon: User },
  ]
  if (isAdmin.value) entries.push({ key: 'admin', label: '后台管理', icon: Settings })
  if (isModerator.value) entries.push({ key: 'moderation', label: '内容处置', icon: ShieldCheck })
  entries.push({ key: 'logout', label: '退出登录', icon: LogOut })
  return entries
})

const handleUserMenuClick = (key: string) => {
  userMenuOpen.value = false
  if (key === 'my_space') {
    router.push('/my_space')
  } else if (key === 'user_center') {
    router.push('/user/center')
  } else if (key === 'admin') {
    router.push('/admin')
  } else if (key === 'moderation') {
    router.push('/moderation')
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

const getRoleText = (role?: string) => {
  const roleMap: Record<string, string> = {
    admin: '管理员',
    moderator: '内容处置员',
    user: '用户',
  }
  return roleMap[role ?? ''] || '用户'
}

const goToLogin = () => router.push('/user/login')
const goToRegister = () => router.push('/user/register')

watch(current, updateMarker)
watch(items, updateMarker)
watch(desktopItems, updateMarker)

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', updateMarker)
  document.fonts?.ready.then(updateMarker).catch(() => {})
  updateMarker()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', updateMarker)
})
</script>

<style lang="less" scoped>
/* ===================================================================
   全局页头 — 档案编辑式
   纸面 + 墨色 + 单一紫调；72px 固定高度（其他页面按此值定位吸顶元素）。
   =================================================================== */

.vs-header {
  position: relative;
  height: 72px;
  background: var(--vs-paper);
  color: var(--vs-ink);
  font-family: var(--vs-sans);

  &::after {
    content: '';
    position: absolute;
    inset-inline: 0;
    bottom: 0;
    height: 1px;
    background: var(--vs-line);
    transition: background-color 0.4s var(--vs-ease);
  }

  &.is-scrolled::after {
    background: var(--vs-line-2);
  }

  ::selection {
    background: var(--vs-accent-soft);
    color: var(--vs-ink);
  }

  :focus-visible {
    outline: 2px solid var(--vs-accent);
    outline-offset: 3px;
  }
}

.vs-header__inner {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  height: 100%;
  max-width: 1360px;
  margin: 0 auto;
  padding: 0 clamp(16px, 2.4vw, 32px);
}

/* ========== 品牌 ========== */
.vs-brand {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 11px;
  height: 100%;
  color: inherit;
  text-decoration: none;
  border-radius: 2px;

  &__mark {
    width: 26px;
    height: 26px;
    display: block;
    transition: transform 0.5s var(--vs-ease);
  }

  &__word {
    font-family: var(--vs-serif);
    font-size: 17px;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0.02em;
    color: var(--vs-ink);
    white-space: nowrap;
  }

  &:hover &__mark {
    transform: rotate(-8deg) scale(1.05);
  }
}

/* ========== 导航 ========== */
.vs-nav {
  display: flex;
  justify-content: center;
  height: 100%;
  min-width: 0;
}

.vs-nav__track {
  position: relative;
  display: flex;
  height: 100%;
}

.vs-nav__marker {
  position: absolute;
  left: 0;
  bottom: 0;
  height: 1px;
  width: 0;
  background: var(--vs-accent);
  opacity: 0;
  z-index: 2;
  pointer-events: none;
  transition:
    transform 0.42s var(--vs-ease),
    width 0.42s var(--vs-ease),
    opacity 0.25s linear;
}

.vs-nav__menu {
  height: 100%;
  width: max-content;
  display: flex;
  align-items: stretch;
  justify-content: center;
  background: transparent;
  border: 0;
  font-family: var(--vs-sans);

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    position: relative;
    display: flex;
    align-items: center;
    height: 100%;
    margin: 0;
    padding: 0 14px !important;
    line-height: 1;
    border-radius: 0;
    color: var(--vs-ink-2);
    font-size: 13.5px;
    font-weight: 400;
    letter-spacing: 0.02em;
    transition: color 0.2s ease;
    background: transparent !important;

    &::after {
      display: none !important;
    }
  }

  /* 带子菜单的项补一个细描边折角，提示还有一层 */
  :deep(.ant-menu-submenu-title) {
    padding-right: 30px !important;

    &::after {
      content: '';
      position: absolute;
      right: 14px;
      top: calc(50% - 1px);
      width: 5px;
      height: 5px;
      border-right: 1px solid var(--vs-ink-3);
      border-bottom: 1px solid var(--vs-ink-3);
      transform: translateY(-50%) rotate(45deg);
      transition: border-color 0.2s ease;
      display: block !important;
    }

    &:hover::after {
      border-color: var(--vs-ink);
    }
  }

  :deep(.ant-menu-item:hover),
  :deep(.ant-menu-submenu-title:hover),
  :deep(.ant-menu-item-selected),
  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title) {
    color: var(--vs-ink) !important;
    background: transparent !important;
  }
}

/* ========== 右侧动作 ========== */
.vs-actions {
  justify-self: end;
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 主题开关 */
.vs-mode {
  position: relative;
  display: flex;
  align-items: center;
  width: 52px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--vs-line-2);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  transition: border-color 0.25s ease;

  &__slot {
    position: relative;
    z-index: 2;
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--vs-ink-3);
    transition:
      color 0.3s ease,
      opacity 0.3s ease;
  }

  &__thumb {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 22px;
    height: 22px;
    border-radius: 999px;
    background: var(--vs-accent);
    z-index: 1;
    transition: transform 0.38s var(--vs-ease);
  }

  &.aurora &__thumb {
    transform: translateX(24px);
  }

  &.aurora &__slot.moon,
  &.pop &__slot.sun {
    color: var(--vs-accent-ink);
  }

  &:hover {
    border-color: var(--vs-ink-3);
  }
}

/* 用户入口 */
.vs-user {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 10px 0 6px;
  border: 1px solid var(--vs-line-2);
  border-radius: 2px;
  background: transparent;
  color: var(--vs-ink);
  cursor: pointer;
  transition: border-color 0.2s ease;

  &__avatar {
    border-radius: 0;
    flex-shrink: 0;
  }

  &__name {
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    font-weight: 500;
  }

  &__crown {
    color: var(--vs-accent);
    flex-shrink: 0;
  }

  &__caret {
    width: 10px;
    height: 10px;
    color: var(--vs-ink-3);
    transition: transform 0.3s var(--vs-ease);
  }

  &:hover {
    border-color: var(--vs-ink-3);
  }

  &[aria-expanded='true'] .vs-user__caret {
    transform: rotate(180deg);
  }
}

/* 未登录 */
.vs-auth {
  display: flex;
  align-items: center;
  gap: 4px;

  &__quiet,
  &__solid {
    height: 38px;
    border-radius: 2px;
    font-family: var(--vs-sans);
    font-size: 13px;
    cursor: pointer;
    transition:
      color 0.2s ease,
      background-color 0.25s ease,
      border-color 0.25s ease;
  }

  &__quiet {
    padding: 0 12px;
    border: 1px solid transparent;
    background: none;
    color: var(--vs-ink-2);
    font-weight: 500;

    &:hover {
      color: var(--vs-ink);
    }
  }

  &__solid {
    padding: 0 18px;
    border: 1px solid var(--vs-ink);
    background: var(--vs-ink);
    color: var(--vs-paper);
    font-weight: 600;
    letter-spacing: 0.04em;

    &:hover {
      border-color: var(--vs-accent);
      background: var(--vs-accent);
      color: var(--vs-accent-ink);
    }
  }
}

/* 移动端菜单键 */
.vs-burger {
  display: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid var(--vs-line-2);
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  transition: border-color 0.2s ease;

  &__line {
    display: block;
    width: 15px;
    height: 1px;
    background: var(--vs-ink);
    transition:
      transform 0.3s var(--vs-ease),
      opacity 0.2s ease;
  }

  &:hover {
    border-color: var(--vs-ink-3);
  }

  &.active &__line:nth-child(1) {
    transform: translateY(3px) rotate(45deg);
  }

  &.active &__line:nth-child(2) {
    transform: translateY(-3px) rotate(-45deg);
  }
}

/* ========== 响应式 ========== */
/* 1183px 以下导航收紧，避免右侧被裁 */
@media (max-width: 1183px) {
  .vs-nav__menu {
    :deep(.ant-menu-item),
    :deep(.ant-menu-submenu-title) {
      padding: 0 10px !important;
      font-size: 13px;
    }
  }

  .vs-header__inner {
    gap: 12px;
  }
}

/* 平板及以下改用抽屉，导航与账号信息不再互相挤压 */
@media (max-width: 959px) {
  .vs-header__inner {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .vs-nav {
    display: none;
  }

  .vs-burger {
    display: flex;
  }
}

@media (max-width: 520px) {
  .vs-brand__word {
    display: none;
  }

  .vs-header__inner {
    padding: 0 16px;
  }

  .vs-actions {
    gap: 8px;
  }

  .vs-auth__quiet {
    padding: 0 8px;
  }

  .vs-auth__solid {
    padding: 0 14px;
  }
}

/* ========== 减少动态 ========== */
@media (prefers-reduced-motion: reduce) {
  .vs-header *,
  .vs-header *::before,
  .vs-header *::after {
    animation-duration: 0.001ms !important;
    transition-duration: 0.001ms !important;
  }
}
</style>

<style lang="less">
/* ===================================================================
   页头令牌：挂在 html 的主题类上，抽屉与下拉层（teleport 到 body）
   也能继承同一套纸面颜色。
   =================================================================== */
html.theme-aurora {
  --vs-paper: #05060c;
  --vs-raised: #0a0c15;
  --vs-ink: #f4f1ea;
  --vs-ink-2: rgba(244, 241, 234, 0.72);
  --vs-ink-3: rgba(244, 241, 234, 0.52);
  --vs-line: rgba(244, 241, 234, 0.12);
  --vs-line-2: rgba(244, 241, 234, 0.26);
  --vs-accent: #b7a2ff;
  --vs-accent-ink: #0b0716;
  --vs-accent-soft: rgba(183, 162, 255, 0.12);
  --vs-danger: #ff8fa3;
  --vs-serif: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --vs-sans: 'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --vs-mono: 'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace;
  --vs-ease: cubic-bezier(0.22, 1, 0.36, 1);
}

html.theme-pop {
  --vs-paper: #faf7ff;
  --vs-raised: #f2edff;
  --vs-ink: #1e1b4b;
  --vs-ink-2: rgba(30, 27, 75, 0.74);
  --vs-ink-3: rgba(30, 27, 75, 0.64);
  --vs-line: rgba(76, 29, 149, 0.16);
  --vs-line-2: rgba(76, 29, 149, 0.34);
  --vs-accent: #7c3aed;
  --vs-accent-ink: #ffffff;
  --vs-accent-soft: rgba(124, 58, 237, 0.09);
  --vs-danger: #b91c1c;
  --vs-serif: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --vs-sans: 'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --vs-mono: 'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace;
  --vs-ease: cubic-bezier(0.22, 1, 0.36, 1);
}

/* ========== 账号下拉 ========== */
.vs-menu {
  min-width: 236px;
  padding: 6px;
  background: var(--vs-raised);
  border: 1px solid var(--vs-line-2);
  border-radius: 2px;
  box-shadow: 0 18px 40px -28px rgba(0, 0, 0, 0.7);
  font-family: var(--vs-sans);
  color: var(--vs-ink);
}

.vs-menu__id {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 12px 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--vs-line);
}

.vs-menu__avatar {
  border-radius: 0;
  flex-shrink: 0;
}

.vs-menu__id-text {
  min-width: 0;
}

.vs-menu__name {
  font-family: var(--vs-serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--vs-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vs-menu__role {
  margin-top: 5px;
  font-family: var(--vs-mono);
  font-size: 9.5px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--vs-ink-3);
}

.vs-menu__row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 0;
  border-radius: 2px;
  background: none;
  color: var(--vs-ink-2);
  font-family: var(--vs-sans);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    color 0.18s ease;
}

.vs-menu__row:hover,
.vs-menu__row:focus-visible {
  background: var(--vs-accent-soft);
  color: var(--vs-ink);
}

.vs-menu__row:focus-visible {
  outline: 2px solid var(--vs-accent);
  outline-offset: -2px;
}

.vs-menu__row.is-danger {
  color: var(--vs-danger);
}

.vs-menu__row.is-danger:hover,
.vs-menu__row.is-danger:focus-visible {
  background: color-mix(in srgb, var(--vs-danger) 9%, transparent);
  color: var(--vs-danger);
}

.vs-menu__icon {
  display: flex;
  justify-content: center;
  width: 16px;
  opacity: 0.85;
}

/* ========== 导航弹出层（创建空间 / 我的团队） ========== */
.vs-popup .ant-menu {
  padding: 6px;
  background: var(--vs-raised);
  border: 1px solid var(--vs-line-2);
  border-radius: 2px;
  box-shadow: 0 18px 40px -28px rgba(0, 0, 0, 0.7);
}

.vs-popup .ant-menu-item {
  height: 36px;
  line-height: 36px;
  margin: 0;
  border-radius: 2px;
  font-family: var(--vs-sans);
  font-size: 13px;
  color: var(--vs-ink-2);
}

.vs-popup .ant-menu-item:hover {
  background: var(--vs-accent-soft);
  color: var(--vs-ink);
}

.vs-popup .ant-menu-item-selected {
  background: var(--vs-accent-soft) !important;
  color: var(--vs-ink) !important;
}

.vs-popup .ant-menu-item::after {
  display: none !important;
}

/* ========== 移动端抽屉 ========== */
.vs-drawer .ant-drawer-content {
  background: var(--vs-paper) !important;
  color: var(--vs-ink);
}

.vs-drawer .ant-drawer-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--vs-line) !important;
  background: transparent !important;
}

.vs-drawer .ant-drawer-title {
  color: var(--vs-ink);
}

.vs-drawer .ant-drawer-close {
  color: var(--vs-ink-2);
}

.vs-drawer .ant-drawer-body {
  padding: 10px !important;
}

.vs-drawer__word {
  font-family: var(--vs-serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--vs-ink);
}

.vs-drawer__menu.ant-menu {
  background: transparent;
  border-inline-end: 0;
  color: var(--vs-ink-2);
  font-family: var(--vs-sans);
}

.vs-drawer__menu .ant-menu-item,
.vs-drawer__menu .ant-menu-submenu-title {
  height: 44px;
  line-height: 44px;
  margin: 2px 0;
  border-radius: 2px;
  font-size: 14px;
  font-weight: 500;
  color: var(--vs-ink-2);
}

.vs-drawer__menu .ant-menu-item::after,
.vs-drawer__menu .ant-menu-submenu-title::after {
  display: none !important;
}

.vs-drawer__menu .ant-menu-item:hover,
.vs-drawer__menu .ant-menu-submenu-title:hover {
  background: transparent;
  color: var(--vs-ink);
}

.vs-drawer__menu .ant-menu-item-selected,
.vs-drawer__menu .ant-menu-submenu-selected > .ant-menu-submenu-title {
  background: var(--vs-accent-soft) !important;
  color: var(--vs-ink) !important;
}

.vs-drawer__menu .ant-menu-sub {
  background: transparent;
}

.vs-drawer__menu .ant-menu-item-selected::after {
  display: none;
}
</style>
