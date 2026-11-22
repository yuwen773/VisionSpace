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
  snippet: string
  domain: string
}

/** `![alt](url)` */
const IMAGE_SYNTAX = /!\[([^\]]*)\]\(([^)]+)\)/g
/** `[text](url)`，用后行断言排除 `![alt](url)` 里的方括号 */
const LINK_SYNTAX = /(?<!!)\[([^\]]+)\]\(([^)]+)\)/g

const IMAGE_EXTENSION = /\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)(\?.*)?$/i
const IMAGE_HOSTS = ['images.pexels.com', 'images.unsplash.com', 'imgur.com']

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
      links.push({ url, title: match[1], snippet: match[1], domain: new URL(url).hostname })
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
