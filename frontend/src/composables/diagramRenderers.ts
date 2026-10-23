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

type MermaidLike = {
  initialize: (options: Record<string, unknown>) => void
  render: (id: string, code: string) => Promise<{ svg: string }>
}

export type MermaidLoader = () => Promise<MermaidLike>

type DrawioLike = {
  /** 返回渲染后的 SVG/HTML 字符串 */
  renderToHtml: (xml: string) => Promise<string>
}

export type DrawioLoader = () => Promise<DrawioLike | null>

/** mxgraph 打包版导出的工厂：调用后返回 mxGraph/mxCodec/mxUtils 等类型。 */
type MxFactory = (options: object) => Record<string, unknown>

/** 渲染 draw.io 时需要的最小接口，避免把整个库的类型引进来。 */
type MxRuntime = {
  mxGraph: new (container: HTMLElement) => {
    setEnabled: (enabled: boolean) => void
    getModel: () => unknown
    refresh: () => void
  }
  mxCodec: new () => { decode: (node: Element, model: unknown) => void }
  mxUtils: { parseXml: (xml: string) => XMLDocument }
}

const MERMAID_OPTIONS = {
  startOnLoad: false,
  theme: 'default',
  // strict：禁止 HTML 标签、click 交互与危险链接，配合产物清理构成双保险
  securityLevel: 'strict',
  htmlLabels: false,
  fontFamily: 'var(--font-body, inherit)',
} as const

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

const defaultDrawioLoader: DrawioLoader = async () => {
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

  return {
    renderToHtml: async (xml: string) => {
      const container = document.createElement('div')
      container.style.width = '100%'
      container.style.minHeight = '200px'
      const graph = new mx.mxGraph(container)
      graph.setEnabled(false)
      // mxgraph 4.x 的 XML 解析入口是 mxUtils.parseXml（不是 parseXmlString）
      const doc = mx.mxUtils.parseXml(xml)
      const codec = new mx.mxCodec()
      codec.decode(doc.documentElement, graph.getModel())
      graph.refresh()
      return container.innerHTML
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
  deps: { load?: MermaidLoader } = {},
): Promise<string> {
  return renderSafely('Mermaid', async () => {
    const load = deps.load ?? defaultMermaidLoader
    const mermaid = await load()
    const themeVariables = deps.load ? undefined : readThemeColors()
    mermaid.initialize(
      themeVariables ? { ...MERMAID_OPTIONS, themeVariables } : { ...MERMAID_OPTIONS },
    )
    const { svg } = await mermaid.render(containerId, code.trim())
    return sanitizeDiagramSvg(svg)
  })
}

/** 渲染 draw.io，产物经过清理。 */
export async function renderDrawioBlock(
  code: string,
  deps: { load?: DrawioLoader } = {},
): Promise<string> {
  return renderSafely('draw.io', async () => {
    const load = deps.load ?? defaultDrawioLoader
    const renderer = await load()
    if (!renderer) return errorBox('draw.io 渲染库不可用，已显示源码')
    return sanitizeDiagramSvg(await renderer.renderToHtml(code))
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
