<template>
  <aside class="admin-sidebar" :class="{ collapsed: isCollapsed }" aria-label="管理端导航">
    <!-- 品牌 -->
    <div class="sidebar-header">
      <router-link to="/" class="sidebar-logo" title="返回 VisionSpace">
        <img src="@/assets/logo.svg" alt="VisionSpace" class="logo-image" />
        <span v-show="!isCollapsed" class="logo-copy">
          <span class="logo-text">VisionSpace</span>
          <span class="logo-sub">Admin Console</span>
        </span>
      </router-link>
      <button
        class="collapse-btn"
        type="button"
        :aria-label="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
        :title="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="toggleCollapse"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path v-if="isCollapsed" d="M9 18l6-6-6-6" />
          <path v-else d="M15 18l-6-6 6-6" />
        </svg>
      </button>
    </div>

    <!-- 导航 -->
    <nav class="sidebar-nav">
      <div class="nav-section">
        <span v-show="!isCollapsed" class="nav-section-title">Index / 管理</span>
        <router-link
          v-for="(item, index) in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          :title="isCollapsed ? item.label : undefined"
          :aria-label="item.label"
        >
          <span class="nav-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
          <component :is="item.icon" :size="17" :stroke-width="1.6" class="nav-icon" />
          <span v-show="!isCollapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </div>
    </nav>

    <!-- 状态 -->
    <div class="sidebar-foot">
      <span v-show="!isCollapsed" class="foot-status">
        <span class="foot-dot" aria-hidden="true"></span>
        <!-- 静态标识：这里没有健康检查数据，不假装实时在线状态 -->
        <span class="foot-text">Admin workspace</span>
      </span>
      <span v-show="!isCollapsed" class="foot-mark">VS / ADMIN</span>
      <span v-show="isCollapsed" class="foot-dot foot-dot--solo" aria-hidden="true"></span>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import { inject, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { BarChart3, Users, Image, Cloud, HardDrive, MessageSquare } from 'lucide-vue-next'

const route = useRoute()

const sidebarCollapsed = inject<Ref<boolean>>('sidebarCollapsed', ref(false))
const isCollapsed = sidebarCollapsed

const menuItems = [
  { path: '/admin/dashboard', label: '仪表盘', icon: BarChart3 },
  { path: '/admin/user_manage', label: '用户管理', icon: Users },
  { path: '/admin/picture_manage', label: '图片管理', icon: Image },
  { path: '/admin/space_manage', label: '空间管理', icon: Cloud },
  { path: '/admin/storage_config_manage', label: '存储配置', icon: HardDrive },
  { path: '/admin/feedback_manage', label: '反馈管理', icon: MessageSquare },
]

const isActive = (path: string) => route.path.startsWith(path)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style lang="less" scoped>
.admin-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  width: 252px;
  height: 100vh;
  background: var(--admin-sidebar-bg);
  border-right: 1px solid var(--admin-border-default);
  transition: width var(--admin-transition-base);

  &.collapsed {
    width: 76px;

    .sidebar-header {
      flex-direction: column;
      gap: 14px;
      padding: 18px 0;
    }

    .logo-copy {
      display: none;
    }

    .nav-item {
      grid-template-columns: 1fr;
      justify-items: center;
      padding: 0;
    }

    .nav-index {
      display: none;
    }

    .sidebar-foot {
      justify-content: center;
    }
  }
}

/* ============ 品牌 ============ */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 84px;
  padding: 18px 18px 18px 20px;
  border-bottom: 1px solid var(--admin-border-default);
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  text-decoration: none;
}

.logo-image {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: var(--admin-radius-sm);
}

.logo-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.logo-text {
  font-family: var(--admin-font);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.01em;
  line-height: 1.1;
  color: var(--admin-text-primary);
  white-space: nowrap;
}

.logo-sub {
  font-family: var(--admin-font-mono);
  font-size: 8px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  white-space: nowrap;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  flex-shrink: 0;
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-tertiary);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    border-color var(--admin-transition-fast),
    background var(--admin-transition-fast);

  &:hover {
    color: var(--admin-text-primary);
    border-color: var(--admin-border-strong);
    background: var(--admin-bg-hover);
  }

  svg {
    width: 14px;
    height: 14px;
  }
}

/* ============ 导航 ============ */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  padding: 26px 14px;
}

.nav-section-title {
  display: block;
  padding: 0 10px;
  margin-bottom: 14px;
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
}

.nav-item {
  position: relative;
  display: grid;
  grid-template-columns: 22px 18px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 10px;
  color: var(--admin-sidebar-text);
  font-family: var(--admin-font);
  font-size: 13.5px;
  font-weight: 500;
  text-decoration: none;
  border-radius: var(--admin-radius-sm);
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    transform var(--admin-transition-fast);

  &::before {
    content: '';
    position: absolute;
    left: -14px;
    top: 50%;
    width: 2px;
    height: 0;
    background: var(--admin-primary);
    transform: translateY(-50%);
    transition: height var(--admin-transition-base);
  }

  &:hover {
    color: var(--admin-sidebar-text-hover);
    background: var(--admin-sidebar-bg-hover);
    transform: translateX(2px);
  }

  &.active {
    color: var(--admin-sidebar-text-active);
    background: var(--admin-sidebar-bg-active);

    &::before {
      height: 22px;
    }

    .nav-index,
    .nav-icon {
      color: var(--admin-primary);
    }

    .nav-label {
      font-weight: 600;
    }
  }
}

.nav-index {
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--admin-text-tertiary);
  transition: color var(--admin-transition-fast);
}

.nav-icon {
  color: var(--admin-text-tertiary);
  transition:
    color var(--admin-transition-fast),
    transform var(--admin-transition-fast);
}

.nav-item:hover .nav-icon {
  color: var(--admin-text-secondary);
  transform: translateX(1px);
}

.nav-label {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ============ 状态 ============ */
.sidebar-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--admin-border-default);
}

.foot-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.foot-dot {
  position: relative;
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--admin-success);
}

.foot-dot::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 1px solid var(--admin-success);
  opacity: 0.35;
}

.foot-dot--solo {
  margin: 0 auto;
}

.foot-text {
  font-family: var(--admin-font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  white-space: nowrap;
}

.foot-mark {
  font-family: var(--admin-font-mono);
  font-size: 8px;
  letter-spacing: 0.18em;
  color: var(--admin-text-disabled);
  white-space: nowrap;
}

/* ============ 响应式 ============ */
/* 窄屏下展开的侧栏是浮层，需要阴影区分层级 */
@media (max-width: 1024px) {
  .admin-sidebar:not(.collapsed) {
    box-shadow: var(--admin-shadow-lg);
  }
}

@media (max-width: 640px) {
  .admin-sidebar {
    &.collapsed {
      width: 60px;
    }

    /* 展开态在窄屏是浮层，保持可读宽度而不是硬挤成图标栏 */
    &:not(.collapsed) {
      width: 232px;
    }
  }

  .sidebar-header {
    min-height: 70px;
    padding: 14px 12px;
  }

  .sidebar-nav {
    padding: 18px 8px;
  }
}
</style>
