import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
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
})
