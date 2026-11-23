import { API_BASE_URL, API_PREFIX } from '../config/env'

/** 相对内容入口 → 可直接放进 <image src> 的绝对地址。 */
export function resolveContentUrl(path: string | null | undefined): string {
  const value = typeof path === 'string' ? path.trim() : ''
  if (value === '') {
    return ''
  }
  if (/^https?:\/\//i.test(value)) {
    return value
  }
  const normalized = value.startsWith('/') ? value : `/${value}`
  return `${API_BASE_URL}${API_PREFIX}${normalized}`
}
