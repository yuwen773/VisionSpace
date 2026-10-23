/**
 * 逗号分隔的主机清单解析。
 *
 * 这张清单在两处使用：构建期生成 CSP 的 img-src（Node 侧），
 * 以及运行期判断 Markdown 图片能否加载（浏览器侧）。
 * 两边必须用同一份解析结果，否则策略会悄悄漂移——
 * 比如 CSP 放行了某个主机，而渲染阶段却不认它。
 *
 * 放在独立模块里，是为了让两侧都能引用，而不必互相依赖。
 */
export function parseHostList(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(',')
    .map((h) => h.trim())
    .filter(Boolean)
}
