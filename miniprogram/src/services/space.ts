import { request } from './http'
import type { SpacePage, SpaceUserVO, WireLong } from './types'

/**
 * 空间相关接口。
 *
 * 没有 `/space/list/my`：可见空间走分页接口（非管理员 pageSize 上限 20），
 * 角色走 `/spaceUser/list/my`；个人空间没有 space_user 记录，角色为空。
 */
export interface MySpacesQuery {
  /** 0 = 个人空间，1 = 团队空间；省略表示不筛选。 */
  spaceType?: number
  current?: number
  pageSize?: number
}

export function fetchMySpaces(query: MySpacesQuery = {}): Promise<SpacePage> {
  const data: Record<string, unknown> = {
    current: query.current ?? 1,
    pageSize: query.pageSize ?? 20,
    sortField: 'createTime',
    sortOrder: 'descend',
  }
  if (query.spaceType !== undefined) {
    data.spaceType = query.spaceType
  }
  return request<SpacePage>({ path: '/space/list/page/vo', method: 'POST', data, auth: true })
}

/**
 * 是否已有某一类型的空间。只取一行，不受「第一页只有 20 条」影响；
 * 个人空间每类唯一，存在性判断只需要一条。
 */
export async function hasSpaceOfType(spaceType: number): Promise<boolean> {
  const page = await fetchMySpaces({ spaceType, pageSize: 1 })
  return (page.records ?? []).length > 0
}

export function fetchMyMemberships(): Promise<SpaceUserVO[]> {
  return request<SpaceUserVO[]>({ path: '/spaceUser/list/my', method: 'POST', auth: true })
}

/**
 * 创建空间。后端对「每个用户每类空间只能创建一个」用唯一索引兜底，
 * 重复创建返回 50001；调用方据此把并发/重复请求当成成功处理。
 */
export function createSpace(spaceName: string, spaceType: number): Promise<WireLong> {
  return request<WireLong>({
    path: '/space/add',
    method: 'POST',
    data: { spaceName, spaceType, spaceLevel: 0 },
    auth: true,
  })
}
