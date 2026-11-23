import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useAgentStream } from '@/composables/useAgentStream'

// fetch-event-source 直接驱动 SSE，这里用受控替身把后端事件喂给 composable，
// 不发起任何真实请求。
const captured: { opts?: any; body?: any } = {}
let openResponse: { ok: boolean; headers: { get: () => string } } = {
  ok: true,
  headers: { get: () => 'text/event-stream' },
}
// 真实 SSE 会一直开到后端结束；默认立即收尾，需要模拟"仍在生成"时打开 holdOpen
let holdOpen = false
let releaseStream: (() => void) | null = null

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(async (_url: string, opts: any) => {
    captured.opts = opts
    captured.body = JSON.parse(opts.body)
    await opts.onopen(openResponse)
    if (holdOpen) {
      await new Promise<void>((resolve) => {
        releaseStream = resolve
      })
    } else {
      opts.onclose?.()
    }
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
  it('deduplicates proposal notices and never executes writes for repeated or older board events', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('hello', 'thread-events')
    const notice = {
      schemaVersion: 1,
      eventId: 'proposal:p1',
      type: 'CONFIRMATION_REQUIRED',
      payload: {
        taskId: 't1',
        proposalId: 'p1',
        proposalRevision: 1,
        boardId: 'b1',
        expectedRevision: 1,
      },
    }
    for (let i = 0; i < 2; i++) captured.opts.onmessage({ data: JSON.stringify(notice) })
    expect(stream.messages.value.filter((m) => m.taskId === 't1')).toHaveLength(1)
    for (const revision of [3, 2, 3])
      captured.opts.onmessage({
        data: JSON.stringify({
          schemaVersion: 1,
          eventId: `board:${revision}`,
          type: 'BOARD_UPDATED',
          payload: { boardId: 'b1', revision },
        }),
      })
    expect(stream.messages.value.filter((m) => m.type === 'board-updated')).toHaveLength(1)
    expect(stream.messages.value.find((m) => m.type === 'board-updated')?.revision).toBe(3)
    expect(stream.protocolError.value).toBe('')
  })
  it('normalizes tool-confirm and legacy mcp-confirm into one read-only confirmation', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('hello', 'thread-confirm')
    for (const messageType of ['tool-confirm', 'mcp-confirm']) {
      captured.opts.onmessage({ data: JSON.stringify({ messageType, content: '同一确认' }) })
    }
    expect(stream.messages.value.filter((m) => m.type === 'tool-confirm')).toHaveLength(1)
  })
  it('显式协商只读找图协议，重复结果只显示一次且不抽取 Markdown', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('横图', 'thread-asset', [], '123')
    expect(captured.body.assetSpaceId).toBe('123')
    expect(captured.opts.headers['X-VS-AI-Event-Version']).toBe('1')
    const event = {
      schemaVersion: 1,
      eventId: 'search:snapshot-1',
      type: 'SEARCH_RESULTS_READY',
      payload: { resultId: 'snapshot-1' },
      content: '![unsafe](https://untrusted.invalid/image.jpg)',
    }
    captured.opts.onmessage({ data: JSON.stringify(event) })
    captured.opts.onmessage({ data: JSON.stringify(event) })
    expect(stream.messages.value.filter((m) => m.type === 'asset-result')).toHaveLength(1)
    expect(stream.messages.value[0].resultId).toBe('snapshot-1')
    expect(stream.images.value).toEqual([])
    captured.opts.onmessage({ data: JSON.stringify({ ...event, schemaVersion: 2 }) })
    expect(stream.protocolError.value).toContain('刷新')
    expect(stream.messages.value).toHaveLength(1)
  })
  beforeEach(() => {
    deleteAgentImage.mockClear()
    captured.opts = undefined
    captured.body = undefined
    openResponse = { ok: true, headers: { get: () => 'text/event-stream' } }
    holdOpen = false
    releaseStream = null
  })

  /** 让 mock 里的 onopen 与挂起的流跑起来。 */
  const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

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

  it('发送时不清空已经放入的用户消息', async () => {
    const stream = useAgentStream()
    stream.pushMessage({ type: 'user', content: '帮我找一张星空', node: 'user' })

    await stream.sendMessage('帮我找一张星空', 'thread-keep-user')

    expect(stream.messages.value[0]).toMatchObject({ type: 'user', content: '帮我找一张星空' })
  })

  it('服务端错误事件会落成一条错误消息，而不是无声断流', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('hi', 'thread-error')

    captured.opts.onmessage({
      data: JSON.stringify({ messageType: 'error', content: '系统繁忙，请稍后重试' }),
    })

    expect(stream.messages.value).toHaveLength(1)
    expect(stream.messages.value[0]).toMatchObject({
      type: 'error',
      content: '系统繁忙，请稍后重试',
    })
  })

  it('切换会话（reset）后旧流的回调不再写入', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('hello', 'thread-old')
    const stale = captured.opts

    stream.reset()
    stale.onmessage({ data: JSON.stringify({ messageType: 'assistant', content: '旧会话的输出' }) })

    expect(stream.messages.value).toHaveLength(0)
  })

  it('取消后旧流既不再写入，也不会关掉新流的 loading', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('hello', 'thread-abort')
    const stale = captured.opts

    stream.abort()
    expect(stream.isStreaming.value).toBe(false)
    stale.onmessage({ data: JSON.stringify({ messageType: 'assistant', content: '迟到内容' }) })
    expect(stream.messages.value).toHaveLength(0)
  })

  it('重新发送后，上一段流的回调失效', async () => {
    const stream = useAgentStream()
    await stream.sendMessage('first', 'thread-a')
    const first = captured.opts

    await stream.sendMessage('second', 'thread-b')
    first.onmessage({ data: JSON.stringify({ messageType: 'assistant', content: '旧输出' }) })

    expect(stream.messages.value).toHaveLength(0)
  })

  it('停止生成后，已经显示的气泡不再保持流式状态', async () => {
    holdOpen = true
    const stream = useAgentStream()
    const pending = stream.sendMessage('hello', 'thread-stop-bubble')
    await flush()

    captured.opts.onmessage({
      data: JSON.stringify({ messageType: 'assistant', content: '半截回复' }),
    })
    expect(stream.messages.value.at(-1)).toMatchObject({ type: 'assistant', isLoading: true })

    stream.abort()
    expect(stream.messages.value.at(-1)).toMatchObject({ type: 'assistant', isLoading: false })

    releaseStream?.()
    await pending
  })

  it('服务端错误帧会结束当前气泡的流式状态并保留已生成内容', async () => {
    holdOpen = true
    const stream = useAgentStream()
    const pending = stream.sendMessage('hello', 'thread-error-bubble')
    await flush()

    captured.opts.onmessage({
      data: JSON.stringify({ messageType: 'assistant', content: '半截回复' }),
    })
    captured.opts.onmessage({
      data: JSON.stringify({ messageType: 'error', content: '系统繁忙，请稍后重试' }),
    })

    expect(stream.messages.value[0]).toMatchObject({
      type: 'assistant',
      content: '半截回复',
      isLoading: false,
    })
    expect(stream.messages.value[1]).toMatchObject({ type: 'error' })

    releaseStream?.()
    await pending
  })

  it('正文开始输出后，思考块不再显示流式状态', async () => {
    holdOpen = true
    const stream = useAgentStream()
    const pending = stream.sendMessage('hello', 'thread-reasoning')
    await flush()

    captured.opts.onmessage({
      data: JSON.stringify({ messageType: 'reasoning', content: '思考中' }),
    })
    expect(stream.messages.value[0]).toMatchObject({ type: 'reasoning', isLoading: true })

    captured.opts.onmessage({ data: JSON.stringify({ messageType: 'assistant', content: '答案' }) })
    expect(stream.messages.value[0]).toMatchObject({ type: 'reasoning', isLoading: false })

    releaseStream?.()
    await pending
  })
})
