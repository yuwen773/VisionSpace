import { applyPage, createFeed, type FeedState } from '../../logic/feed'
import { describeError } from '../../logic/errors'
import { toCard, type GalleryCard } from '../../logic/gallery'
import {
  ACTION_CLICK,
  ACTION_IMPRESSION,
  actionEvent,
  createReportGate,
  makeEventId,
  type AnonymousActionType,
} from '../../logic/telemetry'
import { resolveContentUrl } from '../../services/content'
import {
  fetchRecommend,
  fetchTagCategory,
  reportAction,
  searchPictures,
  type RecommendType,
} from '../../services/gallery'
import type { PictureVO, RecommendPicture } from '../../services/types'

const PAGE_SIZE = 12
const ALL_CATEGORIES = '全部'

type FeedRecord = RecommendPicture | PictureVO
type Mode = 'recommend' | 'search'

interface TabItem {
  key: RecommendType
  label: string
}

const TABS: TabItem[] = [
  { key: 'hot', label: '热门' },
  { key: 'latest', label: '最新' },
  { key: 'quality', label: '精选' },
  { key: 'random', label: '随机' },
]

function isTabKey(value: string): value is RecommendType {
  return TABS.some((tab) => tab.key === value)
}

Page({
  data: {
    tabs: TABS,
    activeTab: 'hot' as RecommendType,
    mode: 'recommend' as Mode,
    searchText: '',
    categories: [ALL_CATEGORIES] as string[],
    activeCategory: ALL_CATEGORIES,
    cards: [] as GalleryCard[],
    loading: false,
    loadingMore: false,
    error: '',
    ended: false,
    summary: '',
  },

  feed: createFeed<FeedRecord>(),
  gate: createReportGate(),
  inFlight: false,
  loadSeq: 0,
  observer: null as WechatMiniprogram.IntersectionObserver | null,

  onLoad() {
    this.loadCategories()
    this.loadPage(true)
  },

  onUnload() {
    if (this.observer) {
      this.observer.disconnect()
      this.observer = null
    }
  },

  onPullDownRefresh() {
    this.restart({}).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.inFlight || !this.feed.hasMore || this.data.error !== '') {
      return
    }
    this.loadPage(false)
  },

  /** 切换数据源（榜单/检索/分类）时整体重置；在途请求由 loadSeq 失效。 */
  restart(updates: Record<string, unknown>): Promise<void> {
    this.setData({ ...updates, cards: [], ended: false, error: '', summary: '' })
    return this.loadPage(true)
  },

  async loadPage(reset: boolean): Promise<void> {
    if (!reset && (this.inFlight || !this.feed.hasMore)) {
      return
    }
    if (reset) {
      this.loadSeq += 1
      this.inFlight = false
      this.feed = createFeed<FeedRecord>()
    }
    const seq = ++this.loadSeq
    const requestedPage = this.feed.nextPage
    const hasCards = this.data.cards.length > 0
    this.inFlight = true
    this.setData({ loading: !hasCards, loadingMore: hasCards, error: '' })

    try {
      let records: FeedRecord[]
      if (this.data.mode === 'recommend') {
        const response = await fetchRecommend(
          this.data.activeTab,
          requestedPage,
          PAGE_SIZE,
          this.feed.context,
        )
        if (seq !== this.loadSeq) {
          return
        }
        records = response.records ?? []
        this.feed = applyPage(this.feed, {
          records,
          // contextValid=false 表示令牌失效，服务端返回的是新一轮第一页
          page: response.contextValid === false ? 1 : requestedPage,
          pageSize: PAGE_SIZE,
          context: response.context,
          contextValid: response.contextValid,
          total: response.total,
        })
      } else {
        const category = this.data.activeCategory === ALL_CATEGORIES ? '' : this.data.activeCategory
        const response = await searchPictures({
          current: requestedPage,
          pageSize: PAGE_SIZE,
          searchText: this.data.searchText.trim(),
          category,
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
      }
      this.renderFeed()
    } catch (error) {
      if (seq !== this.loadSeq) {
        return
      }
      console.warn('图库加载失败', error)
      const message = error instanceof Error && error.message !== ''
        ? error.message
        : describeError(50000).message
      this.setData({ error: message })
    } finally {
      if (seq === this.loadSeq) {
        this.inFlight = false
        this.setData({ loading: false, loadingMore: false })
      }
    }
  },

  renderFeed(): void {
    const cards = this.feed.items.map((record) => toCard(record, resolveContentUrl))
    const total = this.feed.total
    this.setData(
      {
        cards,
        ended: !this.feed.hasMore && cards.length > 0,
        summary: this.data.mode === 'search' && total !== null ? `共 ${total} 件作品` : '',
      },
      () => this.observeImpressions(),
    )
  },

  /**
   * 曝光统计：滚到一半以上才记一次。disconnect 后重新 observe 是刷新节点集合的手段，
   * 已上报的条目由 gate 去重，不会重复请求。
   */
  observeImpressions(): void {
    if (!this.observer) {
      this.observer = this.createIntersectionObserver({ thresholds: [0.5], observeAll: true })
      this.observer.relativeToViewport({ bottom: 0 })
    }
    this.observer.disconnect()
    this.observer.observe('.impression-anchor', (result) => {
      if (result.intersectionRatio < 0.5) {
        return
      }
      const id = Number(result.dataset.pictureId)
      if (Number.isFinite(id) && id > 0) {
        this.report(id, ACTION_IMPRESSION)
      }
    })
  },

  report(pictureId: number, actionType: AnonymousActionType): void {
    if (!this.gate.tryAcquire(pictureId, actionType)) {
      return
    }
    const eventId = makeEventId(Date.now(), Math.random().toString(36).slice(2, 10))
    reportAction(actionEvent(pictureId, actionType, eventId)).catch((error) => {
      // 统计失败不影响浏览
      console.warn('行为上报失败', actionType, error)
    })
  },

  async loadCategories(): Promise<void> {
    try {
      const result = await fetchTagCategory()
      const list = (result.categoryList ?? []).filter(
        (item) => typeof item === 'string' && item.trim() !== '',
      )
      this.setData({ categories: [ALL_CATEGORIES, ...list] })
    } catch (error) {
      // 分类拉取失败只影响筛选入口，不影响榜单浏览
      console.warn('分类加载失败', error)
    }
  },

  onSearchInput(event: WechatMiniprogram.Input) {
    this.setData({ searchText: event.detail.value })
  },

  onSearchConfirm() {
    const searchText = this.data.searchText.trim()
    this.applyFilter({ searchText })
  },

  onClearSearch() {
    if (this.data.searchText === '') {
      return
    }
    this.applyFilter({ searchText: '' })
  },

  onCategoryTap(event: WechatMiniprogram.TouchEvent) {
    const category = String(event.currentTarget.dataset.category ?? '')
    if (category === '' || category === this.data.activeCategory) {
      return
    }
    this.applyFilter({ activeCategory: category })
  },

  onTabTap(event: WechatMiniprogram.TouchEvent) {
    const key = String(event.currentTarget.dataset.key ?? '')
    if (!isTabKey(key)) {
      return
    }
    const alreadyActive =
      key === this.data.activeTab &&
      this.data.mode === 'recommend' &&
      this.data.searchText === '' &&
      this.data.activeCategory === ALL_CATEGORIES
    if (alreadyActive) {
      return
    }
    // 切榜单即清空筛选，回到推荐流
    this.setData({
      activeTab: key,
      searchText: '',
      activeCategory: ALL_CATEGORIES,
      mode: 'recommend',
    })
    this.restart({})
  },

  /** 关键词与分类任一成立就走检索接口，否则回到当前榜单。 */
  applyFilter(updates: { searchText?: string; activeCategory?: string }) {
    const searchText = updates.searchText ?? this.data.searchText
    const activeCategory = updates.activeCategory ?? this.data.activeCategory
    const filtering = searchText.trim() !== '' || activeCategory !== ALL_CATEGORIES
    const mode: Mode = filtering ? 'search' : 'recommend'
    this.setData({ ...updates, searchText, activeCategory, mode })
    this.restart({})
  },

  onCardTap(event: WechatMiniprogram.CustomEvent<{ id: number }>) {
    const id = Number(event.detail.id)
    if (!Number.isFinite(id) || id <= 0) {
      return
    }
    this.report(id, ACTION_CLICK)
    wx.navigateTo({ url: `/pages/picture/index?id=${id}` })
  },

  onRetry() {
    this.loadPage(this.feed.items.length === 0)
  },
})
