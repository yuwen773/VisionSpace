// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /picture/collab/monitor */
export async function monitorUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseCollabMonitorVO>('/picture/collab/monitor', {
    method: 'GET',
    ...(options || {}),
  })
}
