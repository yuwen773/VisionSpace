/**
 * 匿名行为上报（纯逻辑）。
 *
 * 约束来自后端 `PictureActionServiceImpl`：
 * - 匿名只能上报曝光(0) / 点击(1) / 浏览(2)，点赞等需要登录；
 * - `userId` 不得由客户端声明；
 * - `eventId` 可选，形如 `[A-Za-z0-9_.:-]{1,64}`，服务端会按它去重。
 */
export const ACTION_IMPRESSION = 0
export const ACTION_CLICK = 1
export const ACTION_VIEW = 2

/** `ActionSourceEnum.MINI_PROGRAM` */
export const SOURCE_MINI_PROGRAM = 4

export type AnonymousActionType = typeof ACTION_IMPRESSION | typeof ACTION_CLICK | typeof ACTION_VIEW

export interface ActionEvent {
  pictureId: number
  actionType: AnonymousActionType
  source: number
  eventId: string
}

const EVENT_ID_PATTERN = /^[A-Za-z0-9_.:-]{1,64}$/
const MAX_EVENT_ID_LENGTH = 64

/** 生成合法 eventId；entropy 会被过滤成 `[a-z0-9]`，保证符合后端校验。 */
export function makeEventId(now: number, entropy: string): string {
  const stamp = Math.floor(now).toString(36)
  const random = entropy.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 16)
  const id = random === '' ? `mp-${stamp}` : `mp-${stamp}-${random}`
  return id.length > MAX_EVENT_ID_LENGTH ? id.slice(0, MAX_EVENT_ID_LENGTH) : id
}

export function actionEvent(
  pictureId: number,
  actionType: AnonymousActionType,
  eventId: string,
): ActionEvent {
  return { pictureId, actionType, source: SOURCE_MINI_PROGRAM, eventId }
}

export function isValidEventId(eventId: string): boolean {
  return EVENT_ID_PATTERN.test(eventId)
}

/**
 * 去重闸门：同一张图同一种行为，每个页面实例最多上报一次。
 *
 * 与服务端不做重复计数不同，这里省掉的是网络请求本身：
 * 曝光在滚动时会反复进入视口，没有闸门会把请求打满。
 */
export interface ReportGate {
  tryAcquire(pictureId: number, actionType: number): boolean
  reset(): void
  size(): number
}

export function createReportGate(): ReportGate {
  const seen = new Set<string>()
  return {
    tryAcquire(pictureId: number, actionType: number): boolean {
      if (!Number.isSafeInteger(pictureId) || pictureId <= 0) {
        return false
      }
      if (!isAnonymousAction(actionType)) {
        return false
      }
      const key = `${pictureId}:${actionType}`
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    },
    reset(): void {
      seen.clear()
    },
    size(): number {
      return seen.size
    },
  }
}

export function isAnonymousAction(actionType: number): actionType is AnonymousActionType {
  return actionType === ACTION_IMPRESSION || actionType === ACTION_CLICK || actionType === ACTION_VIEW
}
