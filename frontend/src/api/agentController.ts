// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * SSE 流式对话
 * POST /api/agent/image/chat/stream
 */
export async function chatStreamUsingPost(
  body: {
    message: string
    threadId?: string
    files?: File[]
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/chat/stream', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}

/**
 * 普通对话（非流式）
 * POST /api/agent/image/chat
 */
export async function chatUsingPost(
  body: {
    message: string
    threadId?: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/**
 * 发送反馈
 * POST /api/agent/image/feedback
 */
export async function feedbackUsingPost(
  body: {
    threadId: string
    userId?: string
    satisfied: boolean
    reason?: string
    action?: string
    currentPhase?: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/feedback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

// ============ 图片上传相关 ============

const BASE_URL = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'

/**
 * 上传 Agent 聊天图片，返回公开 URL
 * POST /api/agent/image/upload
 */
export async function uploadAgentImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE_URL}/api/agent/image/upload`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
  })
  const json = await res.json()
  if (json.code !== 0) {
    throw new Error(json.message || '图片上传失败')
  }
  return json.data as string
}

/**
 * 删除已上传的 Agent 聊天图片
 * POST /api/agent/image/upload/delete
 */
export async function deleteAgentImage(url: string): Promise<void> {
  await fetch(`${BASE_URL}/api/agent/image/upload/delete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
    credentials: 'include',
  })
}

// ============ 历史相关 ============

export interface HistoryMessage {
  id: number
  role: string
  subType: string | null
  content: string
  isSummary: boolean
  createdTime: string
}

export interface ChatHistoryResponse {
  messages: HistoryMessage[]
  hasMore: boolean
  nextCursor: number | null
}

export interface SessionItem {
  sessionId: string
  title: string
  lastMessage: string
  messageCount: number
  summaryContent: string | null
  createdTime: string
  updatedTime: string
}

export interface SessionListResponse {
  data: SessionItem[]
}

export interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

/**
 * 获取会话下的聊天历史（分页）
 * GET /api/agent/history/{threadId}
 */
export async function getHistory(
  threadId: string,
  params?: { beforeId?: number; limit?: number }
) {
  // Response: { code: number, data: ChatHistoryResponse, message: string }
  return request<ApiResponse<ChatHistoryResponse>>(`/api/agent/history/${threadId}`, {
    method: 'GET',
    params,
  })
}

/**
 * 获取用户的会话列表（分页）
 * GET /api/agent/sessions
 */
export async function getSessions(
  params?: { beforeId?: number; limit?: number }
) {
  // Response: { code: number, data: SessionListResponse, message: string }
  return request<ApiResponse<SessionListResponse>>(`/api/agent/sessions`, {
    method: 'GET',
    params,
  })
}
