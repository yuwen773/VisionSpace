/**
 * 消息正文里的资源解析。
 *
 * <p>Agent 回复中的图片与链接会被两处用到：消息气泡里的内联资源，
 * 以及右侧资源面板的汇总。两处必须按同一套规则识别，
 * 否则同一条回复在两处会显示成不同的资源清单。
 */

export interface MarkdownImage {
  url: string
  title?: string
}

export interface MarkdownLink {
  url: string
  title: string
  /** 链接所在段落的上下文摘要；段落里只有链接本身时为空 */
  snippet: string
  domain: string
  /** 站点图标地址；加载失败时由 SourceCard 回退到首字母 */
  faviconUrl?: string
  /** 在本次回复里的顺序号，从 1 开始 */
  index?: number
}

/** `![alt](url)` */
const IMAGE_SYNTAX = /!\[([^\]]*)\]\(([^)]+)\)/g
/** `[text](url)`，用后行断言排除 `![alt](url)` 里的方括号 */
const LINK_SYNTAX = /(?<!!)\[([^\]]+)\]\(([^)]+)\)/g

const IMAGE_EXTENSION = /\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)(\?.*)?$/i
const IMAGE_HOSTS = ['images.pexels.com', 'images.unsplash.com', 'imgur.com']

const MAX_SNIPPET_LENGTH = 120

/** 清掉段落里的 Markdown 标记，留下可读文本。 */
const stripMarkdown = (text: string): string =>
  text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*`_~|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

/** 句子边界：摘要取链接所在句，而不是整段，避免列表/长段带进无关内容。 */
const SENTENCE_BOUNDARIES = ['。', '！', '？', '!', '?', '\n']

/**
 * 取链接所在句子的上下文作为摘要。
 * 句子去掉链接文字后为空时返回空串，避免标题和摘要完全重复。
 */
function extractSnippet(content: string, start: number, end: number, label: string): string {
  const paragraphBreak = content.lastIndexOf('\n\n', start)
  const paragraphStart = paragraphBreak === -1 ? 0 : paragraphBreak + 2
  const paragraphEnd = content.indexOf('\n\n', end)
  const paragraph = content.slice(
    paragraphStart,
    paragraphEnd === -1 ? content.length : paragraphEnd,
  )

  const relStart = Math.min(Math.max(0, start - paragraphStart), paragraph.length)
  const relEnd = Math.min(Math.max(relStart, end - paragraphStart), paragraph.length)
  const head = paragraph.slice(0, relStart)
  const tail = paragraph.slice(relEnd)

  let sentenceStart = 0
  for (const boundary of SENTENCE_BOUNDARIES) {
    sentenceStart = Math.max(sentenceStart, head.lastIndexOf(boundary) + 1)
  }
  let sentenceEnd = paragraph.length
  for (const boundary of SENTENCE_BOUNDARIES) {
    const index = tail.indexOf(boundary)
    if (index !== -1) sentenceEnd = Math.min(sentenceEnd, relEnd + index + 1)
  }

  const toSnippet = (raw: string): string => {
    let text = stripMarkdown(raw)
    if (label) text = text.split(label).join(' ')
    return text.replace(/\s+/g, ' ').trim()
  }

  // 链接自成一整段（如资源清单）时没有上下文，不要用标题凑摘要
  const text = toSnippet(paragraph.slice(sentenceStart, sentenceEnd))
  if (!text) return ''
  return text.length > MAX_SNIPPET_LENGTH ? `${text.slice(0, MAX_SNIPPET_LENGTH).trimEnd()}…` : text
}

function faviconFor(domain: string): string | undefined {
  return domain ? `https://${domain}/favicon.ico` : undefined
}

/**
 * 地址本身看起来是不是图片：扩展名，或已知的图床域名。
 */
export function isImageUrl(url: string): boolean {
  if (!url) return false
  if (IMAGE_EXTENSION.test(url)) return true
  try {
    const hostname = new URL(url).hostname
    return IMAGE_HOSTS.some((host) => hostname.includes(host))
  } catch {
    return false
  }
}

/**
 * 正文里的图片：显式图片语法，以及直接指向图片的普通链接。
 */
export function extractImages(content: string): MarkdownImage[] {
  if (!content) return []
  const images: MarkdownImage[] = []
  for (const match of content.matchAll(IMAGE_SYNTAX)) {
    images.push({ url: match[2], title: match[1] || undefined })
  }
  for (const match of content.matchAll(LINK_SYNTAX)) {
    if (isImageUrl(match[2])) {
      images.push({ url: match[2], title: match[1] })
    }
  }
  return images
}

/**
 * 正文里的链接：按地址去重，解析不出域名的地址不算资源。
 */
export function extractLinks(content: string): MarkdownLink[] {
  if (!content) return []
  const seen = new Set<string>()
  const links: MarkdownLink[] = []
  for (const match of content.matchAll(LINK_SYNTAX)) {
    const url = match[2]
    if (seen.has(url)) continue
    seen.add(url)
    try {
      const domain = new URL(url).hostname
      const title = match[1]
      const start = match.index ?? 0
      links.push({
        url,
        title,
        snippet: extractSnippet(content, start, start + match[0].length, title),
        domain,
        faviconUrl: faviconFor(domain),
        index: links.length + 1,
      })
    } catch {
      // 解析不出域名的地址不当作链接资源
    }
  }
  return links
}

/**
 * 一次解析出两类资源。
 */
export function extractResources(content: string): {
  images: MarkdownImage[]
  links: MarkdownLink[]
} {
  return { images: extractImages(content), links: extractLinks(content) }
}
