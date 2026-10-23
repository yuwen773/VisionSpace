/**
 * 图表渲染 Composable
 * 支持 Mermaid、draw.io XML、PlantUML 三种图表格式的渲染。
 *
 * 安全边界：
 * - 渲染产物一律先清理再进入 innerHTML（见 diagramRenderers / diagramSanitizer）
 * - 图表 tab 切换使用组件内事件委托，不注册全局 document 监听，避免泄漏
 * - PlantUML 只指向受控自托管服务，未配置时展示源码
 */

import { escapeHtml } from '@/utils/jsonHighlight'
import {
  EXTERNAL_LINK_REL,
  isExternalHref,
  isSafeLinkHref,
  sanitizeDiagramSvg,
} from '@/utils/diagramSanitizer'
import { DIAGRAM_LANGUAGES } from './useMarkdown'
import {
  controlledImageOrigins,
  renderDrawioBlock,
  renderMermaidBlock,
  renderPlantumlBlock,
  type DrawioLoader,
  type MermaidLoader,
} from './diagramRenderers'

export type DiagramRendererDeps = {
  mermaid?: { load?: MermaidLoader }
  drawio?: { load?: DrawioLoader }
  /** 受控 PlantUML 自托管服务地址；空值表示未配置 */
  plantumlServer?: string
}

type DiagramLang = 'mermaid' | 'drawio' | 'plantuml'

function isDiagramLang(lang: string | undefined): lang is DiagramLang {
  return !!lang && DIAGRAM_LANGUAGES.includes(lang.toLowerCase())
}

/** 清理 Markdown 产物里的链接，危险协议与站外链接的处理与渲染期保持同一套规则。 */
export function sanitizeLinks(root: ParentNode): void {
  for (const anchor of Array.from(root.querySelectorAll('a[href]'))) {
    const href = anchor.getAttribute('href') ?? ''
    if (!isSafeLinkHref(href)) {
      anchor.removeAttribute('href')
      anchor.setAttribute('data-blocked-href', '')
    } else if (isExternalHref(href)) {
      anchor.setAttribute('rel', EXTERNAL_LINK_REL)
      anchor.setAttribute('target', '_blank')
    }
  }
}

function buildDiagramMarkup(lang: DiagramLang, rendered: string, code: string): string {
  return (
    `<div class="diagram-tabs">` +
    `<div class="diagram-tabs-left">` +
    `<button type="button" class="diagram-tab active" data-tab="preview">预览</button>` +
    `<button type="button" class="diagram-tab" data-tab="code">代码</button>` +
    `</div>` +
    `<button type="button" class="copy-btn" data-code>复制</button>` +
    `</div>` +
    `<div class="diagram-content" data-lang="${lang}">` +
    `<div class="diagram-preview">${rendered}</div>` +
    `<div class="diagram-code"><pre class="code-block"><code>${escapeHtml(code)}</code></pre></div>` +
    `</div>`
  )
}

/** 渲染失败或降级时，让代码面板成为默认可见的那个。 */
function preferCodePanel(wrapper: HTMLElement): void {
  const previewPanel = wrapper.querySelector<HTMLElement>('.diagram-preview')
  const codePanel = wrapper.querySelector<HTMLElement>('.diagram-code')
  if (previewPanel) previewPanel.style.display = 'none'
  if (codePanel) codePanel.style.display = 'block'
  wrapper.querySelector('.diagram-tab[data-tab="preview"]')?.classList.remove('active')
  wrapper.querySelector('.diagram-tab[data-tab="code"]')?.classList.add('active')
}

export function useDiagramRenderer(deps: DiagramRendererDeps = {}) {
  const plantumlServer =
    deps.plantumlServer ?? (import.meta.env.VITE_PLANTUML_SERVER as string | undefined)

  const renderers: Record<DiagramLang, (code: string, containerId: string) => Promise<string>> = {
    mermaid: (code, id) =>
      renderMermaidBlock(code, id, { load: deps.mermaid?.load, plantumlServer }),
    drawio: (code) => renderDrawioBlock(code, { load: deps.drawio?.load, plantumlServer }),
    plantuml: (code) => renderPlantumlBlock(code, plantumlServer),
  }

  const renderOne = (lang: DiagramLang, code: string, containerId: string) =>
    renderers[lang](code, containerId)

  /**
   * 解析并渲染所有图表代码块。
   * @param htmlContent Markdown 渲染后的 HTML 内容
   * @returns 图表渲染后的 HTML 内容
   */
  const renderDiagrams = async (htmlContent: string): Promise<string> => {
    if (!htmlContent) return htmlContent

    const tempDiv = document.createElement('div')
    tempDiv.innerHTML = htmlContent

    // 先收敛链接，再处理图表
    sanitizeLinks(tempDiv)

    const codeBlocks = Array.from(
      tempDiv.querySelectorAll<HTMLElement>('pre.code-block[data-lang]'),
    )

    for (const pre of codeBlocks) {
      const lang = pre.dataset.lang
      if (!isDiagramLang(lang)) continue

      const codeElement = pre.querySelector('code')
      if (!codeElement) continue

      const code = codeElement.textContent || ''
      const containerId = `diagram-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`

      let rendered = ''
      try {
        rendered = await renderOne(lang.toLowerCase() as DiagramLang, code, containerId)
      } catch (e) {
        console.error(`Diagram render error (${lang}):`, e)
        rendered = `<div class="diagram-error">${escapeHtml(lang)} 渲染失败</div>`
      }

      // 渲染产物再走一次清理，防止渲染器自身拼出危险内容。
      // 受控图片源与 Markdown 图片策略共用同一份配置，避免"同一张图
      // 在 Markdown 里能加载、在图表里被摘掉"的不一致。
      rendered = sanitizeDiagramSvg(rendered, {
        imageOrigins: controlledImageOrigins(plantumlServer),
      })

      const wrapper = document.createElement('div')
      wrapper.className = `diagram-wrapper diagram-${lang.toLowerCase()}`
      // 局部构建 DOM：不把未清理的字符串直接交给 innerHTML
      wrapper.innerHTML = buildDiagramMarkup(lang.toLowerCase() as DiagramLang, rendered, code)

      const hasRendered = wrapper.querySelector('.diagram-preview svg, .diagram-preview img')
      if (!hasRendered) {
        preferCodePanel(wrapper)
      }

      pre.replaceWith(wrapper)
    }

    return tempDiv.innerHTML
  }

  return { renderDiagrams }
}

// 交互（预览/代码切换、复制）在 utils/diagramEvents 中，
// 由宿主组件用事件委托绑定，不注册全局监听。
