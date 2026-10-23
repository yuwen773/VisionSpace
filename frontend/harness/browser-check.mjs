/**
 * 真实浏览器验收：在 Chrome 里跑隔离 harness，采集三类证据。
 *
 * 1) 最终 DOM —— 危险元素/属性是否真的不在 DOM 里
 * 2) 脚本执行 —— payload 是否真的跑了（window.__executed 标记）
 * 3) 实际网络 —— 是否真的向第三方主机发出请求（Network.requestWillBeSent）
 * 4) CSP 违规 —— 策略是否真的在浏览器里执行（SecurityPolicyViolation）
 *
 * 不使用公开 CDN 或公共渲染服务；页面由本地静态服务器提供。
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..', 'dist-harness')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
}

function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let rel = decodeURIComponent((req.url || '/').split('?')[0])
      if (rel === '/' || rel === '') rel = '/index.html'
      const file = path.join(root, rel.replace(/^\/+/, ''))
      if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404).end('not found')
        return
      }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
      fs.createReadStream(file).pipe(res)
    })
    server.listen(0, '127.0.0.1', () => resolve(server))
  })
}

const cdpPort = Number(process.env.CDP_PORT || 0)
const getJson = (p, method = 'GET') =>
  new Promise((resolve, reject) => {
    const r = http.request(
      { host: '127.0.0.1', port: cdpPort, path: p, method, family: 4 },
      (resp) => {
        let d = ''
        resp.on('data', (c) => (d += c))
        resp.on('end', () => {
          try {
            resolve(JSON.parse(d))
          } catch {
            reject(new Error(`bad json: ${d.slice(0, 200)}`))
          }
        })
      },
    )
    r.on('error', reject)
    r.end()
  })

// 主题由查询参数决定：harness 在渲染前把 class 挂到 <html> 上，
// 渲染器按 CSS 变量取色。不传则用页面默认主题。
const theme = process.env.HARNESS_THEME || ''

const server = await serve()
const port = server.address().port
const pageUrl = `http://127.0.0.1:${port}/index.html${theme ? `?theme=${theme}` : ''}`
console.log(`[harness] serving ${root} at ${pageUrl}`)

const target = await getJson('/json/new?' + encodeURIComponent(pageUrl), 'PUT')
const ws = new WebSocket(target.webSocketDebuggerUrl)

let msgId = 0
const pending = new Map()
const events = []
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const id = ++msgId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })

ws.onmessage = (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pending.has(m.id)) {
    pending.get(m.id)(m)
    pending.delete(m.id)
  } else if (m.method) {
    events.push(m)
  }
}
await new Promise((r) => (ws.onopen = r))

await send('Runtime.enable')
await send('Network.enable')
await send('Page.enable')
await send('Log.enable')

// 重新加载，确保能捕获全部请求
await send('Page.reload', { ignoreCache: true })

// 等待渲染完成：harness 的编排结果落地后才会出现对应节点
const waitForReady = async (timeoutMs = 15000) => {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const ready = await send('Runtime.evaluate', {
      expression:
        '!!document.querySelector(\'[data-case="normal-drawio"] .diagram-preview svg\') && ' +
        "document.getElementById('harness-plantuml-orchestrated')?.children.length > 0",
      returnByValue: true,
    })
    if (ready.result?.result?.value) return true
    await new Promise((r) => setTimeout(r, 250))
  }
  return false
}
const ready = await waitForReady()
if (!ready) console.log('[harness] WARN: 等待渲染超时，结果可能不完整')

// 生命周期用例会重复渲染并卸载，等它自己报告完成（而不是固定睡眠）
const waitForLifecycle = async (timeoutMs = 20000) => {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const r = await send('Runtime.evaluate', {
      expression: 'window.__lifecycle?.done === true',
      returnByValue: true,
    })
    if (r.result?.result?.value) return true
    await new Promise((r) => setTimeout(r, 100))
  }
  return false
}
const lifecycleDone = await waitForLifecycle()
// 渲染后再等一拍，让迟到的请求也被记录
await new Promise((r) => setTimeout(r, 1500))
if (!lifecycleDone) console.log('[harness] WARN: 生命周期用例未在超时内完成')

const evaluate = async (expression) => {
  const r = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  })
  if (r.result?.exceptionDetails) {
    throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 400))
  }
  return r.result?.result?.value
}

const dom = await evaluate(`(() => {
  const root = document.getElementById('harness-root')
  const svgs = [...root.querySelectorAll('.diagram-preview svg')]
  const previewText = (id) => {
    const node = root.querySelector('[data-case="' + id + '"]')
    return node ? node.textContent.slice(0, 200) : null
  }
  const all = [...root.querySelectorAll('*')]
  const scripts = all.filter(el => el.tagName.toLowerCase() === 'script').length
  const eventAttrs = all.flatMap(el => [...el.attributes].filter(a => /^on/i.test(a.name)).map(a => a.name))
  const dangerousHrefs = [...root.querySelectorAll('[href],[src]')]
    .map(el => el.getAttribute('href') || el.getAttribute('src'))
    .filter(v => v && /^\\s*(javascript|vbscript|data:text\\/html)/i.test(v))
  const external = [...root.querySelectorAll('[href],[src]')]
    .map(el => el.getAttribute('href') || el.getAttribute('src'))
    .filter(v => v && /^https?:/i.test(v) && !v.startsWith(location.origin))
  // 配置了受控 PlantUML 服务时，编排结果只能出现该服务的地址
  const orchestrated = document.getElementById('harness-plantuml-orchestrated')
  const plantumlServer = document.getElementById('harness-plantuml-server')?.getAttribute('data-value') || ''
  const orchestratedSrc = orchestrated?.querySelector('.plantuml-diagram')?.getAttribute('src') || null
  // 恶意用例里刻意写了 https://evil.example/steal，用来验证内容不能改变请求目标
  const evilCase = root.querySelector('[data-case="evil-plantuml-target"]')
  const evilCaseSrcs = evilCase
    ? [...evilCase.querySelectorAll('[href],[src]')].map(el => el.getAttribute('href') || el.getAttribute('src'))
    : []
  return {
    evilPlantumlSrcs: evilCaseSrcs,
    evilPlantumlText: evilCase ? evilCase.textContent.slice(0, 160) : null,
    plantumlServer,
    orchestratedSrc,
    orchestratedOtherHosts: orchestrated
      ? [...orchestrated.querySelectorAll('[href],[src]')]
          .map(el => el.getAttribute('href') || el.getAttribute('src'))
          .filter(v => v && /^https?:/i.test(v) && (!plantumlServer || !v.startsWith(plantumlServer)))
      : [],
    orchestratedSourceFallback: !!orchestrated?.querySelector('.plantuml-source--unconfigured'),
    hasLoadableThumbnail: !!root.querySelector('.message-images img'),
    csp: document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content || null,
    mermaidSvgCount: svgs.length,
    mermaidSvgHasScript: svgs.some(s => s.querySelector('script')),
    mermaidSvgHasOnAttr: svgs.some(s => [...s.querySelectorAll('*')].some(el => [...el.attributes].some(a => /^on/i.test(a.name)))),
    drawioSvgCount: root.querySelectorAll('[data-case="normal-drawio"] .diagram-preview svg').length,
    // 空图（只有两层空 <g>）同样会被上面的计数算进去，所以单独看有没有形状
    drawioShapeCount: root.querySelectorAll(
      '[data-case="normal-drawio"] .diagram-preview svg rect, ' +
        '[data-case="normal-drawio"] .diagram-preview svg path, ' +
        '[data-case="normal-drawio"] .diagram-preview svg text',
    ).length,
    drawioPreviewText: previewText('normal-drawio'),
    // 审查缺陷回归：这三项都必须为空/为假
    drawioCaseExternalRefs: [...root.querySelectorAll('[data-case="drawio-image-request"] [href],[data-case="drawio-image-request"] [src]')]
      .map(el => el.getAttribute('href') || el.getAttribute('src'))
      .filter(v => v && /^https?:/i.test(v)),
    // 只看预览区：代码面板本来就该原样显示源码，那里出现地址是正常的
    // 这段字符串会被求值进页面，反斜杠要写两次才到得了浏览器
    mermaidCssCaseLeaksUrl: /unsplash\\.com\\/component43-mermaid/.test(
      root.querySelector('[data-case="mermaid-escaped-css"] .diagram-preview')?.innerHTML ?? '',
    ),
    mermaidCssCaseHasSvg: !!root.querySelector('[data-case="mermaid-escaped-css"] .diagram-preview svg'),
    mermaidLooseCaseHasImg: !!root.querySelector('[data-case="mermaid-loose-directive"] .diagram-preview img'),
    mermaidLooseCaseHasSvg: !!root.querySelector('[data-case="mermaid-loose-directive"] .diagram-preview svg'),
    mermaidImageShapeRefs: [...root.querySelectorAll('[data-case="mermaid-image-shape"] [href],[data-case="mermaid-image-shape"] [src],[data-case="mermaid-image-shape"] image')]
      .map(el => el.getAttribute('href') || el.getAttribute('src') || '')
      .filter(v => /^https?:/i.test(v)),
    // 拒渲染时应当明确说明原因，而不是留一张空图
    mermaidImageShapeRefused: /外部资源地址/.test(
      root.querySelector('[data-case="mermaid-image-shape"] .diagram-preview')?.textContent ?? '',
    ),
    mermaidStyleUrlRefs: [
      ...root.querySelectorAll(
        '[data-case="mermaid-style-url"] [href],[data-case="mermaid-style-url"] [src]',
      ),
    ]
      .map(el => el.getAttribute('href') || el.getAttribute('src') || '')
      .filter(v => /^https?:/i.test(v)),
    mermaidStyleUrlRefused: /外部资源地址/.test(
      root.querySelector('[data-case="mermaid-style-url"] .diagram-preview')?.textContent ?? '',
    ),
    mermaidBackslashImgRefused: /外部资源地址/.test(
      root.querySelector('[data-case="mermaid-backslash-img"] .diagram-preview')?.textContent ??
        '',
    ),
    // loose 指令被剥离后，标签只能以文本出现，不能变成真的 <b>/foreignObject
    mermaidLooseCaseHasHtmlTag: !!root.querySelector(
      '[data-case="mermaid-loose-directive"] .diagram-preview b, ' +
        '[data-case="mermaid-loose-directive"] .diagram-preview foreignObject',
    ),
    mermaidLooseCaseText: root
      .querySelector('[data-case="mermaid-loose-directive"] .diagram-preview')
      ?.textContent?.slice(0, 120) ?? null,
    // 渲染期中间 DOM：清理器会在最后一步摘掉危险内容，
    // 只看最终产物会把"渲染期确实生成过"这件事抹平
    intermediate: window.__intermediate || null,
    // 图表是否真的跟随主题变量（内置主题会忽略 themeVariables）
    mermaidNodeFill: (() => {
      const node = root.querySelector('[data-case="normal-mermaid"] .diagram-preview g.node rect')
      return node ? getComputedStyle(node).fill : null
    })(),
    themeClass: document.documentElement.className,
    lifecycle: window.__lifecycle || null,
    docListenersLive: window.__docListeners?.live?.() ?? null,
    plantumlImgCount: root.querySelectorAll('.plantuml-diagram').length,
    plantumlSourceShown: !!root.querySelector('[data-case="normal-plantuml"] .plantuml-source'),
    plantumlText: previewText('normal-plantuml'),
    totalScriptTags: scripts,
    eventAttrNames: [...new Set(eventAttrs)],
    dangerousHrefs,
    externalRefs: [...new Set(external)],
    executed: [...(window.__executed || [])],
  }
})()`)

const requests = events
  .filter((e) => e.method === 'Network.requestWillBeSent')
  .map((e) => e.params.request.url)

const blocked = events
  .filter((e) => e.method === 'Network.loadingFailed')
  .map((e) => ({
    error: e.params.errorText,
    type: e.params.type,
    blockedReason: e.params.blockedReason,
  }))

const violations = await evaluate(`(window.__cspViolations || []).slice(0, 50)`)
const cspListenerInstalled = await evaluate(`window.__cspListenerInstalled === true`)

const externalRequests = [...new Set(requests)].filter(
  (u) => !u.startsWith('http://127.0.0.1:') && !u.startsWith('data:') && !u.startsWith('blob:'),
)

// 第三方主机应当收到零请求。配置了受控 PlantUML 服务时，允许出现该服务这一条
// ——它是部署配置显式指定的目标，不是内容决定的。
//
// harness 的正向对照会主动请求 csp-probe.invalid 来证明策略在执行。它必然出现在
// 请求记录里（CSP 拦的是"拿到响应"，不是"尝试发送"），所以单独列出，
// 并且下面会要求它确实被 CSP 拦下——否则就成了真的外发。
const CSP_PROBE_HOST = 'https://csp-probe.invalid/'
const allowed = dom.plantumlServer ? [dom.plantumlServer.replace(/\/+$/, '')] : []
const probeRequests = externalRequests.filter((u) => u.startsWith(CSP_PROBE_HOST))

// 既有的字体依赖：由主题样式表 @import 引入，只携带字体名，
// 与对话内容无关，也不是本次改动引入的（与 prod-smoke 用同一份清单）。
// 除它之外，任何外发都算失败——尤其是内容里的地址。
const KNOWN_FONT_ORIGINS = [/^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//]
const fontRequests = externalRequests.filter((u) => KNOWN_FONT_ORIGINS.some((re) => re.test(u)))

const unexpectedRequests = externalRequests.filter(
  (u) =>
    !allowed.some((a) => u.startsWith(a)) &&
    !u.startsWith(CSP_PROBE_HOST) &&
    !KNOWN_FONT_ORIGINS.some((re) => re.test(u)),
)

// 主题期望值要在关掉 CDP 连接之前取：这里还要用 evaluate
let expectedThemeFill = null
if (theme) {
  expectedThemeFill = await evaluate(`(() => {
    const v = getComputedStyle(document.documentElement).getPropertyValue('--color-bg-tertiary').trim()
    const probe = document.createElement('div')
    probe.style.color = v
    document.body.appendChild(probe)
    const rgb = getComputedStyle(probe).color
    probe.remove()
    return rgb
  })()`)
}

const report = {
  pageUrl,
  expectedThemeFill,
  fontRequests,
  unexpectedRequests,
  cspProbeRequests: probeRequests,
  externalRequests,
  localRequestCount: requests.length - externalRequests.length,
  blockedRequests: blocked,
  cspListenerInstalled,
  cspViolations: violations,
  dom,
}
console.log('\n' + JSON.stringify(report, null, 2))

ws.close()
server.close()

// 判定：有任何未授权外发 / 脚本执行 / 危险 href / DOM 中的事件属性 => 失败
const failures = []
if (unexpectedRequests.length) failures.push(`未授权外发: ${unexpectedRequests.join(', ')}`)
if (dom.executed.length) failures.push(`payload 被执行: ${dom.executed.join(', ')}`)
if (dom.dangerousHrefs.length) failures.push(`危险链接: ${dom.dangerousHrefs.join(', ')}`)
if (dom.eventAttrNames.length) failures.push(`DOM 事件属性: ${dom.eventAttrNames.join(', ')}`)
if (dom.totalScriptTags) failures.push(`DOM 中存在 script 元素: ${dom.totalScriptTags}`)
if (dom.mermaidSvgHasScript) failures.push('Mermaid 产物含 script')
if (dom.mermaidSvgHasOnAttr) failures.push('Mermaid 产物含事件属性')
if (dom.orchestratedOtherHosts?.length) {
  failures.push(`PlantUML 目标被改写: ${dom.orchestratedOtherHosts.join(', ')}`)
}

// CSP 必须真的在浏览器里执行，且我们确实在观测它。
// 只断言"没有违规"是不够的：没装监听器时它恒为空。
if (!cspListenerInstalled) failures.push('CSP 违规监听器未安装，无法证明策略被执行')
if (!violations.length) failures.push('未观测到任何 CSP 违规：正向对照未生效，策略执行情况未知')
// 正向对照请求必须被 CSP 拦下，而不是真的送达对方
const blockedByCsp = blocked.filter((b) => b.blockedReason === 'csp')
if (probeRequests.length && !blockedByCsp.length) {
  failures.push(`正向对照请求未被 CSP 拦截: ${probeRequests.join(', ')}`)
}
if (!probeRequests.length) failures.push('正向对照未产生请求：无法验证拦截路径')

// ── 审查缺陷 1：draw.io 在清理前就发请求 ──────────────────────────
// 用例里的 image 指向 CSP 放行的公共图源，所以 CSP 不会替我们兜底：
// 只要渲染期真的去取，它就会出现在 externalRequests 里。
if (dom.drawioCaseExternalRefs?.length) {
  failures.push(`draw.io 用例残留外部引用: ${dom.drawioCaseExternalRefs.join(', ')}`)
}

// ── 审查缺陷 2：Mermaid CSS 转义绕过清理 ──────────────────────────
if (dom.mermaidCssCaseLeaksUrl) failures.push('Mermaid 转义 CSS 的地址留在了最终 DOM')
// 正常图表结构必须还在，不能靠"清空整个图表"通过
if (!dom.mermaidCssCaseHasSvg) failures.push('Mermaid 转义 CSS 用例没有渲染出图表')
if (dom.mermaidLooseCaseHasImg) failures.push('Mermaid 内容里的 loose 指令生效了（产生了 <img>）')
// 指令被剥离后图表本身照常渲染——不能靠"整段拒渲染"通过
if (!dom.mermaidLooseCaseHasSvg) failures.push('Mermaid loose 指令用例没有渲染出图表')
// 而 HTML 标签只能以文本形式出现：出现真的 <b>/foreignObject 就说明 loose 生效了
if (dom.mermaidLooseCaseHasHtmlTag)
  failures.push('Mermaid loose 指令生效了（标签变成了真的 DOM 元素）')
// 渲染库自己会去取图的形状（不经过内容指令，剥离指令拦不住）
if (dom.mermaidImageShapeRefs?.length) {
  failures.push(`Mermaid image 形状残留外部引用: ${dom.mermaidImageShapeRefs.join(', ')}`)
}
// 这条必须明确拒渲染并说明原因：图没画出来是预期结果，静默留白不行
if (!dom.mermaidImageShapeRefused) {
  failures.push('Mermaid image 形状未被明确拒绝（没有说明外部资源地址）')
}
// `style A fill:url(…)` 是 CSS 语义的另一个入口，同样在渲染前拒绝
if (dom.mermaidStyleUrlRefs?.length) {
  failures.push(`Mermaid style 语句残留外部引用: ${dom.mermaidStyleUrlRefs.join(', ')}`)
}
if (!dom.mermaidStyleUrlRefused) {
  failures.push('Mermaid style 语句未被明确拒绝（没有说明外部资源地址）')
}
// 反斜杠伪装：门禁必须按 URL 解析器语义归一后再找 //host
if (!dom.mermaidBackslashImgRefused) {
  failures.push('Mermaid 反斜杠伪装的 img 形状未被拒绝（门禁漏掉了 //host 的伪装写法）')
}

// ── 审查缺陷 4：draw.io 泄漏图实例与 document 监听器 ──────────────
// 渲染 → 更新 → 更新 → 卸载，每一步的存活监听器都不能比上一步多。
// 判据是相对基线的增量：页面上还挂着别的用例，绝对值里混着它们的监听器。
const lifecycleSteps = dom.lifecycle?.steps ?? []
const baseline = dom.lifecycle?.baseline ?? 0
if (!dom.lifecycle?.done) failures.push('生命周期用例未完成，无法判断是否泄漏')
// 观测器自检：探针没生效时"没有增长"毫无意义
if (dom.lifecycle?.probeSelfTest !== true) {
  failures.push('监听器探针自检失败（无法确认增减被记录）')
}
if (lifecycleSteps.length < 4) {
  failures.push(`生命周期用例步骤不足: ${JSON.stringify(lifecycleSteps)}`)
} else {
  for (let i = 1; i < lifecycleSteps.length; i++) {
    if (lifecycleSteps[i].live > lifecycleSteps[i - 1].live) {
      failures.push(
        `document 监听器在 ${lifecycleSteps[i].name} 后增加: ` +
          `${lifecycleSteps[i - 1].live} → ${lifecycleSteps[i].live}`,
      )
    }
  }
  // 卸载后增量必须归零：渲染期挂上的监听器一个都不能留
  const last = lifecycleSteps[lifecycleSteps.length - 1]
  if (last.live - baseline > 0) {
    failures.push(`卸载后仍多出 ${last.live - baseline} 个 document 监听器（基线 ${baseline}）`)
  }
  // 相对"任何用例渲染之前"的整页增量：这一条才抓得住**别的**用例
  // （比如两个 draw.io 用例）在渲染期泄漏的监听器——它们会被挂载前的
  // 基线吸收掉，只看上面那条永远成立。
  if (dom.lifecycle.leakAfterAll > 0) {
    failures.push(
      `整页在全部用例渲染后仍多出 ${dom.lifecycle.leakAfterAll} 个 document 监听器` +
        `（pageBaseline ${dom.lifecycle.pageBaseline}）`,
    )
  }
}

// ── 渲染期中间 DOM：不能只保证"最后一次清理" ─────────────────────
// 中间态里出现外部资源引用，说明资源策略没能在渲染前生效；
// 即使最终 DOM 被清干净，请求也已经发出去了。
const intermediate = dom.intermediate ?? {}
if (intermediate.externalRefs?.length) {
  failures.push(
    `渲染期中间 DOM 出现外部引用: ${[...new Set(intermediate.externalRefs)].join(', ')}`,
  )
}
if (intermediate.externalCss?.length) {
  failures.push(`渲染期中间样式出现外部地址: ${[...new Set(intermediate.externalCss)].join(', ')}`)
}
// foreignObject 是 HTML 回到 SVG 的通道：strict + htmlLabels:false 下不该出现。
// 中间态出现过就说明内容自述配置确实生效过，即使最终产物被清干净。
if (intermediate.foreignObjects > 0) {
  failures.push(
    `渲染期中间 DOM 出现 ${intermediate.foreignObjects} 个 foreignObject（HTML 通道被打开过）`,
  )
}
// 正向对照：中间 DOM 观测器本身必须有效。
// 恶意用例被剥离后不应再产生 foreignObject；但要证明观测器不是恒零，
// 这里检查它至少记录过被剥离之前的形态（见 harness 的 __intermediate 自检）。
if (intermediate.observerSelfTest !== true) {
  failures.push('中间 DOM 观测器未通过自检，无法证明它真的在看')
}

// 图表必须跟随当前主题的变量：固定成浅色在深空主题下会与背景撞成一片。
// 期望值取自主题令牌本身，而不是写死某个色号。
if (theme) {
  if (dom.mermaidNodeFill !== expectedThemeFill) {
    failures.push(
      `图表未跟随主题变量: 节点填充 ${dom.mermaidNodeFill}，` +
        `主题 --color-bg-tertiary 为 ${expectedThemeFill}`,
    )
  }
}

// 正常图表必须真的渲染出来，否则"清空"也能骗过上面的检查
if (dom.mermaidSvgCount < 2) failures.push(`Mermaid 正常图表未渲染（仅 ${dom.mermaidSvgCount} 个）`)
if (dom.drawioSvgCount < 1) failures.push('draw.io 正常图表未渲染')
// 空 SVG 也含两层 <g>，所以必须检查真的画出了图形元素
if (dom.drawioSvgCount >= 1 && !(dom.drawioShapeCount > 0)) {
  failures.push('draw.io 产出空图：SVG 中没有形状元素')
}

// 配置了受控服务：必须指向它；未配置：必须显示源码且不产生图片
if (dom.plantumlServer) {
  if (!dom.orchestratedSrc) failures.push('配置了自托管 PlantUML，但未生成预览图地址')
  else if (!dom.orchestratedSrc.startsWith(dom.plantumlServer)) {
    failures.push(`PlantUML 预览地址不指向受控服务: ${dom.orchestratedSrc}`)
  }
} else {
  if (!dom.orchestratedSourceFallback) failures.push('未配置自托管 PlantUML，但未显示源码回退')
  if (dom.plantumlImgCount) failures.push('未配置 PlantUML 却产生了图片请求')
}

if (dom.hasLoadableThumbnail) failures.push('不可控来源的图片被渲染成缩略图')

// 恶意 PlantUML 里的字符串不能变成请求目标（无论是否配置了自托管服务）
for (const src of dom.evilPlantumlSrcs ?? []) {
  if (src && !dom.plantumlServer?.startsWith(src) && !src.startsWith('/')) {
    if (!dom.plantumlServer || !src.startsWith(dom.plantumlServer)) {
      failures.push(`恶意 PlantUML 改变了请求目标: ${src}`)
    }
  }
}

if (failures.length) {
  console.log('\nRESULT: FAIL')
  for (const f of failures) console.log(' - ' + f)
  process.exit(1)
}
console.log('\nRESULT: PASS')
