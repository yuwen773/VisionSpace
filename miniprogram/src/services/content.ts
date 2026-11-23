import { API_BASE_URL, API_PREFIX } from '../config/env'
import { withTokenQuery } from '../logic/url'
import { getToken, getTokenQueryName } from './session'

/**
 * 相对内容入口 → 可直接放进 <image src> 的绝对地址。
 *
 * `withToken=true` 时在查询参数里带上令牌：`<image>` 发不了自定义头，
 * 空间图片必须靠查询参数通过内容鉴权；公开图库不加，避免令牌无谓地进日志。
 * 查询参数名可能与请求头名不同，由登录响应下发。
 */
export function resolveContentUrl(path: string | null | undefined, withToken = false): string {
  const value = typeof path === 'string' ? path.trim() : ''
  if (value === '') {
    return ''
  }
  const absolute = /^https?:\/\//i.test(value)
    ? value
    : `${API_BASE_URL}${API_PREFIX}${value.startsWith('/') ? value : `/${value}`}`
  return withToken ? withTokenQuery(absolute, getToken(), getTokenQueryName()) : absolute
}
