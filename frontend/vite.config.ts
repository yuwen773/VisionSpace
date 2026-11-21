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
      setupFiles: ['./src/test/setup.ts'],
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
    build: {
      // 重依赖按用途拆包：首屏只加载当前路由需要的块
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return
            if (id.includes('/three/')) return 'vendor-three'
            if (id.includes('/echarts/') || id.includes('/zrender/')) return 'vendor-echarts'
            if (id.includes('/mxgraph/')) return 'vendor-mxgraph'
            if (id.includes('ant-design-vue') || id.includes('@ant-design')) return 'vendor-antd'
            // 其余交给 Rollup 默认分包：mermaid 的图表渲染器本身已按需动态加载，
            // 强行合并会退化成单个大 chunk，失去按需加载的意义。
            return undefined
          },
        },
      },
    },
  }
})
