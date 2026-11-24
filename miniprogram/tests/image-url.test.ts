import { describe, expect, it } from 'vitest'
import { isTrustedContentUrl } from '../src/logic/image-url'

const BASE = 'https://visionspace.space'

describe('isTrustedContentUrl', () => {
  it('accepts content urls on the api origin', () => {
    expect(isTrustedContentUrl(`${BASE}/api/picture/content/1/thumbnail`, BASE)).toBe(true)
    // 开发期把 API 指到本地时同样按该源判断
    expect(isTrustedContentUrl('http://127.0.0.1:8081/api/x', 'http://127.0.0.1:8081')).toBe(true)
  })

  it('rejects other origins even when they prefix-match the api host', () => {
    expect(isTrustedContentUrl('https://visionspace.space.evil.com/api/x', BASE)).toBe(false)
    expect(isTrustedContentUrl('https://evil.com/https://visionspace.space/api/x', BASE)).toBe(false)
    expect(isTrustedContentUrl('http://visionspace.space/api/x', BASE)).toBe(false)
  })

  it('rejects non-http schemes and relative paths', () => {
    expect(isTrustedContentUrl('wxfile://tmp_x', BASE)).toBe(false)
    expect(isTrustedContentUrl('/api/picture/content/1', BASE)).toBe(false)
    expect(isTrustedContentUrl('', BASE)).toBe(false)
  })

  it('tolerates a trailing slash on the configured base', () => {
    expect(isTrustedContentUrl(`${BASE}/api/x`, `${BASE}/`)).toBe(true)
  })

  it('rejects everything when the base is empty', () => {
    expect(isTrustedContentUrl(`${BASE}/api/x`, '')).toBe(false)
  })
})
