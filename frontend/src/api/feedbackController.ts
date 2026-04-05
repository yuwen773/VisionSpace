// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** addFeedback POST /api/feedback/add */
export async function addFeedbackUsingPost(
  body: API.FeedbackAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>('/api/feedback/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** getFeedbackById GET /api/feedback/get/{id} */
export async function getFeedbackByIdUsingGet(
  params: { id: number },
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseFeedbackVO_>(`/api/feedback/get/${params.id}`, {
    method: 'GET',
    ...(options || {}),
  })
}

/** listFeedbackByPage POST /api/feedback/list/page */
export async function listFeedbackByPageUsingPost(
  body: API.FeedbackQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageFeedbackVO_>('/api/feedback/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** updateFeedbackStatus POST /api/feedback/update/status */
export async function updateFeedbackStatusUsingPost(
  body: API.FeedbackUpdateStatusRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/feedback/update/status', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** uploadFeedbackAttachment POST /feedback/upload */
export async function uploadFeedbackAttachment(
  file: File,
  options?: { [key: string]: any }
) {
  const formData = new FormData()
  formData.append('file', file)

  return request<string>('/api/feedback/upload', {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  })
}
