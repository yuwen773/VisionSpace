import type { ReportGate } from '../logic/telemetry'
import { actionEvent, makeEventId, type AnonymousActionType } from '../logic/telemetry'
import { reportAction } from './gallery'

/**
 * 行为上报的统一出口：闸门去重 → 生成合法 eventId → 发送。
 *
 * 统计失败只影响数据，不能打断浏览，因此异常只记日志。
 */
export function reportOnce(
  gate: ReportGate,
  pictureId: string,
  actionType: AnonymousActionType,
): void {
  if (!gate.tryAcquire(pictureId, actionType)) {
    return
  }
  const eventId = makeEventId(Date.now(), Math.random().toString(36).slice(2, 10))
  reportAction(actionEvent(pictureId, actionType, eventId)).catch((error) => {
    console.warn('行为上报失败', actionType, error)
  })
}
