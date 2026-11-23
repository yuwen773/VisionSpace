import { COLOR_PALETTE, normalizeHex } from '../../logic/color'
import { errorMessage } from '../../logic/errors'
import { applyPage, createFeed } from '../../logic/feed'
import { toCard, type GalleryCard } from '../../logic/gallery'
import { resolveContentUrl } from '../../services/content'
import { searchByColor, searchPictures } from '../../services/gallery'
import { pickImageColor } from '../../services/image-color'
import { getToken } from '../../services/session'
import { fetchSpaceDetail } from '../../services/space'
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
    this.setData({ id, name })
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
      this.setData({ loading: false, needsLogin: true, error: '' })
      return
    }
    this.setData({ loading: true, needsLogin: false, error: '' })
    // 刷新空间时一并清掉颜色筛选，避免旧结果残留在新列表上
    this.colorSeq += 1
    this.setData({
      colorMode: false,
      filterColor: '',
      colorLoading: false,
      colorError: '',
      colorCards: [],
      colorCount: 0,
    })
    try {
      const space = await fetchSpaceDetail(this.data.id)
      const name = (space.spaceName ?? '').trim() || this.data.name
      this.setData({ name, typeText: space.spaceType === 1 ? '团队空间' : '个人空间' })
      wx.setNavigationBarTitle({ title: name })
      await this.loadPage(true)
    } catch (error) {
      console.warn('空间详情加载失败', error)
      this.setData({ loading: false, error: errorMessage(error) })
    }
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
    wx.navigateTo({ url: '/pages/login/index' })
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
