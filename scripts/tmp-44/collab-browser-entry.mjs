// 工单 44 浏览器多连接协作验收（真实 Chromium + 真实 WebSocket 协议）。
//
// 由 scripts/tmp-44/collab-browser.ps1 启动隔离后端与本脚本：
//   VS44_E2E_API_TARGET   隔离验收后端地址
//   VS44_E2E_HTTPS_CERT / VS44_E2E_HTTPS_KEY  自签证书
//   VS44_E2E_ARTIFACTS    截图与结果目录
//
// 覆盖：双标签 UI 编辑权反馈（一个编辑一个被拒）、同一浏览器第二标签
// 关闭不误释放、撤权后 UI 状态与操作反馈、最终数据结果。
// 失败退出码 1；环境缺失退出码 2（BLOCKED_ENV）。
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import https from 'node:https'
import { chromium } from 'playwright-core'
import { startHttpEntry, startHttpsEntry } from '../../e2e/fixture-server.mjs'

const API_TARGET = process.env.VS44_E2E_API_TARGET
const CERT = process.env.VS44_E2E_HTTPS_CERT
const KEY = process.env.VS44_E2E_HTTPS_KEY
const ARTIFACTS = process.env.VS44_E2E_ARTIFACTS || 'collab-browser-artifacts'
const PASSWORD = 'acceptance-password'

if (!API_TARGET || !CERT || !KEY) {
  console.error('BLOCKED_ENV: VS44_E2E_API_TARGET / VS44_E2E_HTTPS_CERT / VS44_E2E_HTTPS_KEY are required')
  process.exit(2)
}
fs.mkdirSync(ARTIFACTS, { recursive: true })

const results = []

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

// 团队空间 + 图片 seed 信息由 PS 驱动注入（register-only 阶段可以没有）
const fixturePath = process.env.VS44_E2E_FIXTURE_JSON
const fixture = fixturePath && fs.existsSync(fixturePath) ? JSON.parse(fs.readFileSync(fixturePath, 'utf8')) : null
const { spaceId, pictureId } = fixture || { spaceId: 0, pictureId: 0 }
const registerOnly = process.argv.includes('--register-only')
const ownerAccount = `collab_owner_${Date.now()}`

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

async function registerAndLogin(page, account) {
  const registration = await post(page, '/api/user/register', {
    userAccount: account, userPassword: PASSWORD, checkPassword: PASSWORD,
  })
  assert.equal(registration.body?.code, 0, JSON.stringify(registration))
  const login = await post(page, '/api/user/login', { userAccount: account, userPassword: PASSWORD })
  assert.equal(login.body?.code, 0, JSON.stringify(login))
}

/** 页面内建立真实 WebSocket（浏览器原生协议栈），返回消息记录器句柄。 */
async function openEditSocket(page, targetPictureId) {
  return page.evaluateHandle(async (pid) => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const ws = new WebSocket(`${proto}://${location.host}/api/ws/picture/edit?pictureId=${pid}`)
    const log = []
    ws.onmessage = (e) => {
      try { log.push(JSON.parse(e.data)) } catch { log.push({ type: 'UNPARSEABLE' }) }
    }
    // 等待握手结果
    await new Promise((resolve, reject) => {
      ws.onopen = resolve
      ws.onerror = () => reject(new Error('handshake rejected'))
    })
    return { ws, log, send: (o) => ws.send(JSON.stringify(o)) }
  }, targetPictureId)
}

async function socketSend(page, handle, message) {
  await page.evaluate((h) => h.send(message), handle)
}

const http = startHttpEntry(API_TARGET)
const httpsEntry = startHttpsEntry(API_TARGET, CERT, KEY)
await http.listen()
await httpsEntry.listen()
const origin = `https://127.0.0.1:${httpsEntry.port}`

