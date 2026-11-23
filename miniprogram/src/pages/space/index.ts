import { ApiError } from '../../services/http'
import { createSpace, fetchMyMemberships, fetchMySpaces } from '../../services/space'
import { getStoredUser, getToken } from '../../services/session'
import { errorMessage } from '../../logic/errors'
import {
  defaultPersonalSpaceName,
  hasPersonalSpace,
  mergeSpaceSummaries,
  roleLabel,
  type SpaceSummary,
} from '../../logic/spaces'

interface SpaceCard extends SpaceSummary {
  roleText: string
  typeText: string
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
      let page = await fetchMySpaces()
      let records = page.records ?? []
      if (!hasPersonalSpace(records)) {
        await this.ensurePersonalSpace()
        page = await fetchMySpaces()
        records = page.records ?? []
      }
      const memberships = await fetchMyMemberships()
      if (seq !== this.loadSeq) {
        return
      }
      const spaces: SpaceCard[] = mergeSpaceSummaries(records, memberships).map((space) => ({
        ...space,
        roleText: roleLabel(space, currentUserId),
        typeText: space.isPersonal ? '个人' : '团队',
      }))
      this.setData({ spaces, loading: false })
    } catch (error) {
      if (seq !== this.loadSeq) {
        return
      }
      console.warn('空间列表加载失败', error)
      // 令牌被后端判定失效时 http 层已清理本地令牌，这里退回未登录态
      if (error instanceof ApiError && error.code === 40100) {
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
   * 注册不会创建空间：列表里没有个人空间时按计划自动建一个。
   * 唯一索引保证每人每类一个，重复创建返回 50001，这里视为已存在。
   */
  async ensurePersonalSpace(): Promise<void> {
    try {
      await createSpace(defaultPersonalSpaceName(this.data.userName), 0)
    } catch (error) {
      if (error instanceof ApiError && error.code === 50001) {
        return
      }
      throw error
    }
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  onSpaceTap(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    const name = String(event.currentTarget.dataset.name ?? '')
    if (!Number.isFinite(id) || id <= 0) {
      return
    }
    wx.navigateTo({
      url: `/pages/space-detail/index?id=${id}&name=${encodeURIComponent(name)}`,
    })
  },

  onRetry() {
    this.load()
  },
})
