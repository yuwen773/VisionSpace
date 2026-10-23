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

const server = await serve()
const port = server.address().port
const pageUrl = `http://127.0.0.1:${port}/index.html`
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
// 渲染后再等一拍，让迟到的请求也被记录
await new Promise((r) => setTimeout(r, 1500))
if (!ready) console.log('[harness] WARN: 等待渲染超时，结果可能不完整')

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
const unexpectedRequests = externalRequests.filter(
  (u) => !allowed.some((a) => u.startsWith(a)) && !u.startsWith(CSP_PROBE_HOST),
)

const report = {
  pageUrl,
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
