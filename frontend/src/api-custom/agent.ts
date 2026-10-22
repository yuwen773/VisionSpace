// 手写 API 客户端：生成器无法覆盖的部分。
//
// 这个目录不在 src/api/ 下——生成器每次运行都会 rimraf 整个 src/api/，
// 所以手写文件必须放在它的作用范围之外。
import request, { API_BASE_URL } from '@/request'

const BASE_URL = API_BASE_URL

// ============ 图片上传 ============
// 走原生 fetch 而非 axios：上传需要 FormData，且要读原始 JSON 拿 data 字段。

/**
 * 上传结果：资源标识用于后续删除，url 用于对话展示。
 */
export interface AgentImage {
  resourceId: number
  url: string
  size?: number
}

/**
 * 上传 Agent 聊天图片，返回资源标识与地址
 * POST /api/agent/image/upload
 */
export async function uploadAgentImage(file: File): Promise<AgentImage> {
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
  return json.data as AgentImage
}

/**
 * 按资源标识删除已上传的 Agent 聊天图片。
 * 不提交 URL：删除只依据服务端归属记录，重复删除按成功处理。
 * POST /api/agent/image/upload/delete
 */
export async function deleteAgentImage(resourceId: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/api/agent/image/upload/delete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resourceId }),
    credentials: 'include',
  })
  const json = await res.json().catch(() => null)
  if (json && json.code !== 0 && json.code !== 40101) {
    throw new Error(json.message || '图片删除失败')
  }
}

// ============ 聊天历史 ============

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
  return request<ApiResponse<ChatHistoryResponse>>(`/api/agent/history/${threadId}`, {
    method: 'GET',
    params,
  })
}

/**
 * 获取用户的会话列表（分页）
 * GET /api/agent/sessions
 */
export async function getSessions(params?: { beforeId?: number; limit?: number }) {
  return request<ApiResponse<SessionItem[]>>(`/api/agent/sessions`, {
    method: 'GET',
    params,
  })
}
