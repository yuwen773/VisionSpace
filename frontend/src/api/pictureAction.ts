// @ts-ignore
/* eslint-disable */
import request from '@/request'

export interface PictureActionRequest {
  pictureId: number
  actionType: 'impression' | 'click' | 'view' | 'like' | 'collect' | 'download' | 'share'
  source?: string
}

/** 上报图片行为 */
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
