/**
 * 图库分页状态机（纯逻辑，不依赖 wx API，可在 node 下单测）。
 *
 * 两种数据源共用一份状态：
 * - 榜单：`GET /picture/recommend/list`，服务端用 context 令牌保证翻页稳定；
 *   `contextValid=false` 表示令牌已失效，返回的是新一轮第一页，必须整体重置。
 * - 检索：`POST /picture/list/page/vo`，按 current/total 翻页。
 */
export interface FeedRecord {
  id: number
}

export interface FeedState<T extends FeedRecord = FeedRecord> {
  items: T[]
  nextPage: number
  context: string | null
  total: number | null
  hasMore: boolean
}

export interface PagePayload<T extends FeedRecord> {
  records: T[]
  page: number
  pageSize: number
  context?: string | null
  contextValid?: boolean | null
  total?: number | string | null
}

export function createFeed<T extends FeedRecord>(): FeedState<T> {
  return { items: [], nextPage: 1, context: null, total: null, hasMore: true }
}

/**
 * 接受一页数据，返回新状态（不修改入参）。
 *
 * - `page <= 1` 或 `contextValid === false` 时视为重置，丢弃旧条目；
 * - 其余情况追加，并按 id 去重（随机榜单可能跨页重复）；
 * - 只有「本页拿满」且「总数未知或尚未取完」时才认为还有下一页。
 */
export function applyPage<T extends FeedRecord>(
  state: FeedState<T>,
  payload: PagePayload<T>,
): FeedState<T> {
  const reset = payload.page <= 1 || payload.contextValid === false
  const items = mergeUnique(reset ? [] : state.items, payload.records)
  const total = normalizeTotal(payload.total)
  const pageFull = payload.records.length >= payload.pageSize
  const hasMore = pageFull && (total === null || items.length < total)
  return {
    items,
    nextPage: reset ? 2 : payload.page + 1,
    context: payload.context == null ? state.context : payload.context,
    total: total === null ? state.total : total,
    hasMore,
  }
}

/** 按 id 去重后追加，保持已有顺序，再按返回顺序追加新条目。 */
export function mergeUnique<T extends FeedRecord>(existing: readonly T[], incoming: readonly T[]): T[] {
  const seen = new Set<number>()
  const merged: T[] = []
  for (const record of existing) {
    if (!seen.has(record.id)) {
      seen.add(record.id)
      merged.push(record)
    }
  }
  for (const record of incoming) {
    if (!seen.has(record.id)) {
      seen.add(record.id)
      merged.push(record)
    }
  }
  return merged
}

/**
 * 后端把 Long 统一序列化成十进制字符串，且推荐接口的 total 可能是无效值。
 * 解析失败一律返回 null，绝不退化成 0（0 会被误判成「没有更多」）。
 */
export function normalizeTotal(value: number | string | null | undefined): number | null {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value >= 0 ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null
  }
  return null
}
