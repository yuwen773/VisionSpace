/**
 * 私有图片下载的地址白名单（纯逻辑）。
 *
 * 图片地址来自服务端返回的数据。一旦数据被改成外链，带着鉴权头去下载就等于把
 * 登录令牌送给第三方域名。这里只允许与 API 同源的内容地址，其余一律拒绝；
 * 用前缀匹配而不是 `includes`，避免 `visionspace.space.evil.com` 这类前缀伪装。
 */

/** 去掉末尾斜杠，避免传入 `https://host/` 时拼出双斜杠前缀。 */
function normalizeBase(base: string): string {
  return base.trim().replace(/\/+$/, '')
}

export function isTrustedContentUrl(url: string, apiBase: string): boolean {
  const base = normalizeBase(apiBase)
  if (base === '' || !/^https?:\/\//i.test(url)) {
    return false
  }
  return url.startsWith(`${base}/`)
}
