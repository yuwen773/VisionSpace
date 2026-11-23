import { request, ApiError } from './http'
import type {
  PicturePage,
  PictureTagCategory,
  PictureVO,
  RecommendPage,
} from './types'
import type { ActionEvent } from '../logic/telemetry'

export const RECOMMEND_TYPES = ['hot', 'latest', 'quality', 'random'] as const
export type RecommendType = (typeof RECOMMEND_TYPES)[number]

export interface SearchParams {
  current: number
  pageSize: number
  searchText?: string
  category?: string
  /** 带 spaceId 时后端按空间鉴权，并且不过滤审核状态（隔离行 URL 会被置空）。 */
  spaceId?: number
}

/** 榜单：hot / latest / quality / random，用 context 令牌保证翻页不重复。 */
export function fetchRecommend(
  type: RecommendType,
  page: number,
  size: number,
  context: string | null,
): Promise<RecommendPage> {
  const data: Record<string, unknown> = { type, page, size }
  if (context !== null && context !== '') {
    data.context = context
  }
  return request<RecommendPage>({ path: '/picture/recommend/list', data })
}

/** 检索：关键词与分类都走这个接口；匿名访问时后端强制只返回已审核的公开图。 */
export function searchPictures(params: SearchParams): Promise<PicturePage> {
  const data: Record<string, unknown> = {
    current: params.current,
    pageSize: params.pageSize,
    sortField: 'createTime',
    sortOrder: 'descend',
  }
  if (params.searchText) {
    data.searchText = params.searchText
  }
  if (params.category) {
    data.category = params.category
  }
  if (typeof params.spaceId === 'number') {
    data.spaceId = params.spaceId
  }
  return request<PicturePage>({
    path: '/picture/list/page/vo',
    method: 'POST',
    data,
    auth: typeof params.spaceId === 'number',
  })
}

export function fetchTagCategory(): Promise<PictureTagCategory> {
  return request<PictureTagCategory>({ path: '/picture/tag_category' })
}

/**
 * 作品详情。先带令牌请求（空间作品需要），令牌过期时后端返回 40100，
 * 此时退回匿名请求：公开作品照常可看，空间作品会得到 40101 由页面引导登录。
 */
export async function fetchPicture(id: number): Promise<PictureVO> {
  try {
    return await request<PictureVO>({ path: '/picture/get/vo', data: { id }, auth: true })
  } catch (error) {
    if (error instanceof ApiError && error.code === 40100) {
      return request<PictureVO>({ path: '/picture/get/vo', data: { id } })
    }
    throw error
  }
}

/**
 * 按颜色检索空间内最相近的 12 张图片。仅空间内成员可用；公开图库不支持。
 * 返回值没有分页信息，页面按一次性结果展示。
 */
export function searchByColor(spaceId: number, picColor: string): Promise<PictureVO[]> {
  return request<PictureVO[]>({
    path: '/picture/search/color',
    method: 'POST',
    data: { spaceId, picColor },
    auth: true,
  })
}

/** 匿名行为上报；失败只影响统计，调用方应吞掉异常。 */
export function reportAction(event: ActionEvent): Promise<boolean> {
  return request<boolean>({
    path: '/picture/action/report',
    method: 'POST',
    data: {
      pictureId: event.pictureId,
      actionType: event.actionType,
      source: event.source,
      eventId: event.eventId,
    },
    timeout: 5000,
  })
}
