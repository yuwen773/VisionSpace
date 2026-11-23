/**
 * 后端线格式类型。字段来源：
 * - RecommendPictureVO / RecommendPageVO
 * - PictureVO / Page<PictureVO>
 * - PictureTagCategory
 *
 * 注意：后端把 Long 序列化成十进制字符串，`total` 是 `number | string`。
 */
export interface PictureTagCategory {
  tagList: string[]
  categoryList: string[]
}

export interface RecommendPicture {
  id: number
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
  total: number | string | null
  context: string | null
  contextValid: boolean | null
}

export interface PictureUser {
  id: number
  userName: string | null
  userAvatar: string | null
}

export interface PictureVO {
  id: number
  url: string | null
  thumbnailUrl: string | null
  previewUrl: string | null
  name: string | null
  introduction: string | null
  tags: string[] | null
  category: string | null
  picSize: number | null
  picWidth: number | null
  picHeight: number | null
  picScale: number | null
  picFormat: string | null
  picColor: string | null
  userId: number | null
  spaceId: number | null
  createTime: string | null
  user: PictureUser | null
  quarantined: boolean | null
  viewCount: number | null
  likeCount: number | null
  collectCount: number | null
  downloadCount: number | null
  shareCount: number | null
}

export interface PicturePage {
  records: PictureVO[] | null
  total: number | string | null
  current: number | null
  size: number | null
  pages: number | null
}
