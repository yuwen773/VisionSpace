// 手写 API 客户端：管理后台在生成契约之外新增的读/写接口。
//
// 生成版 typings 与控制器函数只在 `npm run openapi2ts` 之后才会带上这些字段，
// 后台页面不能为此卡住。等契约重新导出后，这些函数可以逐步换回生成版。
import request from '@/request'

export interface AdminResponse<T> {
  code?: number
  data?: T
  message?: string
}

export interface FeedbackStatsVO {
  pending: number
  processing: number
  resolved: number
  total: number
}

export interface AdminSpaceOwnerVO {
  id?: number
  userName?: string
  userAccount?: string
  userAvatar?: string
}

export interface AdminSpaceVO {
  id: number
  spaceName?: string
  spaceLevel?: number
  spaceType?: number
  maxCount?: number
  maxSize?: number
  totalCount?: number
  totalSize?: number
  status?: number
  userId?: number
  createTime?: string
  editTime?: string
  user?: AdminSpaceOwnerVO
}

export interface AdminSpaceQuery {
  current?: number
  pageSize?: number
  sortField?: string
  sortOrder?: string
  spaceName?: string
  spaceLevel?: number
  spaceType?: number
  status?: number
  /** 匹配所有者姓名或账号 */
  userKeyword?: string
}

export interface AdminSpaceUpdateRequest {
  id: number
  spaceName?: string
  spaceLevel?: number
  maxCount?: number
  maxSize?: number
  status?: number
}

export interface AdminSpaceAddRequest {
  spaceName: string
  userId: number
  spaceLevel?: number
  spaceType?: number
  maxCount?: number
  maxSize?: number
}

export interface AdminPage<T> {
  records?: T[]
  total?: number
  current?: number
  pageSize?: number
}

/** 全量状态聚合，不受分页与当前页影响 */
export async function getFeedbackStats(params: { type?: number } = {}) {
  return request<AdminResponse<FeedbackStatsVO>>('/feedback/stats', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: { type: params.type },
  })
}

/** 管理端空间列表：带所有者信息与真实状态 */
export async function listAdminSpaces(query: AdminSpaceQuery) {
  return request<AdminResponse<AdminPage<AdminSpaceVO>>>('/space/list/page/vo', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: query,
  })
}

/** 管理员替指定用户创建空间 */
export async function adminAddSpace(body: AdminSpaceAddRequest) {
  return request<AdminResponse<number>>('/space/admin/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  })
}

/** 更新空间，含启用 / 禁用状态 */
export async function updateAdminSpace(body: AdminSpaceUpdateRequest) {
  return request<AdminResponse<boolean>>('/space/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  })
}

/** 按姓名或账号搜索用户，供所有者选择器使用 */
export async function searchAdminUsers(keyword: string) {
  return request<AdminResponse<AdminSpaceOwnerVO[]>>('/user/search', {
    method: 'GET',
    params: { keyword },
  })
}
