import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AssistantMessage from '@/components/agent/message/AssistantMessage.vue'
import ReasoningMessage from '@/components/agent/message/ReasoningMessage.vue'

/**
 * 组件层验收：挂在真实 DOM 上的最终结果。
 * 图表正文由 v-html 注入，因此断言 DOM 里"实际有什么"。
 */
const settle = () => new Promise((r) => setTimeout(r, 50))

describe('AssistantMessage 图表展示', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('危险链接在最终 DOM 里没有可用的 href', async () => {
    const wrapper = mount(AssistantMessage, {
      props: { content: '[点我](javascript:alert(1))\n\n[站外](https://example.com/a)' },
    })
    await settle()

    const anchors = wrapper.findAll('.message-body a')
    const dangerous = anchors.find((a) => (a.attributes('href') ?? '').startsWith('javascript:'))
    expect(dangerous).toBeUndefined()

    const external = anchors.find((a) => a.attributes('href') === 'https://example.com/a')
    expect(external?.attributes('rel')).toBe('noopener noreferrer nofollow')
  })

  it('原始 HTML 不会变成元素', async () => {
    const wrapper = mount(AssistantMessage, {
      props: { content: '<img src=x onerror="alert(1)">' },
    })
    await settle()
    expect(wrapper.find('.message-body img').exists()).toBe(false)
    expect(wrapper.html()).toContain('&lt;img')
  })

  it('未配置 PlantUML 时显示源码，不产生外部图片请求', async () => {
    const wrapper = mount(AssistantMessage, {
      props: { content: '```plantuml\n@startuml\nAlice -> Bob\n@enduml\n```' },
    })
    await settle()

    const html = wrapper.html()
    expect(html).not.toContain('plantuml.com')
    expect(html).toContain('@startuml')
    expect(wrapper.find('.message-body img.plantuml-diagram').exists()).toBe(false)
  })

  it('图表框提供预览与代码两个面板', async () => {
    const wrapper = mount(AssistantMessage, {
      props: { content: '```mermaid\ngraph TD\n A-->B\n```' },
    })
    await settle()

    expect(wrapper.find('.diagram-wrapper').exists()).toBe(true)
    expect(wrapper.findAll('.diagram-tab')).toHaveLength(2)
    expect(wrapper.find('.diagram-code').text()).toContain('A-->B')
  })

  it('内容更新时重新渲染，不叠加旧的图表框', async () => {
    const wrapper = mount(AssistantMessage, {
      props: { content: '```mermaid\ngraph TD\n A-->B\n```' },
    })
    await settle()
    await wrapper.setProps({ content: '```mermaid\ngraph TD\n C-->D\n```' })
    await settle()

    const wrappers = wrapper.findAll('.diagram-wrapper')
    expect(wrappers).toHaveLength(1)
    expect(wrappers[0].find('.diagram-code').text()).toContain('C-->D')
  })

  it('卸载后再更新内容不会抛错（在途渲染被丢弃）', async () => {
    const unhandled: unknown[] = []
    const onRejection = (e: unknown) => unhandled.push(e)
    process.on('unhandledRejection', onRejection)

    const wrapper = mount(AssistantMessage, {
      props: { content: '```mermaid\ngraph TD\n A-->B\n```' },
    })
    wrapper.unmount()
    // 卸载后事件循环里若仍有 pending 渲染，不能抛错、不能写回已销毁的状态
    await settle()

    process.off('unhandledRejection', onRejection)
    expect(unhandled).toEqual([])
  })
})

describe('ReasoningMessage 复用同一套安全渲染', () => {
  it('思考过程里的危险链接同样被降级', async () => {
    const wrapper = mount(ReasoningMessage, {
      props: { content: '[点我](javascript:alert(1))' },
      global: { stubs: {} },
    })
    // 折叠状态下内容用 v-show 隐藏，展开后才在页面上可见
    await wrapper.find('.reasoning-header').trigger('click')
    await settle()

    // 危险协议要么不生成链接，要么生成没有 href 的链接——两者都可接受
    const anchors = wrapper.findAll('.markdown-body a')
    for (const a of anchors) {
      expect(a.attributes('href') ?? '').not.toContain('javascript:')
    }
    expect(wrapper.find('.reasoning-content .markdown-body').text()).toContain('点我')
  })
})
