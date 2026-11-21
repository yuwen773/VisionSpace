<script setup lang="ts">
/**
 * 管理后台页面页头（标题 + 副标题 + 图标 + 右侧操作区）。
 *
 * 六个管理页此前各自写一份 `.page-header / .header-content / .page-title /
 * .page-subtitle`，字号、图标尺寸、外边距、响应式断点各不相同（28px、xl、3xl；
 * 768 / 1024 折叠；24 / 32px 下边距）。改一次页头要改六处，且六处本来就该长一样。
 *
 * 这里只保留两档字号：`lg` 给台账类页面（反馈、空间、存储、仪表盘），
 * `md` 给信息密度更高的列表页（图片、用户）。图标走 `#icon` 插槽，
 * 右侧操作区走 `#extra`（页面自己的 `.header-actions` / `.header-stats`
 * 仍由页面样式控制）。
 */
withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    size?: 'lg' | 'md'
  }>(),
  { size: 'lg' },
)
</script>

<template>
  <header class="admin-page-header" :class="[`size-${size}`, { 'has-extra': !!$slots.extra }]">
    <div class="header-content">
      <h1 class="page-title">
        <slot name="icon" />
        <span class="title-text"
          ><slot>{{ title }}</slot></span
        >
      </h1>
      <p v-if="subtitle || $slots.subtitle" class="page-subtitle">
        <slot name="subtitle">{{ subtitle }}</slot>
      </p>
    </div>
    <!-- 不包额外容器：页面自己的 .header-actions / .header-stats 直接作为 flex 子项，
         它们的响应式规则继续由页面样式负责 -->
    <template v-if="$slots.extra">
      <slot name="extra" />
    </template>
  </header>
</template>

<style scoped>
.admin-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--admin-space-6);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--admin-font);
  font-weight: 700;
  margin: 0 0 var(--admin-space-2) 0;
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  color: var(--admin-text-primary);
}

/* 插槽里的图标由使用方编译，作用域属性不在本组件上，必须穿透 */
.page-title :deep(svg) {
  flex-shrink: 0;
  color: var(--admin-primary);
}

.title-text {
  display: flex;
  align-items: center;
}

.size-lg .page-title {
  font-size: var(--admin-text-3xl);
}

.size-lg .page-title :deep(svg) {
  width: 32px;
  height: 32px;
}

.size-md .page-title {
  font-size: var(--admin-text-xl);
}

.size-md .page-title :deep(svg) {
  width: 28px;
  height: 28px;
}

.page-subtitle {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-secondary);
  margin: 0;
}

@media (max-width: 1024px) {
  .has-extra {
    flex-direction: column;
    gap: var(--admin-space-4);
  }
}

@media (max-width: 768px) {
  .size-lg .page-title {
    font-size: var(--admin-text-2xl);
  }
}
</style>
