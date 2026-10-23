import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ActionTypeEnum } from '@/api-custom/picture'

const { reportPictureAction } = vi.hoisted(() => ({ reportPictureAction: vi.fn() }))

vi.mock('@/api-custom/picture', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api-custom/picture')>()
  return { ...actual, reportPictureAction }
})

import { usePictureAction } from '@/composables/usePictureAction'

/**
 * 行为上报的事件幂等键：
 * 曝光、浏览在一次会话内复用同一个键，点击每次都是新的逻辑事件。
 */
describe('picture action reporting', () => {
  beforeEach(() => {
    reportPictureAction.mockReset()
    reportPictureAction.mockResolvedValue({ data: { code: 0, data: true } })
  })

  it('同一张图片的重复曝光复用同一个事件键', async () => {
    const { report } = usePictureAction()

    await report(1001, ActionTypeEnum.IMPRESSION, 0)
    await report(1001, ActionTypeEnum.IMPRESSION, 0)

    expect(reportPictureAction).toHaveBeenCalledTimes(2)
    const first = reportPictureAction.mock.calls[0][0]
    const second = reportPictureAction.mock.calls[1][0]
    expect(first.eventId).toBeTruthy()
    expect(second.eventId).toBe(first.eventId)
  })

  it('不同图片的曝光事件键不同', async () => {
    const { report } = usePictureAction()

    await report(1001, ActionTypeEnum.IMPRESSION, 0)
    await report(1002, ActionTypeEnum.IMPRESSION, 0)

    const first = reportPictureAction.mock.calls[0][0]
    const second = reportPictureAction.mock.calls[1][0]
    expect(second.eventId).not.toBe(first.eventId)
  })

  it('同一张图片的重复点击是两次独立的逻辑事件', async () => {
    const { report } = usePictureAction()

    await report(1001, ActionTypeEnum.CLICK, 0)
    await report(1001, ActionTypeEnum.CLICK, 0)

    expect(reportPictureAction).toHaveBeenCalledTimes(2)
    const first = reportPictureAction.mock.calls[0][0]
    const second = reportPictureAction.mock.calls[1][0]
    expect(second.eventId).not.toBe(first.eventId)
  })

  it('上报失败不影响页面本身', async () => {
    reportPictureAction.mockRejectedValueOnce(new Error('network down'))
    const { report } = usePictureAction()

    await expect(report(1001, ActionTypeEnum.VIEW, 1)).resolves.toBeUndefined()
  })
})
