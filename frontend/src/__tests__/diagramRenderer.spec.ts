import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useDiagramRenderer } from '@/composables/useDiagramRenderer'
import {
  renderMermaidBlock,
  renderDrawioBlock,
  renderPlantumlBlock,
} from '@/composables/diagramRenderers'
import { useMarkdown } from '@/composables/useMarkdown'

/**
 * 图表渲染的安全契约：
 * 产物进入 innerHTML 之前必须已清理，缺失配置时退回源码而不是外发。
 */

describe('图表渲染：Mermaid', () => {
  it('使用严格安全级别，禁用 loose 模式下的 HTML 与交互', async () => {
    const initialize = vi.fn()
    await renderMermaidBlock('graph TD\n A-->B', 'id1', {
      load: async () => ({
        initialize,
        render: async () => ({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>' }),
      }),
    })

    expect(initialize).toHaveBeenCalledTimes(1)
    const options = initialize.mock.calls[0][0]
    expect(options.securityLevel).toBe('strict')
    expect(options.htmlLabels).toBe(false)
  })

  it('渲染结果先清理再返回，脚本进不了最终 DOM', async () => {
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script>' +
      '<rect width="4" height="4" onload="alert(2)"/></svg>'
    const out = await renderMermaidBlock('graph TD\n A-->B', 'id2', {
      load: async () => ({ initialize: vi.fn(), render: async () => ({ svg }) }),
    })

    expect(out).not.toContain('<script')
    expect(out).not.toMatch(/onload=/i)
    expect(out).toContain('<rect')
  })

  it('渲染失败时返回错误提示而不是抛出，页面其余内容仍可显示', async () => {
    const out = await renderMermaidBlock('bad', 'id3', {
      load: async () => ({
        initialize: vi.fn(),
        render: async () => {
          throw new Error('解析失败')
        },
      }),
    })
    expect(out).toContain('diagram-error')
  })
})

describe('图表渲染：draw.io', () => {
  it('渲染产物同样经过清理', async () => {
    const out = await renderDrawioBlock('<mxGraphModel/>', {
      load: async () => ({
        renderToHtml: async () =>
          '<svg xmlns="http://www.w3.org/2000/svg"><image href="https://evil.example/x.png"/></svg>',
      }),
    })
    expect(out).not.toContain('evil.example')
  })

  it('本地渲染库不可用时提示而不是回落远程渲染', async () => {
    const out = await renderDrawioBlock('<mxGraphModel/>', {
      load: async () => null,
    })
    expect(out).toContain('diagram-error')
    expect(out).not.toMatch(/https?:\/\/(?!.*\.example)/)
  })
})

describe('图表渲染：PlantUML', () => {
  it('配置了受控服务时指向该服务的地址', async () => {
    const out = await renderPlantumlBlock(
      '@startuml\nA->B\n@enduml',
      'https://uml.internal.example',
    )
    expect(out).toContain('https://uml.internal.example/svg/')
    expect(out).toContain('<img')
  })

  it('未配置时标记为源码回退，且不产生任何外部地址', async () => {
    const out = await renderPlantumlBlock('@startuml\nA->B\n@enduml', '')
    expect(out).not.toContain('plantuml.com')
    expect(out).not.toMatch(/<img/)
    // 预览区说明原因；源码本身由"代码"面板承载，不重复插入第二份
    expect(out).toContain('plantuml-source')
    expect(out).toContain('已显示源码')
    expect(out).not.toContain('@startuml')
  })
})

describe('图表渲染：整体编排', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('图表代码块被替换为带预览与源码的结构', async () => {
    const { renderDiagrams } = useDiagramRenderer({
      mermaid: {
        load: async () => ({
          initialize: vi.fn(),
          render: async () => ({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>' }),
        }),
      },
    })

    const html = useMarkdown().render('```mermaid\ngraph TD\n A-->B\n```')
    const out = await renderDiagrams(html)

    expect(out).toContain('diagram-wrapper')
    expect(out).toContain('diagram-preview')
    expect(out).toContain('diagram-code')
    // 未渲染成功的代码块不该原样残留两份
    expect(out.match(/class="diagram-wrapper/g)?.length ?? 0).toBe(1)
  })

  it('普通代码块不受影响', async () => {
    const { renderDiagrams } = useDiagramRenderer()
    const html = useMarkdown().render('```text\n普通文本内容\n```')
    const out = await renderDiagrams(html)
    expect(out).toContain('普通文本内容')
    expect(out).toContain('code-block')
    expect(out).not.toContain('diagram-wrapper')
  })

  it('渲染后产物中的脚本与外部资源均被清除', async () => {
    const { renderDiagrams } = useDiagramRenderer({
      mermaid: {
        load: async () => ({
          initialize: vi.fn(),
          render: async () => ({
            svg:
              '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script>' +
              '<image href="https://evil.example/a.png"/><g/></svg>',
          }),
        }),
      },
    })

    const html = useMarkdown().render('```mermaid\ngraph TD\n A-->B\n```')
    const out = await renderDiagrams(html)

    expect(out).not.toContain('<script')
    expect(out).not.toContain('evil.example')
  })
})