const browser = await chromium.launch()
let failed = 0
try {
  // === register-only 阶段：只注册 owner 账号并通知 PS 驱动，供 seed 反查 userId ===
  if (registerOnly) {
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    await page.goto(`${origin}/user/login`)
    await registerAndLogin(page, ownerAccount)
    fs.writeFileSync(path.join(ARTIFACTS, 'owner-account.txt'), ownerAccount)
    await context.close()
    await browser.close()
    http.close()
    httpsEntry.close()
    console.log('registered owner:', ownerAccount)
    process.exit(0)
  }

  // === 场景 1：同一浏览器两个标签页，标签 A 拿到编辑权，标签 B 被拒绝并看到 UI 反馈 ===
  const contextA = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageA1 = await contextA.newPage()
  const pageA2 = await contextA.newPage()
  const accountA = `collab_${Date.now()}`
  await pageA1.goto(`${origin}/user/login`)
  await registerAndLogin(pageA1, accountA)
  // 标签 2 共享 context 的 Cookie：打开后即登录
  await pageA2.goto(`${origin}/user/login`)

  await step('标签A获得编辑权（真实 wss 握手 + ENTER_EDIT）', async () => {
    const sock = await openEditSocket(pageA1, pictureId)
    await socketSend(pageA1, sock, { type: 'ENTER_EDIT' })
    await pageA1.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ENTER_EDIT' || m.type === 'ERROR'),
      sock, { timeout: 8000 },
    )
    const types = await pageA1.evaluate((h) => h.log.map((m) => m.type), sock)
    assert.ok(types.includes('ENTER_EDIT'), 'expected ENTER_EDIT, got ' + JSON.stringify(types))
    await pageA1.screenshot({ path: path.join(ARTIFACTS, 'tab-a-holder.png') })
    globalThis.__sockA1 = sock
  })

  await step('标签B（同用户）进入被拒并收到错误反馈', async () => {
    const sock2 = await openEditSocket(pageA2, pictureId)
    await socketSend(pageA2, sock2, { type: 'ENTER_EDIT' })
    await pageA2.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ERROR'),
      sock2, { timeout: 8000 },
    )
    await pageA2.screenshot({ path: path.join(ARTIFACTS, 'tab-b-rejected.png') })
    globalThis.__sockA2 = sock2
  })

  await step('标签B关闭后标签A的编辑权仍在（多标签不误释放）', async () => {
    await pageA2.evaluate((h) => h.ws.close(), globalThis.__sockA2)
    await pageA1.waitForTimeout(600)
    const before = await pageA1.evaluate((h) => h.log.filter((m) => m.type === 'ERROR').length, globalThis.__sockA1)
    await socketSend(pageA1, globalThis.__sockA1, { type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    await pageA1.waitForTimeout(800)
    // 持有者的操作不被拒绝：不出现新的 ERROR
    const after = await pageA1.evaluate((h) => h.log.filter((m) => m.type === 'ERROR').length, globalThis.__sockA1)
    assert.equal(after, before, 'holder must keep the lease after another tab closed')
  })

  await pageA1.screenshot({ path: path.join(ARTIFACTS, 'final-tab-a.png') })
  await contextA.close()

  // === 场景 2：另一个用户（editor）与标签 A 竞争，被拒；随后撤权其连接操作被拒 ===
  const contextB = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageB = await contextB.newPage()
  const accountB = `rival_${Date.now()}`
  await pageB.goto(`${origin}/user/login`)
  await registerAndLogin(pageB, accountB)

  await step('另一用户竞争编辑权被拒（跨连接竞争 UI 反馈）', async () => {
    const sock = await openEditSocket(pageB, pictureId)
    await socketSend(pageB, sock, { type: 'ENTER_EDIT' })
    await pageB.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ERROR'),
      sock, { timeout: 8000 },
    )
    await pageB.screenshot({ path: path.join(ARTIFACTS, 'rival-rejected.png') })
  })

  await contextB.close()

  // === 场景 3：持有者注销（REST logout），后续操作被拒 ===
  const contextC = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageC = await contextC.newPage()
  const accountC = `holder_${Date.now()}`
  await pageC.goto(`${origin}/user/login`)
  await registerAndLogin(pageC, accountC)
  await step('持有者注销后写入被拒（会话撤销契约）', async () => {
    const sock = await openEditSocket(pageC, pictureId)
    await socketSend(pageC, sock, { type: 'ENTER_EDIT' })
    await pageC.waitForFunction((h) => h.log.some((m) => m.type === 'ENTER_EDIT'), sock, { timeout: 8000 })
    const logout = await post(pageC, '/api/user/logout', {})
    assert.equal(logout.body?.code, 0, JSON.stringify(logout))
    await pageC.waitForTimeout(600)
    await socketSend(pageC, sock, { type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    await pageC.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ERROR'),
      sock, { timeout: 8000 },
    )
    await pageC.screenshot({ path: path.join(ARTIFACTS, 'after-logout-denied.png') })
  })
  await contextC.close()

  fs.writeFileSync(path.join(ARTIFACTS, 'browser-results.json'), JSON.stringify(results, null, 2))
} catch (e) {
  console.error('FATAL', e)
  fs.writeFileSync(path.join(ARTIFACTS, 'browser-fatal.txt'), String(e && e.stack) || String(e))
  failed = 1
} finally {
  await browser.close().catch(() => {})
  http.close()
  httpsEntry.close()
}
failed = failed || (results.some((r) => r.status === 'FAIL') ? 1 : 0)
process.exit(failed)
