import { API_BASE_URL, API_PREFIX } from '../config/env'

/**
 * 相对内容入口 → 可直接放进 `<image src>` 的绝对地址。
 *
 * 这里不拼任何令牌：查询参数里的令牌会进 nginx 访问日志、Referer 与图片缓存，
 * 等于把凭据散出去。私有图片改由 `services/image-download.ts` 带鉴权头下载到本地，
 * 公开图库本来就不需要鉴权。
 */
export function resolveContentUrl(path: string | null | undefined): string {
  const value = typeof path === 'string' ? path.trim() : ''
  if (value === '') {
    return ''
  }
  return /^https?:\/\//i.test(value)
    ? value
    : `${API_BASE_URL}${API_PREFIX}${value.startsWith('/') ? value : `/${value}`}`
}
