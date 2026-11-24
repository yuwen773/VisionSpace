import { describe, expect, it } from 'vitest'
import { averageColor, COLOR_PALETTE, normalizeHex } from '../src/logic/color'

describe('normalizeHex', () => {
  it('accepts with or without #, any case', () => {
    expect(normalizeHex('#AABBCC')).toBe('#aabbcc')
    expect(normalizeHex('aabbcc')).toBe('#aabbcc')
    expect(normalizeHex('  #12Ef34 ')).toBe('#12ef34')
  })

  it('expands three-digit shorthand', () => {
    expect(normalizeHex('#abc')).toBe('#aabbcc')
    expect(normalizeHex('f0a')).toBe('#ff00aa')
  })

  it('rejects malformed values', () => {
    expect(normalizeHex('#12345')).toBeNull()
    expect(normalizeHex('zzzzzz')).toBeNull()
    expect(normalizeHex('')).toBeNull()
    expect(normalizeHex(null)).toBeNull()
  })
})

describe('averageColor', () => {
  it('averages opaque pixels', () => {
    const rgba = [
      255, 0, 0, 255,
      0, 0, 255, 255,
    ]
    expect(averageColor(rgba, 2, 1)).toBe('#800080')
  })

  it('skips transparent pixels', () => {
    const rgba = [
      0, 0, 0, 0,
      0, 255, 0, 255,
    ]
    expect(averageColor(rgba, 2, 1)).toBe('#00ff00')
  })

  it('returns null when every pixel is transparent', () => {
    expect(averageColor([0, 0, 0, 0], 1, 1)).toBeNull()
  })

  it('returns null for invalid dimensions', () => {
    expect(averageColor([255, 0, 0, 255], 0, 0)).toBeNull()
  })
})

describe('COLOR_PALETTE', () => {
  it('contains only valid six-digit hex values', () => {
    for (const color of COLOR_PALETTE) {
      expect(normalizeHex(color)).toBe(color)
    }
  })
})
