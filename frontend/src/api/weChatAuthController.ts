// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /user/wx/bind */
export async function bindUsingPost(body: API.WxBindRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseWxLoginVO>('/user/wx/bind', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/wx/login */
export async function loginUsingPost(body: API.WxLoginRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseWxLoginVO>('/user/wx/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
