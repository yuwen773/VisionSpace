import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useAgentStream } from '@/composables/useAgentStream'

// fetch-event-source 直接驱动 SSE，这里用受控替身把后端事件喂给 composable，
// 不发起任何真实请求。
const captured: { opts?: any; body?: any } = {}
let openResponse: { ok: boolean; headers: { get: () => string } } = {
  ok: true,
  headers: { get: () => 'text/event-stream' },
}

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(async (_url: string, opts: any) => {
    captured.opts = opts
    captured.body = JSON.parse(opts.body)
    await opts.onopen(openResponse)
    opts.onclose?.()
  }),
}))

vi.mock('@/apiOrigin', () => ({
  API_ROOT: '/api',
  CSRF_HEADER: 'X-CSRF-Token',
  readCsrfToken: () => 'token',
}))

const deleteAgentImage = vi.fn(async (_resourceId: string | number) => undefined)
vi.mock('@/api-custom/agent', () => ({
  deleteAgentImage: (resourceId: string | number) => deleteAgentImage(resourceId),
  attachmentContentUrl: (id: string) => `/api/agent/attachment/${id}/content`,
}))

const uploaded = [
  { resourceId: '1900000000000000001', url: 'https://acceptance.invalid/a.png' },
  { resourceId: '1900000000000000002', url: 'https://acceptance.invalid/b.png' },
]

describe('useAgentStream 的附件生命周期', () => {
  beforeEach(() => {
    deleteAgentImage.mockClear()
    captured.opts = undefined
    captured.body = undefined
    openResponse = { ok: true, headers: { get: () => 'text/event-stream' } }
  })

  it('绑定的是资源标识而不是地址', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('看看这张图', 'thread-1', uploaded)

    expect(captured.body.attachmentIds).toEqual(['1900000000000000001', '1900000000000000002'])
    expect(captured.body.imageUrls).toBeUndefined()
    expect(captured.body.threadId).toBe('thread-1')
  })

  it('流成功结束后不删除已上传的图片', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('看看这张图', 'thread-1', uploaded)

    expect(deleteAgentImage).not.toHaveBeenCalled()
  })

  it('服务端拒绝请求时丢弃尚未绑定的上传', async () => {
    openResponse = { ok: true, headers: { get: () => 'application/json' } }
    const stream = useAgentStream()
    await expect(stream.sendMessage('看看这张图', 'thread-1', uploaded)).rejects.toBeTruthy()

    expect(deleteAgentImage).toHaveBeenCalledTimes(2)
    expect(deleteAgentImage).toHaveBeenCalledWith('1900000000000000001')
    expect(deleteAgentImage).toHaveBeenCalledWith('1900000000000000002')
  })
})
