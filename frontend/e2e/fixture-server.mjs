// 浏览器验收用的最小同源入口。
//
// 真实生产由 Nginx 提供静态文件并把 /api 反代到后端。这里用同样的形状：
//   - 静态文件来自 `npm run build-only` 的 dist/
//   - /api/* 反代到验收后端（含 SSE 与 WebSocket upgrade）
//   - 额外挂一套自签 TLS，证明 https 页面推导出 wss 而不是混合内容
//
// 只监听 127.0.0.1，不读取任何项目配置，端口由调用方传入。
import http from 'node:http'
import https from 'node:https'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const dist = path.resolve(here, '..', 'dist')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function serveStatic(req, res) {
  const url = new URL(req.url, 'http://localhost')
  let file = path.join(dist, decodeURIComponent(url.pathname))
  if (!file.startsWith(dist)) {
    res.writeHead(403).end()
    return
  }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    // SPA 路由回落到 index.html
    file = path.join(dist, 'index.html')
  }
  if (!fs.existsSync(file)) {
    res.writeHead(404).end('build output missing')
    return
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
  fs.createReadStream(file).pipe(res)
}

function proxyApi(apiTarget, req, res) {
  const target = new URL(apiTarget)
  const outbound = http.request(
    {
      host: target.hostname,
      port: target.port,
      method: req.method,
      path: req.url,
      // 让后端看到浏览器的真实 Host 与协议，来源判定才能同源
      headers: {
        ...req.headers,
        host: req.headers.host,
        'x-forwarded-host': req.headers.host,
        'x-forwarded-proto': req.socket.encrypted ? 'https' : 'http',
      },
    },
    (upstream) => {
      res.writeHead(upstream.statusCode || 502, upstream.headers)
      upstream.pipe(res)
    },
  )
  outbound.on('error', (error) => {
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'text/plain' })
    res.end(`upstream error: ${error.message}`)
  })
  req.pipe(outbound)
}

function handleUpgrade(apiTarget, req, socket, head) {
  // 浏览器在握手被拒后立刻断开是正常路径；不处理会让进程抛未捕获异常而中断整轮验收
  socket.on('error', () => socket.destroy())
  const target = new URL(apiTarget)
  const outbound = http.request({
    host: target.hostname,
    port: target.port,
    method: req.method,
    path: req.url,
    headers: {
      ...req.headers,
      host: req.headers.host,
      'x-forwarded-host': req.headers.host,
      'x-forwarded-proto': req.socket.encrypted ? 'https' : 'http',
    },
  })
  outbound.on('upgrade', (upstream, upstreamSocket, upstreamHead) => {
    const lines = [`HTTP/1.1 101 ${upstream.statusMessage || 'Switching Protocols'}`]
    for (const [k, v] of Object.entries(upstream.headers)) {
      for (const value of Array.isArray(v) ? v : [v]) lines.push(`${k}: ${value}`)
    }
    socket.write(`${lines.join('\r\n')}\r\n\r\n`)
    if (upstreamHead?.length) socket.write(upstreamHead)
    if (head?.length) upstreamSocket.write(head)
    upstreamSocket.pipe(socket).pipe(upstreamSocket)
  })
  outbound.on('response', (response) => {
    const lines = [`HTTP/1.1 ${response.statusCode} ${response.statusMessage}`]
    for (const [k, v] of Object.entries(response.headers)) {
      for (const value of Array.isArray(v) ? v : [v]) lines.push(`${k}: ${value}`)
    }
    socket.write(`${lines.join('\r\n')}\r\n\r\n`)
    socket.end()
  })
  outbound.on('error', () => socket.destroy())
  outbound.end()
}

function create(apiTarget, tls) {
  const handler = (req, res) => {
    if (req.url.startsWith('/api/')) return proxyApi(apiTarget, req, res)
    serveStatic(req, res)
  }
  const server = tls ? https.createServer(tls, handler) : http.createServer(handler)
  // 浏览器/客户端中途断开是验收里的正常路径，不能因此中断整个进程
  server.on('clientError', (error, socket) => socket.destroy())
  server.on('tlsClientError', (error, socket) => socket.destroy())
  server.on('upgrade', (req, socket, head) => {
    if (req.url.startsWith('/api/')) return handleUpgrade(apiTarget, req, socket, head)
    socket.destroy()
  })
  let actualPort = 0
  server.on('listening', () => {
    actualPort = server.address().port
  })
  return {
    server,
    listen: () =>
      new Promise((resolve) => {
        server.listen(0, '127.0.0.1', () => {
          actualPort = server.address().port
          resolve(actualPort)
        })
      }),
    get port() {
      return actualPort
    },
    close: () => new Promise((resolve) => server.close(resolve)),
  }
}

export function startHttpEntry(apiTarget) {
  return create(apiTarget, null)
}

export function startHttpsEntry(apiTarget, certPath, keyPath) {
  return create(apiTarget, {
    cert: fs.readFileSync(certPath),
    key: fs.readFileSync(keyPath),
  })
}
