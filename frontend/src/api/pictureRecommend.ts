// @ts-ignore
/* eslint-disable */
import request from '@/request'

export interface PictureRecommendRequest {
  type?: 'hot' | 'latest' | 'quality' | 'random'
  page?: number
  size?: number
}

/** getRecommendList GET /api/picture/recommend/list */
export async function getRecommendListUsingGet(
  params: PictureRecommendRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListPictureVO_>('/api/picture/recommend/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** refreshCache POST /api/picture/recommend/refresh */
export async function refreshCacheUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean_>('/api/picture/recommend/refresh', {
    method: 'POST',
    ...(options || {}),
  })
}
