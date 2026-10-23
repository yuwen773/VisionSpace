/**
 * CSS 资源安全判定。
 *
 * 为什么不能只匹配字面 `url(`：CSS 允许用转义写函数名，
 * `u\72l(...)` 会被浏览器解析成 `url(...)`，而字符串正则看不见它。
 * 大小写、注释、换行同样可以绕过。
 *
 * 这里按 CSS 语法自己解码转义、跳过注释与字符串，再在**解码后**的声明值里
 * 找 `url()`，因此 `u\72l`、`URL`、`u/**\/rl`、`\75 rl` 都归一到同一种形式。
 * 不依赖浏览器 CSSOM：jsdom 不实现 `style.sheet`，测试与生产必须走同一套逻辑，
 * 否则测试覆盖的是另一份行为。
 *
 * 覆盖的加载面（均在真实 Chrome 中验证过会发请求）：
 *   任意属性值上的 `url()`（background-image / fill / stroke / mask /
 *   clip-path / filter / cursor / marker-* 等），自定义属性同样校验，
 *   避免 `--x: url(...)` + `var(--x)` 绕开按属性名的白名单；以及 `@import`。
 */

import { isProtocolRelative, isSameOriginReference, normalizeUrlForPolicy } from './hostList'

/** data: 里只放行内联位图；svg 可承载脚本，不在此列。 */
export const INLINE_BITMAP_DATA = /^data:image\/(png|jpe?g|gif|webp);/i

/** 判断一个 CSS 资源引用是否只指向本地。 */
export function isLocalCssResource(target: string): boolean {
  // 归一后再判断：URL 解析器会删掉 tab/LF/CR 并把 `\` 当 `/`，
  // `/\t/evil.example/a.png` 这类写法字面看是站内路径，实际发往外部主机。
  const v = normalizeUrlForPolicy(target).trim()
  if (!v) return true
  if (v.startsWith('#')) return true // 文档内引用，如 url(#gradient)
  if (isProtocolRelative(v)) return false // 协议相对地址（含伪装写法）
  if (isSameOriginReference(v)) return true // 相对路径，浏览器按当前文档解析
  // 带协议的：只放行内联位图
  return INLINE_BITMAP_DATA.test(v)
}

/**
 * 解码 CSS 转义（CSS Syntax Level 3 §4.3.7）。
 * - `\` + 1~6 位十六进制 + 可选空白 → 对应码点
 * - `\` + 换行 → 行连接符，删除
 * - `\` + 其它字符 → 该字符本身
 */
export function decodeCssEscapes(input: string): string {
  let out = ''
  for (let i = 0; i < input.length; i++) {
    const ch = input[i]
    if (ch !== '\\') {
      out += ch
      continue
    }
    const next = input[i + 1]
    if (next === undefined) break
    if (next === '\n' || next === '\r' || next === '\f') {
      // 行连接符：连同后面的换行一起吃掉
      if (next === '\r' && input[i + 2] === '\n') i += 2
      else i += 1
      continue
    }
    const hexMatch = /^[0-9a-fA-F]{1,6}/.exec(input.slice(i + 1))
    if (hexMatch) {
      const codePoint = parseInt(hexMatch[0], 16)
      // 0 与代理区按规范替换为 U+FFFD
      const safe =
        codePoint === 0 || (codePoint >= 0xd800 && codePoint <= 0xdfff) ? 0xfffd : codePoint
      out += String.fromCodePoint(safe)
      i += hexMatch[0].length
      // 十六进制转义后面允许一个空白作为分隔符，需要吃掉
      const after = input[i + 1]
      if (after === ' ' || after === '\t' || after === '\n' || after === '\r' || after === '\f') {
        if (after === '\r' && input[i + 2] === '\n') i += 2
        else i += 1
      }
      continue
    }
    out += next
    i += 1
  }
  return out
}

/** 去掉 CSS 注释（字符串内的 `/*` 不算注释）。 */
function stripComments(input: string): string {
  let out = ''
  let i = 0
  while (i < input.length) {
    const ch = input[i]
    if (ch === '"' || ch === "'") {
      const end = skipString(input, i)
      out += input.slice(i, end)
      i = end
      continue
    }
    if (ch === '/' && input[i + 1] === '*') {
      const end = input.indexOf('*/', i + 2)
      i = end === -1 ? input.length : end + 2
      continue
    }
    out += ch
    i++
  }
  return out
}

