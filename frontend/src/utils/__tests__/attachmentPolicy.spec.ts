import { describe, expect, it } from 'vitest'
import { validateAttachment } from '@/utils/attachmentPolicy'

const file = (type: string, size = 1024) =>
  new File([new Uint8Array(size)], 'f', { type })

describe('attachmentPolicy', () => {
  it('接受后端支持的三种图片格式', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      expect(validateAttachment(file(type))).toBeNull()
    }
  })

  it('拒绝其他格式', () => {
    expect(validateAttachment(file('application/pdf'))).toContain('JPG')
    expect(validateAttachment(file('image/gif'))).toContain('JPG')
  })

  it('拒绝超过 2MB 的图片', () => {
    expect(validateAttachment(file('image/png', 2 * 1024 * 1024 + 1))).toContain('2MB')
    expect(validateAttachment(file('image/png', 2 * 1024 * 1024))).toBeNull()
  })
})
