// @ts-ignore
/* eslint-disable */
import request from '@/request'

export interface StorageConfigVO {
  id?: number
  platform: string
  platformName: string
  endpoint?: string
  region?: string
  bucket: string
  accessKey?: string
  secretKey?: string
  domain?: string
  basePath?: string
  isActive?: number
  isDefault?: number
  orderNum?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface StorageConfigAddRequest {
  platform: string
  platformName: string
  endpoint?: string
  region?: string
  bucket: string
  accessKey: string
  secretKey: string
  domain?: string
  basePath?: string
  isActive?: number
  isDefault?: number
  orderNum?: number
  status?: number
}

export interface StorageConfigUpdateRequest extends StorageConfigAddRequest {
  id: number
}

export interface StorageConfigQueryRequest {
  id?: number
  platform?: string
  status?: number
}

export interface StorageConfigActiveRequest {
  id: number
}

export function getStorageConfigListUsingGet(
  params: StorageConfigQueryRequest,
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

export function addStorageConfigUsingPost(
  body: StorageConfigAddRequest,
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export function updateStorageConfigUsingPost(
  body: StorageConfigUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export function deleteStorageConfigUsingPost(
  body: { id: number },
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export function setActivePlatformUsingPost(
  body: StorageConfigActiveRequest,
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/set_active', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export function setDefaultPlatformUsingPost(
  body: StorageConfigActiveRequest,
  options?: { [key: string]: any }
) {
  return request<any>('/api/storage/config/set_default', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export function refreshStorageConfigUsingPost(options?: { [key: string]: any }) {
  return request<any>('/api/storage/config/refresh', {
    method: 'POST',
    ...(options || {}),
  })
}
