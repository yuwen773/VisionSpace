<template>
  <div class="agent-chat-layout">
    <!-- 窄屏侧栏改为浮层时的遮罩：点它收起当前面板 -->
    <div
      class="layout-scrim"
      :class="{ show: !leftCollapsed || rightExpanded }"
      aria-hidden="true"
      @click="emit('closeOverlays')"
    />

    <!-- 左栏：历史列表 -->
    <aside class="layout-left" :class="{ collapsed: leftCollapsed }">
      <slot name="left" />
    </aside>

    <!-- 中间：消息+输入 -->
    <main class="layout-center">
      <slot name="center" />
    </main>

    <!-- 右栏：资源面板 -->
    <aside class="layout-right" :class="{ expanded: rightExpanded }">
      <slot name="right" />
    </aside>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  leftCollapsed?: boolean
  rightExpanded?: boolean
}>()

const emit = defineEmits<{
  (e: 'closeOverlays'): void
}>()
</script>

<style scoped>
.agent-chat-layout {
  position: relative;
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-primary);
  position: relative;
}

.layout-scrim {
  display: none;
}

.layout-left {
  width: 276px;
  flex-shrink: 0;
  transition:
    width 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.25s ease;
  overflow: hidden;
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border-subtle);
}

.layout-left.collapsed {
  width: 0;
  opacity: 0;
  border-right-color: transparent;
}

.layout-center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: var(--color-bg-primary);
}

.layout-right {
  width: 0;
  flex-shrink: 0;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  background: var(--color-bg-secondary);
  border-left: 1px solid var(--color-border-subtle);
}

.layout-right.expanded {
  width: 340px;
}

@media (max-width: 1280px) {
  .layout-right.expanded {
    width: 300px;
  }
}

/*
 * 窄屏：左右两栏改为覆盖式抽屉。
 * 否则 276px 侧栏会挤掉消息区，手机上只剩一条窄缝。
 * （断点统一在下方 1024px 区块，这里不重复声明）
 */

/* 平板及以下：两侧栏改成浮层，不再挤压对话区
 * （375px 下左栏 260px 一开，中间只剩 115px，消息无法阅读） */
@media (max-width: 1024px) {
  .layout-left {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 30;
    width: min(280px, 86vw);
    background: var(--color-bg-secondary);
    border-right: 1px solid var(--color-border-subtle);
    box-shadow: var(--shadow-lg, 0 10px 30px rgba(0, 0, 0, 0.35));
  }

  .layout-left.collapsed {
    width: 0;
    box-shadow: none;
    border-right: 0;
  }

  .layout-right.expanded {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    width: min(340px, 100vw);
  }

  .layout-scrim.show {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 25;
    background: rgba(8, 10, 24, 0.45);
  }
}
</style>
