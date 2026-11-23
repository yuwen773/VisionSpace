import { describe, expect, it, vi } from 'vitest'
import { useDiagramRenderer } from '@/composables/useDiagramRenderer'

const mermaidBlock = (code: string) =>
  `<pre class="hljs code-block diagram-block" data-lang="mermaid"><div class="code-header"><span class="code-lang">mermaid</span><button class="copy-btn" data-code>复制</button></div><code>${code}</code></pre>`

const mermaidLoader = async () => ({
  initialize: vi.fn(() => undefined),
  render: vi.fn(async () => ({ svg: '<svg id="mermaid-output"></svg>' })),
})

describe('useDiagramRenderer 的流式治理', () => {
  it('流式阶段不渲染图表，只保留源码块', async () => {
    const load = vi.fn(mermaidLoader)
    const { renderDiagrams } = useDiagramRenderer({ mermaid: { load } })

    const html = mermaidBlock('graph TD; A-->B;')
    const result = await renderDiagrams(html, { streaming: true })

    expect(load).not.toHaveBeenCalled()
    expect(result).toContain('data-lang="mermaid"')
    expect(result).not.toContain('diagram-wrapper')
  })

  it('终稿才把图表代码块替换成渲染结果', async () => {
    const load = vi.fn(mermaidLoader)
    const { renderDiagrams } = useDiagramRenderer({ mermaid: { load } })

    const result = await renderDiagrams(mermaidBlock('graph TD; A-->B;'), {})

    expect(load).toHaveBeenCalledTimes(1)
    expect(result).toContain('diagram-wrapper')
    expect(result).toContain('mermaid-output')
  })

  it('流式阶段仍然收敛链接，危险 href 不会漏出去', async () => {
    const { renderDiagrams } = useDiagramRenderer({})
    const html = '<p><a href="javascript:alert(1)">坏链接</a></p>'

    const result = await renderDiagrams(html, { streaming: true })

    expect(result).not.toContain('javascript:alert(1)')
    expect(result).toContain('data-blocked-href')
  })
})
