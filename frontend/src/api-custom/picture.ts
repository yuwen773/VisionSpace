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
  /**
   * 事件幂等键：一次逻辑事件一个键，重放同一个事件不会被重复入账。
   */
  eventId?: string
}

export interface PictureRecommendRequest {
  type?: 'hot' | 'latest' | 'quality' | 'random'
  page?: number
  size?: number
  /**
   * 分页上下文：同一个上下文里的分页结果不重复、不遗漏。
   * 首次请求不传，服务端会在响应里给出，翻页时带上即可。
   */
  context?: string
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
 *
 * 返回体直接用生成器给出的契约（API.BaseResponseRecommendPageVO）：
 * 后端把 Long 序列化成十进制字符串，total 在契约里就是 string，
 * 解析规则见 useRecommendPaging 的 parseRecommendTotal。
 */
export async function getRecommendListUsingGet(
  params: PictureRecommendRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseRecommendPageVO>('/api/picture/recommend/list', {
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
