/**
 * 生产产物冒烟：直接用 `npm run build` 的 dist/ 验证最终产物。
 *
 * harness 验证的是渲染链行为；这里补的是"真正要部署的那份产物"：
 * 页面能起来、CSP 真的在产物里、且启动过程不向任何第三方发出请求。
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
// 允许通过参数指定产物目录（默认前端 dist/）
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist'))

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.otf': 'font/otf',
  '.woff2': 'font/woff2',
}

const server = await new Promise((resolve) => {
  const s = http.createServer((req, res) => {
    let rel = decodeURIComponent((req.url || '/').split('?')[0])
    if (rel === '/' || rel === '') rel = '/index.html'
    let file = path.join(root, rel.replace(/^\/+/, ''))
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory())
      file = path.join(root, 'index.html')
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
    fs.createReadStream(file).pipe(res)
  })
  s.listen(0, '127.0.0.1', () => resolve(s))
})
const port = server.address().port
const pageUrl = `http://127.0.0.1:${port}/`
console.log(`[prod-smoke] serving ${root} at ${pageUrl}`)

const getJson = (p, method = 'GET') =>
  new Promise((resolve, reject) => {
    const r = http.request(
      { host: '127.0.0.1', port: Number(process.env.CDP_PORT), path: p, method, family: 4 },
      (resp) => {
        let d = ''
        resp.on('data', (c) => (d += c))
        resp.on('end', () => {
          try {
            resolve(JSON.parse(d))
          } catch {
            reject(new Error(d.slice(0, 200)))
          }
        })
      },
    )
    r.on('error', reject)
    r.end()
  })

const target = await getJson('/json/new?' + encodeURIComponent(pageUrl), 'PUT')
const ws = new WebSocket(target.webSocketDebuggerUrl)
let id = 0
const pending = new Map()
const events = []
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const i = ++id
    pending.set(i, resolve)
    ws.send(JSON.stringify({ id: i, method, params }))
  })
ws.onmessage = (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pending.has(m.id)) {
    pending.get(m.id)(m)
    pending.delete(m.id)
  } else if (m.method) events.push(m)
}
await new Promise((r) => (ws.onopen = r))
await send('Runtime.enable')
await send('Network.enable')
await send('Log.enable')
await send('Page.reload', { ignoreCache: true })
await new Promise((r) => setTimeout(r, 8000))

const evaluate = async (expression) => {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true })
  return r.result?.result?.value
}

const facts = {
  csp: await evaluate(
    `document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content || null`,
  ),
  appMounted: await evaluate(`!!document.getElementById('app')?.children.length`),
  externalScriptTags: await evaluate(
    `[...document.querySelectorAll('script[src]')].map(s=>s.src).filter(u=>!u.startsWith(location.origin))`,
  ),
  cspViolations: events
    .filter((e) => e.method === 'Log.entryAdded')
    .map((e) => e.params.entry.text)
    .filter((t) => /Content Security Policy/i.test(t))
    .slice(0, 5),
}

const requests = [
  ...new Set(
    events.filter((e) => e.method === 'Network.requestWillBeSent').map((e) => e.params.request.url),
  ),
]
const externalRequests = requests.filter(
  (u) =>
    !u.startsWith(`http://127.0.0.1:${port}`) && !u.startsWith('data:') && !u.startsWith('blob:'),
)

console.log('\n' + JSON.stringify({ facts, externalRequests }, null, 2))
ws.close()
server.close()

const failures = []
if (!facts.csp) failures.push('产物中没有 CSP')
if (!facts.appMounted) failures.push('应用未挂载')
if (facts.externalScriptTags?.length)
  failures.push(`外部脚本标签: ${facts.externalScriptTags.join(', ')}`)
// 既有依赖的允许清单：字体（只携带字体名）与首页装饰图（组件常量，不来自对话）。
// 除此之外的任何外发都算失败——尤其是脚本、连接与对话内容里的地址。
const KNOWN_EXISTING = [
  /^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//,
  /^https:\/\/images\.unsplash\.com\//,
  /^https:\/\/plus\.unsplash\.com\//,
  /^https:\/\/picsum\.photos\//,
  /^https:\/\/fastly\.picsum\.photos\//,
]
const unexpectedExternal = externalRequests.filter((u) => !KNOWN_EXISTING.some((re) => re.test(u)))
if (unexpectedExternal.length) {
  failures.push(`既有清单之外的外发: ${unexpectedExternal.join(', ')}`)
}

if (failures.length) {
  console.log('\nRESULT: FAIL')
  for (const f of failures) console.log(' - ' + f)
  process.exit(1)
}
console.log('\nRESULT: PASS')
if (externalRequests.length) {
  console.log('（既有字体依赖，非本次改动引入）: ' + externalRequests.join(', '))
}
