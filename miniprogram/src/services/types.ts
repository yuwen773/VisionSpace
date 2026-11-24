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

/** 已登录用户（脱敏）；Long 字段同样以字符串到达。 */
export interface LoginUserVO {
  id: WireLong
  userAccount: string | null
  userName: string | null
  userAvatar: string | null
  userProfile: string | null
  userRole: string | null
}

/** `/user/wx/login` 与 `/user/wx/bind` 的成功响应。 */
export interface WxLoginVO {
  token: string
  /** 请求头名；令牌只放在请求头里，服务端不接受 URL 形式的令牌 */
  tokenName: string
  user: LoginUserVO
}

/** `/space/list/page/vo` 的条目。`spaceType`：0 个人，1 团队。 */
export interface SpaceVO {
  id: WireLong
  spaceName: string | null
  spaceLevel: number | null
  spaceType: number | null
  status: number | null
  userId: WireLong | null
  user: PictureUser | null
  permissionList: string[] | null
}

export interface SpacePage {
  records: SpaceVO[] | null
  total: WireLong | null
  current: number | null
  size: number | null
  pages: number | null
}

/** `/spaceUser/list/my`：我在各空间里的成员关系（个人空间没有这条记录）。 */
export interface SpaceUserVO {
  id: WireLong
  spaceId: WireLong
  userId: WireLong
  spaceRole: string | null
  space?: SpaceVO | null
}
