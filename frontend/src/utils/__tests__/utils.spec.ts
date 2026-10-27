import { describe, it, expect } from 'vitest'
import { contentSrc, formatSize, toHexColor, lerp, clamp } from '@/utils'

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

describe('toHexColor', () => {
  it('把 0x 前缀的整数转成 6 位十六进制', () => {
    expect(toHexColor('0xFF0000')).toBe('#ff0000')
    expect(toHexColor('0x00FF00')).toBe('#00ff00')
  })

  it('不足 6 位时补零', () => {
    expect(toHexColor('0x0000FF')).toBe('#0000ff')
    expect(toHexColor('0xF')).toBe('#00000f')
  })

  it('空值回退为黑色', () => {
    expect(toHexColor('')).toBe('#000000')
  })
})

describe('lerp / clamp', () => {
  it('lerp 在端点处取原值', () => {
    expect(lerp(10, 20, 0)).toBe(10)
    expect(lerp(10, 20, 1)).toBe(20)
    expect(lerp(10, 20, 0.5)).toBe(15)
  })

  it('clamp 收敛到区间内', () => {
    expect(clamp(5, 1, 10)).toBe(5)
    expect(clamp(-5, 1, 10)).toBe(1)
    expect(clamp(50, 1, 10)).toBe(10)
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
