/**
 * 逗号分隔的图片源清单解析与规范化。
 *
 * 这张清单在三处使用：构建期生成 CSP 的 img-src（Node 侧）、
 * 运行期判断 Markdown 图片能否加载、以及图表产物清理时的受控图片源。
 * 三处必须用同一份解析结果，否则策略会悄悄漂移——
 * 比如 CSP 放行了某个源，而渲染阶段却不认它。
 *
 * 语义统一为 **origin**（scheme://host[:port]）：
 * `.env.example` 里写的是完整 origin（`https://cdn.internal.example`），
 * 所以解析结果必须是 origin，不能是裸 hostname，否则两边永远对不上。
 * 只接受 http/https，非法项直接丢弃而不是放宽为通配。
 */

/** 把一个配置项规范化为 origin；不合法时返回 null。 */
export function normalizeOrigin(raw: string | undefined): string | null {
  const value = (raw ?? '').trim()
  if (!value) return null
  // 必须写成完整 origin（带 scheme）。裸主机名不猜协议——猜错会让策略
  // 悄悄多放行一个目标，而配置者以为写的是别的东西。
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(value)) return null
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null
    if (!url.hostname) return null
    // 丢弃路径/查询，只保留 origin：CSP 的 img-src 与运行期比较都按 origin
    return url.origin
  } catch {
    return null
  }
}

/** 解析并规范化整张清单；非法项被丢弃，重复项合并。 */
export function parseOriginList(raw: string | undefined): string[] {
  const out: string[] = []
  for (const item of (raw ?? '').split(',')) {
    const origin = normalizeOrigin(item)
    if (origin && !out.includes(origin)) out.push(origin)
  }
  return out
}

/** 判断某个绝对地址是否落在给定的 origin 清单里。 */
export function isOriginAllowed(url: string, origins: string[]): boolean {
  const origin = normalizeOrigin(url)
  return origin !== null && origins.includes(origin)
}

/**
 * 按 URL 解析器的规则归一地址，用于**判定**而不是用于取值。
 *
 * URL 规范要求解析前删除所有 ASCII tab / LF / CR，并且在 http(s) 这类
 * 特殊协议下把 `\` 当作 `/`。所以下面三种写法在浏览器里完全等价，
 * 都会把请求发往 `evil.example`：
 *
 *   /\evil.example/a.png
 *   /\t/evil.example/a.png      ← 中间夹一个 tab
 *   //evil.example/a.png
 *
 * 只判断"是否以 / 开头""是否含反斜杠"都会漏掉其中一种。判定前先做同样的
 * 归一，才不会出现"字面上像站内路径、实际发往外部"的缝隙。
 */
export function normalizeUrlForPolicy(value: string): string {
  return value.replace(/[\t\n\r]/g, '').replace(/\\/g, '/')
}

/**
 * 是否是一个**同源相对引用**（浏览器会相对当前文档地址解析）。
 *
 * 归一之后：带协议的、以 `//` 开头的都指向别处；剩下的 `a.png`、
 * `./a.png`、`/a.png` 都落在当前源。
 *
 * 不能只看"以 / 开头"：`a.png` 也是同源的，漏掉它会把正常图片一并关掉。
 */
export function isSameOriginReference(value: string): boolean {
  const v = normalizeUrlForPolicy(value).trim()
  if (!v) return false
  if (v.startsWith('//')) return false // 协议相对地址（含 `/\host`、`/\t/host` 这类伪装）
  return !/^[a-z][a-z0-9+.-]*:/i.test(v) // 带协议的一律不算相对引用
}

/**
 * 归一后是否以 `//` 开头——即指向外部主机（或伪装成这样的相对地址）。
 * 链接与资源两处共用，避免各自写一套判断。
 */
export function isProtocolRelative(value: string): boolean {
  return normalizeUrlForPolicy(value).trim().startsWith('//')
}
