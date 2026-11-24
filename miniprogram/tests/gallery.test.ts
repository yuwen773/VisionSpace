import { describe, expect, it } from 'vitest'
import {
  clampAspect,
  formatCount,
  formatDate,
  formatFileSize,
  heightForAspect,
  resolveAspect,
  toCard,
  toPrivateCard,
  withDownloadFailure,
  withLocalPath,
  withPendingDownload,
} from '../src/logic/gallery'
import { toNumberOrNull } from '../src/logic/long'

const resolve = (path: string) => `https://example.test/api${path}`

describe('toCard', () => {
  it('resolves the thumbnail endpoint and keeps display metadata', () => {
    const card = toCard(
      {
        id: 7,
        name: '山间清晨',
        thumbnailUrl: '/picture/content/7/thumbnail',
        picWidth: 800,
        picHeight: 1200,
        picColor: '#123456',
        tags: ['风景', ''],
      },
      resolve,
    )
    expect(card.imageUrl).toBe('https://example.test/api/picture/content/7/thumbnail')
    expect(card.title).toBe('山间清晨')
    expect(card.color).toBe('#123456')
    expect(card.tags).toEqual(['风景'])
    expect(card.aspect).toBeCloseTo(800 / 1200)
    expect(card.broken).toBe(false)
  })

  it('marks pictures without a content URL as broken instead of rendering a broken image', () => {
    const card = toCard({ id: 8, thumbnailUrl: null }, resolve)
    expect(card.imageUrl).toBe('')
    expect(card.broken).toBe(true)
    expect(card.title).toBe('作品 8')
  })

  it('falls back to a neutral placeholder colour', () => {
    const card = toCard({ id: 9, thumbnailUrl: '/x', picColor: null }, resolve)
    expect(card.color).toContain('rgba')
  })

  it('marks quarantined pictures as placeholders even if a URL slipped through', () => {
    const card = toCard(
      { id: 11, thumbnailUrl: '/picture/content/11/thumbnail', quarantined: true },
      resolve,
    )
    expect(card.quarantined).toBe(true)
    expect(card.broken).toBe(true)
  })

  it('accepts the string id produced by the Long serializer', () => {
    const card = toCard({ id: '42', thumbnailUrl: '/picture/content/42/thumbnail' }, resolve)
    expect(card.id).toBe('42')
    expect(card.title).toBe('作品 42')
  })

  it('keeps 19-digit snowflake ids exact instead of rounding them', () => {
    const card = toCard({ id: '2038608856269361154', thumbnailUrl: '/x' }, resolve)
    expect(card.id).toBe('2038608856269361154')
  })

  it('derives a square-ish aspect when dimensions are missing', () => {
    expect(toCard({ id: 10 }, resolve).aspect).toBe(1)
  })
})

describe('toPrivateCard', () => {
  it('keeps the remote url but waits for an authenticated download', () => {
    const card = toPrivateCard({ id: 7, thumbnailUrl: '/picture/content/7/thumbnail' }, resolve)
    expect(card.imageUrl).toBe('https://example.test/api/picture/content/7/thumbnail')
    expect(card.src).toBe('')
    expect(card.localPath).toBe('')
    expect(card.pending).toBe(true)
    expect(card.broken).toBe(false)
  })

  it('does not put a token into the url', () => {
    const card = toPrivateCard({ id: 7, thumbnailUrl: '/picture/content/7/thumbnail' }, resolve)
    expect(card.imageUrl).not.toContain('satoken')
    expect(card.imageUrl).not.toContain('?')
  })

  it('keeps quarantined pictures as placeholders instead of pending downloads', () => {
    const card = toPrivateCard({ id: 8, thumbnailUrl: '/x', quarantined: true }, resolve)
    expect(card.pending).toBe(false)
    expect(card.quarantined).toBe(true)
  })

  it('fills the local path once the download succeeds', () => {
    const card = toPrivateCard({ id: 7, thumbnailUrl: '/x' }, resolve)
    const loaded = withLocalPath(card, '/tmp/7')
    expect(loaded.src).toBe('/tmp/7')
    expect(loaded.localPath).toBe('/tmp/7')
    expect(loaded.pending).toBe(false)
    expect(loaded.broken).toBe(false)
  })

  it('falls back to an unavailable placeholder when the download fails', () => {
    const card = toPrivateCard({ id: 7, thumbnailUrl: '/x' }, resolve)
    const failed = withDownloadFailure(card)
    expect(failed.src).toBe('')
    expect(failed.pending).toBe(false)
    expect(failed.broken).toBe(true)
  })

  it('resets an expired card back to pending so it is re-authorised', () => {
    const card = withLocalPath(toPrivateCard({ id: 7, thumbnailUrl: '/x' }, resolve), '/tmp/7')
    const stale = withPendingDownload(withDownloadFailure(card))
    // 换账号或缓存过期后不能再显示旧本地文件，要退回「下载中」重新鉴权
    expect(stale.src).toBe('')
    expect(stale.localPath).toBe('')
    expect(stale.pending).toBe(true)
    expect(stale.broken).toBe(false)
    expect(stale.imageUrl).toBe(card.imageUrl)
  })
})

