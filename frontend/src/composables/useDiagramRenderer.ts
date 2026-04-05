/**
 * 图表渲染 Composable
 * 支持 Mermaid、draw.io XML、PlantUML 三种图表格式的渲染
 */

import { escapeHtml } from '@/utils/jsonHighlight'
import { DIAGRAM_LANGUAGES } from './useMarkdown'

// 动态导入以减少初始包体积
let mermaidInstance: Awaited<ReturnType<typeof import('mermaid')>> | null = null
let plantumlEncoderInstance: ((code: string) => string) | null = null

/**
 * 初始化 Mermaid
 */
async function getMermaid() {
  if (!mermaidInstance) {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      fontFamily: 'var(--font-body, inherit)',
    })
    mermaidInstance = mermaid
  }
  return mermaidInstance
}

/**
 * 获取 PlantUML Encoder
 */
async function getPlantumlEncoder(): Promise<(code: string) => string> {
  if (!plantumlEncoderInstance) {
    const encoder = (await import('plantuml-encoder')).default
    plantumlEncoderInstance = encoder
  }
  return plantumlEncoderInstance
}

/**
 * 渲染 Mermaid 图表
 */
async function renderMermaid(code: string, containerId: string): Promise<string> {
  try {
    const mermaid = await getMermaid()
    const { svg } = await mermaid.render(containerId, code.trim())
    return svg
  } catch (e) {
    console.error('Mermaid render error:', e)
    return `<div class="diagram-error">Mermaid 渲染失败: ${e instanceof Error ? e.message : '未知错误'}</div>`
  }
}

/**
 * 渲染 draw.io 图表
 * 使用 mxGraph 从 window.mxClient 获取
 */
async function renderDrawio(code: string, containerId: string): Promise<string> {
  // 检查 mxClient 是否可用
  if (typeof window.mxClient === 'undefined') {
    return `<div class="diagram-error">draw.io 渲染库未加载，请刷新页面或检查网络连接</div>`
  }

  return new Promise((resolve) => {
    const container = document.createElement('div')
    container.id = containerId
    container.style.width = '100%'
    container.style.minHeight = '200px'
    container.style.backgroundColor = '#ffffff'

    // mxClient.init 需要 DOM 就绪
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
      initGraph()
    } else {
      window.addEventListener('load', initGraph)
    }

    function initGraph() {
      try {
        // 创建图表实例
        const graph = new window.mxGraph(container)

        // 只读模式
        graph.setEnabled(false)

        // 解析 XML 并渲染
        const codec = new window.mxCodec()
        const doc = window.mxUtils.parseXmlString(code)
        codec.decode(doc.documentElement, graph.getModel())

        // 触发渲染
        graph.refresh()

        resolve(container.innerHTML)
      } catch (e) {
        console.error('mxGraph render error:', e)
        resolve(`<div class="diagram-error">draw.io 渲染失败: ${e instanceof Error ? e.message : '未知错误'}</div>`)
      }
    }
  })
}

/**
 * 渲染 PlantUML 图表（通过 plantuml.com 在线服务）
 */
async function renderPlantuml(code: string): Promise<string> {
  try {
    const encoder = await getPlantumlEncoder()
    const encoded = encoder(code.trim())
    const url = `https://www.plantuml.com/plantuml/svg/${encoded}`
    return `<img src="${url}" class="plantuml-diagram" alt="PlantUML Diagram" loading="lazy" />`
  } catch (e) {
    console.error('PlantUML render error:', e)
    return `<div class="diagram-error">PlantUML 渲染失败: ${e instanceof Error ? e.message : '未知错误'}</div>`
  }
}

/**
 * 图表 tab 切换处理函数（事件委托）
 */
function handleDiagramTabClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const tabButton = target.closest('.diagram-tab') as HTMLElement | null
  if (!tabButton) return

  const wrapper = tabButton.closest('.diagram-wrapper') as HTMLElement | null
  if (!wrapper) return

  const tabName = tabButton.dataset.tab as 'preview' | 'code'
  const previewPanel = wrapper.querySelector('.diagram-preview') as HTMLElement | null
  const codePanel = wrapper.querySelector('.diagram-code') as HTMLElement | null
  const previewTab = wrapper.querySelector('.diagram-tab[data-tab="preview"]') as HTMLElement | null
  const codeTab = wrapper.querySelector('.diagram-tab[data-tab="code"]') as HTMLElement | null

  if (tabName === 'preview') {
    previewPanel?.style.setProperty('display', 'block')
    codePanel?.style.setProperty('display', 'none')
    previewTab?.classList.add('active')
    codeTab?.classList.remove('active')
  } else {
    previewPanel?.style.setProperty('display', 'none')
    codePanel?.style.setProperty('display', 'block')
    previewTab?.classList.remove('active')
    codeTab?.classList.add('active')
  }
}

