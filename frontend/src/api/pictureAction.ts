// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**

 * 行为类型枚举
 * 0=impression 1=click 2=view 3=like 4=collect 5=download 6=share
 */
export const ActionTypeEnum = {
  IMPRESSION: 0,
  CLICK: 1,
  VIEW: 2,
  LIKE: 3,
  COLLECT: 4,
  DOWNLOAD: 5,
  SHARE: 6,
} as const

/**

 * 来源枚举
 * 0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER
 */
export const ActionSourceEnum = {
  HOME_RECOMMEND: 0,
  DETAIL: 1,
  SEARCH: 2,
  OTHER: 3,
} as const

export interface PictureActionRequest {
  pictureId: number
  actionType: number
  source: number
}

/**
 * 上报图片行为
 */
export async function reportPictureAction(
  params: PictureActionRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/picture/action/report', {
    method: 'POST',
    data: params,
    ...(options || {}),
  })
}
