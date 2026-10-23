import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getRecommendListUsingGet } = vi.hoisted(() => ({ getRecommendListUsingGet: vi.fn() }))

vi.mock('@/api-custom/picture', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api-custom/picture')>()
  return { ...actual, getRecommendListUsingGet }
})

import { useRecommendPaging } from '@/composables/useRecommendPaging'

/**
 * 页面翻页：分页上下文由服务端给出，页面翻页时原样带回，
 * 翻完的结果与真实 HTTP 的返回一致。
 */
describe('recommend paging', () => {
  beforeEach(() => {
    getRecommendListUsingGet.mockReset()
  })

  it('第一次请求不带上下文，翻页时带上服务端给出的上下文', async () => {
    getRecommendListUsingGet
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 1 }, { id: 2 }], total: 4, context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 3 }, { id: 4 }], total: 4, context: 'ctx-1' } },
      })

    const paging = useRecommendPaging()
    await paging.loadPage('random')
    expect(getRecommendListUsingGet).toHaveBeenCalledTimes(1)
    expect(getRecommendListUsingGet.mock.calls[0][0]).toMatchObject({ page: 1, type: 'random' })
    expect(getRecommendListUsingGet.mock.calls[0][0].context).toBeUndefined()

    paging.nextPage()
    await paging.loadPage('random')
    expect(getRecommendListUsingGet.mock.calls[1][0]).toMatchObject({
      page: 2,
      type: 'random',
      context: 'ctx-1',
    })
  })

  it('总数取服务端给出的真实数量，越界页不会把总数抹成零', async () => {
    getRecommendListUsingGet.mockResolvedValue({
      data: { code: 0, data: { records: [], total: 12, context: 'ctx-1' } },
    })

    const paging = useRecommendPaging()
    const records = await paging.loadPage('hot')

    expect(records).toEqual([])
    expect(paging.total.value).toBe(12)
    expect(paging.hasMore.value).toBe(false)
  })

  it('重新开始时清空上下文与页码', async () => {
    getRecommendListUsingGet.mockResolvedValue({
      data: { code: 0, data: { records: [{ id: 1 }], total: 1, context: 'ctx-1' } },
    })

    const paging = useRecommendPaging()
    await paging.loadPage('hot')
    expect(paging.context.value).toBe('ctx-1')

    paging.reset()
    expect(paging.context.value).toBeUndefined()
    expect(paging.page.value).toBe(1)

    await paging.loadPage('hot')
    expect(getRecommendListUsingGet.mock.calls[1][0].context).toBeUndefined()
  })
})
