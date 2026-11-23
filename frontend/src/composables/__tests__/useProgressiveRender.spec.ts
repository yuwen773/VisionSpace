import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useProgressiveRender } from '@/composables/useProgressiveRender'

describe('useProgressiveRender', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('流式期间把连续到达的 token 合并成一次渲染', async () => {
    const render = vi.fn((content: string) => `<p>${content}</p>`)
    const { html, update } = useProgressiveRender({ render })

    update('a', true)
    update('ab', true)
    update('abc', true)
    expect(render).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(20)

    expect(render).toHaveBeenCalledTimes(1)
    expect(render).toHaveBeenLastCalledWith('abc', true)
    expect(html.value).toBe('<p>abc</p>')
  })

  it('流结束时立即渲染终稿，并丢弃排队的中间帧', async () => {
    const render = vi.fn((content: string) => `<p>${content}</p>`)
    const { html, update } = useProgressiveRender({ render })

    update('a', true)
    update('abc', false)
    await vi.advanceTimersByTimeAsync(0)

    expect(render).toHaveBeenCalledTimes(1)
    expect(render).toHaveBeenLastCalledWith('abc', false)
    expect(html.value).toBe('<p>abc</p>')

    // 被取消的流式定时器不应再触发一次渲染
    await vi.advanceTimersByTimeAsync(1000)
    expect(render).toHaveBeenCalledTimes(1)
  })

  it('过期的异步渲染结果不会覆盖更新的内容', async () => {
    let resolveSlow: ((value: string) => void) | undefined
    const render = vi.fn((content: string) => {
      if (content === 'slow') {
        return new Promise<string>((resolve) => {
          resolveSlow = resolve
        })
      }
      return Promise.resolve(`<p>${content}</p>`)
    })
    const { html, update } = useProgressiveRender({ render })

    update('slow', false)
    await Promise.resolve()
    update('fast', false)
    resolveSlow?.('<p>slow</p>')
    await vi.runAllTimersAsync()
    await Promise.resolve()

    expect(html.value).toBe('<p>fast</p>')
  })
})
