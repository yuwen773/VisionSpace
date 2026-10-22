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
