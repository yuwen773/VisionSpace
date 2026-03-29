// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** getDashboardStats POST /api/admin/stats/dashboard */
export async function getDashboardStatsUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseAdminDashboardStatsVO_>('/api/admin/stats/dashboard', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  })
}
