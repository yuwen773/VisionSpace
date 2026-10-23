/**
 * 具体图表渲染器。
 *
 * 三者共享同一条安全边界：渲染库本身通过注入的 loader 取用（便于测试和不
 * 依赖全局脚本），产物一律经过 sanitizeDiagramSvg 才能进入 DOM。
 * PlantUML 不在这里发起任何网络请求——只生成指向受控服务的地址，
 * 未配置时退回源码。
 */

import { escapeHtml } from '@/utils/jsonHighlight'
import { resolvePlantUmlUrl, sanitizeDiagramSvg } from '@/utils/diagramSanitizer'
import { normalizeOrigin, normalizeUrlForPolicy } from '@/utils/hostList'
import { allowedImageOrigins } from '@/utils/imagePolicy'

type MermaidLike = {
  initialize: (options: Record<string, unknown>) => void
  render: (id: string, code: string) => Promise<{ svg: string }>
}

/**
 * 图表产物里允许出现的受控图片源。
 *
 * 由部署配置决定（`VITE_CSP_IMAGE_HOSTS` + 自托管 PlantUML），不接受内容自述。
 * 三者（Markdown 图片、CSP img-src、图表产物清理）必须用同一份清单：
 * 否则会出现"Markdown 里同一张图能加载，图表里的却被摘掉"这种不一致。
 * 环境变量的解析只发生在 imagePolicy.allowedImageOrigins 一处。
 */
export function controlledImageOrigins(plantumlServer?: string): string[] {
  const fromEnv = allowedImageOrigins()
  const plantuml = normalizeOrigin(plantumlServer)
  return plantuml && !fromEnv.includes(plantuml) ? [...fromEnv, plantuml] : fromEnv
}

export type MermaidLoader = () => Promise<MermaidLike>

type DrawioLike = {
  /** 返回渲染后的 SVG/HTML 字符串 */
  renderToHtml: (xml: string) => Promise<string>
}

export type DrawioLoader = () => Promise<DrawioLike | null>

/** mxgraph 打包版导出的工厂：调用后返回 mxGraph/mxCodec/mxUtils 等类型。 */
type MxFactory = (options: object) => Record<string, unknown>

/** 解码后的模型：只需要能数出真正画出来的顶点/边。 */
type MxModel = {
  getRoot: () => unknown
  /** 递归列出所有单元格；未解码成功时只有默认的两层根节点 */
  getDescendants: (parent: unknown) => Array<{ isVertex?: () => boolean; isEdge?: () => boolean }>
}

/** 渲染 draw.io 时需要的最小接口，避免把整个库的类型引进来。 */
type MxRuntime = {
  mxGraph: new (container: HTMLElement) => {
    setEnabled: (enabled: boolean) => void
    getModel: () => MxModel
    refresh: () => void
    /** 解绑 document 级监听器并释放容器；不解绑会按渲染次数累积 */
    destroy: () => void
  }
  mxCodec: new () => { decode: (node: Element, model: unknown) => void }
  mxUtils: { parseXml: (xml: string) => XMLDocument }
  /** 编解码器注册表：库在加载期会为每类对象各建一个模板实例 */
  mxCodecRegistry?: { codecs: Record<string, { template?: unknown }> }
  /** 解码时需要按节点名找到的构造器，见 registerCodecConstructors */
  mxGraphModel?: unknown
  mxCell?: unknown
  mxGeometry?: unknown
  mxPoint?: unknown
  mxRectangle?: unknown
}

/**
 * mxgraph 的 `mxCodec.decode` 用 `window[node.nodeName]` 找解码器，
 * 找不到就退化成 `node.cloneNode`——图形结构被原样复制，一个单元格都进不了模型，
 * 最终产出一张空 SVG。CDN 版把构造器挂在 window 上所以碰不到这个问题，
 * ESM 打包版不会。这里只补解码真正会用到的几个节点名。
 */
function registerCodecConstructors(mx: MxRuntime): void {
  if (typeof window === 'undefined') return
  const w = window as unknown as Record<string, unknown>
  for (const name of ['mxGraphModel', 'mxCell', 'mxGeometry', 'mxPoint', 'mxRectangle']) {
    if (w[name] === undefined && mx[name as keyof MxRuntime]) {
      w[name] = mx[name as keyof MxRuntime]
    }
  }
}

/**
 * 释放编解码器注册表里的模板实例。
 *
 * mxgraph 在**模块加载期**为 `mxGraph` 与 `mxEditor` 各创建一个模板对象
 * （编解码器靠 `template.constructor` 反查类型）。这两个对象没有容器，
 * 但构造过程照样会创建 graphHandler/panningHandler，往 document 上挂
 * keydown/keyup/mouseup——它们不随任何一次渲染结束而消失。
 *
 * 我们只做解码，不需要这两个模板：`mxCodec.decode` 走的是
 * `mxCodecRegistry.getCodec(ctor)`，按节点名找的是 mxGraphModel/mxCell 这类
 * 解码器，与这两个模板无关。所以加载完直接销毁，把监听器数量压到零。
 */
