// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /agent/board-operations/${param0} */
export async function readBoardOperationUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.readBoardOperationUsingGetParams,
  options?: { [key: string]: any },
) {
  const { operationId: param0, ...queryParams } = params
  return request<API.BaseResponseAssetBoardOperation>(`/agent/board-operations/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/board-proposals */
export async function proposeSaveUsingPost(body: API.Input, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoardProposalTask>('/agent/board-proposals', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/board-proposals/${param0}/decision */
export async function decideProposalUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.decideProposalUsingPostParams,
  body: API.Decision,
  options?: { [key: string]: any },
) {
  const { proposalId: param0, ...queryParams } = params
  return request<API.BaseResponseBoardProposalTask>(`/agent/board-proposals/${param0}/decision`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/board-proposals/plan */
export async function planSaveUsingPost(body: API.Plan, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoardProposalTask>('/agent/board-proposals/plan', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /agent/boards */
export async function createBoardUsingPost(
  body: API.SaveAssetBoardRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAssetBoardOperation>('/agent/boards', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/boards/${param0} */
export async function readBoardUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.readBoardUsingGetParams,
  options?: { [key: string]: any },
) {
  const { boardId: param0, ...queryParams } = params
  return request<API.BaseResponseAssetBoard>(`/agent/boards/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /agent/boards/${param0} */
export async function replaceBoardUsingPut(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.replaceBoardUsingPutParams,
  body: API.SaveAssetBoardRequest,
  options?: { [key: string]: any },
) {
  const { boardId: param0, ...queryParams } = params
  return request<API.BaseResponseAssetBoardOperation>(`/agent/boards/${param0}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/boards/${param0}/items/${param1}/content */
export async function boardPreviewUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.boardPreviewUsingGetParams,
  options?: { [key: string]: any },
) {
  const { boardId: param0, itemId: param1, ...queryParams } = params
  return request<string>(`/agent/boards/${param0}/items/${param1}/content`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/tasks/${param0} */
export async function readTaskUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.readTaskUsingGetParams,
  options?: { [key: string]: any },
) {
  const { taskId: param0, ...queryParams } = params
  return request<API.BaseResponseBoardProposalTask>(`/agent/tasks/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /agent/tasks/${param0}/events */
export async function taskEventsUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.taskEventsUsingGetParams,
  options?: { [key: string]: any },
) {
  const { taskId: param0, ...queryParams } = params
  return request<API.ServerSentEventObject[]>(`/agent/tasks/${param0}/events`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}
