// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /agent/preference */
export async function getPreferenceUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponsePreferenceViewVO>('/agent/preference', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/preference/auto-learn */
export async function setAutoLearnUsingPost(
  body: API.PreferenceAutoLearnRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePreferenceViewVO>('/agent/preference/auto-learn', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/preference/clear */
export async function clearPreferenceUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponsePreferenceViewVO>('/agent/preference/clear', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/preference/update */
export async function updatePreferenceUsingPost(
  body: API.PreferenceUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePreferenceViewVO>('/agent/preference/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
