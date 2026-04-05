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
const isLoading = ref(false)

// 样式文件ID
const GLOBAL_CSS_ID = 'theme-global-css'
const ANTD_CSS_ID = 'theme-antd-css'

/**
 * 主题管理 Composable
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
   * 应用主题
   */
  const applyTheme = async (theme: ThemeType) => {
    isLoading.value = true

    try {
      // 移除旧的主题样式
      removeThemeStyles()

      // 加载新主题样式
      await loadThemeStyles(theme)

      // 更新当前主题
      currentTheme.value = theme

      // 触发主题切换动画
      window.dispatchEvent(new CustomEvent('theme-changed', { detail: { theme } }))

      // 保存到 localStorage
      localStorage.setItem(STORAGE_KEY, theme)

      // 更新 document 的 data-theme 属性（用于 CSS 选择器）
      document.documentElement.setAttribute('data-theme', theme)
    } catch (error) {
      console.error('应用主题失败:', error)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 移除主题样式
   */
  const removeThemeStyles = () => {
    const globalCss = document.getElementById(GLOBAL_CSS_ID)
    const antdCss = document.getElementById(ANTD_CSS_ID)

    if (globalCss) globalCss.remove()
    if (antdCss) antdCss.remove()
  }

  /**
   * 加载主题样式
   */
  const loadThemeStyles = (theme: ThemeType): Promise<void> => {
    return new Promise((resolve, reject) => {
      const suffix = theme === 'pop' ? '-ziyan' : ''

      // 加载全局样式
      const globalCss = document.createElement('link')
      globalCss.id = GLOBAL_CSS_ID
      globalCss.rel = 'stylesheet'
      globalCss.href = `/src/styles/global${suffix}.css`

      // 加载 Ant Design 主题样式
      const antdCss = document.createElement('link')
      antdCss.id = ANTD_CSS_ID
      antdCss.rel = 'stylesheet'
      antdCss.href = `/src/styles/antd-theme${suffix}.css`

      // 监听加载完成
      let loadCount = 0
      const onLoad = () => {
        loadCount++
        if (loadCount === 2) {
          resolve()
        }
      }

      globalCss.onload = onLoad
      globalCss.onerror = reject
      antdCss.onload = onLoad
      antdCss.onerror = reject

      // 添加到文档
      document.head.appendChild(globalCss)
      document.head.appendChild(antdCss)
    })
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
    isLoading,
    THEMES,
    initTheme,
    applyTheme,
    toggleTheme,
    setTheme,
    getCurrentThemeInfo
  }
}
