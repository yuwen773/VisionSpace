<template>
  <div class="agent-chat-layout">
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
</script>

<style scoped>
.agent-chat-layout {
  position: relative;
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-primary);
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
 */
@media (max-width: 900px) {
  .layout-left {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 30;
    width: min(84vw, 300px);
    box-shadow: var(--shadow-2xl, 0 25px 50px -12px rgba(0, 0, 0, 0.4));
  }

  .layout-left.collapsed {
    width: 0;
    box-shadow: none;
  }

  .layout-right.expanded {
    position: absolute;
    inset: 0 0 0 auto;
    z-index: 30;
    width: min(88vw, 360px);
    box-shadow: var(--shadow-2xl, 0 25px 50px -12px rgba(0, 0, 0, 0.4));
  }
}
</style>
