import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, attachCsrf, CSRF_COOKIE, CSRF_HEADER, readCsrfToken, webSocketUrl } from '@/apiOrigin'

function setCookie(value: string) {
  document.cookie = `${CSRF_COOKIE}=${value}; path=/`
}

describe('same-origin entry', () => {
  it('相对路径补上 API 源站，绝对地址原样返回', () => {
    expect(apiUrl('/api/picture/content/1/original')).toBe('/api/picture/content/1/original')
    expect(apiUrl('/picture/content/1/original')).toBe('/picture/content/1/original')
    expect(apiUrl('https://example.com/a.jpg')).toBe('https://example.com/a.jpg')
    expect(apiUrl('blob:http://localhost/123')).toBe('blob:http://localhost/123')
  })

  it('WebSocket 地址由页面来源推导，不出现固定公网地址', () => {
    const url = webSocketUrl('/api/ws/picture/edit?pictureId=1')
    expect(url.startsWith('ws://') || url.startsWith('wss://')).toBe(true)
    expect(url).not.toContain('8.163.10.130')
    expect(url).not.toContain('localhost:8081')
    expect(url.endsWith('/api/ws/picture/edit?pictureId=1')).toBe(true)
  })

  it('https 页面推导出 wss，避免混合内容', () => {
    const original = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...original, origin: 'https://visionspace.space' },
    })
    try {
      // 无显式源站时以页面来源为准
      expect(webSocketUrl('/api/ws/picture/edit?pictureId=1')).toBe(
        'wss://visionspace.space/api/ws/picture/edit?pictureId=1',
      )
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: original })
    }
  })
})

describe('CSRF double submit', () => {
  beforeEach(() => {
    document.cookie = `${CSRF_COOKIE}=; path=/; max-age=0`
  })

  it('读取同名的双提交令牌', () => {
    setCookie('token-value')
    expect(readCsrfToken()).toBe('token-value')
  })

  it('缺失时返回空串而不是抛错', () => {
    expect(readCsrfToken()).toBe('')
  })

  it('状态修改请求带上令牌，GET 不带', () => {
    setCookie('token-value')
    expect(attachCsrf({ method: 'post', headers: {} as Record<string, string> }).headers[CSRF_HEADER]).toBe('token-value')
    expect(attachCsrf({ method: 'DELETE', headers: {} as Record<string, string> }).headers[CSRF_HEADER]).toBe('token-value')
    expect(attachCsrf({ method: 'get', headers: {} as Record<string, string> }).headers[CSRF_HEADER]).toBeUndefined()
  })

  it('没有令牌时保持请求原样，由后端决定拒绝', () => {
    const untouched = attachCsrf({ method: 'post' })
    expect('headers' in untouched).toBe(false)
  })
})
