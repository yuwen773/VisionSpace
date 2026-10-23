import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import { EXTERNAL_LINK_REL, isExternalHref, isSafeLinkHref } from '@/utils/diagramSanitizer'
import { isLoadableImage } from '@/utils/imagePolicy'

// 图表代码块语言列表
export const DIAGRAM_LANGUAGES = ['mermaid', 'drawio', 'plantuml']

// 独立的 HTML 转义，避免在 markdown-it 的 highlight 里引用尚未初始化的 md
const escapeHtml = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// 创建 markdown-it 实例，启用 GFM
const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string): string {
    // 图表语言不做语法高亮，保持原始内容供前端渲染
    if (lang && DIAGRAM_LANGUAGES.includes(lang.toLowerCase())) {
      return `<pre class="hljs code-block diagram-block" data-lang="${lang}"><div class="code-header"><span class="code-lang">${lang}</span><button class="copy-btn" data-code>复制</button></div><code>${escapeHtml(str)}</code></pre>`
    }

    // 如果有语言标识，使用 highlight.js 高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs code-block"><div class="code-header"><span class="code-lang">' +
          lang +
          '</span><button class="copy-btn" data-code>复制</button></div><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
        )
      } catch (__) {}
    }
    // 否则返回普通代码块
    return (
      '<pre class="hljs code-block"><div class="code-header"><span class="code-lang">code</span><button class="copy-btn" data-code>复制</button></div><code>' +
      escapeHtml(str) +
      '</code></pre>'
    )
  },
})

/**
 * 链接与图片的统一收敛：
 * - 危险协议（javascript:/data:text/html/…）降级为不可点击的纯文本
 * - 外链补 noopener/noreferrer 并新标签打开
 * - 图片不发送 Referer，避免把会话地址带给第三方主机
 */
md.renderer.rules.link_open = (tokens, idx, options, _env, self) => {
  const href = tokens[idx].attrGet('href') ?? ''
  if (!isSafeLinkHref(href)) {
    tokens[idx].attrSet('data-blocked-href', '')
    tokens[idx].attrSet('href', '')
    return self.renderToken(tokens, idx, options)
  }
  if (isExternalHref(href)) {
    tokens[idx].attrSet('target', '_blank')
    tokens[idx].attrSet('rel', EXTERNAL_LINK_REL)
  }
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.image = (tokens, idx, options, _env, self) => {
  const src = tokens[idx].attrGet('src') ?? ''
  if (!isLoadableImage(src)) {
    // 未受控的外部图片：不生成 img，改为可读的占位说明，
    // 因为一旦渲染成 img，浏览器就会把地址发出去（CSP 只能事后阻止）。
    const alt = tokens[idx].content || '图片'
    const host = (() => {
      try {
        return new URL(src).hostname
      } catch {
        return ''
      }
    })()
    tokens[idx].type = 'text'
    tokens[idx].content = `[图片未加载：${alt}${host ? `（${host}）` : ''}]`
    return escapeHtml(tokens[idx].content)
  }
  tokens[idx].attrSet('referrerpolicy', 'no-referrer')
  return self.renderToken(tokens, idx, options)
}

export function useMarkdown() {
  const render = (content: string): string => {
    if (!content) return ''
    return md.render(content)
  }

  return {
    render,
  }
}