/** 返回从 start（引号位置）开始到字符串结束后的下标。 */
function skipString(input: string, start: number): number {
  const quote = input[start]
  let i = start + 1
  while (i < input.length) {
    const ch = input[i]
    if (ch === '\\') {
      i += 2
      continue
    }
    if (ch === quote) return i + 1
    if (ch === '\n') return i // 未闭合的字符串在换行处终止
    i++
  }
  return i
}

/**
 * `image-set()` 里的地址可以写成裸字符串，没有 `url()` 包裹：
 *
 *   background-image: image-set("https://evil.example/a.png" 1x)
 *
 * 浏览器会照取不误。只匹配 `url(` 会漏掉这一整类。
 */
const IMAGE_SET_RE = /\bimage-set\s*\(([\s\S]*)\)/gi

/** 取出 CSS 值里所有会加载资源的目标（转义已解码）。 */
function urlsInValue(value: string): string[] {
  const decoded = decodeCssEscapes(stripComments(value))
  const out: string[] = []
  const re = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)/gi
  let m: RegExpExecArray | null
  while ((m = re.exec(decoded)) !== null) out.push((m[1] ?? m[2] ?? m[3] ?? '').trim())

  // image-set 的候选地址：每项形如 "url" 1x / 'url' 2x / url(...) 1x。
  // 逐项取字符串字面量即可；url() 形式已被上面的循环覆盖。
  IMAGE_SET_RE.lastIndex = 0
  while ((m = IMAGE_SET_RE.exec(decoded)) !== null) {
    const inner = m[1]
    const str = /(["'])((?:(?!\1)[\s\S])*)\1/g
    let s: RegExpExecArray | null
    while ((s = str.exec(inner)) !== null) out.push(s[2].trim())
  }
  return out
}

/** 值里是否有不允许的资源引用。 */
function hasUnsafeUrl(value: string): boolean {
  return urlsInValue(value).some((target) => !isLocalCssResource(target))
}

/**
 * 一段 CSS **值**里是否引用了外部资源。
 *
 * 供非样式表的场景复用：SVG 表现属性（`fill="url(…)"`、`filter`、`mask`、
 * `clip-path`、`marker-*`）的值就是 CSS 值，浏览器同样会去取。
 * 让它们走同一份转义解码，避免在清理器里再写一套会漂移的正则。
 */
export function hasExternalCssUrl(value: string): boolean {
  return hasUnsafeUrl(value)
}

/** 在 depth 0 处按分隔符切分，跳过字符串、注释与括号内部。 */
function splitTopLevel(input: string, separator: string): string[] {
  const parts: string[] = []
  let current = ''
  let depth = 0
  let i = 0
  while (i < input.length) {
    const ch = input[i]
    if (ch === '"' || ch === "'") {
      const end = skipString(input, i)
      current += input.slice(i, end)
      i = end
      continue
    }
    if (ch === '/' && input[i + 1] === '*') {
      const end = input.indexOf('*/', i + 2)
      const stop = end === -1 ? input.length : end + 2
      current += input.slice(i, stop)
      i = stop
      continue
    }
    if (ch === '(' || ch === '[') depth++
    else if (ch === ')' || ch === ']') depth = Math.max(0, depth - 1)
    if (ch === separator && depth === 0) {
      parts.push(current)
      current = ''
      i++
      continue
    }
    current += ch
    i++
  }
  parts.push(current)
  return parts
}

/** 找到与 openIndex 处 `{` 匹配的 `}` 下标；找不到返回 -1。 */
function findBlockEnd(input: string, openIndex: number): number {
  let depth = 0
  let i = openIndex
  while (i < input.length) {
    const ch = input[i]
    if (ch === '"' || ch === "'") {
      i = skipString(input, i)
      continue
    }
    if (ch === '/' && input[i + 1] === '*') {
      const end = input.indexOf('*/', i + 2)
      i = end === -1 ? input.length : end + 2
      continue
    }
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return i
    }
    i++
  }
  return -1
}

export type CssCleanResult = {
  /** 清理后的样式文本 */
  css: string
  /** 被移除的外部引用（诊断与测试断言用） */
  removed: string[]
}

/** 清理一条声明；不安全则返回 null。 */
function sanitizeDeclaration(decl: string, removed: string[]): string | null {
  const trimmed = decl.trim()
  if (!trimmed) return null
  const colon = trimmed.indexOf(':')
  if (colon === -1) return null
  const value = trimmed.slice(colon + 1)
  if (hasUnsafeUrl(value)) {
    removed.push(trimmed.slice(0, 120))
    return null
  }
  return trimmed
}

