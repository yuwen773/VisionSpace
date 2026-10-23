import { describe, it, expect, vi } from 'vitest'
import {
  findExternalResourceUrl,
  renderMermaidBlock,
  stripMermaidDirectives,
} from '@/composables/diagramRenderers'

/**
 * Mermaid 内容自述配置的回归。
 *
 * `mermaid.initialize()` 设的 securityLevel 会被**内容里**的 `%%{init: …}%%`
 * 覆盖；一旦被改成 loose，Mermaid 会恢复 HTML 标签并跳过它自己的清理，
 * 在渲染期间就把外部资源发出去。事后清理产物已经晚了，所以必须在渲染前剥离。
 */
describe('stripMermaidDirectives', () => {
  it('移除 init 指令，内容无法自述配置', () => {
    const code = '%%{init: {"securityLevel":"loose","htmlLabels":true}}%%\nflowchart TD\n  A --> B'
    const stripped = stripMermaidDirectives(code)
    expect(stripped).not.toContain('securityLevel')
    expect(stripped).not.toContain('loose')
    // 图表本身必须保留，不能靠"整段丢掉"通过
    expect(stripped).toContain('flowchart TD')
    expect(stripped).toContain('A --> B')
  })

  it('移除带 themeCSS 的指令（CSS 外发通道）', () => {
    const code =
      '%%{init: {"themeCSS":".node rect { background-image:url(https://evil.example/x) }"}}%%\n' +
      'flowchart TD\n  A --> B'
    expect(stripMermaidDirectives(code)).not.toContain('evil.example')
    expect(stripMermaidDirectives(code)).toContain('flowchart TD')
  })

  it('移除开头的 frontmatter 配置块', () => {
    const code = '---\nconfig:\n  securityLevel: loose\n---\nflowchart TD\n  A --> B'
    const stripped = stripMermaidDirectives(code)
    expect(stripped).not.toContain('securityLevel')
    expect(stripped).toContain('flowchart TD')
  })

  it('跨行的指令块也能整体移除', () => {
    const code = '%%{init: {\n  "securityLevel": "loose"\n}}%%\nflowchart TD\n  A --> B'
    expect(stripMermaidDirectives(code)).not.toContain('securityLevel')
    expect(stripMermaidDirectives(code)).toContain('flowchart TD')
  })

  it('正常图表不被改写', () => {
    const code = 'flowchart TD\n  A[开始] --> B{判断}\n  B -->|是| C[执行]'
    expect(stripMermaidDirectives(code).trim()).toBe(code)
  })
})

describe('findExternalResourceUrl：渲染库自己取图的通道', () => {
  it('识别 img 形状里的地址', () => {
    const code = 'flowchart TD\n A@{ img: "https://evil.example/a.png", label: "图" } --> B'
    expect(findExternalResourceUrl(code)).toBe('https://evil.example/a.png')
  })

  it('识别协议相对地址', () => {
    expect(findExternalResourceUrl('flowchart TD\n A[//evil.example/a.png] --> B')).toBe(
      '//evil.example/a.png',
    )
  })

  it('识别反斜杠与空白伪装的地址（浏览器会把它们归一成 //host）', () => {
    // URL 解析器删掉 tab/LF/CR、把 `\` 当 `/`：这三种写法都会真的发往 evil.example，
    // 渲染库拿到的是原始源码，`img.src = "/\evil.example/x"` 照样发出请求
    expect(findExternalResourceUrl('A@{ img: "/\\evil.example/x.png" }')).toBe(
      '//evil.example/x.png',
    )
    expect(findExternalResourceUrl('A@{ img: "/\t/evil.example/x.png" }')).toBe(
      '//evil.example/x.png',
    )
    expect(findExternalResourceUrl('A@{ img: "https:/evil.example/x" }')).not.toBeNull()
  })

  it('危险协议与可执行 data: 同样在渲染前拦下', () => {
    expect(findExternalResourceUrl('A@{ img: "javascript:alert(1)" }')).not.toBeNull()
    expect(findExternalResourceUrl('A@{ img: "file:///C:/x" }')).not.toBeNull()
    expect(findExternalResourceUrl('A@{ img: "data:image/svg+xml;base64,AAAA" }')).not.toBeNull()
    // 内联位图不构成外发，照常渲染
    expect(findExternalResourceUrl('A@{ img: "data:image/png;base64,AAAA" }')).toBeNull()
  })

  it('受控源放行：配置里信任的图床不算内容自述的外发', () => {
    const code = 'flowchart TD\n A@{ img: "https://cdn.internal.example/a.png" } --> B'
    expect(findExternalResourceUrl(code)).toBe('https://cdn.internal.example/a.png')
    expect(findExternalResourceUrl(code, ['https://cdn.internal.example'])).toBeNull()
    // 受控清单按 origin 比较，不是前缀匹配
    expect(findExternalResourceUrl(code, ['https://cdn.other.example'])).not.toBeNull()
    expect(findExternalResourceUrl(code, ['https://cdn.internal.example.evil.com'])).not.toBeNull()
  })

  it('正常图表不误伤', () => {
    expect(findExternalResourceUrl('flowchart TD\n A[开始] --> B{判断}')).toBeNull()
    expect(findExternalResourceUrl('sequenceDiagram\n Alice->>Bob: 你好')).toBeNull()
  })
})

describe('renderMermaidBlock：外部资源在渲染前就被拦住', () => {
  it('源码含外部地址时不调用渲染库（请求不会发出）', async () => {
    const render = vi.fn(async () => ({
      svg: '<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>',
    }))

    const out = await renderMermaidBlock(
      'flowchart TD\n A@{ img: "https://evil.example/a.png", label: "图" } --> B',
      'id-img-shape',
      { load: async () => ({ initialize: vi.fn(), render }) },
    )

    // 关键：渲染库一次都没被调用，因此不存在"渲染期已发出请求"的窗口
    expect(render).not.toHaveBeenCalled()
    expect(out).toContain('diagram-error')
  })
})

describe('renderMermaidBlock：交给渲染库的是剥离后的内容', () => {
  it('渲染库收到的代码里没有内容自述配置', async () => {
    // 显式声明签名，否则 mock.calls 的类型退化成空元组，取不到收到的代码
    const render = vi.fn<(id: string, code: string) => Promise<{ svg: string }>>()
    render.mockResolvedValue({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>' })
    const initialize = vi.fn()

    await renderMermaidBlock(
      '%%{init: {"securityLevel":"loose"}}%%\nflowchart TD\n  A --> B',
      'id-strip',
      { load: async () => ({ initialize, render }) },
    )

    expect(render).toHaveBeenCalledTimes(1)
    const received = render.mock.calls[0][1]
    expect(received).not.toContain('securityLevel')
    expect(received).toContain('flowchart TD')
    // 应用自己的严格配置必须仍然生效
    expect(initialize.mock.calls[0][0].securityLevel).toBe('strict')
    expect(initialize.mock.calls[0][0].htmlLabels).toBe(false)
  })
})

describe('Mermaid 主题：内置主题会忽略 themeVariables', () => {
  it('使用 base 主题，图表才会跟随双主题变量', async () => {
    const initialize = vi.fn()
    await renderMermaidBlock('flowchart TD\n A --> B', 'id-theme', {
      load: async () => ({
        initialize,
        render: async () => ({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>' }),
      }),
    })

    // default/forest 等内置主题不接受 themeVariables，图表会固定成浅色，
    // 在深空主题下与背景撞成一片。base 是官方给调用方自定义变量的主题。
    expect(initialize.mock.calls[0][0].theme).toBe('base')
  })
})
