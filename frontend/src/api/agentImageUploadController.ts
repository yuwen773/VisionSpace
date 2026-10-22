// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /agent/image/upload */
export async function uploadUsingPost(body: {}, options?: { [key: string]: any }) {
  return request<API.BaseResponseAgentImageVO>('/agent/image/upload', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/image/upload/delete */
export async function deleteUsingPost(body: Record<string, any>, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/agent/image/upload/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
