// 工单 44 浏览器多连接协作验收（真实 Chromium + 真实 WebSocket 协议）。
//
// 由 scripts/collab-verification/collab-browser.ps1 启动隔离后端与本脚本：
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
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
// playwright-core 从 frontend/node_modules 解析（本脚本目录没有 node_modules）
const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..', '..')
const require = createRequire(path.join(repoRoot, 'frontend', 'package.json'))
const { chromium } = require('playwright-core')
const { startHttpEntry, startHttpsEntry } = await import(
  new URL('../../frontend/e2e/fixture-server.mjs', import.meta.url).href)

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
// 跨 step 共享的浏览器内 WebSocket 句柄（JSHandle 只在脚本进程内有效）
let __sockA1 = null
let __sockA2 = null

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
  // message 必须作为参数传入浏览器上下文，闭包里的变量不会跨进 evaluate
  await page.evaluate(({ h, message }) => h.send(message), { h: handle, message })
}

const http = startHttpEntry(API_TARGET)
const httpsEntry = startHttpsEntry(API_TARGET, CERT, KEY)
await http.listen()
await httpsEntry.listen()
const origin = `https://127.0.0.1:${httpsEntry.port}`

const browser = await chromium.launch()
fs.writeFileSync(path.join(ARTIFACTS, 'browser-environment.json'), JSON.stringify({
  node: process.version,
  playwright: require('playwright-core/package.json').version,
  chromium: browser.version(),
  transport: 'HTTPS + WSS via loopback reverse proxy',
}, null, 2))
let failed = 0
try {
  // === register-only 阶段：只注册 owner 账号并通知 PS 驱动，供 seed 反查 userId ===
  if (registerOnly) {
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    await page.goto(`${origin}/user/login`)
    // 三个账号都通过真实注册入口创建；角色由 PS seed 到 space_user
    const accounts = {
      owner: `collab_owner_${Date.now()}`,
      editor: `collab_editor_${Date.now()}`,
      viewer: `collab_viewer_${Date.now()}`,
    }
    for (const account of Object.values(accounts)) {
      await registerAndLogin(page, account)
    }
    fs.writeFileSync(path.join(ARTIFACTS, 'owner-account.txt'), accounts.owner)
    fs.writeFileSync(path.join(ARTIFACTS, 'accounts.json'), JSON.stringify(accounts))
    await context.close()
    await browser.close()
    http.close()
    httpsEntry.close()
    console.log('registered owner:', ownerAccount)
    process.exit(0)
  }

  // === 场景 1：同一浏览器两个标签页，标签 A 拿到编辑权，标签 B 被拒绝并看到 UI 反馈 ===
  // 账号与角色由 PS 驱动预先注册并 seed 到 space_user：
  //   owner（admin）、editor（可编辑）、viewer（只读）
  const owner = process.env.VS44_E2E_OWNER_ACCOUNT
  const editor = process.env.VS44_E2E_EDITOR_ACCOUNT
  const viewer = process.env.VS44_E2E_VIEWER_ACCOUNT
  assert.ok(owner && editor && viewer, 'BLOCKED_ENV: VS44_E2E_*_ACCOUNT are required')
  const loginOnly = async (page, account) => {
    const login = await post(page, '/api/user/login', { userAccount: account, userPassword: PASSWORD })
    assert.equal(login.body?.code, 0, `login ${account}: ${JSON.stringify(login)}`)
  }

  const contextA = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageA1 = await contextA.newPage()
  const pageA2 = await contextA.newPage()
  await pageA1.goto(`${origin}/user/login`)
  await loginOnly(pageA1, owner)
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
    __sockA1 = sock
  })

  await step('标签B（同用户）进入被拒并收到错误反馈', async () => {
    const sock2 = await openEditSocket(pageA2, pictureId)
    await socketSend(pageA2, sock2, { type: 'ENTER_EDIT' })
    await pageA2.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ERROR'),
      sock2, { timeout: 8000 },
    )
    await pageA2.screenshot({ path: path.join(ARTIFACTS, 'tab-b-rejected.png') })
    __sockA2 = sock2
  })

  await step('标签B关闭后标签A的编辑权仍在（多标签不误释放）', async () => {
    await pageA2.evaluate((h) => h.ws.close(), __sockA2)
    await pageA1.waitForTimeout(600)
    const before = await pageA1.evaluate((h) => h.log.filter((m) => m.type === 'ERROR').length, __sockA1)
    await socketSend(pageA1, __sockA1, { type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    await pageA1.waitForTimeout(800)
    // 持有者的操作不被拒绝：不出现新的 ERROR
    const after = await pageA1.evaluate((h) => h.log.filter((m) => m.type === 'ERROR').length, __sockA1)
    assert.equal(after, before, 'holder must keep the lease after another tab closed')
  })

  await pageA1.screenshot({ path: path.join(ARTIFACTS, 'final-tab-a.png') })

  // === 场景 2：另一个用户（editor）与标签 A 竞争，被拒；随后撤权其连接操作被拒 ===
  const contextB = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageB = await contextB.newPage()
  await pageB.goto(`${origin}/user/login`)
  await loginOnly(pageB, editor)

  await step('另一用户竞争编辑权被拒（跨连接竞争 UI 反馈）', async () => {
    const sock = await openEditSocket(pageB, pictureId)
    await socketSend(pageB, sock, { type: 'ENTER_EDIT' })
    await pageB.waitForFunction(
      (h) => h.log.some((m) => m.type === 'ERROR'),
      sock, { timeout: 8000 },
    ).catch(async (error) => {
      const messages = await pageB.evaluate((h) => h.log, sock)
      throw new Error(`${error.message}; rival messages: ${JSON.stringify(messages)}`)
    })
    await pageB.screenshot({ path: path.join(ARTIFACTS, 'rival-rejected.png') })
  })

  await contextB.close()
  await contextA.close()

  // === 场景 3：持有者注销（REST logout），后续操作被拒 ===
  // 用 owner 账号：场景 1 的上下文已关闭，租约随连接释放
  const contextC = await browser.newContext({ ignoreHTTPSErrors: true })
  const pageC = await contextC.newPage()
  await pageC.goto(`${origin}/user/login`)
  await loginOnly(pageC, owner)
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

  // === 实际产品 UI：打开真实裁剪弹窗，记录其原生 WSS，不替换业务响应 ===
  const uiContext = await browser.newContext({ ignoreHTTPSErrors: true })
  await uiContext.addInitScript(() => {
    globalThis.__vs44UiSockets = []
    const NativeSocket = globalThis.WebSocket
    globalThis.WebSocket = class extends NativeSocket {
      constructor(...args) {
        super(...args)
        if (String(args[0]).includes('/ws/picture/edit')) {
          const entry = { ws: this, received: [], sent: [] }
          globalThis.__vs44UiSockets.push(entry)
          this.addEventListener('message', (event) => entry.received.push(JSON.parse(event.data)))
          const send = this.send.bind(this)
          this.send = (data) => { entry.sent.push(JSON.parse(data)); send(data) }
        }
      }
    }
  })
  const uiA = await uiContext.newPage()
  await uiA.goto(`${origin}/user/login`)
  await loginOnly(uiA, owner)
  const openCropper = async (page) => {
    await page.goto(`${origin}/add_picture?id=${pictureId}&spaceId=${spaceId}`)
    await page.waitForFunction(() => document.querySelector('.header-space')?.textContent.includes('collab-team'))
    await page.locator('button[title="裁剪编辑"]').click()
    await page.locator('.cropper-modal .collab-status').waitFor()
    await page.waitForFunction(() => globalThis.__vs44UiSockets.some(
      (s) => s.received.some((m) => m.selfSessionId)), { timeout: 10000 })
  }
  await openCropper(uiA)
  const uiB = await uiContext.newPage()
  await openCropper(uiB)
  const toolA = uiA.locator('.cropper-modal button[title="放大"]')
  const toolB = uiB.locator('.cropper-modal button[title="放大"]')
  let uiObserver

  await step('实际UI：持有标签工具启用，同用户第二标签保持禁用', async () => {
    await uiA.getByRole('button', { name: '进入编辑', exact: true }).click()
    await uiA.waitForFunction(() => !document.querySelector('.cropper-modal button[title="放大"]').disabled)
    await uiB.waitForFunction(() => document.querySelector('.collab-status').textContent.includes('正在编辑'))
    assert.equal(await toolA.isEnabled(), true)
    assert.equal(await toolB.isDisabled(), true)
    assert.equal(await uiB.getByRole('button', { name: '退出编辑', exact: true }).count(), 0)
    await uiA.screenshot({ path: path.join(ARTIFACTS, 'ui-tab-a-holder.png') })
    await uiB.screenshot({ path: path.join(ARTIFACTS, 'ui-tab-b-disabled.png') })
  })

  await step('实际UI：普通消息错误后持有者仍能编辑和退出', async () => {
    await uiA.evaluate(() => globalThis.__vs44UiSockets.at(-1).ws.send(JSON.stringify({
      type: 'EDIT_ACTION', editAction: 'INVALID_ACTION',
    })))
    await uiA.getByText('无效的编辑动作', { exact: true }).waitFor()
    assert.equal(await toolA.isEnabled(), true)
    assert.equal(await uiA.getByRole('button', { name: '退出编辑', exact: true }).count(), 1)
    await uiA.screenshot({ path: path.join(ARTIFACTS, 'ui-retryable-error.png') })
  })

  await step('实际UI：竞争拒绝可见，单次事件不重复回放', async () => {
    await uiB.evaluate(() => globalThis.__vs44UiSockets.at(-1).ws.send(JSON.stringify({ type: 'ENTER_EDIT' })))
    await uiB.getByText('其他用户正在编辑', { exact: true }).waitFor()
    assert.equal(await toolB.isDisabled(), true)
    const count = await uiA.evaluate(() => globalThis.__vs44UiSockets.at(-1).received.filter(
      (m) => m.type === 'ENTER_EDIT').length)
    assert.equal(count, 1, 'local ENTER_EDIT must be delivered once')
    await uiB.screenshot({ path: path.join(ARTIFACTS, 'ui-competition-error.png') })
  })

  await step('实际UI：关闭第二标签后持有者操作仍送达观察者', async () => {
    await uiB.close()
    uiObserver = await uiContext.newPage()
    await openCropper(uiObserver)
    await uiObserver.waitForFunction(() => document.querySelector('.collab-status').textContent.includes('正在编辑'))
    assert.equal(await uiObserver.locator('button[title="放大"]').isDisabled(), true)
    await toolA.click()
    await uiObserver.waitForFunction(() => globalThis.__vs44UiSockets.at(-1).received.some(
      (m) => m.type === 'EDIT_ACTION' && m.editAction === 'ZOOM_IN'))
    const echoed = await uiObserver.evaluate(() => globalThis.__vs44UiSockets.at(-1).sent.filter(
      (m) => m.type === 'EDIT_ACTION').length)
    assert.equal(echoed, 0, 'receiving an action must not send it back')
    assert.equal(await toolA.isEnabled(), true)
    await uiObserver.screenshot({ path: path.join(ARTIFACTS, 'ui-observer-action.png') })
  })

  await step('实际UI：空闲持有者注销后工具禁用且其他连接可接管', async () => {
    const logout = await post(uiA, '/api/user/logout', {})
    assert.equal(logout.body?.code, 0)
    await uiA.waitForFunction(() => document.querySelector('.cropper-modal button[title="放大"]').disabled,
      null, { timeout: 10000 })
    assert.equal(await toolA.isDisabled(), true)
    await uiA.screenshot({ path: path.join(ARTIFACTS, 'ui-after-idle-logout.png') })
    const next = await uiContext.newPage()
    await next.goto(`${origin}/user/login`)
    await loginOnly(next, editor)
    await openCropper(next)
    await next.getByRole('button', { name: '进入编辑', exact: true }).click()
    await next.waitForFunction(() => !document.querySelector('.cropper-modal button[title="放大"]').disabled)
    await next.screenshot({ path: path.join(ARTIFACTS, 'ui-successor.png') })
  })
  await uiContext.close()

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
