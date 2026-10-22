// 浏览器真实链路验收（工单 32 / 原 30.4）。
//
// 由 scripts/browser-acceptance.ps1 启动隔离后端与本脚本：
//   VS_E2E_API_TARGET   隔离验收后端地址
//   VS_E2E_HTTPS_CERT   / VS_E2E_HTTPS_KEY：自签证书（用于 wss 与 Secure Cookie）
//   VS_E2E_ARTIFACTS    截图与结果目录
//
// 只依赖 playwright-core，不额外引入测试框架；失败时退出码为 1，
// 环境缺失时退出码为 2（BLOCKED_ENV），不会把「没跑」报告成「通过」。
import assert from 'node:assert/strict'
import fs from 'node:fs'
import https from 'node:https'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { startHttpEntry, startHttpsEntry } from './fixture-server.mjs'

const API_TARGET = process.env.VS_E2E_API_TARGET
const CERT = process.env.VS_E2E_HTTPS_CERT
const KEY = process.env.VS_E2E_HTTPS_KEY
const ARTIFACTS = process.env.VS_E2E_ARTIFACTS || 'e2e-artifacts'

const PASSWORD = 'acceptance-password'

if (!API_TARGET || !CERT || !KEY) {
  console.error('BLOCKED_ENV: VS_E2E_API_TARGET / VS_E2E_HTTPS_CERT / VS_E2E_HTTPS_KEY are required')
  process.exit(2)
}
fs.mkdirSync(ARTIFACTS, { recursive: true })

const calls = []
const results = []

function record(page) {
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) {
      calls.push({ kind: 'http', method: request.method(), url: request.url() })
    }
  })
  page.on('websocket', (ws) => calls.push({ kind: 'ws', method: 'WS', url: ws.url() }))
}

async function step(name, fn) {
  process.stdout.write(`- ${name} ... `)
  try {
    await fn()
    results.push({ name, status: 'PASS' })
    console.log('PASS')
  } catch (error) {
    results.push({ name, status: 'FAIL', error: String((error && error.message) || error) })
    console.log('FAIL')
    console.log(String((error && error.stack) || error))
  }
}

async function post(page, path_, body) {
  return page.evaluate(
    async ({ path: p, body }) => {
      const token = document.cookie
        .split(';')
        .map((part) => part.trim())
        .find((part) => part.startsWith('VS_CSRF='))
      const res = await fetch(p, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'X-CSRF-Token': decodeURIComponent(token.slice('VS_CSRF='.length)) } : {}),
        },
        body: JSON.stringify(body),
      })
      return { status: res.status, body: await res.json().catch(() => null) }
    },
    { path: path_, body },
  )
}

/**
 * 绕过浏览器直接向验收入口发请求。
 *
 * 用途有两个：
 *   1. 复现浏览器脚本无法伪造的 Origin 头（跨站来源判定）；
 *   2. 用自签证书访问验收入口（node 的 fetch 会拒绝不受信证书）。
 * 只发给本地回环入口，不涉及外部服务。
 */
function directRequest(origin, pathname, { method = 'POST', cookie, token, origin: requestOrigin } = {}) {
  const target = new URL(pathname, origin)
  return new Promise((resolve, reject) => {
    const request = https.request(
      {
        host: target.hostname,
        port: target.port || 443,
        path: target.pathname + target.search,
        method,
        // 验收专用：证书是本地自签的，只连 127.0.0.1
        rejectUnauthorized: false,
        headers: {
          'Content-Type': 'application/json',
          ...(method === 'GET' ? {} : { 'Content-Length': 2 }),
          ...(requestOrigin ? { Origin: requestOrigin } : {}),
          ...(token ? { 'X-CSRF-Token': token } : {}),
          ...(cookie ? { Cookie: cookie } : {}),
        },
      },
      (response) => {
        let body = ''
        response.setEncoding('utf8')
        response.on('data', (chunk) => (body += chunk))
        response.on('end', () => {
          let parsed = null
          try {
            parsed = JSON.parse(body)
          } catch {
            // 非 JSON 响应保留 null，断言会指出状态码
          }
          resolve({ status: response.statusCode, body: parsed, raw: body })
        })
      },
    )
    request.on('error', reject)
    request.end(method === 'GET' ? undefined : '{}')
  })
}

async function registerAndLogin(page, account, { withToken = true } = {}) {
  const registration = await post(page, '/api/user/register', {
    userAccount: account,
    userPassword: PASSWORD,
    checkPassword: PASSWORD,
  })
  assert.equal(registration.body?.code, 0, JSON.stringify(registration))

  let login
  if (withToken) {
    login = await post(page, '/api/user/login', { userAccount: account, userPassword: PASSWORD })
  } else {
    login = await page.evaluate(
      async ({ account }) => {
        const res = await fetch('/api/user/login', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userAccount: account, userPassword: 'acceptance-password' }),
        })
        return { status: res.status, body: await res.json() }
      },
      { account },
    )
  }
  assert.equal(login.body?.code, 0, JSON.stringify(login))
}

