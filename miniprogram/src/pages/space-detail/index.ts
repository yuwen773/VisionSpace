import { COLOR_PALETTE, normalizeHex } from '../../logic/color'
import { errorMessage, isLoginExpired } from '../../logic/errors'
import { applyPage, createFeed } from '../../logic/feed'
import {
  toPrivateCard,
  withDownloadFailure,
  withLocalPath,
  withPendingDownload,
  type GalleryCard,
} from '../../logic/gallery'
import { isValidId } from '../../logic/long'
import { spaceTypeLabel } from '../../logic/spaces'
import { resolveContentUrl } from '../../services/content'
import { searchByColor, searchPictures } from '../../services/gallery'
import { pickImageColor } from '../../services/image-color'
import { imageQueue, resolveImageFailure } from '../../services/image-download'
import { openLogin } from '../../services/navigation'
import { getToken } from '../../services/session'
import { ApiError } from '../../services/http'
import { consumeSpaceUploaded } from '../../services/upload-signal'
import type { PictureVO } from '../../services/types'

const PAGE_SIZE = 12

type SpaceRecord = PictureVO

Page({
  data: {
    /** 雪花 id 字符串：转 number 会丢精度，导致查不到空间。 */
    id: '',
    name: '',
    typeText: '',
    canUpload: false,
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
    const id = String(options.id ?? '')
    if (!isValidId(id)) {
      this.setData({ loading: false, error: '空间不存在' })
      return
    }
    const name = options.name ? decodeURIComponent(options.name) : '空间'
    // 类型由列表页带过来；空间详情接口对普通成员不开放，没必要为一行文案多打一次请求
    const spaceType = options.type === undefined ? undefined : Number(options.type)
    const typeText = spaceType === undefined || !Number.isFinite(spaceType)
      ? ''
      : spaceTypeLabel(spaceType)
    const canUpload = options.canUpload === '1'
    this.setData({ id, name, typeText, canUpload })
    wx.setNavigationBarTitle({ title: name })
    this.load()
  },

  onShow() {
    // 从登录页返回后自动重试一次；上传页成功后回到本页也要刷新列表
    if (this.data.needsLogin && getToken() !== '') {
      this.load()
      return
    }
    if (consumeSpaceUploaded(this.data.id)) {
      this.load()
      return
    }
    this.revalidateImages()
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
      const cards = this.privateCards(this.feed.items)
      this.setData({
        cards,
        total: this.feed.total,
        ended: !this.feed.hasMore && cards.length > 0,
      })
      this.loadPrivateImages(cards, 'cards')
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

  /** 空间图片的内容接口需要令牌，令牌放在请求头里，地址本身不带凭据。 */
  contentUrl(path: string): string {
    return resolveContentUrl(path)
  },

  /**
   * 私有图片卡片：下载过的直接用缓存路径，没下载过的先占位。
   *
   * 翻页时列表会整体重建，不复用缓存就会让已下载的图片重新闪一次占位。
   */
  privateCards(records: SpaceRecord[]): GalleryCard[] {
    return records.map((record) => {
      const card = toPrivateCard(record, this.contentUrl)
      const cached = card.imageUrl === '' ? '' : imageQueue.cached(card.imageUrl)
      return cached === '' ? card : withLocalPath(card, cached)
    })
  },

  /**
   * 下载私有图片并回填本地路径。
   *
   * 下载走全局队列（限流 + 去重），列表与颜色检索的结果都写回各自的卡片数组。
   */
  loadPrivateImages(cards: GalleryCard[], field: 'cards' | 'colorCards'): void {
    cards.forEach((card) => {
      if (!card.pending || card.imageUrl === '') {
        return
      }
      imageQueue.request(card.imageUrl).then(
        (path) => this.patchCard(field, card.id, withLocalPath(card, path)),
        (error: unknown) => {
          // 401/403 的分流（令牌过期 vs 单张撤权）在服务里统一判断
          resolveImageFailure(card.imageUrl, error).then((action) => {
            if (action.needsLogin) {
              this.setData({ needsLogin: true })
            }
            this.patchCard(field, card.id, withDownloadFailure(card))
          }, () => this.patchCard(field, card.id, withDownloadFailure(card)))
        },
      )
    })
  },

  /**
   * 复核已展示的私有图片：缓存过期或换了账号就重新鉴权。
   *
   * 队列的 TTL 只在读取缓存时生效，页面停着不动时卡片里仍留着旧本地路径。
   * 这里在页面重新可见时把过期的那几张复位成「下载中」，走同一条鉴权下载路径；
   * 撤权因此最晚在一个 TTL 之后生效，而不是一直显示旧图。
   */
  revalidateImages(): void {
    const fields: Array<'cards' | 'colorCards'> = ['cards', 'colorCards']
    fields.forEach((field) => {
      const list = this.data[field] as GalleryCard[]
      const stale = list
        .filter((card) => card.localPath !== '' && imageQueue.cached(card.imageUrl) === '')
        .map(withPendingDownload)
      if (stale.length === 0) {
        return
      }
      stale.forEach((card) => this.patchCard(field, card.id, card))
      this.loadPrivateImages(stale, field)
    })
  },

  /** 按 id 替换一张卡片；找不到就忽略（页面可能已经切走）。 */
  patchCard(field: 'cards' | 'colorCards', id: string, next: GalleryCard): void {
    const list = this.data[field] as GalleryCard[]
    const index = list.findIndex((card) => card.id === id)
    if (index < 0) {
      return
    }
    this.setData({ [`${field}[${index}]`]: next })
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
        const cards = this.privateCards(records)
        this.setData({ colorCards: cards, colorCount: cards.length, colorLoading: false })
        this.loadPrivateImages(cards, 'colorCards')
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

  onUploadTap() {
    if (getToken() === '') {
      openLogin()
      return
    }
    wx.navigateTo({
      url: `/pages/upload/index?spaceId=${this.data.id}&spaceName=${encodeURIComponent(
        this.data.name,
      )}`,
    })
  },

  onCardTap(event: WechatMiniprogram.CustomEvent<{ id: string }>) {
    const id = String(event.detail.id ?? '')
    if (!isValidId(id)) {
      return
    }
    wx.navigateTo({ url: `/pages/picture/index?id=${id}` })
  },

  onRetry() {
    this.load()
  },
})
