// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /picture/grant/add */
export async function createGrantUsingPost(
  body: API.PictureGrantCreateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseResourceGrantVO>('/picture/grant/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /picture/grant/list */
export async function listGrantsUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listGrantsUsingGetParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListResourceGrantVO>('/picture/grant/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /picture/grant/mine */
export async function listMyGrantsUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseListResourceGrantVO>('/picture/grant/mine', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /picture/grant/revoke */
export async function revokeGrantUsingPost(
  body: API.PictureGrantRevokeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseResourceGrantVO>('/picture/grant/revoke', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
