/**
 * 运行环境配置。
 *
 * 小程序请求域名必须是已备案的 HTTPS 域名（体验版同样校验），因此默认直连生产站点。
 * 开发期可在开发者工具控制台执行 `wx.setStorageSync('vs_api_base', 'http://127.0.0.1:8081')`
 * 并打开「不校验合法域名」把请求指向本地后端。
 */
const DEFAULT_API_BASE = 'https://visionspace.space'
const API_BASE_OVERRIDE_KEY = 'vs_api_base'

function resolveApiBase(): string {
  let override = ''
  try {
    const stored = wx.getStorageSync(API_BASE_OVERRIDE_KEY)
    override = typeof stored === 'string' ? stored.trim() : ''
  } catch (error) {
    // 存储不可用时退回默认域名，不影响正常使用
    console.warn('读取 API 覆盖地址失败', error)
  }
  const base = override === '' ? DEFAULT_API_BASE : override
  return base.replace(/\/+$/, '')
}

export const API_BASE_URL = resolveApiBase()

/** 后端 context-path 固定为 /api */
export const API_PREFIX = '/api'

export const APP_VERSION = '0.1.0'
