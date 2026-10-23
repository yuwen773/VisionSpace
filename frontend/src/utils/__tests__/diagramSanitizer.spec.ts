import { describe, it, expect } from 'vitest'
import { sanitizeDiagramSvg, resolvePlantUmlUrl, isSafeLinkHref } from '@/utils/diagramSanitizer'

/**
 * 图表产物清理：LLM 输出的 SVG 是不可信输入。
 * 断言的是"清理后浏览器实际会做什么"，不是源码里有没有某个字符串。
 */
describe('sanitizeDiagramSvg', () => {
  it('移除 script 与事件处理器，保留图形本身', () => {
    const dirty =
      '<svg xmlns="http://www.w3.org/2000/svg">' +
      '<script>alert(1)</script>' +
      '<rect width="10" height="10" fill="#f00" onload="alert(2)" onclick="alert(3)"/>' +
      '<text x="1" y="2">正常文字</text>' +
      '</svg>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean).not.toContain('<script')
    expect(clean).not.toMatch(/onload=/i)
    expect(clean).not.toMatch(/onclick=/i)
    // 正常图形必须保留，否则等于用"清空"冒充安全
    expect(clean).toContain('<rect')
    expect(clean).toContain('正常文字')
  })

  it('移除指向外部主机的资源引用，避免未经允许的内容外发', () => {
    const dirty =
      '<svg xmlns="http://www.w3.org/2000/svg">' +
      '<image href="https://evil.example/a.png"/>' +
      '<image xlink:href="http://evil.example/b.png"/>' +
      '<use href="https://evil.example/s.svg#icon"/>' +
      '<rect width="4" height="4"/>' +
      '</svg>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean).not.toContain('evil.example')
    expect(clean).toContain('<rect')
  })

  it('移除远程样式与 CSS 中的 url() / @import', () => {
    const dirty =
      '<svg xmlns="http://www.w3.org/2000/svg">' +
      '<style>@import url(https://evil.example/a.css); .a{fill:red}</style>' +
      '<rect class="a" style="fill:url(https://evil.example/b.svg#p)"/>' +
      '</svg>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean).not.toContain('evil.example')
    expect(clean).not.toContain('@import')
  })

  it('保留受控服务的预览图地址（PlantUML 自托管）', () => {
    const legit =
      '<img class="plantuml-diagram" src="https://uml.internal.example/svg/abc" alt="图表" />'
    const clean = sanitizeDiagramSvg(legit, {
      imageOrigins: ['https://uml.internal.example'],
    })

    expect(clean).toContain('src="https://uml.internal.example/svg/abc"')
    expect(clean).toContain('plantuml-diagram')
  })

  it('未声明为受控源的 https 图片一律摘掉 src', () => {
    // 这是"内容自述一个外部地址"的典型形态：协议合法，但主机不受控。
    const dirty =
      '<img src="https://evil.example/track?c=secret" alt="a"/>' +
      '<img src="//evil.example/protocol-relative.png" alt="b"/>' +
      '<img src="https://uml.internal.example/ok.png" alt="c"/>'
    const clean = sanitizeDiagramSvg(dirty, {
      imageOrigins: ['https://uml.internal.example'],
    })

    expect(clean).not.toContain('evil.example')
    // 受控源仍然保留，否则等于把 PlantUML 预览一起关掉
    expect(clean).toContain('src="https://uml.internal.example/ok.png"')
  })

  it('受控源按 origin 匹配，不能靠路径或子域绕过', () => {
    const clean = sanitizeDiagramSvg(
      '<img src="https://uml.internal.example.evil.com/a.png" alt="a"/>' +
        '<img src="http://uml.internal.example:8080/b.png" alt="b"/>',
      { imageOrigins: ['https://uml.internal.example'] },
    )

    expect(clean).not.toContain('evil.com')
    // 协议或端口不同就是不同的源
    expect(clean).not.toContain(':8080')
  })

  it('图片地址的危险协议被摘掉', () => {
    const dirty =
      '<img src="javascript:alert(1)" alt="a"/>' +
      '<img src="data:text/html;base64,PHNjcmlwdD4=" alt="b"/>' +
      '<img src="vbscript:msgbox(1)" alt="c"/>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean).not.toMatch(/src="javascript:/i)
    expect(clean).not.toContain('data:text/html')
    expect(clean).not.toMatch(/src="vbscript:/i)
  })

  it('移除 foreignObject，HTML 不能借 SVG 通道回到 DOM', () => {
    const dirty =
      '<svg xmlns="http://www.w3.org/2000/svg"><foreignObject>' +
      '<div xmlns="http://www.w3.org/1999/xhtml"><img src="https://evil.example/x.png"></div>' +
      '</foreignObject></svg>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean.toLowerCase()).not.toContain('foreignobject')
    expect(clean).not.toContain('evil.example')
  })

  it('移除 javascript: 等危险协议的链接', () => {
    const dirty =
      '<svg xmlns="http://www.w3.org/2000/svg">' +
      '<a href="javascript:alert(1)"><text>x</text></a>' +
      '<a href="data:text/html;base64,PHNjcmlwdD4="><text>y</text></a>' +
      '</svg>'
    const clean = sanitizeDiagramSvg(dirty)

    expect(clean).not.toMatch(/javascript:/i)
    expect(clean).not.toContain('data:text/html')
  })

  it('保留 Mermaid 产物需要的结构（style 标签与内部引用）', () => {
    const legit =
      '<svg xmlns="http://www.w3.org/2000/svg" id="d1" width="100" height="50">' +
      '<style>#d1 .node rect{fill:#eee;stroke:#333}</style>' +
      '<g class="node"><rect width="10" height="10"/><text>开始</text></g>' +
      '</svg>'
    const clean = sanitizeDiagramSvg(legit)

    expect(clean).toContain('<style')
    expect(clean).toContain('class="node"')
    expect(clean).toContain('开始')
  })
})

describe('isSafeLinkHref', () => {
  it('允许 http/https 与站内相对链接', () => {
    expect(isSafeLinkHref('https://example.com/a')).toBe(true)
    expect(isSafeLinkHref('http://example.com/a')).toBe(true)
    expect(isSafeLinkHref('/picture/1')).toBe(true)
    expect(isSafeLinkHref('#anchor')).toBe(true)
  })

  it('拒绝脚本与内联文档协议', () => {
    expect(isSafeLinkHref('javascript:alert(1)')).toBe(false)
    expect(isSafeLinkHref('JavaScript:alert(1)')).toBe(false)
    expect(isSafeLinkHref('vbscript:msgbox(1)')).toBe(false)
    expect(isSafeLinkHref('data:text/html;base64,PHNjcmlwdD4=')).toBe(false)
    expect(isSafeLinkHref('file:///C:/secret.txt')).toBe(false)
  })

  it('拒绝用空白与控制字符伪装的协议', () => {
    expect(isSafeLinkHref('  javascript:alert(1)')).toBe(false)
    expect(isSafeLinkHref('java\u0000script:alert(1)')).toBe(false)
  })
})

describe('resolvePlantUmlUrl', () => {
  const code = '@startuml\nAlice -> Bob\n@enduml'

  it('使用受控自托管服务生成地址', () => {
    const url = resolvePlantUmlUrl(code, 'https://uml.internal.example')
    expect(url).not.toBeNull()
    expect(url!.startsWith('https://uml.internal.example/')).toBe(true)
    // 编码后的正文不能再拼出第二个主机名
    expect(url!.slice('https://uml.internal.example/'.length)).not.toContain('://')
  })

  it('未配置自托管服务时返回 null，不回退公共渲染服务', () => {
    expect(resolvePlantUmlUrl(code, '')).toBeNull()
    expect(resolvePlantUmlUrl(code, undefined)).toBeNull()
    // 公共渲染服务不得被隐式选中
    expect(resolvePlantUmlUrl(code, 'https://www.plantuml.com')).toBeNull()
  })

  it('拒绝非法的受控配置，避免内容被送往任意目标', () => {
    expect(resolvePlantUmlUrl(code, 'javascript:alert(1)')).toBeNull()
    expect(resolvePlantUmlUrl(code, 'uml.internal.example')).toBeNull()
    expect(resolvePlantUmlUrl(code, 'http://user:pass@uml.internal.example')).toBeNull()
  })
})
