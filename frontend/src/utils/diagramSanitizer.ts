/**
 * 图表产物清理：LLM 生成的 SVG / HTML 一律视为不可信输入。
 *
 * 只用 DOMPurify 不足以挡住外发——它会把 `<image href="https://…">`
 * 和 CSS `@import url(…)` 原样留下。因此这里在清理之外再加一层
 * "只允许本地引用"的资源策略：任何指向其它主机的资源引用与远程样式
 * 都被移除，而不是依赖内容里恰好没有危险写法。
 */

import createDOMPurify from 'dompurify'
import plantumlEncoder from 'plantuml-encoder'

type PurifyInstance = ReturnType<typeof createDOMPurify>

let purify: PurifyInstance | null = null

function getPurify(): PurifyInstance {
  if (!purify) {
    purify = createDOMPurify(window)
  }
  return purify
}

/** 允许的协议：仅站内相对引用、锚点和用 data 内联的位图。 */
const SAFE_RESOURCE_SCHEMES = [
  'data:image/png',
  'data:image/jpeg',
  'data:image/gif',
  'data:image/webp',
]

/** 会被浏览器当作资源去取的属性，必须逐项校验。 */
const RESOURCE_ATTRS = ['href', 'xlink:href', 'src', 'action', 'formaction']

/**
 * DOMPurify 在 SVG 配置下默认允许的 URI 属性含 href/xlink:href，不含 img 的 src。
 * 图表预览需要 src，但对于 img 我们仍只接受 http(s)/站内/data 位图——
 * 也就是先把危险协议筛掉，再让 DOMPurify 做它那层结构清理。
 */
const IMG_SRC_SCHEME = /^(https?:|\/|\.\/|\.\.\/|data:image\/(png|jpe?g|gif|webp);|blob:)/i

/**
 * 判断资源引用是否只指向本地。
 * 空值、锚点、相对路径和受限协议视为本地；其它一律拒绝。
 */
function isLocalResource(value: string): boolean {
  const v = value.trim()
  if (!v) return true
  if (v.startsWith('#')) return true
  const lower = v.toLowerCase()
  if (SAFE_RESOURCE_SCHEMES.some((scheme) => lower.startsWith(scheme))) {
    // data: 只放行位图，且不允许 svg 这类可执行文档
    return !lower.startsWith('data:image/svg')
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(v)) return false // 带协议的绝对地址
  if (v.startsWith('//')) return false // 协议相对地址
  return true
}

