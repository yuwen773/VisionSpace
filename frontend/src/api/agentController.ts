// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * SSE 流式对话
 * GET /api/agent/image/chat/stream?message=xxx&threadId=xxx
 */
export async function chatStreamUsingGet(
  params: {
    message: string
    threadId: string
  },
  options?: { [key: string]: any }
) {
  return request('/api/agent/image/chat/stream', {
    method: 'GET',
    params,
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
