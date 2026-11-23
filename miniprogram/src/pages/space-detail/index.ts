import { COLOR_PALETTE, normalizeHex } from '../../logic/color'
import { errorMessage, isLoginExpired } from '../../logic/errors'
import { applyPage, createFeed } from '../../logic/feed'
import { toCard, type GalleryCard } from '../../logic/gallery'
import { spaceTypeLabel } from '../../logic/spaces'
import { resolveContentUrl } from '../../services/content'
import { searchByColor, searchPictures } from '../../services/gallery'
import { pickImageColor } from '../../services/image-color'
import { openLogin } from '../../services/navigation'
import { getToken } from '../../services/session'
import { ApiError } from '../../services/http'
import type { PictureVO } from '../../services/types'

const PAGE_SIZE = 12

type SpaceRecord = PictureVO

Page({
  data: {
    id: 0,
    name: '',
    typeText: '',
    needsLogin: false,
    loading: true,
    error: '',
    cards: [] as GalleryCard[],
    loadingMore: false,
    ended: false,
    total: null as number | null,
    palette: [...COLOR_PALETTE] as string[],
    filterColor: '',
    colorMode: false,
    colorLoading: false,
    colorError: '',
    colorCards: [] as GalleryCard[],
    colorCount: 0,
  },

  feed: createFeed<SpaceRecord>(),
  inFlight: false,
  loadSeq: 0,
  colorSeq: 0,

  onLoad(options: Record<string, string | undefined>) {
    const id = Number(options.id)
    if (!Number.isFinite(id) || id <= 0) {
      this.setData({ loading: false, error: '空间不存在' })
      return
    }
    const name = options.name ? decodeURIComponent(options.name) : '空间'
    // 类型由列表页带过来；空间详情接口对普通成员不开放，没必要为一行文案多打一次请求
    const spaceType = options.type === undefined ? undefined : Number(options.type)
    const typeText = spaceType === undefined || !Number.isFinite(spaceType)
      ? ''
      : spaceTypeLabel(spaceType)
    this.setData({ id, name, typeText })
    wx.setNavigationBarTitle({ title: name })
    this.load()
  },

  onShow() {
    // 从登录页返回后自动重试一次
    if (this.data.needsLogin && getToken() !== '') {
      this.load()
    }
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.colorMode || this.inFlight || !this.feed.hasMore || this.data.error !== '') {
      return
    }
    this.loadPage(false)
  },

  async load(): Promise<void> {
    if (getToken() === '') {
      this.setData({ loading: false, needsLogin: true, error: '', cards: [], ended: false })
      return
    }
    this.setData({ loading: true, needsLogin: false, error: '' })
    // 刷新空间时一并清掉颜色筛选，避免旧结果残留在新列表上
    this.resetColorFilter()
    await this.loadPage(true)
  },

  /** 清空颜色筛选状态；会作废正在进行中的颜色请求。 */
  resetColorFilter(): void {
    this.colorSeq += 1
    this.setData({
      colorMode: false,
      filterColor: '',
      colorLoading: false,
      colorError: '',
      colorCards: [],
      colorCount: 0,
    })
  },

  async loadPage(reset: boolean): Promise<void> {
    if (!reset && (this.inFlight || !this.feed.hasMore)) {
      return
    }
    if (reset) {
      this.loadSeq += 1
      this.inFlight = false
      this.feed = createFeed<SpaceRecord>()
    }
    const seq = ++this.loadSeq
    const requestedPage = this.feed.nextPage
    const hasCards = this.data.cards.length > 0
    this.inFlight = true
    this.setData({ loading: !hasCards, loadingMore: hasCards, error: '' })
    try {
      const response = await searchPictures({
        spaceId: this.data.id,
        current: requestedPage,
        pageSize: PAGE_SIZE,
      })
      if (seq !== this.loadSeq) {
        return
      }
      this.feed = applyPage(this.feed, {
        records: response.records ?? [],
        page: response.current ?? requestedPage,
        pageSize: PAGE_SIZE,
        total: response.total,
      })
      const cards = this.feed.items.map((record) => toCard(record, this.tokenizedUrl))
      this.setData({
        cards,
        total: this.feed.total,
        ended: !this.feed.hasMore && cards.length > 0,
      })
    } catch (error) {
      if (seq !== this.loadSeq) {
        return
      }
      console.warn('空间图片加载失败', error)
      // 令牌在加载过程中失效：http 层已清理本地令牌，这里退回登录引导
      if (error instanceof ApiError && isLoginExpired(error.code)) {
        this.setData({
          needsLogin: true,
          error: '',
          cards: [],
          ended: false,
          loading: false,
          loadingMore: false,
        })
        return
      }
      this.setData({ error: errorMessage(error) })
    } finally {
      if (seq === this.loadSeq) {
        this.inFlight = false
        this.setData({ loading: false, loadingMore: false })
      }
    }
  },

  /** 空间图片的内容接口需要令牌，`<image>` 只能把令牌放查询参数。 */
  tokenizedUrl(path: string): string {
    return resolveContentUrl(path, true)
  },

  onSwatchTap(event: WechatMiniprogram.TouchEvent) {
    const color = String(event.currentTarget.dataset.color ?? '')
    this.searchColor(color)
  },

  onPickColor() {
    wx.showLoading({ title: '取色中…', mask: true })
    pickImageColor()
      .then((color) => this.searchColor(color))
      .catch((error: Error) => {
        if (error.message !== '已取消') {
          wx.showToast({ title: error.message, icon: 'none' })
        }
      })
      .finally(() => wx.hideLoading())
  },

  searchColor(rawColor: string): void {
    const color = normalizeHex(rawColor)
    if (color === null) {
      wx.showToast({ title: '颜色格式不正确', icon: 'none' })
      return
    }
    const seq = ++this.colorSeq
    this.setData({
      filterColor: color,
      colorMode: true,
      colorLoading: true,
      colorError: '',
    })
    searchByColor(this.data.id, color)
      .then((records) => {
        if (seq !== this.colorSeq) {
          return
        }
        const cards = records.map((record) => toCard(record, this.tokenizedUrl))
        this.setData({ colorCards: cards, colorCount: cards.length, colorLoading: false })
      })
      .catch((error) => {
        if (seq !== this.colorSeq) {
          return
        }
        console.warn('颜色检索失败', error)
        this.setData({ colorError: errorMessage(error), colorLoading: false })
      })
  },

  onClearColor() {
    this.colorSeq += 1
    this.setData({
      colorMode: false,
      filterColor: '',
      colorLoading: false,
      colorError: '',
      colorCards: [],
      colorCount: 0,
    })
  },

  onLoginTap() {
    openLogin()
  },

  onCardTap(event: WechatMiniprogram.CustomEvent<{ id: number }>) {
    const id = Number(event.detail.id)
    if (!Number.isFinite(id) || id <= 0) {
      return
    }
    wx.navigateTo({ url: `/pages/picture/index?id=${id}` })
  },

  onRetry() {
    this.load()
  },
})
