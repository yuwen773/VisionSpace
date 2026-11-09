import request from '@/request'
import type { AssetBoard } from './assetBoard'

export interface BoardTask {
  schemaVersion: 1
  type: 'BOARD_SAVE_PROPOSAL'
  taskId: string
  proposalId: string
  proposalRevision: 1
  operationId: string
  status: 'AWAITING_CONFIRMATION' | 'COMMITTED' | 'CANCELLED' | 'CONFLICT' | 'REJECTED'
  reasonCode?: string
  boardId?: string
  spaceId?: string
  threadId?: string
  expectedRevision?: number
  revision?: number
  current?: AssetBoard
  target?: AssetBoard
  canConfirm: boolean
}
export interface BoardPlan {
  proposalRequestId: string
  threadId: string
  boardId: string
  expectedRevision: number
  message: string
}
interface Response<T> {
  code: number
  data?: T
  message?: string
}
export const planBoard = (data: BoardPlan) =>
  request<Response<BoardTask>>('/agent/board-proposals/plan', { method: 'POST', data })
export const readTask = (id: string) =>
  request<Response<BoardTask>>(`/agent/tasks/${encodeURIComponent(id)}`, { method: 'GET' })
export const decideProposal = (
  id: string,
  data: { operationId: string; proposalRevision: number; decision: 'CONFIRM' | 'CANCEL' },
) =>
  request<Response<BoardTask>>(`/agent/board-proposals/${encodeURIComponent(id)}/decision`, {
    method: 'POST',
    data,
  })
