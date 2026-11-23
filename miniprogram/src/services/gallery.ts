import { request } from './http'
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
  return request<PicturePage>({ path: '/picture/list/page/vo', method: 'POST', data })
}

export function fetchTagCategory(): Promise<PictureTagCategory> {
  return request<PictureTagCategory>({ path: '/picture/tag_category' })
}

export function fetchPicture(id: number): Promise<PictureVO> {
  return request<PictureVO>({ path: '/picture/get/vo', data: { id } })
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
