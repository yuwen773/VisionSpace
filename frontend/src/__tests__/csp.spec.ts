import { describe, it, expect } from 'vitest'
import { buildCsp } from '@/utils/csp'

/**
 * CSP 是"最终产物上生效的策略"，所以断言生成的指令本身，
 * 而不是断言源码里出现了某个字符串。
 */
const parse = (csp: string) =>
  Object.fromEntries(
    csp
      .split(';')
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const [name, ...values] = part.split(/\s+/)
        return [name, values]
      }),
  )

describe('buildCsp', () => {
  it('脚本只允许同源，禁用内联与 eval', () => {
    const d = parse(buildCsp())
    expect(d['script-src']).toEqual(["'self'"])
  })

  it('默认拒绝外部连接与插件内容', () => {
    const d = parse(buildCsp())
    expect(d['connect-src']).toEqual(["'self'"])
    expect(d['object-src']).toEqual(["'none'"])
    expect(d['frame-src']).toEqual(["'none'"])
    expect(d['base-uri']).toEqual(["'self'"])
  })

  it('脚本与连接仍然只允许同源（字体来源不放宽这两项）', () => {
    const d = parse(buildCsp())
    expect(d['script-src']).toEqual(["'self'"])
    expect(d['connect-src']).toEqual(["'self'"])
    // 字体来源只出现在 style-src / font-src
    expect(d['script-src'].some((v) => v.includes('fonts'))).toBe(false)
    expect(d['connect-src'].some((v) => v.includes('fonts'))).toBe(false)
    expect(d['font-src'].some((v) => v.includes('fonts.gstatic.com'))).toBe(true)
  })

  it('既有的字体与首页装饰图源被保留，避免收紧策略时造成功能回退', () => {
    const d = parse(buildCsp())
    expect(d['font-src'].some((v) => v.includes('fonts.gstatic'))).toBe(true)
    expect(d['style-src'].some((v) => v.includes('fonts.googleapis'))).toBe(true)
    // 首页装饰图源由组件常量给出，与对话内容无关
    expect(d['img-src']).toContain('https://images.unsplash.com')
    expect(d['img-src']).toContain('https://picsum.photos')
  })

  it('未配置 PlantUML 时不放行任意的渲染目标', () => {
    const d = parse(buildCsp())
    expect(d['img-src']).not.toContain('https://uml.internal.example')
    // 外部图片源只能是既有装饰图源这一份固定清单，不能是任意主机
    const external = d['img-src'].filter((v) => /^https?:\/\//.test(v))
    expect(external.sort()).toEqual(
      [
        'https://images.unsplash.com',
        'https://picsum.photos',
        'https://fastly.picsum.photos',
        'https://plus.unsplash.com',
        'https://pbs.twimg.com',
      ].sort(),
    )
  })

  it('配置了自托管 PlantUML 时额外放行且只放行该源', () => {
    const base = new Set(parse(buildCsp())['img-src'])
    const d = parse(buildCsp({ plantumlServer: 'https://uml.internal.example/render' }))
    const added = d['img-src'].filter((v) => /^https?:\/\//.test(v) && !base.has(v))
    expect(added).toEqual(['https://uml.internal.example'])
  })

  it('只放行受控对象存储域名，不接受任意写法', () => {
    const base = new Set(parse(buildCsp())['img-src'])
    const d = parse(
      buildCsp({ imageHosts: 'https://cdn.internal.example, not-a-url, javascript:alert(1)' }),
    )
    const added = d['img-src'].filter((v) => /^https?:\/\//.test(v) && !base.has(v))
    expect(added).toEqual(['https://cdn.internal.example'])
  })
})
