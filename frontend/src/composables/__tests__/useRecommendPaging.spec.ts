import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getRecommendListUsingGet } = vi.hoisted(() => ({ getRecommendListUsingGet: vi.fn() }))

vi.mock('@/api-custom/picture', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api-custom/picture')>()
  return { ...actual, getRecommendListUsingGet }
})

import { parseRecommendTotal, useRecommendPaging } from '@/composables/useRecommendPaging'

/**
 * 页面翻页：分页上下文由服务端给出，页面翻页时原样带回，
 * 翻完的结果与真实 HTTP 的返回一致。
 *
 * <p>响应形状按后端的真实序列化给出：Long 被序列化成十进制字符串，
 * 所以 total 是 "12" 而不是 12。
 */
describe('recommend paging', () => {
  beforeEach(() => {
    getRecommendListUsingGet.mockReset()
  })

  it('第一次请求不带上下文，翻页时带上服务端给出的上下文', async () => {
    getRecommendListUsingGet
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 1 }, { id: 2 }], total: '4', context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 3 }, { id: 4 }], total: '4', context: 'ctx-1' } },
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
      data: { code: 0, data: { records: [], total: '12', context: 'ctx-1' } },
    })

    const paging = useRecommendPaging()
    const records = await paging.loadPage('hot')

    expect(records).toEqual([])
    expect(paging.total.value).toBe(12)
    expect(paging.totalValid.value).toBe(true)
    expect(paging.hasMore.value).toBe(false)
  })

  it('非空结果、末页与空集合都取服务端给出的真实总数', async () => {
    getRecommendListUsingGet
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 1 }, { id: 2 }], total: '12', context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 11 }, { id: 12 }], total: '12', context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [], total: '0', context: 'ctx-2' } },
      })

    const paging = useRecommendPaging()

    const first = await paging.loadPage('hot')
    expect(first).toHaveLength(2)
    expect(paging.total.value).toBe(12)

    paging.nextPage()
    const last = await paging.loadPage('hot')
    expect(last).toHaveLength(2)
    expect(paging.total.value).toBe(12)
    expect(paging.hasMore.value).toBe(false)

    paging.reset()
    const empty = await paging.loadPage('hot')
    expect(empty).toEqual([])
    expect(paging.total.value).toBe(0)
  })

  it('非法总数被明确标记，不当成零，也不覆盖上一次的真实总数', async () => {
    getRecommendListUsingGet
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 1 }], total: '12', context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 2 }], total: 'not-a-number', context: 'ctx-1' } },
      })

    const paging = useRecommendPaging()
    await paging.loadPage('hot')
    expect(paging.total.value).toBe(12)
    expect(paging.totalValid.value).toBe(true)

    paging.nextPage()
    await paging.loadPage('hot')
    expect(paging.totalValid.value).toBe(false)
    expect(paging.total.value).toBe(12)
  })

  it('总数解析只接受非负安全整数', () => {
    expect(parseRecommendTotal('12')).toBe(12)
    expect(parseRecommendTotal(12)).toBe(12)
    expect(parseRecommendTotal('0')).toBe(0)
    expect(parseRecommendTotal('-1')).toBeUndefined()
    expect(parseRecommendTotal('1.5')).toBeUndefined()
    expect(parseRecommendTotal('9007199254740993')).toBeUndefined()
    expect(parseRecommendTotal(undefined)).toBeUndefined()
    expect(parseRecommendTotal(null)).toBeUndefined()
  })

  it('重新开始时清空上下文与页码', async () => {
    getRecommendListUsingGet.mockResolvedValue({
      data: { code: 0, data: { records: [{ id: 1 }], total: '1', context: 'ctx-1' } },
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

  it('服务端明确结束上下文时从新的一份结果重新开始', async () => {
    getRecommendListUsingGet
      .mockResolvedValueOnce({
        data: { code: 0, data: { records: [{ id: 1 }, { id: 2 }], total: '4', context: 'ctx-1' } },
      })
      .mockResolvedValueOnce({
        data: {
          code: 0,
          data: {
            records: [{ id: 3 }, { id: 4 }],
            total: '4',
            context: 'ctx-2',
            contextValid: false,
          },
        },
      })

    const paging = useRecommendPaging()
    await paging.loadPage('hot')
    expect(paging.context.value).toBe('ctx-1')

    paging.nextPage()
    const records = await paging.loadPage('hot')

    expect(records).toHaveLength(2)
    expect(paging.page.value).toBe(1)
    expect(paging.context.value).toBe('ctx-2')
    expect(paging.total.value).toBe(4)
  })
})