const http = startHttpEntry(API_TARGET)
const httpsEntry = startHttpsEntry(API_TARGET, CERT, KEY)
await http.listen()
await httpsEntry.listen()

const browser = await chromium.launch()
let failed = 0

try {
  const context = await browser.newContext({ ignoreHTTPSErrors: true })
  const origin = `https://127.0.0.1:${httpsEntry.port}`

  await step('https 同源入口下发 Secure 会话 Cookie 与 CSRF 令牌', async () => {
    const page = await context.newPage()
    record(page)
    const setCookieHeaders = []
    page.on('response', async (response) => {
      try {
        const headers = await response.allHeaders()
        if (headers['set-cookie']) setCookieHeaders.push(headers['set-cookie'])
      } catch {
        // 响应已关闭：忽略
      }
    })
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, `browser_${Date.now()}`)
    const cookies = await context.cookies()
    assert.ok(cookies.length > 0, 'must receive cookies')
    const sessionCookies = cookies.filter((c) => c.name !== 'VS_CSRF')
    // 防止空泛通过：没有会话 Cookie 时下面的循环什么也不断言
    assert.ok(sessionCookies.length > 0, `must receive a session cookie, got: ${JSON.stringify(cookies)}`)
    for (const cookie of cookies.filter((c) => c.name !== 'VS_CSRF')) {
      assert.equal(cookie.secure, true, `${cookie.name} must be Secure`)
    }
    assert.ok(cookies.some((c) => c.name === 'VS_CSRF'), 'CSRF token cookie must be issued')

    // 同源入口下跨站请求本就不该带会话：会话与令牌 Cookie 都必须 SameSite=Strict。
    // 直接看 Set-Cookie 头，而不是浏览器解析后的值——缺失属性时浏览器会回报默认值，
    // 那样断言会空泛通过。
    const guarded = setCookieHeaders.filter((h) => /^(SESSION|satoken)=/i.test(h))
    assert.ok(guarded.length >= 2, `必须看到会话与令牌两个 Set-Cookie：${JSON.stringify(setCookieHeaders)}`)
    for (const header of guarded) {
      assert.match(header, /SameSite=Strict/i, `cookie must be SameSite=Strict: ${header}`)
    }
    await page.screenshot({ path: path.join(ARTIFACTS, 'https-login.png') })
    await page.close()
  })

  await step('真实登录页走通：生成的 API 客户端解析到 /api 前缀', async () => {
    // 这一条专治「只断言原始 fetch」的空洞：页面用的是生成的客户端，
    // 它们请求的是 /user/login（不含 /api）。baseURL 不含 /api 时这些请求
    // 会落到站点根路径、被 SPA 回落成 index.html，看起来同源却没到后端。
    //
    // 必须用独立 context：共用 context 里已有前面步骤登录的会话，
    // 会让「登录成功」这个信号无条件成立，断言就变成了空泛通过。
    const uiContext = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      const page = await uiContext.newPage()
      const pageCalls = []
      page.on('request', (request) => {
        const url = new URL(request.url())
        // 只记录同源的 XHR/fetch：这里要抓的恰恰是「漏了 /api 前缀」的请求，
        // 若只记录 /api/ 路径，缺陷请求会被过滤掉，断言就永远看不到它。
        // axios 在浏览器里走 XHR，原始 fetch 走 fetch，两者都要收。
        const kind = request.resourceType()
        if (url.origin === origin && (kind === 'fetch' || kind === 'xhr')) {
          pageCalls.push({ method: request.method(), url: request.url() })
        }
      })
      await page.goto(`${origin}/user/login`)

      const account = `ui_${Date.now()}`
      // 注册走原始接口（不在被测范围内），随后只观察表单登录
      await page.evaluate(
        async ({ account, password }) => {
          await fetch('/api/user/register', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              userAccount: account,
              userPassword: password,
              checkPassword: password,
            }),
          })
        },
        { account, password: PASSWORD },
      )
      const beforeLogin = pageCalls.length

      await page.fill('input[placeholder="用户账号"]', account)
      await page.fill('input[placeholder="密码"]', PASSWORD)
      // 登录按钮是原生 <button>（html-type 是 antd 的 a-button 属性，不会渲染成 type）
      await page.click('.pop-btn.primary')

      // 登录成功后会跳走；不依赖任何既有会话，只等 URL 离开登录页
      await page
        .waitForFunction(() => !location.pathname.startsWith('/user/login'), null, { timeout: 15000 })
        .catch(() => {})

      // 只检查表单登录这一步自己发出的请求：前面步骤用原始 fetch 也会打 user/login，
      // 混在一起就无法证明「生成的客户端」带了前缀。
      const loginCalls = pageCalls
        .slice(beforeLogin)
        .filter((c) => c.method === 'POST' && new URL(c.url).pathname.endsWith('/user/login'))
      assert.ok(loginCalls.length > 0, '登录表单必须真的向后端发请求')
      for (const call of loginCalls) {
        assert.ok(
          new URL(call.url).pathname.startsWith('/api/'),
          `生成的客户端必须带 /api 前缀：${call.url}`,
        )
      }

      // 最终确认身份真的建立了（走生成的 getLoginUserUsingGet）
      const me = await page.evaluate(async () => {
        const res = await fetch('/api/user/get/login', { credentials: 'include' })
        return res.json()
      })
      assert.equal(me.code, 0, `登录后必须能识别本人：${JSON.stringify(me)}`)
      assert.equal(me.data.userAccount, account, '识别出的必须是刚注册的账号')
      await page.screenshot({ path: path.join(ARTIFACTS, 'ui-login.png') })
    } finally {
      await uiContext.close()
    }
  })

  await step('页面发起的所有 API 请求都与页面同源，且没有 http 混合内容', async () => {
    const apiCalls = calls.filter((c) => c.kind === 'http')
    assert.ok(apiCalls.length > 0, 'page must call the API')
    for (const call of apiCalls) {
      assert.equal(new URL(call.url).origin, origin, `unexpected origin: ${call.url}`)
    }
  })

  await step('退出后旧会话的受保护请求被拒绝', async () => {
    const page = await context.newPage()
    record(page)
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, `logout_${Date.now()}`)

    const before = await page.evaluate(async () => {
      const res = await fetch('/api/user/get/login', { credentials: 'include' })
      return res.json()
    })
    assert.equal(before.code, 0, JSON.stringify(before))

    const logout = await post(page, '/api/user/logout', {})
    assert.equal(logout.body?.code, 0, JSON.stringify(logout))

    const after = await page.evaluate(async () => {
      const res = await fetch('/api/user/get/login', { credentials: 'include' })
      return res.json()
    })
    assert.equal(after.code, 40100, 'old session must not identify the user')
    await page.screenshot({ path: path.join(ARTIFACTS, 'after-logout.png') })
    await page.close()
  })

  await step('协作 WebSocket 由 https 页面推导为 wss，未登录握手失败', async () => {
    const page = await context.newPage()
    record(page)
    await page.goto(`${origin}/user/login`)
    const url = `wss://127.0.0.1:${httpsEntry.port}/api/ws/picture/edit?pictureId=1`
    const outcome = await page.evaluate(
      (target) =>
        new Promise((resolve) => {
          const socket = new WebSocket(target)
          socket.onopen = () => resolve('open')
          socket.onerror = () => resolve('error')
          socket.onclose = () => resolve('closed')
          setTimeout(() => resolve('timeout'), 5000)
        }),
      url,
    )
    assert.notEqual(outcome, 'open', 'anonymous handshake must not succeed')
    const wsCalls = calls.filter((c) => c.kind === 'ws')
    assert.ok(wsCalls.length > 0, 'attempt must reach the server')
    for (const call of wsCalls) {
      assert.ok(call.url.startsWith('wss://'), `must be wss: ${call.url}`)
    }

    // 握手被拒有两个独立原因，必须分别证明：
    //   1. 未登录（会话校验）；
    //   2. 跨站 Origin（来源校验，与 REST 同一套规则）。
    // 第 2 条用直连请求把 Origin 换成别站，响应必须是 403 而不是登录错误。
    const crossOrigin = await directRequest(origin, '/api/ws/picture/edit?pictureId=1', {
      origin: 'https://evil.example',
    })
    assert.equal(crossOrigin.status, 403, `跨站握手必须被来源校验拒绝：${crossOrigin.raw}`)
    await page.screenshot({ path: path.join(ARTIFACTS, 'wss-handshake.png') })
    await page.close()
  })

  await step('跨站 Origin 的状态修改被拒绝且本人会话不受影响', async () => {
    const page = await context.newPage()
    record(page)
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, `csrf_${Date.now()}`)

    // 浏览器禁止脚本设置 Origin 头，所以「另一个站点发起」用真实 HTTP 请求复现：
    // 带着受害者本人有效的会话 Cookie 与 CSRF 令牌，只把 Origin 换成别站。
    // 攻击者能凑齐令牌也不要紧——来源判定必须独立挡住它。
    const cookies = await context.cookies(origin)
    const csrf = cookies.find((c) => c.name === 'VS_CSRF')
    const rejected = await directRequest(origin, '/api/user/logout', {
      cookie: cookies.map((c) => `${c.name}=${c.value}`).join('; '),
      token: csrf ? csrf.value : '',
      origin: 'https://evil.example',
    })
    assert.equal(rejected.status, 403, JSON.stringify(rejected))
    assert.equal(rejected.body?.code, 40300, JSON.stringify(rejected))
    const still = await page.evaluate(async () => {
      const res = await fetch('/api/user/get/login', { credentials: 'include' })
      return res.json()
    })
    assert.equal(still.code, 0, 'rejected cross-site write must not end the session')
    await page.close()
  })

  await step('缺少 CSRF 令牌的登录后写请求被拒绝且不改状态', async () => {
    const page = await context.newPage()
    record(page)
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, `nocsrf_${Date.now()}`)
    const missing = await page.evaluate(async () => {
      const res = await fetch('/api/user/logout', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      })
      return { status: res.status, body: await res.json() }
    })
    assert.equal(missing.status, 403, JSON.stringify(missing))
    assert.equal(missing.body.code, 40300)
    const still = await page.evaluate(async () => {
      const res = await fetch('/api/user/get/login', { credentials: 'include' })
      return res.json()
    })
    assert.equal(still.code, 0)
    await page.close()
  })

  await step('SSE 流式入口同源可用，且缺令牌的流式写请求被拒绝', async () => {
    const page = await context.newPage()
    record(page)
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, `sse_${Date.now()}`)

    // 带令牌：必须建立同源的 text/event-stream 连接（模型不可达不影响「流已打开」这一契约）
    const opened = await page.evaluate(async () => {
      const token = document.cookie
        .split(';')
        .map((part) => part.trim())
        .find((part) => part.startsWith('VS_CSRF='))
      const controller = new AbortController()
      const res = await fetch('/api/agent/image/chat/stream', {
        method: 'POST',
        credentials: 'include',
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': decodeURIComponent(token.slice('VS_CSRF='.length)),
        },
        body: JSON.stringify({ message: 'acceptance ping', threadId: 'acceptance-thread' }),
      })
      const contentType = res.headers.get('content-type') || ''
      // 只需要读到响应头；随后立刻中断，避免等待模型
      controller.abort()
      return { ok: res.ok, status: res.status, contentType }
    })
    assert.equal(opened.ok, true, `SSE 入口必须接受同源带令牌请求：${JSON.stringify(opened)}`)
    assert.ok(
      opened.contentType.includes('text/event-stream'),
      `SSE 必须返回事件流：${JSON.stringify(opened)}`,
    )

    // 缺令牌：同源但未带双提交令牌的流式写请求必须被挡在建立流之前
    const missing = await page.evaluate(async () => {
      const res = await fetch('/api/agent/image/chat/stream', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: 'acceptance ping' }),
      })
      return { status: res.status, contentType: res.headers.get('content-type') || '' }
    })
    assert.equal(missing.status, 403, `缺令牌的 SSE 写请求必须被拒绝：${JSON.stringify(missing)}`)
    const sseCalls = calls.filter((c) => c.kind === 'http' && c.url.includes('/agent/image/chat/stream'))
    assert.ok(sseCalls.length > 0, 'SSE 请求必须真实到达同源入口')
    for (const call of sseCalls) {
      assert.equal(new URL(call.url).origin, origin, `SSE 必须同源：${call.url}`)
    }
    await page.close()
  })

  await step('生产构建不使用固定主机：http 入口只服务当前源', async () => {
    const page = await browser.newPage()
    record(page)
    const devOrigin = `http://127.0.0.1:${http.port}`
    await page.goto(`${devOrigin}/user/login`)
    await registerAndLogin(page, `http_${Date.now()}`)
    const httpCalls = calls.filter((c) => c.kind === 'http' && c.url.startsWith('http://'))
    assert.ok(httpCalls.length > 0, 'http entry must be exercised')
    for (const call of httpCalls) {
      assert.equal(new URL(call.url).origin, devOrigin, `unexpected origin: ${call.url}`)
    }
    await page.close()
  })
} finally {
  await browser.close()
  await http.close()
  await httpsEntry.close()
}

failed = results.filter((r) => r.status === 'FAIL').length
fs.writeFileSync(
  path.join(ARTIFACTS, 'results.json'),
  JSON.stringify({ apiTarget: API_TARGET, results, calls }, null, 2),
)
console.log(JSON.stringify({ passed: results.length - failed, failed }, null, 2))
process.exit(failed > 0 ? 1 : 0)
