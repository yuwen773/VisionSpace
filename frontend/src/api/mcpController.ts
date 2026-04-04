// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * 创建 MCP Server
 * POST /mcp-server
 */
export const createMcpServer = (data: any) =>
  request('/mcp-server', { method: 'POST', data })

/**
 * 更新 MCP Server
 * PUT /mcp-server
 */
export const updateMcpServer = (data: any) =>
  request('/mcp-server', { method: 'PUT', data })

/**
 * 删除 MCP Server
 * DELETE /mcp-server/{mcpServerCode}
 */
export const deleteMcpServer = (mcpServerCode: string) =>
  request(`/mcp-server/${mcpServerCode}`, { method: 'DELETE' })

/**
 * 获取 MCP Server 详情
 * GET /mcp-server/{mcpServerCode}
 */
export const getMcpServer = (mcpServerCode: string, needTools = false) =>
  request(`/mcp-server/${mcpServerCode}`, { params: { needTools } })

/**
 * 获取 MCP Server 列表
 * GET /mcp-server
 */
export const listMcpServers = (params: { name?: string; current?: number; size?: number }) =>
  request('/mcp-server', { params })

/**
 * 获取用户 MCP 偏好设置
 * GET /user/preference/mcp
 */
export const getUserMcpPreference = () =>
  request('/user/preference/mcp')

/**
 * 设置用户 MCP 偏好设置
 * PUT /user/preference/mcp
 */
export const setUserMcpPreference = (data: string[]) =>
  request('/user/preference/mcp', { method: 'PUT', data })