/** 清理一个声明块的内容。 */
function sanitizeDeclarationBlock(block: string, removed: string[]): string {
  const kept: string[] = []
  for (const decl of splitTopLevel(block, ';')) {
    const clean = sanitizeDeclaration(decl, removed)
    if (clean) kept.push(clean)
  }
  return kept.join('; ')
}

/**
 * 清理单条 `style=` 属性。
 *
 * 属性值是**声明列表**（`fill:red; stroke:blue`），没有选择器也没有花括号。
 * 不能直接丢给 sanitizeCssText：它按"规则"遍历，遇到没有 `{` 的内容会整段
 * 丢弃，正常的内联样式会被清空。
 */
export function sanitizeInlineStyle(style: string): CssCleanResult {
  const removed: string[] = []
  if (!style || !style.trim()) return { css: '', removed }
  return { css: sanitizeDeclarationBlock(style, removed), removed }
}

/**
 * 清理一段**样式表**文本（有选择器的规则）。
 *
 * 逐条声明判断：只要值里出现不允许的 url()，整条声明丢弃
 * （不替换成 `none`——半截值容易在别的属性上变成新问题）。
 * `@import` 一律不保留：它会让浏览器再去取一份外部样式表。
 */
export function sanitizeCssText(css: string): CssCleanResult {
  const removed: string[] = []
  if (!css || !css.trim()) return { css: '', removed }

  const out: string[] = []
  let i = 0

  while (i < css.length) {
    // 跳过空白与注释
    if (/\s/.test(css[i])) {
      i++
      continue
    }
    if (css[i] === '/' && css[i + 1] === '*') {
      const end = css.indexOf('*/', i + 2)
      i = end === -1 ? css.length : end + 2
      continue
    }

    // 收集 prelude，直到 `{` 或 `;`
    const start = i
    let depth = 0
    let openIndex = -1
    let semicolonIndex = -1
    while (i < css.length) {
      const ch = css[i]
      if (ch === '"' || ch === "'") {
        i = skipString(css, i)
        continue
      }
      if (ch === '/' && css[i + 1] === '*') {
        const end = css.indexOf('*/', i + 2)
        i = end === -1 ? css.length : end + 2
        continue
      }
      if (ch === '(') depth++
      else if (ch === ')') depth = Math.max(0, depth - 1)
      else if (depth === 0 && ch === '{') {
        openIndex = i
        break
      } else if (depth === 0 && ch === ';') {
        semicolonIndex = i
        break
      }
      i++
    }

    const prelude = css.slice(start, i).trim()

    // 顶层 @import：整条丢弃
    if (semicolonIndex !== -1) {
      const statement = css.slice(start, semicolonIndex).trim()
      if (/^@import\b/i.test(decodeCssEscapes(stripComments(statement)))) {
        removed.push(statement.slice(0, 120))
      }
      i = semicolonIndex + 1
      continue
    }

    if (openIndex === -1) break // 没有块的尾巴，丢弃

    const closeIndex = findBlockEnd(css, openIndex)
    if (closeIndex === -1) break // 不闭合的样式表不可信，整段丢弃

    const block = css.slice(openIndex + 1, closeIndex)
    const decodedPrelude = decodeCssEscapes(stripComments(prelude))

    if (prelude.startsWith('@')) {
      if (/^@import\b/i.test(decodedPrelude)) {
        removed.push(prelude.slice(0, 120))
      } else if (/^@(media|supports|layer|container)\b/i.test(decodedPrelude)) {
        // 分组规则：内部还是规则，递归清理后重新包裹
        const inner = sanitizeCssText(block)
        removed.push(...inner.removed)
        if (inner.css.trim()) out.push(`${prelude} { ${inner.css} }`)
      } else {
        // @font-face / @keyframes 等：块内出现外部引用就整块丢弃
        // @keyframes 内部是 `from { ... }` 形式，用声明块清理会丢内容，
        // 但只有含外部引用时才需要处理；不含则原样保留。
        const cleaned = sanitizeDeclarationBlock(block, removed)
        if (hasUnsafeUrl(block)) {
          if (cleaned.trim()) out.push(`${prelude} { ${cleaned} }`)
        } else {
          out.push(`${prelude} { ${block.trim()} }`)
        }
      }
    } else {
      const cleaned = sanitizeDeclarationBlock(block, removed)
      if (cleaned) out.push(`${prelude} { ${cleaned} }`)
    }

    i = closeIndex + 1
  }

  return { css: out.join('\n'), removed }
}
