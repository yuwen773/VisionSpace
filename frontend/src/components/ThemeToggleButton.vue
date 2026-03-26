<template>
  <div class="theme-toggle-wrapper">
    <a-tooltip :title="`切换到${nextTheme.name}`" placement="left">
      <a-button
        @click="toggleTheme"
        class="theme-toggle-btn pop-card"
        :loading="isLoading"
        shape="circle"
        :size="size"
      >
        <template #icon>
          <span class="theme-icon">{{ currentThemeInfo.icon }}</span>
        </template>
      </a-button>
    </a-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTheme, type ThemeType } from '@/composables/useTheme'

interface Props {
  size?: 'small' | 'middle' | 'large'
  position?: 'fixed' | 'static'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'large',
  position: 'fixed'
})

const { currentTheme, isLoading, THEMES, toggleTheme } = useTheme()

const currentThemeInfo = computed(() => THEMES[currentTheme.value])

const nextTheme = computed(() => {
  const keys = Object.keys(THEMES) as ThemeType[]
  const currentIndex = keys.indexOf(currentTheme.value)
  const nextIndex = (currentIndex + 1) % keys.length
  return THEMES[keys[nextIndex]]
})
</script>

<style scoped>
.theme-toggle-wrapper {
  position: v-bind('position === "fixed" ? "fixed" : "static"');
  bottom: 24px;
  right: 24px;
  z-index: 999;
}

.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: v-bind('size === "large" ? "56px" : size === "middle" ? "48px" : "40px"');
  height: v-bind('size === "large" ? "56px" : size === "middle" ? "48px" : "40px"');
  font-size: v-bind('size === "large" ? "24px" : size === "middle" ? "20px" : "16px"');
  border: 3px solid var(--border-bold);
  box-shadow: var(--shadow-pop);
  transition: all var(--transition-bounce);
  background: var(--bg-card);
}

.theme-toggle-btn:hover {
  transform: scale(1.1) rotate(15deg);
  border-color: var(--color-sunshine);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.theme-icon {
  display: inline-block;
  animation: themeFloat 3s ease-in-out infinite;
}

@keyframes themeFloat {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-3px) rotate(10deg);
  }
}

/* 响应式 */
@media (max-width: 640px) {
  .theme-toggle-wrapper {
    bottom: 16px;
    right: 16px;
  }
}
</style>