function disposeCodecTemplates(mx: MxRuntime): void {
  const codecs = mx.mxCodecRegistry?.codecs
  if (!codecs) return
  for (const name of ['mxGraph', 'mxEditor']) {
    const template = codecs[name]?.template as { destroy?: () => void } | undefined
    template?.destroy?.()
  }
}

const MERMAID_OPTIONS = {
  startOnLoad: false,
  // 必须是 base：内置主题（default/forest/…）会**忽略** themeVariables，
  // 图表就固定成浅色，在深空主题下与背景撞成一片。base 是官方留给
  // 调用方自定义变量的主题，其余色值由它按变量推导。
  theme: 'base',
  // strict：禁止 HTML 标签、click 交互与危险链接，配合产物清理构成双保险
  securityLevel: 'strict',
  htmlLabels: false,
  fontFamily: 'var(--font-body, inherit)',
} as const

/**
 * 移除内容里的 Mermaid 配置指令（`%%{init: {...}}%%` 与开头的 frontmatter）。
 *
 * 为什么必须在渲染前做：`initialize()` 里设的 securityLevel 会被**内容里的**
 * `init` 指令覆盖——`%%{init: {"securityLevel":"loose"}}%%` 能让 Mermaid 恢复
 * HTML 标签与宽松模式，随后 Mermaid 会把内容插进 body 的临时节点并真的加载资源。
 * 事后清理最终产物已经晚了：请求在渲染期间就发出去了。
 *
 * 图表外观由应用自己的 `initialize()` 配置与主题变量决定，不依赖内容自述配置，
 * 所以剥离指令不影响正常图表。
 */
export function stripMermaidDirectives(code: string): string {
  return (
    code
      // frontmatter 配置块：--- \n config: ... \n ---
      .replace(/^\s*---\r?\n[\s\S]*?\r?\n---[ \t]*\r?\n?/, '')
      // 指令块：%%{ ... }%%（内部可能含换行与嵌套花括号）
      .replace(/[%]{2}\s*\{[\s\S]*?\}\s*[%]{2}/g, '')
  )
}

/**
 * 内容里的外部资源地址。
 *
 * Mermaid 有几条**不经过配置指令**的外发通道，最直接的是 `img` 形状：
 * `A@{ img: "https://…" }` 会让渲染库自己 `new Image()` 并设 src，
 * 请求在渲染期就发出去了——剥离 init/frontmatter 拦不住，事后清理产物也晚了。
 * 这类地址还可能出现在 participant/actor 的图片、`click` 目标里。
 *
 * 所以把限制放在"交给渲染库之前"：源码里只要出现指向别处的地址，
 * 就整块拒绝渲染，退回源码面板。图表内容本来不需要引用外部主机；
 * 真需要时应该由部署配置给出受控地址，而不是由内容自述。
 *
 * 判定前先按 URL 解析器规则归一（删 tab/LF/CR、`\` 当 `/`）：
 * `/\evil.example/x.png` 与 `https:/evil.example/x` 都能被解析成外部地址，
 * 只匹配 `https?://` 会漏掉它们。渲染库拿到的仍是**原始**源码，
 * 归一仅用于判定，不改写内容。
 */
