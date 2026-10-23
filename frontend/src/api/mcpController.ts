// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /mcp-server */
export async function listMcpServersUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listMcpServersUsingGetParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseIPageMcpServerDetail>('/mcp-server', {
    method: 'GET',
    params: {
      // current has a default value: 1
      current: '1',
      // size has a default value: 10
      size: '10',
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /mcp-server */
export async function updateMcpServerUsingPut(
  body: API.McpServerDetail,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseVoid>('/mcp-server', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /mcp-server */
export async function createMcpServerUsingPost(
  body: API.McpServerDetail,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/mcp-server', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /mcp-server/${param0} */
export async function getMcpServerUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getMcpServerUsingGetParams,
  options?: { [key: string]: any }
) {
  const { mcpServerCode: param0, ...queryParams } = params
  return request<API.BaseResponseMcpServerDetail>(`/mcp-server/${param0}`, {
    method: 'GET',
    params: {
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 当前部署允许的 MCP 能力 GET /mcp-server/capabilities */
export async function capabilitiesUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseMcpCapabilities>('/mcp-server/capabilities', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /mcp-server/${param0} */
export async function deleteMcpServerUsingDelete(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteMcpServerUsingDeleteParams,
  options?: { [key: string]: any }
) {
  const { mcpServerCode: param0, ...queryParams } = params
  return request<API.BaseResponseVoid>(`/mcp-server/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}
