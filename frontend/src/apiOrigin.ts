/**
 * 后端入口的唯一来源。
 *
 * REST、上传、SSE 与 WebSocket 都从这里取地址，避免各处各自兜底导致
 * 生产链路指向不同主机。默认值与 vite 代理一致（同源 `/api`）：
 * 开发时由 dev server 代理，生产时由反向代理把 `/api` 转到后端。
 *
 * 只有需要连另一台开发机的后端时才设置 `VITE_API_BASE_URL`。
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

/** 完整的 API 根地址，例如 `/api`。 */
export const API_ROOT = `${API_BASE_URL}/api`

/**
 * 把站内相对路径（受控内容入口、SSE 等）拼成可请求的完整地址。
 * 已是绝对地址（http/https/blob/ws）时原样返回。
 */
export function apiUrl(path: string): string {
  if (/^[a-z][a-z\d+.-]*:/i.test(path)) return path
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

/**
 * WebSocket 地址：与页面同源时用当前页面的协议与主机。
 * https 页面得到 wss，http 页面得到 ws，不会出现混合内容。
 */
export function webSocketUrl(path: string): string {
  if (/^wss?:\/\//i.test(path)) return path
  const base = API_BASE_URL || window.location.origin
  const url = new URL(`${base.replace(/^http/i, 'ws')}${path.startsWith('/') ? path : `/${path}`}`)
  return url.toString()
}

/** CSRF 令牌请求头名，与后端 `vision-space.web.csrf-header-name` 保持一致。 */
export const CSRF_HEADER = 'X-CSRF-Token'

/** CSRF 令牌 Cookie 名，与后端 `vision-space.web.csrf-cookie-name` 保持一致。 */
export const CSRF_COOKIE = 'VS_CSRF'

/** 从 Cookie 读取双提交令牌；缺失时返回空串（请求会由后端按无令牌处理）。 */
export function readCsrfToken(): string {
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${CSRF_COOKIE}=`))
  return match ? decodeURIComponent(match.slice(CSRF_COOKIE.length + 1)) : ''
}

/** 状态修改方法：这些请求必须带 CSRF 令牌。 */
const STATE_CHANGING = new Set(['post', 'put', 'delete', 'patch'])

/**
 * 为 Axios 请求补充 CSRF 令牌。GET/HEAD 不修改状态，不需要令牌。
 * 非浏览器环境（无 document）直接跳过，便于单测与 SSR。
 */
export function attachCsrf<T extends { method?: string; headers?: any }>(config: T): T {
  if (typeof document === 'undefined') return config
  const method = (config.method || 'get').toLowerCase()
  if (!STATE_CHANGING.has(method)) return config
  const token = readCsrfToken()
  if (token) {
    if (config.headers && typeof config.headers.set === 'function') {
      config.headers.set(CSRF_HEADER, token)
    } else {
      config.headers = { ...(config.headers || {}), [CSRF_HEADER]: token }
    }
  }
  return config
}
