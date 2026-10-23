// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /moderation/history */
export async function historyUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.historyUsingGetParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListModerationResourceVO>('/moderation/history', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /moderation/list */
export async function listQuarantinedUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseListModerationResourceVO>('/moderation/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /moderation/quarantine */
export async function quarantineUsingPost(
  body: API.ResourceModerationRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseModerationResourceVO>('/moderation/quarantine', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /moderation/release */
export async function releaseUsingPost(
  body: API.ResourceModerationRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseModerationResourceVO>('/moderation/release', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
