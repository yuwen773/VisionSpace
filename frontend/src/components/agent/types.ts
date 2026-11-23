export interface ImageResource {
  url: string
  title?: string
  source?: 'internal' | 'external' | 'generated'
}

export interface LinkResource {
  url: string
  title: string
  snippet: string
  domain: string
  /** 站点图标；加载失败时回退到域名首字母 */
  faviconUrl?: string
  /** 在本次回复里的顺序号，从 1 开始 */
  index?: number
}

export interface ResourceData {
  images: ImageResource[]
  links: LinkResource[]
}
