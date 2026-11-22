import { contentSrc } from '@/utils'

/**
 * 卡片里一张图片的两个决定：用哪张图、占多高。
 *
 * 这两条规则此前在探索页、空间展厅、用户中心各写一遍——宽高比是逐字相同的三份，
 * 缩略图回落写了四处。规则本身很短，但错了没人会发现：回落忘了走 `contentSrc`
 * 就是一张 403 的图，宽高比忘了兜底就是瀑布流里的零高度卡片。
 */
export interface PictureCardImage {
  thumbnailUrl?: string | null
  url?: string | null
}

export interface PictureCardBox {
  picWidth?: number | null
  picHeight?: number | null
}

/** 优先缩略图，没有才回落原图；受控内容入口的地址前缀仍由 `contentSrc` 统一加。 */
export function pictureImageSrc(picture: PictureCardImage): string {
  return contentSrc(picture.thumbnailUrl || picture.url)
}

/** 图片就位前的占位高度：按原图宽高算，缺宽高时按 4:3 兜底。 */
export function pictureAspectRatio(picture: PictureCardBox): string {
  if (!picture.picWidth || !picture.picHeight) return '75%'
  return `${(picture.picHeight / picture.picWidth) * 100}%`
}
