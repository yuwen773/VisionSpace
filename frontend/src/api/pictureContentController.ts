// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /picture/content/${param0}/original */
export async function originalUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.originalUsingGetParams,
  options?: { [key: string]: any }
) {
  const { pictureId: param0, ...queryParams } = params
  return request<string>(`/picture/content/${param0}/original`, {
    method: 'GET',
    params: {
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /picture/content/${param0}/preview */
export async function previewUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.previewUsingGetParams,
  options?: { [key: string]: any }
) {
  const { pictureId: param0, ...queryParams } = params
  return request<string>(`/picture/content/${param0}/preview`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /picture/content/${param0}/thumbnail */
export async function thumbnailUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.thumbnailUsingGetParams,
  options?: { [key: string]: any }
) {
  const { pictureId: param0, ...queryParams } = params
  return request<string>(`/picture/content/${param0}/thumbnail`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}
