// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * 创建 MCP Server
 * POST /api/mcp-server
 */
export const createMcpServer = (data: any) =>
  request('/api/mcp-server', { method: 'POST', data })

/**
 * 更新 MCP Server
 * PUT /api/mcp-server
 */
export const updateMcpServer = (data: any) =>
  request('/api/mcp-server', { method: 'PUT', data })

/**
 * 删除 MCP Server
 * DELETE /api/mcp-server/{mcpServerCode}
 */
export const deleteMcpServer = (mcpServerCode: string) =>
  request(`/api/mcp-server/${mcpServerCode}`, { method: 'DELETE' })

/**
 * 获取 MCP Server 详情
 * GET /api/mcp-server/{mcpServerCode}
 */
export const getMcpServer = (mcpServerCode: string, needTools = false) =>
  request(`/api/mcp-server/${mcpServerCode}`, { params: { needTools } })

/**
 * 获取 MCP Server 列表
 * GET /api/mcp-server
 */
export const listMcpServers = (params: { name?: string; current?: number; size?: number }) =>
  request('/api/mcp-server', { params })

