import { fileURLToPath, URL } from 'node:url'

import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { cspPlugin } from './src/utils/csp'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 只读 VITE_ 前缀的键，且仅用于生成 CSP 目标清单
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  return {
    plugins: [
      vue(),
      vueDevTools(),
      cspPlugin({
        plantumlServer: env.VITE_PLANTUML_SERVER,
        imageHosts: env.VITE_CSP_IMAGE_HOSTS,
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    // 仅影响 vitest；构建产物不受影响
    test: {
      environment: 'jsdom',
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
      globals: true,
    },
    server: {
      allowedHosts: process.env.VITE_DEV_HOST ? [process.env.VITE_DEV_HOST] : [],
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
  }
})
