import { fileURLToPath, URL } from 'node:url'
import { loadEnv } from 'vite'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { cspPlugin } from '../src/utils/csp'

/**
 * 隔离验收构建：与生产一致的别名与 CSP，但入口只有 harness-src/index.html。
 * PlantUML 服务地址同样从环境变量读取，用来验证"配置了受控服务"的分支。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  return {
    // 以 harness-src 为根，构建产物写到 frontend/dist-harness
    root: fileURLToPath(new URL('.', import.meta.url)),
    // 相对资源路径，便于用任意本地静态服务器托管
    base: './',
    plugins: [
      vue(),
      cspPlugin({
        plantumlServer: env.VITE_PLANTUML_SERVER,
        imageHosts: env.VITE_CSP_IMAGE_HOSTS,
      }),
    ],
    resolve: {
      alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) },
    },
    build: {
      outDir: fileURLToPath(new URL('../dist-harness', import.meta.url)),
      emptyOutDir: true,
    },
  }
})
