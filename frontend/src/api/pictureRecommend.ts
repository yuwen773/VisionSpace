// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /picture/recommend/list */
export async function getRecommendListUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getRecommendListUsingGetParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseRecommendPageVO>('/picture/recommend/list', {
    method: 'GET',
    params: {
      // type has a default value: hot
      type: 'hot',
      // page has a default value: 1
      page: '1',
      // size has a default value: 10
      size: '10',
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /picture/recommend/refresh */
export async function refreshCacheUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/picture/recommend/refresh', {
    method: 'POST',
    ...(options || {}),
  })
}
