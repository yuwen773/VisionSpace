// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /agent/attachment/${param0}/content */
export async function contentUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.contentUsingGetParams,
  options?: { [key: string]: any },
) {
  const { resourceId: param0, ...queryParams } = params
  return request<string>(`/agent/attachment/${param0}/content`, {
    method: 'GET',
    params: {
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/attachment/${param0}/delete */
export async function deleteAttachmentUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteAttachmentUsingPostParams,
  options?: { [key: string]: any },
) {
  const { resourceId: param0, ...queryParams } = params
  return request<API.BaseResponseBoolean>(`/agent/attachment/${param0}/delete`, {
    method: 'POST',
    params: { ...queryParams },
    ...(options || {}),
  })
}
