import { vi } from 'vitest'

/**
 * jsdom 没有实现 window.matchMedia，而组件的响应式断点、减少动效判断都会直接调用它。
 * 统一在测试环境补一个恒为不匹配的实现，避免每个用例各自 mock。
 */
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  window.matchMedia = ((query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }) as unknown as MediaQueryList) as typeof window.matchMedia
}
