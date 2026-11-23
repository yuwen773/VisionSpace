/**
 * 后端线格式类型。字段来源：
 * - RecommendPictureVO / RecommendPageVO
 * - PictureVO / Page<PictureVO>
 * - PictureTagCategory
 *
 * 注意：`JsonConfig` 把 Long 序列化成十进制字符串，所以 id / 计数 / 体积
 * 在运行时可能是字符串；`total` 同理。
 */
export type WireLong = number | string

export interface PictureTagCategory {
  tagList: string[]
  categoryList: string[]
}

export interface RecommendPicture {
  id: WireLong
  thumbnailUrl: string | null
  name: string | null
  picWidth: number | null
  picHeight: number | null
  picScale: number | null
  picColor: string | null
  tags: string[] | null
}

export interface RecommendPage {
  records: RecommendPicture[] | null
  total: WireLong | null
  context: string | null
  contextValid: boolean | null
}

export interface PictureUser {
  id: WireLong
  userName: string | null
  userAvatar: string | null
}

export interface PictureVO {
  id: WireLong
  url: string | null
  thumbnailUrl: string | null
  previewUrl: string | null
  name: string | null
  introduction: string | null
  tags: string[] | null
  category: string | null
  picSize: WireLong | null
  picWidth: number | null
  picHeight: number | null
  picScale: number | null
  picFormat: string | null
  picColor: string | null
  userId: WireLong | null
  spaceId: WireLong | null
  createTime: string | null
  user: PictureUser | null
  quarantined: boolean | null
  viewCount: WireLong | null
  likeCount: WireLong | null
  collectCount: WireLong | null
  downloadCount: WireLong | null
  shareCount: WireLong | null
}

export interface PicturePage {
  records: PictureVO[] | null
  total: WireLong | null
  current: number | null
  size: number | null
  pages: number | null
}
