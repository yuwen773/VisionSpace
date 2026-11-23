import { describe, expect, it } from 'vitest'
import { applyPage, createFeed, mergeUnique, normalizeTotal } from '../src/logic/feed'

interface Item {
  id: number
}

function item(id: number): Item {
  return { id }
}

describe('createFeed', () => {
  it('starts empty and ready for page 1', () => {
    expect(createFeed<Item>()).toEqual({
      items: [],
      nextPage: 1,
      context: null,
      total: null,
      hasMore: true,
    })
  })
})

describe('applyPage', () => {
  it('appends a full second page in order and advances nextPage', () => {
    const first = applyPage(createFeed<Item>(), {
      records: [item(1), item(2)],
      page: 1,
      pageSize: 2,
    })
    const second = applyPage(first, {
      records: [item(3), item(4)],
      page: 2,
      pageSize: 2,
    })
    expect(second.items.map((entry) => entry.id)).toEqual([1, 2, 3, 4])
    expect(second.nextPage).toBe(3)
    expect(second.hasMore).toBe(true)
  })

  it('keeps paging after a partial page when the total is unknown', () => {
    // 后端会在页内过滤隔离/删除的记录，拿不满不代表到底
    const state = applyPage(createFeed<Item>(), {
      records: [item(1)],
      page: 1,
      pageSize: 2,
    })
    expect(state.hasMore).toBe(true)
  })

  it('stops when a page is empty', () => {
    const first = applyPage(createFeed<Item>(), {
      records: [item(1)],
      page: 1,
      pageSize: 2,
    })
    const second = applyPage(first, {
      records: [],
      page: 2,
      pageSize: 2,
    })
    expect(second.hasMore).toBe(false)
    expect(second.items).toHaveLength(1)
  })

  it('stops when accumulated items reach the reported total', () => {
    const state = applyPage(createFeed<Item>(), {
      records: [item(1), item(2)],
      page: 1,
      pageSize: 2,
      total: '2',
    })
    expect(state.total).toBe(2)
    expect(state.hasMore).toBe(false)
  })

  it('drops duplicates across pages', () => {
    const first = applyPage(createFeed<Item>(), {
      records: [item(1), item(2)],
      page: 1,
      pageSize: 2,
    })
    const second = applyPage(first, {
      records: [item(2), item(3)],
      page: 2,
      pageSize: 2,
    })
    expect(second.items.map((entry) => entry.id)).toEqual([1, 2, 3])
  })

  it('resets when the server says the context expired', () => {
    const stale = applyPage(createFeed<Item>(), {
      records: [item(1), item(2)],
      page: 1,
      pageSize: 2,
      context: 'old',
    })
    const refreshed = applyPage(stale, {
      records: [item(7), item(8)],
      page: 3,
      pageSize: 2,
      context: 'new',
      contextValid: false,
    })
    expect(refreshed.items.map((entry) => entry.id)).toEqual([7, 8])
    expect(refreshed.nextPage).toBe(2)
    expect(refreshed.context).toBe('new')
  })

  it('keeps the previous context when the payload omits it', () => {
    const first = applyPage(createFeed<Item>(), {
      records: [item(1)],
      page: 1,
      pageSize: 1,
      context: 'token-1',
    })
    const second = applyPage(first, {
      records: [item(2)],
      page: 2,
      pageSize: 1,
    })
    expect(second.context).toBe('token-1')
  })

  it('never coerces an invalid total to zero', () => {
    const state = applyPage(createFeed<Item>(), {
      records: [item(1)],
      page: 1,
      pageSize: 2,
      total: 'not-a-number',
    })
    expect(state.total).toBeNull()
    // 总数未知时仍按「本页有数据」继续翻页，避免把无效 total 当成到底
    expect(state.hasMore).toBe(true)
  })
})

describe('mergeUnique', () => {
  it('preserves existing order then appends new entries', () => {
    expect(mergeUnique([item(1), item(2)], [item(3), item(1)])).toEqual([item(1), item(2), item(3)])
  })
})

describe('normalizeTotal', () => {
  it('accepts safe non-negative integers from numbers and strings', () => {
    expect(normalizeTotal(0)).toBe(0)
    expect(normalizeTotal('128')).toBe(128)
  })

  it('rejects negatives, floats and blanks', () => {
    expect(normalizeTotal(-1)).toBeNull()
    expect(normalizeTotal(1.5)).toBeNull()
    expect(normalizeTotal('')).toBeNull()
    expect(normalizeTotal(null)).toBeNull()
    expect(normalizeTotal(undefined)).toBeNull()
  })
})
