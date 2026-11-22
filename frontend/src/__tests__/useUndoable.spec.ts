import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const notifications: any[] = []

vi.mock('ant-design-vue', () => ({
  notification: {
    info: (options: any) => {
      notifications.push(options)
      return {}
    },
    close: vi.fn(),
  },
}))

import { useUndoable } from '@/composables/useUndoable'

describe('useUndoable', () => {
  beforeEach(() => {
    notifications.length = 0
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('撤销窗口结束后才真正提交', async () => {
    const commit = vi.fn().mockResolvedValue(undefined)
    const { schedule } = useUndoable()

    schedule({ key: 'space-1', message: '已删除', commit })
    expect(commit).not.toHaveBeenCalled()

    vi.advanceTimersByTime(4999)
    expect(commit).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await Promise.resolve()
    expect(commit).toHaveBeenCalledTimes(1)
  })

  it('点击撤销后不再提交，并调用本地恢复', async () => {
    const commit = vi.fn().mockResolvedValue(undefined)
    const undo = vi.fn()
    const { schedule } = useUndoable()

    schedule({ key: 'space-2', message: '已禁用', commit, undo })

    const options = notifications.at(-1)
    const button = options.btn()
    button.props.onClick()

    expect(undo).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(10000)
    await Promise.resolve()
    expect(commit).not.toHaveBeenCalled()
  })

  it('同一记录再次操作时，先提交上一笔再开新窗口', () => {
    const first = vi.fn().mockResolvedValue(undefined)
    const second = vi.fn().mockResolvedValue(undefined)
    const { schedule } = useUndoable()

    schedule({ key: 'space-3', message: '第一次', commit: first })
    schedule({ key: 'space-3', message: '第二次', commit: second })

    expect(first).toHaveBeenCalledTimes(1)
    expect(second).not.toHaveBeenCalled()

    vi.advanceTimersByTime(5000)
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('flush 立即提交所有未决操作', async () => {
    const first = vi.fn().mockResolvedValue(undefined)
    const second = vi.fn().mockResolvedValue(undefined)
    const { schedule, flush } = useUndoable()

    schedule({ key: 'space-4', message: 'A', commit: first })
    schedule({ key: 'space-5', message: 'B', commit: second })

    flush()
    await Promise.resolve()

    expect(first).toHaveBeenCalledTimes(1)
    expect(second).toHaveBeenCalledTimes(1)
  })
})
