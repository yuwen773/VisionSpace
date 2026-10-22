// 手写 API 客户端：多平台存储配置管理。
//
// 生成版 storageConfigController.ts 依赖 API.StorageConfigVO 等类型，但这里的
// 类型定义更宽松（字段可选性不同），且后台管理页按这里的名字引用。保留手写版，
// 避免生成器改动波及该页面。
import request from '@/request'

export interface StorageConfigVO {
  id?: number
  platform: string
  platformName: string
  endpoint?: string
  region?: string
  bucket: string
  // 响应只返回遮蔽值与否，不含真实密钥
  accessKeyMasked?: string
  secretKeyMasked?: string
  hasAccessKey?: boolean
  hasSecretKey?: boolean
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

export interface StorageConfigUpdateRequest extends Partial<StorageConfigAddRequest> {
  id: number
  // 留空或传 ****** 表示保留原密钥，只有其他值才会被替换
  accessKey?: string
  secretKey?: string
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
    params: { ...params },
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
