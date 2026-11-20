import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import '@/utils/copy-code'
import '@/styles/code-theme.css'

// 引入antDesignVue
import Antd, { message } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import VueCropper from 'vue-cropper'
import 'vue-cropper/dist/index.css'

// 引入主题管理 - 预加载两个主题的 CSS
import '@/styles/global.css'
import '@/styles/global-ziyan.css'
import '@/styles/antd-theme.css'
import '@/styles/antd-theme-ziyan.css'

// 引入主题管理
import { useTheme } from '@/composables/useTheme'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(Antd)
app.use(VueCropper)

// 初始化主题（必须在 mount 之前，避免 FOUC）
const { initTheme } = useTheme()
initTheme()

// 路由级异常（懒加载 chunk 取不到、守卫里未捕获的错误）会让
// <router-view> 一直空白，这里至少给出可读提示
router.onError((error) => {
  console.error('[router]', error)
  message.error('页面加载失败，请刷新重试')
})

app.mount('#app')