// 标记是否已经绑定过事件
let isEventListenerBound = false

/**
 * 绑定图表 tab 切换事件（仅绑定一次）
 */
function bindDiagramEvents() {
  if (isEventListenerBound) return
  isEventListenerBound = true
  document.addEventListener('click', handleDiagramTabClick)
}

/**
 * 解析并渲染所有图表代码块
 * @param htmlContent Markdown 渲染后的 HTML 内容
 * @returns 图表渲染后的 HTML 内容
 */
export function useDiagramRenderer() {
  const renderDiagrams = async (htmlContent: string): Promise<string> => {
    if (!htmlContent) return htmlContent

    // 绑定 tab 切换事件
    bindDiagramEvents()

    const tempDiv = document.createElement('div')
    tempDiv.innerHTML = htmlContent

    // 查找所有图表代码块
    const codeBlocks = tempDiv.querySelectorAll<HTMLElement>('pre.code-block[data-lang]')

    for (const pre of codeBlocks) {
      const lang = pre.dataset.lang?.toLowerCase()
      if (!lang || !DIAGRAM_LANGUAGES.includes(lang)) {
        continue
      }

      const codeElement = pre.querySelector('code')
      if (!codeElement) continue

      const code = codeElement.textContent || ''
      const containerId = `diagram-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`

      let rendered = ''

      try {
        if (lang === 'mermaid') {
          rendered = await renderMermaid(code, containerId)
        } else if (lang === 'drawio') {
          rendered = await renderDrawio(code, containerId)
        } else if (lang === 'plantuml') {
          rendered = await renderPlantuml(code)
        }
      } catch (e) {
        console.error(`Diagram render error (${lang}):`, e)
        rendered = `<div class="diagram-error">${lang} 渲染失败</div>`
      }

      // 构建带 tab 切换的双展示结构
      const tabsHtml = `
        <div class="diagram-tabs">
          <div class="diagram-tabs-left">
            <button class="diagram-tab active" data-tab="preview">预览</button>
            <button class="diagram-tab" data-tab="code">代码</button>
          </div>
          <button class="copy-btn" data-code>复制</button>
        </div>
        <div class="diagram-content" data-lang="${lang}">
          <div class="diagram-preview">${rendered}</div>
          <div class="diagram-code"><pre class="code-block"><code>${escapeHtml(code)}</code></pre></div>
        </div>
      `

      // 替换代码块为渲染结果
      const wrapper = document.createElement('div')
      wrapper.className = `diagram-wrapper diagram-${lang}`
      wrapper.innerHTML = tabsHtml

      // 检查是否渲染成功（包含 svg 或 img）
      const previewDiv = wrapper.querySelector('.diagram-preview')
      const hasRendered = previewDiv?.querySelector('svg, img')
      if (!hasRendered) {
        // 渲染失败，隐藏预览面板
        const previewPanel = wrapper.querySelector('.diagram-preview') as HTMLElement
        if (previewPanel) previewPanel.style.display = 'none'
        // 切换到代码面板
        const codePanel = wrapper.querySelector('.diagram-code') as HTMLElement
        if (codePanel) codePanel.style.display = 'block'
        const previewTab = wrapper.querySelector('.diagram-tab[data-tab="preview"]') as HTMLElement
        const codeTab = wrapper.querySelector('.diagram-tab[data-tab="code"]') as HTMLElement
        if (previewTab) previewTab.classList.remove('active')
        if (codeTab) codeTab.classList.add('active')
      }

      pre.replaceWith(wrapper)
    }

    return tempDiv.innerHTML
  }

  const unbindDiagramEvents = () => {
    if (isEventListenerBound) {
      document.removeEventListener('click', handleDiagramTabClick)
      isEventListenerBound = false
    }
  }

  return {
    renderDiagrams,
    unbindDiagramEvents,
  }
}
