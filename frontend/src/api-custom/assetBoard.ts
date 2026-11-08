import request from '@/request'
import type { AssetResult } from './assetSearch'

export interface BoardGroup {
  groupId: string
  position: number
  name: string
}
export type BoardItem = AssetResult['items'][number] & {
  boardItemId: string
  selected?: boolean
  groupId?: string
  resultId?: string
}
export interface AssetBoard {
  schemaVersion: 1
  boardId: string
  spaceId: string
  title?: string
  revision: number
  groups: BoardGroup[]
  items: BoardItem[]
}
export interface BoardSave {
  operationId: string
  resultId?: string
  expectedRevision?: number
  title: string
  groups: BoardGroup[]
  items: {
    boardItemId?: string
    resultId?: string
    sourcePosition?: number
    sourceVersion: string
    position: number
    selected: boolean
    groupId?: string
  }[]
}
export interface BoardOperation {
  schemaVersion: 1
  operationId: string
  status: 'COMMITTED' | 'NOT_RECORDED'
  boardId?: string
  revision?: number
}
interface Response<T> {
  code: number
  data?: T
  message?: string
}
export const readBoard = (id: string) =>
  request<Response<AssetBoard>>(`/agent/boards/${encodeURIComponent(id)}`, { method: 'GET' })
export const readBoardOperation = (id: string) =>
  request<Response<BoardOperation>>(`/agent/board-operations/${encodeURIComponent(id)}`, {
    method: 'GET',
  })
export const saveBoard = (id: string | undefined, data: BoardSave) =>
  request<Response<BoardOperation>>(
    id ? `/agent/boards/${encodeURIComponent(id)}` : '/agent/boards',
    { method: id ? 'PUT' : 'POST', data },
  )
