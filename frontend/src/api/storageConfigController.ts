// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /storage/config/add */
export async function addStorageConfigUsingPost(
  body: API.StorageConfigAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong>('/storage/config/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /storage/config/delete */
export async function deleteStorageConfigUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/storage/config/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /storage/config/list */
export async function listStorageConfigUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listStorageConfigUsingGetParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListStorageConfigVO>('/storage/config/list', {
    method: 'GET',
    params: {
      ...params,
      queryRequest: undefined,
      ...params['queryRequest'],
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /storage/config/refresh */
export async function refreshStorageConfigUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/storage/config/refresh', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /storage/config/set_active */
export async function setActivePlatformUsingPost(
  body: API.StorageConfigActiveRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/storage/config/set_active', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /storage/config/set_default */
export async function setDefaultPlatformUsingPost(
  body: API.StorageConfigActiveRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/storage/config/set_default', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /storage/config/update */
export async function updateStorageConfigUsingPost(
  body: API.StorageConfigUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/storage/config/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
