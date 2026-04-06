import { ref, watch } from 'vue'

export type ThemeType = 'aurora' | 'pop'

export interface ThemeInfo {
  id: ThemeType
  name: string
  icon: string
  description: string
}

// 主题配置
export const THEMES: Record<ThemeType, ThemeInfo> = {
  aurora: {
    id: 'aurora',
    name: '极光主题',
    icon: '🌌',
    description: '暗色科技风格'
  },
  pop: {
    id: 'pop',
    name: '紫漾主题',
    icon: '💜',
    description: '梦幻紫粉风格'
  }
}

const STORAGE_KEY = 'ycloud-theme'

// 当前主题状态
const currentTheme = ref<ThemeType>('pop')

/**
 * 主题管理 Composable
 * 使用 CSS 类切换主题
 */
export const useTheme = () => {
  /**
   * 初始化主题
   */
  const initTheme = () => {
    // 从 localStorage 读取保存的主题
    const savedTheme = localStorage.getItem(STORAGE_KEY) as ThemeType | null

    // 检查系统偏好（如果用户没有手动选择过）
    if (!savedTheme) {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      currentTheme.value = prefersDark ? 'aurora' : 'pop'
    } else {
      currentTheme.value = savedTheme
    }

    // 应用主题
    applyTheme(currentTheme.value)
  }

  /**
   * 应用主题 - 通过切换 CSS 类实现
   */
  const applyTheme = (theme: ThemeType) => {
    // 移除旧主题类，添加新主题类
    document.documentElement.classList.remove('theme-aurora', 'theme-pop')
    document.documentElement.classList.add(`theme-${theme}`)

    // 更新当前主题
    currentTheme.value = theme

    // 保存到 localStorage
    localStorage.setItem(STORAGE_KEY, theme)

    // 更新 document 的 data-theme 属性
    document.documentElement.setAttribute('data-theme', theme)
  }

  /**
   * 切换主题
   */
  const toggleTheme = () => {
    const newTheme: ThemeType = currentTheme.value === 'pop' ? 'aurora' : 'pop'
    applyTheme(newTheme)
  }

  /**
   * 设置主题
   */
  const setTheme = (theme: ThemeType) => {
    if (theme !== currentTheme.value) {
      applyTheme(theme)
    }
  }

  /**
   * 获取当前主题信息
   */
  const getCurrentThemeInfo = (): ThemeInfo => {
    return THEMES[currentTheme.value]
  }

  // 监听主题变化
  watch(currentTheme, (newTheme) => {
    document.documentElement.setAttribute('data-theme', newTheme)
  })

  return {
    currentTheme,
    THEMES,
    initTheme,
    applyTheme,
    toggleTheme,
    setTheme,
    getCurrentThemeInfo
  }
}
