// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /agent/history/${param0} */
export async function getHistoryUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getHistoryUsingGetParams,
  options?: { [key: string]: any }
) {
  const { threadId: param0, ...queryParams } = params
  return request<API.BaseResponseChatHistoryVO>(`/agent/history/${param0}`, {
    method: 'GET',
    params: {
      // limit has a default value: 40
      limit: '40',
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/image/chat */
export async function chatUsingPost(body: API.AgentChatRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/agent/image/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/image/chat/stream */
export async function chatStreamUsingPost(
  body: API.AgentChatRequest,
  options?: { [key: string]: any }
) {
  return request<Record<string, any>>('/agent/image/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/image/feedback */
export async function feedbackUsingPost(
  body: API.FeedbackRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/agent/image/feedback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/recovery/${param0} */
export async function getRecoveryStateUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getRecoveryStateUsingGetParams,
  options?: { [key: string]: any }
) {
  const { threadId: param0, ...queryParams } = params
  return request<API.BaseResponseRecoveryStateVO>(`/agent/recovery/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/recovery/${param0}/resume */
export async function resumeUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resumeUsingPostParams,
  options?: { [key: string]: any }
) {
  const { threadId: param0, ...queryParams } = params
  return request<API.BaseResponseString>(`/agent/recovery/${param0}/resume`, {
    method: 'POST',
    params: {
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/recovery/action/confirm */
export async function confirmActionUsingPost(
  body: API.ActionReviewRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseRecoveryStateVO>('/agent/recovery/action/confirm', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/recovery/action/dismiss */
export async function dismissActionUsingPost(
  body: API.ActionReviewRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseRecoveryStateVO>('/agent/recovery/action/dismiss', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/sessions */
export async function getSessionsUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getSessionsUsingGetParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListSessionVO>('/agent/sessions', {
    method: 'GET',
    params: {
      // limit has a default value: 20
      limit: '20',
      ...params,
    },
    ...(options || {}),
  })
}
