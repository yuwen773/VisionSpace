import { describe, it, expect } from 'vitest'
import { contentSrc, formatSize } from '@/utils'

describe('formatSize', () => {
  it('按量级选择 B / KB / MB', () => {
    expect(formatSize(512)).toBe('512 B')
    expect(formatSize(2048)).toBe('2.00 KB')
    expect(formatSize(5 * 1024 * 1024)).toBe('5.00 MB')
  })

  it('0 是合法值，不能被当成缺失', () => {
    expect(formatSize(0)).toBe('0 B')
  })

  it('缺失时返回占位文案', () => {
    expect(formatSize(undefined as unknown as number)).toBe('未知')
  })
})

describe('contentSrc', () => {
  it('受控内容入口补上 API 源站与 context-path', () => {
    const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
    expect(contentSrc('/picture/content/1/original')).toBe(`${base}/api/picture/content/1/original`)
  })

  it('反馈附件入口同样走 API 源站，不直连存储', () => {
    const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
    expect(contentSrc('/feedback/attachment/12')).toBe(`${base}/api/feedback/attachment/12`)
    // 方案板预览同样是服务端签发的受控入口，不能在页面里各自拼前缀
    expect(contentSrc('/agent/boards/b1/items/i1/content')).toBe(
      `${base}/api/agent/boards/b1/items/i1/content`,
    )
    // 站内找图结果的素材地址同样是受控入口
    expect(contentSrc('/agent/asset-results/r1/items/i1')).toBe(
      `${base}/api/agent/asset-results/r1/items/i1`,
    )
    // 已经带前缀的地址原样返回：同一条 ref 不会被拼成 /api/api/...
    expect(contentSrc('/api/picture/content/1/original')).toBe('/api/picture/content/1/original')
  })

  it('源站地址与空值不被改写', () => {
    expect(contentSrc('https://example.com/a.jpg')).toBe('https://example.com/a.jpg')
    expect(contentSrc(null)).toBe('')
    expect(contentSrc(undefined)).toBe('')
  })

  it('形似的绝对地址不能被误判为受控入口', () => {
    expect(contentSrc('https://evil.example/feedback/attachment/1')).toBe(
      'https://evil.example/feedback/attachment/1',
    )
  })
})
