// 手写 API 客户端：图片行为上报与推荐。
//
// 生成器有对应的 pictureActionController.ts / pictureRecommendController.ts，
// 但这里多了生成器给不出的东西：行为/来源两个枚举常量，以及更贴合业务语义的
// 请求类型。业务代码按这里的名字引用，改生成器不影响调用方。
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

export interface PictureRecommendRequest {
  type?: 'hot' | 'latest' | 'quality' | 'random'
  page?: number
  size?: number
}

/**
 * 上报图片行为
 * POST /api/picture/action/report
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

/**
 * 获取推荐图片列表
 * GET /api/picture/recommend/list
 */
export async function getRecommendListUsingGet(
  params: PictureRecommendRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListPictureVO_>('/api/picture/recommend/list', {
    method: 'GET',
    params: { ...params },
    ...(options || {}),
  })
}

/**
 * 刷新推荐缓存
 * POST /api/picture/recommend/refresh
 */
export async function refreshCacheUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean_>('/api/picture/recommend/refresh', {
    method: 'POST',
    ...(options || {}),
  })
}
