import { request } from './http'
import type { SpacePage, SpaceVO, SpaceUserVO, WireLong } from './types'

/**
 * 空间相关接口。
 *
 * 没有 `/space/list/my`：可见空间走分页接口（非管理员 pageSize 上限 20），
 * 角色走 `/spaceUser/list/my`；个人空间没有 space_user 记录，角色为空。
 */
export function fetchMySpaces(): Promise<SpacePage> {
  return request<SpacePage>({
    path: '/space/list/page/vo',
    method: 'POST',
    data: { current: 1, pageSize: 20, sortField: 'createTime', sortOrder: 'descend' },
    auth: true,
  })
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

/** 单个空间详情：`permissionList` 在这里才会填充。 */
export function fetchSpaceDetail(id: number): Promise<SpaceVO> {
  return request<SpaceVO>({ path: '/space/get/vo', data: { id }, auth: true })
}
