<script setup lang="ts">
/**
 * 管理后台页面页头（编号 + 标题 + 副标题 + 右侧操作区）。
 *
 * 六个管理页共用一套页头：编号与分区代号取自路由，
 * 页面只需要给标题、副标题和可选的操作区。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    size?: 'lg' | 'md'
  }>(),
  { size: 'lg' },
)

const route = useRoute()

const pageIndex: Record<string, { no: string; code: string }> = {
  '/admin/dashboard': { no: '01', code: 'OVERVIEW' },
  '/admin/user_manage': { no: '02', code: 'USERS' },
  '/admin/picture_manage': { no: '03', code: 'IMAGES' },
  '/admin/space_manage': { no: '04', code: 'SPACES' },
  '/admin/storage_config_manage': { no: '05', code: 'STORAGE' },
  '/admin/feedback_manage': { no: '06', code: 'FEEDBACK' },
}

const section = computed(() => {
  const match = Object.entries(pageIndex).find(([path]) => route.path.startsWith(path))
  return match?.[1] ?? { no: '00', code: 'ADMIN' }
})
</script>

<template>
  <header class="admin-page-header" :class="[`size-${size}`, { 'has-extra': !!$slots.extra }]">
    <div class="header-content">
      <p class="page-kicker">
        <span class="kicker-no" aria-hidden="true">{{ section.no }}</span>
        <span class="kicker-rule" aria-hidden="true"></span>
        <span class="kicker-text">Admin / {{ section.code }}</span>
      </p>

      <h1 class="page-title">
        <span v-if="$slots.icon" class="title-mark">
          <slot name="icon" />
        </span>
        <span class="title-text"
          ><slot>{{ title }}</slot></span
        >
      </h1>

      <p v-if="subtitle || $slots.subtitle" class="page-subtitle">
        <slot name="subtitle">{{ subtitle }}</slot>
      </p>
    </div>

    <!-- 操作区统一收在 .header-extra 里：这一块自己管排版（不换行、靠右），
         页面自己的 .header-actions / .header-stats 仍是这一块里的内容 -->
    <div v-if="$slots.extra" class="header-extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<style scoped>
.admin-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--admin-space-6);
  margin-bottom: clamp(24px, 3vw, 36px);
  padding-bottom: var(--admin-space-5);
  border-bottom: 1px solid var(--admin-border-default);
  animation: admin-header-in 0.5s var(--admin-ease) both;
}

@keyframes admin-header-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.header-content {
  min-width: 0;
}

.page-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 14px;
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
}

.kicker-no {
  color: var(--admin-primary);
}

.kicker-rule {
  width: 20px;
  height: 1px;
  background: var(--admin-border-strong);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0;
  font-family: var(--admin-serif);
  font-weight: 600;
  line-height: 1.18;
  letter-spacing: 0.01em;
  color: var(--admin-text-primary);
  text-wrap: balance;
}

.title-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  color: var(--admin-primary);
  background: var(--admin-primary-bg);
}

/* 插槽里的图标由使用方编译，作用域属性不在本组件上，必须穿透 */
.title-mark :deep(svg) {
  width: 19px;
  height: 19px;
}

.title-text {
  min-width: 0;
}

.size-lg .page-title {
  font-size: clamp(30px, 3.2vw, 42px);
}

.size-md .page-title {
  font-size: clamp(26px, 2.6vw, 34px);
}

.size-md .title-mark {
  width: 34px;
  height: 34px;
}

.size-md .title-mark :deep(svg) {
  width: 17px;
  height: 17px;
}

.page-subtitle {
  max-width: 66ch;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--admin-text-secondary);
  text-wrap: pretty;
}

.header-extra {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  flex-shrink: 0;
}

@media (max-width: 1024px) {
  .has-extra {
    flex-direction: column;
    align-items: stretch;
    gap: var(--admin-space-4);
  }

  .header-extra {
    justify-content: flex-end;
  }
}

@media (max-width: 640px) {
  .admin-page-header {
    align-items: flex-start;
  }

  .page-title {
    gap: 10px;
  }

  .title-mark {
    width: 32px;
    height: 32px;
  }
}
</style>
