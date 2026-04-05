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
}

export interface ResourceData {
  images: ImageResource[]
  links: LinkResource[]
}
