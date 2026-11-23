import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AssistantMessage from '@/components/agent/message/AssistantMessage.vue'

const settle = async () => {
  await Promise.resolve()
  await Promise.resolve()
}

describe('AssistantMessage 的来源链', () => {
  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('资源按钮同时统计图片和链接', async () => {
    const wrapper = mount(AssistantMessage, {
      props: {
        content: '![图](https://cdn.example.com/a.png) [参考](https://example.com/doc)',
      },
    })
    await settle()

    const text = wrapper.find('.resource-trigger-btn').text()
    expect(text).toContain('1 张图片')
    expect(text).toContain('1 个链接')
    wrapper.unmount()
  })

  it('悬停正文链接后显示来源卡，移开即关闭', async () => {
    vi.useFakeTimers()
    const wrapper = mount(AssistantMessage, {
      props: { content: '这是一段说明。详见 [参考文档](https://example.com/doc)。' },
    })
    await vi.advanceTimersByTimeAsync(0)

    const anchor = wrapper.find('.message-body a')
    expect(anchor.exists()).toBe(true)

    await anchor.trigger('mouseover')
    await vi.advanceTimersByTimeAsync(310)

    const preview = document.body.querySelector('.source-preview')
    expect(preview).toBeTruthy()
    expect(preview?.textContent).toContain('example.com')

    await anchor.trigger('mouseout', { relatedTarget: document.body })
    await vi.advanceTimersByTimeAsync(220)
    expect(document.body.querySelector('.source-preview')).toBeFalsy()

    wrapper.unmount()
  })

  it('不是来源链接的锚点不触发预览', async () => {
    vi.useFakeTimers()
    const wrapper = mount(AssistantMessage, {
      props: { content: '<https://example.com/bare>' },
    })
    await vi.advanceTimersByTimeAsync(0)

    const anchor = wrapper.find('.message-body a')
    expect(anchor.exists()).toBe(true)

    await anchor.trigger('mouseover')
    await vi.advanceTimersByTimeAsync(310)

    expect(document.body.querySelector('.source-preview')).toBeFalsy()
    wrapper.unmount()
  })
})
