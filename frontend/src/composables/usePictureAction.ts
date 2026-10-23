import { ActionTypeEnum, reportPictureAction } from '@/api-custom/picture'

/**
 * 一次会话只算一次的行为：同一张图片的重复曝光、重复浏览是同一个逻辑事件，
 * 重放时复用同一个事件键，服务端据此只入账一次。
 *
 * 点击等动作每次都是新的逻辑事件：用户真的点了两次，就应该记两次。
 */
const SESSION_DEDUPED_ACTIONS = new Set<number>([ActionTypeEnum.IMPRESSION, ActionTypeEnum.VIEW])

/**
 * 图片行为上报。
 *
 * 事件幂等键按「图片 + 行为」在一次页面会话内保持不变：
 * 同一个逻辑事件重复渲染或重试时复用同一个键，服务端据此只入账一次。
 */
export function usePictureAction() {
  const eventIds = new Map<string, string>()

  const newEventId = (): string => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
    return `evt-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }

  /**
   * 上报一次图片行为。上报失败不影响页面本身。
   */
  const report = async (pictureId: number, actionType: number, source: number): Promise<void> => {
    const key = `${pictureId}:${actionType}`
    let eventId: string
    if (SESSION_DEDUPED_ACTIONS.has(actionType)) {
      eventId = eventIds.get(key) ?? newEventId()
      eventIds.set(key, eventId)
    } else {
      // 点击等行为每次都算一次新的逻辑事件
      eventId = newEventId()
    }
    try {
      await reportPictureAction({ pictureId, actionType, source, eventId })
    } catch (error) {
      console.warn('上报图片行为失败:', error)
    }
  }

  return { report }
}
