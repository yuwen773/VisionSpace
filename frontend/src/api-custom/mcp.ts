// 手写 API 客户端：MCP Server 管理。
//
// 生成器产出的 mcpServerController.ts 已经覆盖了这五个端点，但这里保留一份
// 薄封装：生成版要求调用方传完整的 API.McpServerDetail 类型，而 MCP 面板里
// 的数据结构是局部构造的，用 any 更省事。等面板类型收敛后再切到生成版。
import request from '@/request'

/**
 * 创建 MCP Server
 * POST /api/mcp-server
 */
export const createMcpServer = (data: any) => request('/api/mcp-server', { method: 'POST', data })

/**
 * 更新 MCP Server
 * PUT /api/mcp-server
 */
export const updateMcpServer = (data: any) => request('/api/mcp-server', { method: 'PUT', data })

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
