import { describe, it, expect } from 'vitest'
import {
  GRANT_DEFAULT_VALIDITY_DAYS,
  GRANT_MAX_VALIDITY_DAYS,
  attachmentReadable,
  attachmentStatusText,
  formatDateTime,
  grantExpiresSoon,
  grantExpiresTime,
  grantStatusText,
  isolationStatusText,
  isGrantActive,
  resourceTypeText,
} from '@/api-custom/resourceProtection'

const DAY = 24 * 60 * 60 * 1000

describe('grantExpiresTime', () => {
  const now = new Date('2026-10-23T00:00:00.000Z')

  it('默认给 7 天', () => {
    expect(GRANT_DEFAULT_VALIDITY_DAYS).toBe(7)
    expect(grantExpiresTime(undefined, now).getTime()).toBe(now.getTime() + 7 * DAY)
  })

  it('有效期至少一天：0 与负数都被抬到 1 天', () => {
    expect(grantExpiresTime(0, now).getTime()).toBe(now.getTime() + DAY)
    expect(grantExpiresTime(-5, now).getTime()).toBe(now.getTime() + DAY)
  })

  it('有效期不超过服务端上限 30 天', () => {
    expect(GRANT_MAX_VALIDITY_DAYS).toBe(30)
    expect(grantExpiresTime(365, now).getTime()).toBe(now.getTime() + 30 * DAY)
  })
})

describe('grantStatusText', () => {
  it('三种状态各有文案', () => {
    expect(grantStatusText('ACTIVE')).toBe('生效中')
    expect(grantStatusText('REVOKED')).toBe('已撤销')
    expect(grantStatusText('EXPIRED')).toBe('已过期')
  })

  it('未知状态不猜测含义', () => {
    expect(grantStatusText('WHAT')).toBe('未知状态')
    expect(grantStatusText(undefined)).toBe('未知状态')
  })
})

describe('isGrantActive', () => {
  const future = new Date(Date.now() + DAY).toISOString()
  const past = new Date(Date.now() - DAY).toISOString()

  it('生效中且未到期才算有效', () => {
    expect(isGrantActive({ status: 'ACTIVE', expiresTime: future })).toBe(true)
  })

  it('已撤销、已过期、缺少到期时间都不算有效', () => {
    expect(isGrantActive({ status: 'REVOKED', expiresTime: future })).toBe(false)
    expect(isGrantActive({ status: 'EXPIRED', expiresTime: future })).toBe(false)
    expect(isGrantActive({ status: 'ACTIVE', expiresTime: past })).toBe(false)
    expect(isGrantActive({ status: 'ACTIVE' })).toBe(false)
    expect(isGrantActive(null)).toBe(false)
  })
})

describe('grantExpiresSoon', () => {
  const now = new Date('2026-10-23T00:00:00.000Z')

  it('24 小时内到期算临近到期', () => {
    const soon = new Date(now.getTime() + 60 * 60 * 1000).toISOString()
    expect(grantExpiresSoon({ expiresTime: soon }, now)).toBe(true)
  })

  it('更晚到期或已经到期都不算', () => {
    const later = new Date(now.getTime() + 3 * DAY).toISOString()
    const past = new Date(now.getTime() - DAY).toISOString()
    expect(grantExpiresSoon({ expiresTime: later }, now)).toBe(false)
    expect(grantExpiresSoon({ expiresTime: past }, now)).toBe(false)
    expect(grantExpiresSoon(null, now)).toBe(false)
  })
})

describe('attachmentStatusText / attachmentReadable', () => {
  it('附件状态各有文案', () => {
    expect(attachmentStatusText('UPLOADED')).toBe('待提交')
    expect(attachmentStatusText('BOUND')).toBe('已随反馈提交')
    expect(attachmentStatusText('LEGACY_VERIFIED')).toBe('归属已确认')
    expect(attachmentStatusText('BLOCKED')).toBe('归属不明，已阻断')
  })

  it('没有受控入口的附件不可读', () => {
    expect(attachmentReadable({ url: '/feedback/attachment/7' })).toBe(true)
    expect(attachmentReadable({ url: null })).toBe(false)
    expect(attachmentReadable({})).toBe(false)
    expect(attachmentReadable(undefined)).toBe(false)
  })
})

describe('resourceTypeText / isolationStatusText', () => {
  it('资源类型与隔离状态各有文案', () => {
    expect(resourceTypeText('picture')).toBe('图片')
    expect(resourceTypeText('feedback-attachment')).toBe('反馈附件')
    expect(resourceTypeText('other')).toBe('未知资源')
    expect(isolationStatusText('QUARANTINED')).toBe('隔离中')
    expect(isolationStatusText('RELEASED')).toBe('已恢复')
  })
})

describe('formatDateTime', () => {
  it('空值与非法值回退为占位符', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('not-a-date')).toBe('-')
  })

  it('合法时间给出可读文本', () => {
    expect(formatDateTime('2026-10-23T08:30:00.000Z')).not.toBe('-')
  })
})