/** CSS 中任何远程 url() 与 @import 都会造成外发。 */
function stripRemoteCss(css: string): string {
  return css
    .replace(/@import[^;]*;?/gi, '')
    .replace(/url\(\s*(['"]?)([^)'"]*)\1\s*\)/gi, (whole, _q, target: string) =>
      isLocalResource(target) ? whole : 'none',
    )
}

/** 兜底：属性里残留的危险协议（DOMPurify 已挡大部分，这里防漏网）。 */
const DANGEROUS_PROTOCOL = /^\s*(javascript|vbscript|data:text\/html|file):/i

/**
 * 在清理之前把 img 上不属于受控协议的 src 直接摘掉。
 * DOMPurify 的结构清理不认 src，这一步才是挡住 `src="javascript:…"`
 * 与内联 SVG 文档的地方；合法地址原样保留，供预览使用。
 */
function stripUnsafeImgSrc(html: string): string {
  if (!html.includes('<img')) return html
  const doc = new DOMParser().parseFromString(html, 'text/html')
  for (const img of Array.from(doc.querySelectorAll('img'))) {
    const src = img.getAttribute('src') ?? ''
    if (!IMG_SRC_SCHEME.test(src.trim())) img.removeAttribute('src')
  }
  return doc.body.innerHTML
}

/**
 * 清理图表 SVG/HTML。
 * 保留正常图形结构（rect/path/text/style 等），只切除可执行与外发能力。
 */
export function sanitizeDiagramSvg(svg: string): string {
  if (!svg) return ''

  const purifier = getPurify()

  // 先剔除 img 上的非法 src，再交给 DOMPurify 做结构清理
  const prefiltered = stripUnsafeImgSrc(svg)

  const clean = purifier.sanitize(prefiltered, {
    // 只额外放行 img 的 src；危险协议在这一步之前已被剔除
    ADD_ATTR: ['src'],
    USE_PROFILES: { svg: true, svgFilters: true, html: true },
    // 结构性交互能力一律不要
    FORBID_TAGS: [
      'script',
      'foreignObject',
      'iframe',
      'object',
      'embed',
      'form',
      'input',
      'button',
    ],
    FORBID_ATTR: ['srcdoc', 'formaction'],
    // 明确允许标签内的内联样式，稍后逐个校验 url()
    ALLOW_DATA_ATTR: false,
  })

  // 在已清理的 DOM 上做资源层收敛，避免字符串替换误伤正文
  const doc = new DOMParser().parseFromString(clean, 'text/html')
  const root = doc.body

  for (const el of Array.from(root.querySelectorAll('*'))) {
    for (const attr of RESOURCE_ATTRS) {
      const value = el.getAttribute(attr)
      if (value === null) continue

      // img 的 src 走图片白名单（已在前置过滤中校验过协议）；
      // 其它资源属性只允许本地引用，避免 SVG 内部把内容发到任意主机。
      const isImageSrc = attr === 'src' && el.tagName.toLowerCase() === 'img'
      const allowed = isImageSrc ? IMG_SRC_SCHEME.test(value.trim()) : isLocalResource(value)
      if (!allowed) el.removeAttribute(attr)
    }

    // 事件处理器（DOMPurify 通常已移除，保留这一层以防配置漂移）
    for (const attr of Array.from(el.attributes)) {
      if (/^on/i.test(attr.name) || DANGEROUS_PROTOCOL.test(attr.value)) {
        el.removeAttribute(attr.name)
      }
    }

    if (el.tagName.toLowerCase() === 'style') {
      el.textContent = stripRemoteCss(el.textContent || '')
    }

    const style = el.getAttribute('style')
    if (style) {
      const stripped = stripRemoteCss(style)
      if (stripped !== style) el.setAttribute('style', stripped)
    }
  }

  return root.innerHTML
}

/** 站外链接统一补的 rel 值：不传 Referer、不给 opener、不传递权重。 */
export const EXTERNAL_LINK_REL = 'noopener noreferrer nofollow'

/** 是否为站外 http(s) 链接（需要新标签打开并补 rel）。 */
export function isExternalHref(href: string): boolean {
  return /^\s*https?:/i.test(href)
}

/**
 * 链接协议白名单：Markdown 里的链接也要过同一套判断。
 * 只允许 http/https 与站内相对地址。
 */
export function isSafeLinkHref(href: string): boolean {
  const raw = (href ?? '').trim()
  if (!raw) return true
  // 控制字符可被用来伪装协议（如 "java\u0000script:"），先剔除再判断
  const v = raw.replace(/[\u0000-\u001F\u007F]/g, '')
  if (v.startsWith('#')) return true
  if (v.startsWith('/')) return true
  if (v.startsWith('./') || v.startsWith('../')) return true
  if (/^[a-z][a-z0-9+.-]*:/i.test(v)) {
    return /^https?:/i.test(v)
  }
  // 没有协议、也不是绝对路径：按相对地址处理
  return true
}

/** 公共渲染服务不得被当作隐式回退目标。 */
const PUBLIC_PLANTUML_HOSTS = ['plantuml.com', 'www.plantuml.com', 'plantuml.io', 'www.plantuml.io']

/**
 * 解析受控的 PlantUML 自托管服务地址。
 * 目标完全由部署配置决定，图表内容只能作为编码后的路径段，
 * 不能改变请求目的地；未配置或配置不合法时返回 null（改为展示源码）。
 */
export function resolvePlantUmlUrl(code: string, baseUrl: string | undefined): string | null {
  const base = (baseUrl ?? '').trim()
  if (!base) return null

  let parsed: URL
  try {
    parsed = new URL(base)
  } catch {
    return null // 缺少协议或写法非法
  }

  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return null
  if (parsed.username || parsed.password) return null // 凭据不能出现在地址里
  if (PUBLIC_PLANTUML_HOSTS.includes(parsed.hostname.toLowerCase())) return null

  const encoded = plantumlEncoder.encode((code ?? '').trim())
  if (!encoded) return null

  // 用 URL 拼接而不是字符串相加，确保内容不会被解释成新的主机或路径段
  const target = new URL(parsed.toString())
  target.pathname = `${target.pathname.replace(/\/+$/, '')}/svg/${encoded}`
  return target.toString()
}
