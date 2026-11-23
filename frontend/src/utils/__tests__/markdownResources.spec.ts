import { describe, expect, it } from 'vitest'
import { extractLinks, extractResources } from '@/utils/markdownResources'

describe('extractLinks 的来源信息', () => {
  it('补充摘要、域名、站点图标与序号', () => {
    const content =
      '这一篇讲的是检索增强。详见 [chat-demo](https://github.com/deepseek-ai/chat-demo) 与 [Vue 文档](https://cn.vuejs.org/guide/)。'

    const links = extractLinks(content)

    expect(links).toHaveLength(2)
    expect(links[0]).toMatchObject({
      title: 'chat-demo',
      domain: 'github.com',
      faviconUrl: 'https://github.com/favicon.ico',
      index: 1,
    })
    expect(links[0].snippet).toContain('检索增强')
    expect(links[0].snippet).not.toContain('chat-demo')
    expect(links[1].index).toBe(2)
  })

  it('段落里只有链接本身时摘要留空，避免与标题重复', () => {
    const links = extractLinks('[示例](https://example.com)')
    expect(links[0].snippet).toBe('')
  })

  it('同一地址只算一个来源', () => {
    const links = extractLinks('[一](https://example.com) [二](https://example.com)')
    expect(links).toHaveLength(1)
  })

  it('解析不出域名的地址不计为来源', () => {
    const links = extractLinks('[相对](not-a-url)')
    expect(links).toEqual([])
  })

  it('资源解析同时返回图片与链接', () => {
    const result = extractResources('![图](https://cdn.example.com/a.png) [页](https://example.com)')
    expect(result.images).toHaveLength(1)
    expect(result.links).toHaveLength(1)
  })
})
