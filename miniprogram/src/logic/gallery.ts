/**
 * 列表/详情展示的纯函数：条目归一化、计数与时间格式化。
 * 不依赖 wx API，可直接在 node 下单测。
 */

/** 推荐接口与检索接口字段的并集；两边都只保证 id。 */
export interface GallerySource {
  id: number
  name?: string | null
  thumbnailUrl?: string | null
  previewUrl?: string | null
  url?: string | null
  picWidth?: number | null
  picHeight?: number | null
  picScale?: number | null
  picColor?: string | null
  tags?: string[] | null
  quarantined?: boolean | null
}

export interface GalleryCard {
  id: number
  title: string
  imageUrl: string
  /** 宽高比（宽 / 高），用于排版；始终落在合理区间。 */
  aspect: number
  /** 双列布局下的图片高度（rpx）。 */
  heightRpx: number
  color: string
  tags: string[]
  /** 没有可用的内容入口，展示占位而不是破图。 */
  broken: boolean
}

export const GRID_COLUMN_WIDTH_RPX = 343
const MIN_ASPECT = 0.5
const MAX_ASPECT = 2
const MIN_HEIGHT_RPX = 260
const MAX_HEIGHT_RPX = 720
const DEFAULT_COLOR = 'rgba(148, 163, 184, 0.12)'

/**
 * 把接口条目转成卡片模型。
 *
 * `resolveUrl` 由调用方提供（页面注入了 API 域名），逻辑层因此不依赖运行环境。
 */
export function toCard(
  source: GallerySource,
  resolveUrl: (path: string) => string,
  columnWidthRpx: number = GRID_COLUMN_WIDTH_RPX,
): GalleryCard {
  const aspect = resolveAspect(source)
  const thumbnail = source.thumbnailUrl
  const imageUrl = typeof thumbnail === 'string' && thumbnail !== '' ? resolveUrl(thumbnail) : ''
  return {
    id: source.id,
    title: nonEmpty(source.name) || `作品 ${source.id}`,
    imageUrl,
    aspect,
    heightRpx: heightForAspect(aspect, columnWidthRpx),
    color: nonEmpty(source.picColor) || DEFAULT_COLOR,
    tags: (source.tags ?? []).filter((tag) => typeof tag === 'string' && tag !== ''),
    broken: imageUrl === '',
  }
}

/** 优先用后端算好的 picScale，缺失时退回宽高，仍缺失则给一个接近方形的默认值。 */
export function resolveAspect(source: Pick<GallerySource, 'picWidth' | 'picHeight' | 'picScale'>): number {
  const scale = source.picScale
  if (typeof scale === 'number' && Number.isFinite(scale) && scale > 0) {
    return clampAspect(scale)
  }
  const width = source.picWidth
  const height = source.picHeight
  if (typeof width === 'number' && typeof height === 'number' && width > 0 && height > 0) {
    return clampAspect(width / height)
  }
  return 1
}

export function clampAspect(aspect: number): number {
  if (!Number.isFinite(aspect) || aspect <= 0) {
    return 1
  }
  return Math.min(MAX_ASPECT, Math.max(MIN_ASPECT, aspect))
}

export function heightForAspect(aspect: number, columnWidthRpx: number = GRID_COLUMN_WIDTH_RPX): number {
  const height = columnWidthRpx / clampAspect(aspect)
  return Math.round(Math.min(MAX_HEIGHT_RPX, Math.max(MIN_HEIGHT_RPX, height)))
}

/** 计数展示：1 万以内用原值，超过用「万」，保留一位小数并去掉多余的 .0。 */
export function formatCount(value: number | string | null | undefined): string {
  const parsed = typeof value === 'string' ? Number(value) : value
  if (typeof parsed !== 'number' || !Number.isFinite(parsed) || parsed < 0) {
    return '0'
  }
  if (parsed < 10000) {
    return String(Math.floor(parsed))
  }
  const wan = parsed / 10000
  const text = wan >= 100 ? String(Math.round(wan)) : wan.toFixed(1)
  return `${text.replace(/\.0$/, '')}万`
}

/** 后端日期序列化格式不完全统一：兼容 ISO、`yyyy-MM-dd HH:mm:ss`、时间戳与 Date。 */
export function formatDate(value: string | number | Date | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const date = value instanceof Date ? value : parseDate(value)
  if (date === null) {
    return ''
  }
  const year = date.getFullYear()
  const month = pad2(date.getMonth() + 1)
  const day = pad2(date.getDate())
  return `${year}-${month}-${day}`
}

export function formatFileSize(bytes: number | string | null | undefined): string {
  const parsed = typeof bytes === 'string' ? Number(bytes) : bytes
  if (typeof parsed !== 'number' || !Number.isFinite(parsed) || parsed <= 0) {
    return ''
  }
  if (parsed < 1024) {
    return `${Math.round(parsed)} B`
  }
  if (parsed < 1024 * 1024) {
    return `${(parsed / 1024).toFixed(1)} KB`
  }
  return `${(parsed / (1024 * 1024)).toFixed(1)} MB`
}

function parseDate(value: string | number): Date | null {
  if (typeof value === 'number') {
    // 秒级时间戳兜底
    const millis = value < 1e12 ? value * 1000 : value
    const date = new Date(millis)
    return Number.isNaN(date.getTime()) ? null : date
  }
  const text = value.trim()
  const epoch = Date.parse(text)
  if (!Number.isNaN(epoch)) {
    return new Date(epoch)
  }
  // iOS 不认 `yyyy-MM-dd HH:mm:ss`：去掉毫秒/时区后统一换成 `/`
  const normalized = text
    .replace(/\.\d+$/, '')
    .replace('T', ' ')
    .replace(/Z$/, '')
    .replace(/-/g, '/')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

function nonEmpty(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : ''
}