const EXTERNAL_URL_IN_SOURCE = /(?:[a-z][a-z0-9+.-]*:)?\/\/[^\s"'`)\]>]*/gi
const SINGLE_SLASH_SCHEME = /\bhttps?:\/(?!\/)[^\s"'`)\]>]*/gi
const DANGEROUS_SCHEME_IN_SOURCE = /\b(?:file|javascript|vbscript):[^\s"'`)\]>]*/gi
/** data: 里只有位图是安全的，其它（尤其 svg/html）可承载脚本 */
const NON_BITMAP_DATA = /\bdata:(?!image\/(?:png|jpe?g|gif|webp))[^\s"'`)\]>]*/gi

/**
 * 这个地址是否落在受控源清单里。
 * 协议相对地址按当前页面的协议解析——浏览器就是这么取的。
 */
function isControlledSourceUrl(url: string, allowedOrigins: string[]): boolean {
  if (allowedOrigins.length === 0) return false
  const base = typeof location !== 'undefined' ? location.href : 'https://localhost/'
  try {
    return allowedOrigins.includes(new URL(url, base).origin)
  } catch {
    return false
  }
}

/**
 * 返回源码里第一个**不受控**的外部地址；没有则返回 null。
 *
 * @param allowedOrigins 部署配置显式指定的受控源（图表里的图片可以指向它们）
 */
export function findExternalResourceUrl(
  code: string,
  allowedOrigins: string[] = [],
): string | null {
  const normalized = normalizeUrlForPolicy(code)
  for (const re of [
    EXTERNAL_URL_IN_SOURCE,
    SINGLE_SLASH_SCHEME,
    DANGEROUS_SCHEME_IN_SOURCE,
    NON_BITMAP_DATA,
  ]) {
    re.lastIndex = 0
    let match: RegExpExecArray | null
    while ((match = re.exec(normalized)) !== null) {
      // 受控源放行：配置里明确信任的目标不算"内容自述的外发"
      if (isControlledSourceUrl(match[0], allowedOrigins)) continue
      return match[0]
    }
  }
  return null
}

/** 读取当前主题下的实际色值，让图表跟随双主题而不是固定浅色。 */
function readThemeColors(): Record<string, string> | null {
  if (typeof document === 'undefined') return null
  const style = getComputedStyle(document.documentElement)
  const pick = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  return {
    background: pick('--color-bg-tertiary', '#ffffff'),
    primaryColor: pick('--color-bg-tertiary', '#ececff'),
    primaryTextColor: pick('--color-text-primary', '#333333'),
    primaryBorderColor: pick('--color-primary-500', '#9370db'),
    lineColor: pick('--color-border-default', '#666666'),
    secondaryColor: pick('--color-bg-secondary', '#f4f4f4'),
    tertiaryColor: pick('--color-bg-tertiary', '#f9f9f9'),
  }
}

const defaultMermaidLoader: MermaidLoader = async () => {
  const mermaid = (await import('mermaid')).default
  return mermaid as unknown as MermaidLike
}

/**
 * mxgraph 运行时只初始化一次。
 *
 * `factory({})` 每次调用都会新建一整份运行时，而 `mxEvent` 的监听器表与
 * `mxCodecRegistry` 是全局共享的：后一份会覆盖前一份，导致早先创建的图实例
 * 无法再通过 destroy() 解绑自己的 document 监听器——表现为每渲染一次泄漏一批。
 * 因此缓存同一个实例，所有渲染共用。
 */
let mxRuntime: Promise<MxRuntime | null> | null = null

async function loadMxRuntime(): Promise<MxRuntime | null> {
  if (mxRuntime) return mxRuntime

  mxRuntime = (async () => {
    // mxgraph 通过一组"可选全局变量"做初始化配置。打包版在 ESM 严格模式下
    // 对这些变量赋值会抛 ReferenceError，所以必须在载入前把它们定义出来；
    // 同时把"是否加载资源/样式"显式关掉——默认开启意味着渲染时会向外请求文件。
    const g = globalThis as Record<string, unknown>
    g.mxLoadResources = false
    g.mxForceIncludes = false
    g.mxLoadStylesheets = false
    g.mxBasePath = ''
    g.mxImageBasePath = ''
    g.mxResourceExtension = '.txt'

    // mxgraph 是本地依赖（不再从公共 CDN 加载），初始化时才会接触 document
    const factory = (await import('mxgraph')).default as unknown as MxFactory
    const mx = factory({}) as unknown as MxRuntime
    if (!mx?.mxGraph || !mx?.mxCodec || !mx?.mxUtils) return null
    registerCodecConstructors(mx)
    // 加载期建的两个模板实例会永久占着 document 监听器，先释放掉
    disposeCodecTemplates(mx)
    return mx
  })()

  return mxRuntime
}

/** 仅供测试：丢弃缓存的运行时，让下一次加载重新初始化。 */
export function __resetMxRuntimeForTests(): void {
  mxRuntime = null
}

const defaultDrawioLoader: DrawioLoader = async () => {
  const mx = await loadMxRuntime()
  if (!mx) return null

  return {
    renderToHtml: async (xml: string) => {
      // 在**惰性文档**里渲染：这个文档没有浏览上下文，其中的 <image href>
      // 与 CSS url() 都不会发请求。放在主文档（哪怕是 detached 容器）里渲染，
      // mxgraph 刷新时就会真的去取外部图片——那时再清理产物已经晚了。
      const inertDoc = document.implementation.createHTMLDocument('drawio-render')
      const container = inertDoc.createElement('div')
      container.style.width = '100%'
      container.style.minHeight = '200px'
      inertDoc.body.appendChild(container)

      const graph = new mx.mxGraph(container)
      try {
        graph.setEnabled(false)
        // mxgraph 4.x 的 XML 解析入口是 mxUtils.parseXml（不是 parseXmlString）
        const doc = mx.mxUtils.parseXml(xml)
        const codec = new mx.mxCodec()
        const model = graph.getModel()
        codec.decode(doc.documentElement, model)
        graph.refresh()
        // 解码失败时 mxgraph 不抛错，只是留下空模型。宁可显式报错，
        // 也不要让一张空图冒充"渲染成功"——那会掩盖真实的安全/兼容问题。
        //
        // 注意不能数根节点的子节点：默认模型本来就有两层根节点，
        // 无论解码成功与否都是 1，这个判断会永远为假。要数真正画出来的
        // 顶点与边。
        const cells = model.getDescendants(model.getRoot()) ?? []
        const drawable = cells.filter((cell) => cell.isVertex?.() || cell.isEdge?.())
        if (drawable.length === 0) {
          throw new Error('draw.io 内容无法解码')
        }
        // 先取出产物再销毁：destroy() 会清空 container
        return container.innerHTML
      } finally {
        // 成功、解码失败、空模型、其它异常都必须解绑。
        // mxgraph 在构造时往 document 上挂了 keydown/keyup/mouseup 等监听器，
        // 只有 destroy() 才会移除；否则每渲染一次就泄漏一批。
        graph.destroy()
        container.remove()
      }
    },
  }
}

function errorBox(message: string): string {
  return `<div class="diagram-error">${escapeHtml(message)}</div>`
}

/**
 * 渲染失败统一降级为可见的错误提示，不让单张图表失败拖垮整条消息。
 * 三种图表格式共用这一层，避免各写一遍 try/catch。
 */
async function renderSafely(label: string, render: () => Promise<string>): Promise<string> {
  try {
    return await render()
  } catch (e) {
    console.error(`${label} render error:`, e)
    return errorBox(`${label} 渲染失败: ${e instanceof Error ? e.message : '未知错误'}`)
  }
}

/** 渲染 Mermaid，产物经过清理。 */
export async function renderMermaidBlock(
  code: string,
  containerId: string,
  deps: { load?: MermaidLoader; plantumlServer?: string } = {},
): Promise<string> {
  return renderSafely('Mermaid', async () => {
    const load = deps.load ?? defaultMermaidLoader
    const mermaid = await load()
    const themeVariables = deps.load ? undefined : readThemeColors()
    mermaid.initialize(
      themeVariables ? { ...MERMAID_OPTIONS, themeVariables } : { ...MERMAID_OPTIONS },
    )
    // 指令在渲染前剥离：内容自述的 securityLevel/themeCSS 不能覆盖应用配置
    const safeCode = stripMermaidDirectives(code).trim()
    // 渲染库会自己取图的形状（img/actor 图片等）必须在渲染前就拦住，
    // 否则请求已经发出去了，清理产物只是事后补救。
    const external = findExternalResourceUrl(safeCode, controlledImageOrigins(deps.plantumlServer))
    if (external) {
      throw new Error(`内容包含外部资源地址，已停止渲染：${external}`)
    }
    const { svg } = await mermaid.render(containerId, safeCode)
    return sanitizeDiagramSvg(svg, { imageOrigins: controlledImageOrigins(deps.plantumlServer) })
  })
}

/** 渲染 draw.io，产物经过清理。 */
export async function renderDrawioBlock(
  code: string,
  deps: { load?: DrawioLoader; plantumlServer?: string } = {},
): Promise<string> {
  return renderSafely('draw.io', async () => {
    const load = deps.load ?? defaultDrawioLoader
    const renderer = await load()
    if (!renderer) return errorBox('draw.io 渲染库不可用，已显示源码')
    return sanitizeDiagramSvg(await renderer.renderToHtml(code), {
      // 与 Markdown 图片同一份受控清单：配置里信任的图床在图表里也应当可用
      imageOrigins: controlledImageOrigins(deps.plantumlServer),
    })
  })
}

/**
 * 渲染 PlantUML。
 * 地址由受控配置（VITE_PLANTUML_SERVER）决定；未配置时展示源码，
 * 不回退任何公共渲染服务，也不在客户端发起额外请求。
 */
export async function renderPlantumlBlock(code: string, server?: string): Promise<string> {
  const url = resolvePlantUmlUrl(code, server)

  if (!url) {
    // 源码已经由外层"代码"面板承载，这里只说明为什么没有预览，避免同一段源码出现两次
    return (
      `<div class="plantuml-source plantuml-source--unconfigured">` +
      `<div class="plantuml-source-hint">未配置自托管 PlantUML 服务，已显示源码</div>` +
      `</div>`
    )
  }

  return `<img src="${escapeHtml(url)}" class="plantuml-diagram" alt="PlantUML Diagram" loading="lazy" />`
}
