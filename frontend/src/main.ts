import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import '@/utils/copy-code'
import '@/styles/code-theme.css'

// 引入antDesignVue
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import VueCropper from 'vue-cropper'
import 'vue-cropper/dist/index.css'

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

app.mount('#app')
