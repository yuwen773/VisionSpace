// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /admin/stats/dashboard */
export async function getDashboardStatsUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseAdminDashboardStatsVO>('/admin/stats/dashboard', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /admin/stats/picture-action/metrics */
export async function getPictureActionMetricsUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponsePictureActionReportMetricsVO>(
    '/admin/stats/picture-action/metrics',
    {
      method: 'POST',
      ...(options || {}),
    }
  )
}

/** 此处后端没有提供注释 POST /admin/stats/picture-stats/backlog */
export async function getPictureStatsBacklogUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponsePictureStatsBacklogVO>('/admin/stats/picture-stats/backlog', {
    method: 'POST',
    ...(options || {}),
  })
}
