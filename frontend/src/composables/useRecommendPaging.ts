import { ref } from 'vue'
import { getRecommendListUsingGet, type PictureRecommendRequest } from '@/api-custom/picture'

/**
 * 推荐接口返回的一页数据：记录、真实总数与分页上下文。
 */
interface RecommendPageResponse {
  data?: {
    data?: {
      records?: unknown[]
      total?: number
      context?: string
    }
  }
}

/**
 * 推荐分页状态。
 *
 * <p>分页上下文由服务端给出，翻页时原样带回：同一份有序结果翻下去不重复、
 * 不遗漏。切换分类、搜索或刷新时上下文清空，重新开始一份新的结果。
 */
export function useRecommendPaging() {
  /** 当前页码，从 1 开始 */
  const page = ref(1)
  /** 每页条数 */
  const size = ref(24)
  /** 服务端给出的真实总数 */
  const total = ref(0)
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
    const res = (await getRecommendListUsingGet({
      type,
      page: page.value,
      size: size.value,
      context: context.value,
    })) as RecommendPageResponse
    const data: unknown[] = res?.data?.data?.records ?? []
    // 总数始终取服务端给出的真实数量：越界页只返回空记录，不会把总数抹成零
    const serverTotal = res?.data?.data?.total
    if (typeof serverTotal === 'number') {
      total.value = serverTotal
    }
    if (data.length > 0) {
      hasMore.value = data.length === size.value
      // 记住服务端给出的上下文，后续页接着这一份翻
      const nextContext = res?.data?.data?.context
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

  return { page, size, total, hasMore, context, reset, loadPage, nextPage }
}
