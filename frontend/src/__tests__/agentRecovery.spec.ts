import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import RecoveryBanner from '@/components/agent/RecoveryBanner.vue'
import type { RecoveryState } from '@/api-custom/agent'

// fetch-event-source 直接驱动 SSE，这里用受控替身把后端事件喂给 composable，
// 不发起任何真实请求。
const handlers: Record<string, any> = {}
vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(async (_url: string, opts: any) => {
    handlers.opts = opts
    await opts.onopen({ ok: true, headers: { get: () => 'text/event-stream' } })
    handlers.opts = opts
  }),
}))

vi.mock('@/apiOrigin', () => ({
  API_ROOT: '/api',
  CSRF_HEADER: 'X-CSRF-Token',
  readCsrfToken: () => 'token',
}))

vi.mock('@/api-custom/agent', () => ({
  uploadAgentImage: vi.fn(),
  deleteAgentImage: vi.fn(async () => ({ data: { code: 0 } })),
}))

const state = (overrides: Partial<RecoveryState> = {}): RecoveryState =>
  ({
    threadId: 'thread-1',
    runStatus: 'PAUSED_UNRECOVERABLE',
    resumable: false,
    reason: '存在结果未知的外部动作，需要先核对',
    stateVersion: 7,
    legacyArchiveOnly: false,
    pendingReviewCount: 1,
    actions: [
      {
        actionId: 'a-1',
        toolName: 'generateImages',
        status: 'UNKNOWN_NEEDS_REVIEW',
        attempt: 1,
        resultSummary: '执行异常: SocketTimeoutException',
      },
    ],
    ...overrides,
  }) as RecoveryState

describe('RecoveryBanner', () => {
  it('separates unknown results from explicit failures and offers review, not retry', async () => {
    const wrapper = mount(RecoveryBanner, { props: { state: state() } })

    expect(wrapper.text()).toContain('有外部动作结果未知，需要核对')
    expect(wrapper.text()).toContain('generateImages')
    expect(wrapper.text()).toContain('结果未知')
    // 待核对时不能一键重试：外部动作可能已经发生
    expect(wrapper.text()).not.toContain('继续执行')

    const links = wrapper.findAll('.link-btn')
    expect(links.map(l => l.text())).toEqual(['已执行', '未执行'])
    await links[0].trigger('click')
    await links[1].trigger('click')
    expect(wrapper.emitted('review')).toEqual([
      ['a-1', 'confirm'],
      ['a-1', 'dismiss'],
    ])
  })

  it('stays out of the way while a run is still healthy', () => {
    const wrapper = mount(RecoveryBanner, {
      props: {
        state: state({ resumable: true, pendingReviewCount: 0, actions: [], runStatus: 'RUNNING' }),
      },
    })

    // 可续跑且没有待核对：正常推进中，不显示提示
    expect(wrapper.find('.recovery-banner').exists()).toBe(false)
  })

  it('never offers resume for a completed run', () => {
    const wrapper = mount(RecoveryBanner, {
      props: { state: state({ resumable: false, pendingReviewCount: 0, actions: [], runStatus: 'COMPLETED' }) },
    })

    expect(wrapper.find('.recovery-banner').exists()).toBe(false)
  })

  it('shows the stale-version guard by passing the current version on resume', async () => {
    const wrapper = mount(RecoveryBanner, {
      props: { state: state({ resumable: true, pendingReviewCount: 1, actions: [] }) },
    })

    const resume = wrapper.find('.primary-btn')
    expect(resume.exists()).toBe(true)
    await resume.trigger('click')
    expect(wrapper.emitted('resume')?.[0]).toEqual([7])
  })

  it('shows the awaiting-confirmation state even though it is resumable', () => {
    // 待确认状态下 resumable 为 true，但用户必须看到「停在哪一步」
    const wrapper = mount(RecoveryBanner, {
      props: {
        state: state({
          resumable: true,
          pendingReviewCount: 0,
          actions: [],
          runStatus: 'AWAITING_CONFIRMATION',
          pendingConfirmation: 'generateImages',
          reason: null,
        }),
      },
    })

    expect(wrapper.find('.recovery-banner').exists()).toBe(true)
    expect(wrapper.text()).toContain('等待你确认下一步')
    expect(wrapper.find('.banner-confirm').text()).toContain('generateImages')
  })

  it('explains that a legacy archive cannot be resumed', () => {
    const wrapper = mount(RecoveryBanner, {
      props: {
        state: state({
          resumable: false,
          pendingReviewCount: 0,
          actions: [],
          legacyArchiveOnly: true,
          reason: '只有旧消息归档，不能证明可以安全续跑',
        }),
      },
    })

    expect(wrapper.text()).toContain('只有历史消息，无法继续执行')
    expect(wrapper.find('.primary-btn').exists()).toBe(false)
  })

  it('surfaces backup failures without hiding them', () => {
    const wrapper = mount(RecoveryBanner, {
      props: {
        state: state({
          pendingReviewCount: 0,
          actions: [],
          backupError: 'IllegalStateException',
          backupAttempts: 3,
        }),
      },
    })

    expect(wrapper.find('.banner-backup').text()).toContain('IllegalStateException')
    expect(wrapper.find('.banner-backup').text()).toContain('3')
  })
})

describe('useAgentStream action-unknown', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('renders an unknown-result message instead of treating it as a retryable error', async () => {
    const { useAgentStream } = await import('@/composables/useAgentStream')
    const stream = useAgentStream()

    const pending = stream.sendMessage('生成图片', 'thread-1')
    await nextTick()

    handlers.opts.onmessage({
      data: JSON.stringify({ messageType: 'action-unknown', content: '结果未知', toolName: 'generateImages' }),
    })
    await pending

    const unknown = stream.messages.value.find(m => m.type === 'action-unknown')
    expect(unknown).toBeDefined()
    expect(unknown?.toolName).toBe('generateImages')
    expect(unknown?.content).toBe('结果未知')
    // 与普通助手消息分开：不会被合并进上一条流式回答
    expect(stream.messages.value.filter(m => m.type === 'assistant')).toHaveLength(0)
    expect(stream.isStreaming.value).toBe(false)
  })

  it('treats a JSON authorization error as a failed send, not an empty stream', async () => {
    const { useAgentStream, AgentStreamError } = await import('@/composables/useAgentStream')
    const stream = useAgentStream()

    const pending = stream.sendMessage('hi', 'someone-elses-thread')
    await nextTick()

    await expect(
      handlers.opts.onopen({
        ok: true,
        headers: { get: () => 'application/json' },
        json: async () => ({ code: 40101, message: '无权访问该会话' }),
      }),
    ).rejects.toBeInstanceOf(AgentStreamError)
    await pending.catch(() => {})
    expect(stream.messages.value).toHaveLength(0)
  })
})
