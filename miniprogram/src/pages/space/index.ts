import { ApiError } from '../../services/http'
import { createSpace, fetchMyMemberships, fetchMySpaces, hasSpaceOfType } from '../../services/space'
import { getStoredUser, getToken } from '../../services/session'
import { openLogin } from '../../services/navigation'
import { errorMessage, isLoginExpired } from '../../logic/errors'
import {
  canUploadToSpace,
  defaultPersonalSpaceName,
  mergeSpaceSummaries,
  PERSONAL_SPACE_TYPE,
  roleLabel,
  spaceTypeLabel,
  type SpaceSummary,
} from '../../logic/spaces'

interface SpaceCard extends SpaceSummary {
  roleText: string
  typeText: string
  canUpload: boolean
}

Page({
  data: {
    loggedIn: false,
    userName: '',
    spaces: [] as SpaceCard[],
    loading: true,
    error: '',
  },

  inFlight: false,
  loadSeq: 0,

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load(): Promise<void> {
    const token = getToken()
    if (token === '') {
      this.setData({ loggedIn: false, loading: false, spaces: [], error: '' })
      return
    }
    const user = getStoredUser()
    this.setData({ loggedIn: true, userName: user?.userName || '我' })
    await this.loadSpaces(user?.id ?? null)
  },

  async loadSpaces(currentUserId: number | null): Promise<void> {
    if (this.inFlight) {
      return
    }
    const seq = ++this.loadSeq
    this.inFlight = true
    this.setData({ loading: this.data.spaces.length === 0, error: '' })
    try {
      await this.ensurePersonalSpace()
      const [page, memberships] = await Promise.all([fetchMySpaces(), fetchMyMemberships()])
      if (seq !== this.loadSeq) {
        return
      }
      const spaces: SpaceCard[] = mergeSpaceSummaries(page.records ?? [], memberships).map(
        (space) => ({
          ...space,
          roleText: roleLabel(space, currentUserId),
          typeText: spaceTypeLabel(space.spaceType, true),
          canUpload: canUploadToSpace(space, currentUserId),
        }),
      )
      this.setData({ spaces, loading: false })
    } catch (error) {
      if (seq !== this.loadSeq) {
        return
      }
      console.warn('空间列表加载失败', error)
      // 令牌被后端判定失效时 http 层已清理本地令牌，这里退回未登录态
      if (error instanceof ApiError && isLoginExpired(error.code)) {
        this.setData({ loggedIn: false, loading: false, spaces: [], error: '' })
        return
      }
      this.setData({ loading: false, error: errorMessage(error) })
    } finally {
      if (seq === this.loadSeq) {
        this.inFlight = false
      }
    }
  },

  /**
   * 注册不会创建空间：没有个人空间时按计划自动建一个。
   *
   * 存在性用服务端 spaceType 过滤判断，只取一行，不受「第一页最多 20 条」影响。
   * 唯一索引保证每人每类一个，并发/重复创建返回 50001；建完复查一次，
   * 复查仍不存在才算失败，避免把真实错误当成「已存在」吞掉。
   */
  async ensurePersonalSpace(): Promise<void> {
    if (await hasSpaceOfType(PERSONAL_SPACE_TYPE)) {
      return
    }
    try {
      await createSpace(defaultPersonalSpaceName(this.data.userName), PERSONAL_SPACE_TYPE)
    } catch (error) {
      if (!(error instanceof ApiError && error.code === 50001)) {
        throw error
      }
    }
    if (!(await hasSpaceOfType(PERSONAL_SPACE_TYPE))) {
      throw new Error('个人空间创建失败，请稍后重试')
    }
  },

  onLoginTap() {
    openLogin()
  },

  onSpaceTap(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    const name = String(event.currentTarget.dataset.name ?? '')
    const spaceType = Number(event.currentTarget.dataset.type)
    const upload = event.currentTarget.dataset.upload
    const canUpload = upload === true || upload === 'true'
    if (!Number.isFinite(id) || id <= 0) {
      return
    }
    const typeQuery = Number.isFinite(spaceType) ? `&type=${spaceType}` : ''
    const uploadQuery = canUpload ? '&canUpload=1' : ''
    wx.navigateTo({
      url: `/pages/space-detail/index?id=${id}${typeQuery}${uploadQuery}&name=${encodeURIComponent(name)}`,
    })
  },

  onRetry() {
    this.load()
  },
})
