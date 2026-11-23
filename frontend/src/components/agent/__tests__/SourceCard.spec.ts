import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SourceCard from '@/components/agent/SourceCard.vue'
import AgentResourcePanel from '@/components/agent/AgentResourcePanel.vue'
import type { ImageResource, LinkResource } from '@/components/agent/types'

const link: LinkResource = {
  url: 'https://github.com/deepseek-ai/chat-demo',
  title: 'chat-demo',
  snippet: '官方开源的静态聊天示例',
  domain: 'github.com',
  faviconUrl: 'https://github.com/favicon.ico',
  index: 3,
}

describe('SourceCard', () => {
  it('展示标题、摘要、序号与域名', () => {
    const wrapper = mount(SourceCard, { props: { link } })

    expect(wrapper.find('.source-title').text()).toBe('chat-demo')
    expect(wrapper.find('.source-snippet').text()).toContain('静态聊天')
    expect(wrapper.find('.source-index').text()).toBe('3')
    expect(wrapper.find('.source-domain').text()).toBe('github.com')
    expect(wrapper.attributes('href')).toBe(link.url)
    expect(wrapper.attributes('rel')).toContain('noopener')
  })

  it('站点图标加载失败时回退到域名首字母', async () => {
    const wrapper = mount(SourceCard, { props: { link } })
    expect(wrapper.find('.source-favicon img').exists()).toBe(true)

    await wrapper.find('.source-favicon img').trigger('error')

    expect(wrapper.find('.source-favicon img').exists()).toBe(false)
    expect(wrapper.find('.source-initial').text()).toBe('G')
  })

  it('紧凑模式不显示摘要', () => {
    const wrapper = mount(SourceCard, { props: { link, compact: true } })
    expect(wrapper.find('.source-snippet').exists()).toBe(false)
  })
})

describe('AgentResourcePanel 的链接页', () => {
  const mountPanel = (images: ImageResource[], links: LinkResource[]) =>
    mount(AgentResourcePanel, {
      props: { images, links },
      global: { stubs: { AssetBoardEditor: true, AssetResultCard: true } },
    })

  it('只有链接时默认落在链接页并渲染来源卡', () => {
    const wrapper = mountPanel([], [link])

    expect(wrapper.find('.tab-btn.active').text()).toContain('链接')
    expect(wrapper.find('.source-card').exists()).toBe(true)
    expect(wrapper.find('.link-list').text()).toContain('chat-demo')
  })
})
