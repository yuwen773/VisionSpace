/**
 * URL 拼接的纯函数。图片内容入口是相对路径，`<image>` 又无法带自定义请求头，
 * 空间图片只能在查询参数里携带令牌。
 */
export function withTokenQuery(url: string, token: string, name = 'satoken'): string {
  if (token === '' || url === '') {
    return url
  }
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}${encodeURIComponent(name)}=${encodeURIComponent(token)}`
}
