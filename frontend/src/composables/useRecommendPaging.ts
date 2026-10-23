import { ref } from 'vue'
import {
  getRecommendListUsingGet,
  type PictureRecommendRequest,
  type RecommendPageData,
} from '@/api-custom/picture'

/**
 * 解析服务端给出的总数。
 *
 * <p>只接受非负安全整数。非法值返回 undefined，由调用方明确处理：
 * 绝不能把它当成 0，那会把一个非空集合说成空的。
 */
export function parseRecommendTotal(raw: unknown): number | undefined {
  if (typeof raw === 'number') {
    return Number.isSafeInteger(raw) && raw >= 0 ? raw : undefined
  }
  if (typeof raw === 'string') {
    const text = raw.trim()
    if (!/^\d+$/.test(text)) {
      return undefined
    }
    const value = Number(text)
    return Number.isSafeInteger(value) ? value : undefined
  }
  return undefined
}

/**
 * 推荐分页状态。
 *
 * <p>分页上下文由服务端给出，翻页时原样带回：同一份有序结果翻下去不重复、
 * 不遗漏。服务端明确结束这份上下文时（候选被刷新或到期），从新的一份结果
 * 重新开始，不把旧页码绑到新的排序上。切换分类、搜索或刷新时上下文清空。
 */
export function useRecommendPaging() {
  /** 当前页码，从 1 开始 */
  const page = ref(1)
  /** 每页条数 */
  const size = ref(24)
  /** 服务端给出的真实总数 */
  const total = ref(0)
  /** 服务端给出的总数是否有效：非法响应时保持上一次的有效值，不抹成 0 */
  const totalValid = ref(true)
  /** 是否还有下一页 */
  const hasMore = ref(true)
  /** 分页上下文：第一次请求不传，服务端返回后记住 */
  const context = ref<string | undefined>(undefined)

  /**
   * 清空分页状态，重新开始一份新的结果。
   */
  const reset = () => {
    page.value = 1
    total.value = 0
    totalValid.value = true
    hasMore.value = true
    context.value = undefined
  }

  /**
   * 拉取当前页。
   *
   * @param type 推荐类型
   * @returns 本页记录；空数组表示没有更多
   */
  const loadPage = async (type: PictureRecommendRequest['type']): Promise<unknown[]> => {
    const res = await getRecommendListUsingGet({
      type,
      page: page.value,
      size: size.value,
      context: context.value,
    })
    const payload: RecommendPageData | undefined = res?.data?.data
    const data: unknown[] = payload?.records ?? []
    // 总数始终取服务端给出的真实数量：越界页只返回空记录，不会把总数抹成零
    const serverTotal = parseRecommendTotal(payload?.total)
    if (serverTotal === undefined) {
      // 非法响应：明确标记出来，保留上一次的真实总数
      totalValid.value = false
      console.warn('推荐接口返回的总数不合法，已保留上一次的总数', payload?.total)
    } else {
      total.value = serverTotal
      totalValid.value = true
    }
    if (payload?.contextValid === false) {
      // 服务端明确结束了这份上下文：候选被刷新或到期，原来的页码不再对应任何候选。
      // 从新的一份结果重新开始。
      page.value = 1
      context.value = payload?.context
      hasMore.value = data.length === size.value
      return data
    }
    if (data.length > 0) {
      hasMore.value = data.length === size.value
      // 记住服务端给出的上下文，后续页接着这一份翻
      const nextContext = payload?.context
      if (nextContext) {
        context.value = nextContext
      }
    } else {
      hasMore.value = false
    }
    return data
  }

  /**
   * 翻到下一页。
   */
  const nextPage = () => {
    page.value += 1
  }

  return { page, size, total, totalValid, hasMore, context, reset, loadPage, nextPage }
}
