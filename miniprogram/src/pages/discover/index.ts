import { applyPage, createFeed } from '../../logic/feed'
import { errorMessage } from '../../logic/errors'
import { GRID_COLUMN_WIDTH_RPX, toCard, type GalleryCard } from '../../logic/gallery'
import { isValidId } from '../../logic/long'
import { ACTION_CLICK, ACTION_IMPRESSION, createReportGate } from '../../logic/telemetry'
import { resolveContentUrl } from '../../services/content'
import {
  fetchRecommend,
  fetchTagCategory,
  RECOMMEND_TYPES,
  searchPictures,
  type RecommendType,
} from '../../services/gallery'
import { reportOnce } from '../../services/telemetry'
import type { PictureVO, RecommendPicture } from '../../services/types'

const PAGE_SIZE = 12
const ALL_CATEGORIES = '全部'

type GalleryRecord = RecommendPicture | PictureVO
type Mode = 'recommend' | 'search'

const TAB_LABELS: Record<RecommendType, string> = {
  hot: '热门',
  latest: '最新',
  quality: '精选',
  random: '随机',
}

const TABS = RECOMMEND_TYPES.map((key) => ({ key, label: TAB_LABELS[key] }))

function isTabKey(value: string): value is RecommendType {
  return (RECOMMEND_TYPES as readonly string[]).includes(value)
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
    /** 双列图版宽度（rpx），与 logic/gallery 的常量同源 */
    columnWidthRpx: GRID_COLUMN_WIDTH_RPX,
    loading: false,
    loadingMore: false,
    error: '',
    ended: false,
    summary: '',
  },

  feed: createFeed<GalleryRecord>(),
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
      this.feed = createFeed<GalleryRecord>()
    }
    const seq = ++this.loadSeq
    const requestedPage = this.feed.nextPage
    const hasCards = this.data.cards.length > 0
    this.inFlight = true
    this.setData({ loading: !hasCards, loadingMore: hasCards, error: '' })

    try {
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
        this.feed = applyPage(this.feed, {
          records: response.records ?? [],
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
      this.setData({ error: errorMessage(error) })
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
      const id = String(result.dataset.pictureId ?? '')
      if (isValidId(id)) {
        reportOnce(this.gate, id, ACTION_IMPRESSION)
      }
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
    this.applyFilter({ searchText: this.data.searchText.trim() })
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

  onCardTap(event: WechatMiniprogram.CustomEvent<{ id: string }>) {
    const id = String(event.detail.id ?? '')
    if (!isValidId(id)) {
      return
    }
    reportOnce(this.gate, id, ACTION_CLICK)
    wx.navigateTo({ url: `/pages/picture/index?id=${id}` })
  },

  onRetry() {
    this.loadPage(this.feed.items.length === 0)
  },
})
