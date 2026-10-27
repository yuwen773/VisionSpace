// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /picture/action/report */
export async function reportActionUsingPost(
  body: API.PictureActionRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/picture/action/report', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
