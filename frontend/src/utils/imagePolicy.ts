/**
 * Markdown 图片的加载策略。
 *
 * 为什么要在渲染阶段就判定，而不是交给 CSP 兜底：CSP 只能阻止"请求成功"，
 * 请求本身已经带着 URL 发向第三方（URL 里可能就含会话信息）。真正要守住的
 * 是"不发起这个请求"，所以决定必须在生成标签时做出。
 *
 * 允许的来源由受控部署配置给出（VITE_CSP_IMAGE_HOSTS），不接受内容自述。
 */

/** 受控图片主机清单；为空表示不信任任何外部主机。 */
function allowedHosts(): string[] {
  const raw = (import.meta.env.VITE_CSP_IMAGE_HOSTS as string | undefined) ?? ''
  return raw
    .split(',')
    .map((h) => h.trim().toLowerCase())
    .filter(Boolean)
}

/** 站内相对路径、data 位图与 blob 属于同源/本地资源。 */
function isLocalImage(src: string): boolean {
  if (!src) return false
  if (src.startsWith('/') && !src.startsWith('//')) return true
  if (src.startsWith('./') || src.startsWith('../')) return true
  if (/^data:image\/(png|jpeg|jpg|gif|webp);/i.test(src)) return true
  if (src.startsWith('blob:')) return true
  return false
}

/** 判断某个图片地址是否允许在消息里加载。 */
export function isLoadableImage(src: string): boolean {
  const value = (src ?? '').trim()
  if (!value) return false
  if (isLocalImage(value)) return true

  let url: URL
  try {
    url = new URL(
      value,
      typeof window !== 'undefined' ? window.location.origin : 'http://localhost',
    )
  } catch {
    return false
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return false

  // 站内绝对地址同样放行
  if (typeof window !== 'undefined' && url.origin === window.location.origin) return true

  return allowedHosts().includes(url.hostname.toLowerCase())
}
