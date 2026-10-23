import { describe, it, expect } from 'vitest'
import { isLocalCssResource, sanitizeCssText, sanitizeInlineStyle } from '@/utils/cssSafety'

/**
 * CSS 资源清理：不能靠字面匹配 `url(`。
 * 断言的是"浏览器解析之后还剩什么"，不是"源码里有没有某个字符串"。
 */
describe('isLocalCssResource', () => {
  it('允许锚点、相对路径与内联位图', () => {
    expect(isLocalCssResource('#gradient')).toBe(true)
    expect(isLocalCssResource('/assets/a.png')).toBe(true)
    expect(isLocalCssResource('data:image/png;base64,AAAA')).toBe(true)
  })

  it('拒绝外部主机、协议相对地址与可执行文档', () => {
    expect(isLocalCssResource('https://evil.example/a.png')).toBe(false)
    expect(isLocalCssResource('//evil.example/a.png')).toBe(false)
    expect(isLocalCssResource('data:image/svg+xml;base64,AAAA')).toBe(false)
  })
})

describe('sanitizeCssText', () => {
  it('CSS 转义的函数名同样被识别（u\\72l 会被浏览器当成 url）', () => {
    // 必须用 String.raw：普通字符串里的 \72 是 JS 八进制转义，
    // 会被解析成 "u:l"，那样测试根本没测到 CSS 转义。
    const css = String.raw`.a { background-image: u\72l(https://evil.example/a.png) }`
    const { css: clean, removed } = sanitizeCssText(css)

    expect(clean).not.toContain('evil.example')
    // 命中的声明必须被记录，证明是"识别后移除"而不是碰巧没匹配上
    expect(removed.some((r) => r.includes('background-image'))).toBe(true)
  })

  it('大写、注释与多行写法都不能绕过', () => {
    const cases = [
      '.a { background-image: URL(https://evil.example/b.png) }',
      '.a { background-image: u/**/rl(https://evil.example/c.png) }',
      '.a { background-image: \\75 rl(https://evil.example/d.png) }',
      '.a { background: url(\n  https://evil.example/e.png\n) }',
    ]
    for (const css of cases) {
      expect(sanitizeCssText(css).css).not.toContain('evil.example')
    }
  })

  it('各种会加载资源的属性都被覆盖', () => {
    const props = ['fill', 'stroke', 'mask', 'clip-path', 'filter', 'cursor', 'marker-start']
    for (const prop of props) {
      const css = `.a { ${prop}: url(https://evil.example/${prop}.png) }`
      expect(sanitizeCssText(css).css, prop).not.toContain('evil.example')
    }
  })

  it('自定义属性里的外部引用同样被移除，避免 var() 绕过', () => {
    const css = '.a { --bg: url(https://evil.example/x.png); background-image: var(--bg) }'
    expect(sanitizeCssText(css).css).not.toContain('evil.example')
  })

  it('@import 一律不保留', () => {
    const css = '@import url(https://evil.example/a.css); .a { fill: red }'
    const { css: clean, removed } = sanitizeCssText(css)

    expect(clean).not.toContain('@import')
    expect(clean).not.toContain('evil.example')
    expect(removed.some((r) => r.includes('evil.example'))).toBe(true)
    // 正常声明保留
    expect(clean).toContain('fill: red')
  })

  it('正常样式原样保留（图形外观不能丢）', () => {
    const css = '.node rect { fill: #eee; stroke: #333; stroke-width: 2px }'
    const clean = sanitizeCssText(css).css

    expect(clean).toContain('fill')
    expect(clean).toContain('#eee')
    expect(clean).toContain('stroke-width')
  })

  it('反斜杠伪装的站内路径同样被拒绝（浏览器把 \\ 当 /）', () => {
    // `\\evil.example/a.png` 在 URL 解析器里等价于 `//evil.example/a.png`，
    // 实际请求发往外部主机。
    expect(isLocalCssResource(String.raw`\\evil.example/a.png`)).toBe(false)

    // CSS 源里要写 4 个反斜杠：CSS 转义 `\\` 才是一个字面反斜杠，
    // 解码后得到 `\\evil.example/…`，与上面的输入等价。
    // 用 String.raw 保证这 4 个反斜杠原样进入 CSS 解析器。
    const css = String.raw`.a { fill: url(\\\\evil.example/a.png) }`
    const { css: clean, removed } = sanitizeCssText(css)

    expect(clean).not.toContain('evil.example')
    expect(removed.length).toBeGreaterThan(0)
  })

  it('用空白与反斜杠伪装的地址同样被拒绝（URL 解析器会归一）', () => {
    // URL 规范要求解析前删掉 tab/LF/CR，并把 `\` 当 `/`。
    // `url(/\\evil…)` 经 CSS 转义解码后是 `/\evil…`，浏览器解析成 `//evil…`。
    // 必须 String.raw：普通字符串里的 `\e` 会被当成十六进制转义，测不到目标写法。
    const cases = [
      String.raw`.a { fill: url(/\\evil.example/a.png) }`,
      '.a { fill: url(/\t/evil.example/b.png) }',
      '.a { fill: url(/\n/evil.example/c.png) }',
    ]
    for (const css of cases) {
      expect(sanitizeCssText(css).css, css).not.toContain('evil.example')
    }
  })

  it('image-set() 里的裸字符串地址同样会加载，必须拦下', () => {
    // image-set 的候选地址可以不带 url() 包裹，浏览器照取不误
    const css = '.a { background-image: image-set("https://evil.example/a.png" 1x) }'
    const { css: clean, removed } = sanitizeCssText(css)

    expect(clean).not.toContain('evil.example')
    expect(removed.length).toBeGreaterThan(0)
  })

  it('image-set() 里的站内地址保留', () => {
    const css = '.a { background-image: image-set("/assets/a.png" 1x, "./a@2x.png" 2x) }'
    expect(sanitizeCssText(css).css).toContain('/assets/a.png')
  })

  it('站内渐变引用保留', () => {
    const css = '.a { fill: url(#gradient-1) }'
    expect(sanitizeCssText(css).css).toContain('url(#gradient-1)')
  })
})

describe('sanitizeInlineStyle', () => {
  it('style 属性里的转义 url 同样被移除', () => {
    const { css, removed } = sanitizeInlineStyle(
      'fill: red; background-image: u\\72l(https://evil.example/a.png)',
    )

    expect(css).not.toContain('evil.example')
    expect(css).toContain('fill: red')
    expect(removed.length).toBeGreaterThan(0)
  })

  it('正常的内联样式原样保留（属性值是声明列表，不是样式表）', () => {
    // 样式表解析器（sanitizeCssText）按规则遍历，遇到没有 `{` 的内容会整段丢弃，
    // 拿它处理 style 属性会把所有正常内联样式清空。
    expect(sanitizeInlineStyle('fill:red').css).toBe('fill:red')
    expect(sanitizeInlineStyle('fill: red; stroke: blue').css).toBe('fill: red; stroke: blue')
  })
})
