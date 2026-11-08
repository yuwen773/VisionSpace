// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /agent/asset-results/${param0} */
export async function readAssetResultUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.readAssetResultUsingGetParams,
  options?: { [key: string]: any },
) {
  const { resultId: param0, ...queryParams } = params
  return request<API.BaseResponseAssetSearchResult>(`/agent/asset-results/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/asset-results/${param0}/items/${param1}/content */
export async function assetPreviewUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.assetPreviewUsingGetParams,
  options?: { [key: string]: any },
) {
  const { resultId: param0, position: param1, ...queryParams } = params
  return request<string>(`/agent/asset-results/${param0}/items/${param1}/content`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/asset-search */
export async function searchAssetsUsingPost(
  body: API.AssetSearchRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAssetSearchResult>('/agent/asset-search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