describe('resolveAspect / clampAspect', () => {
  it('prefers the server-computed picScale', () => {
    expect(resolveAspect({ picScale: 1.5, picWidth: 1, picHeight: 100 })).toBe(1.5)
  })

  it('computes from width and height when picScale is absent', () => {
    expect(resolveAspect({ picScale: null, picWidth: 400, picHeight: 200 })).toBe(2)
  })

  it('clamps extreme ratios into the layout range', () => {
    expect(clampAspect(0.1)).toBe(0.5)
    expect(clampAspect(9)).toBe(2)
    expect(clampAspect(Number.NaN)).toBe(1)
  })
})

describe('heightForAspect', () => {
  it('matches the column width for a square picture', () => {
    expect(heightForAspect(1, 300)).toBe(300)
  })

  it('clamps very tall pictures so one card cannot dominate the screen', () => {
    expect(heightForAspect(0.5, 1000)).toBe(720)
  })
})

describe('formatCount', () => {
  it('shows raw values below ten thousand', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(9999)).toBe('9999')
    expect(formatCount('128')).toBe('128')
  })

  it('uses 万 above ten thousand', () => {
    expect(formatCount(10000)).toBe('1万')
    expect(formatCount(12345)).toBe('1.2万')
    expect(formatCount(105000)).toBe('10.5万')
  })

  it('returns 0 for invalid input', () => {
    expect(formatCount(null)).toBe('0')
    expect(formatCount(-5)).toBe('0')
    expect(formatCount('abc')).toBe('0')
  })
})

describe('formatDate', () => {
  it('formats ISO timestamps', () => {
    expect(formatDate('2026-11-24T08:30:00.000+00:00')).toMatch(/^2026-11-2[34]$/)
  })

  it('formats the legacy space-separated format used by the backend', () => {
    expect(formatDate('2026-11-24 08:30:00')).toBe('2026-11-24')
  })

  it('formats millisecond timestamps', () => {
    expect(formatDate(Date.UTC(2026, 10, 24, 12, 0, 0))).toBe('2026-11-24')
  })

  it('returns an empty string for missing or invalid values', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate('not-a-date')).toBe('')
  })
})

describe('formatFileSize', () => {
  it('formats bytes, KB and MB', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB')
  })

  it('returns an empty string for unknown sizes', () => {
    expect(formatFileSize(null)).toBe('')
    expect(formatFileSize(0)).toBe('')
  })
})

describe('toNumberOrNull', () => {
  it('accepts numbers and decimal strings', () => {
    expect(toNumberOrNull(12)).toBe(12)
    expect(toNumberOrNull('12')).toBe(12)
  })

  it('rejects blanks and non-numeric text', () => {
    expect(toNumberOrNull('')).toBeNull()
    expect(toNumberOrNull('  ')).toBeNull()
    expect(toNumberOrNull('abc')).toBeNull()
    expect(toNumberOrNull(null)).toBeNull()
    expect(toNumberOrNull(undefined)).toBeNull()
    expect(toNumberOrNull(Number.NaN)).toBeNull()
  })
})
