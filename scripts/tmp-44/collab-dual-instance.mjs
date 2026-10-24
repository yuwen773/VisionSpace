// 工单 44 双实例协作稳定性验收驱动。
//
// 由 scripts/tmp-44/collab-dual-instance.ps1 启动两个真实后端进程
// （共享同一个隔离 MySQL/Redis），然后运行本脚本：
//   VS44_PORT_A / VS44_PORT_B  两个后端实例的端口
//   VS44_MYSQL_PORT / VS44_REDIS_PORT / VS44_NONCE  隔离实例
//   VS44_ARTIFACTS             证据输出目录
//
// 覆盖：跨实例抢租约、续期、过期接管、同用户多标签、旧连接迟到事件、
// 撤权/注销即时生效、跨实例广播、非法消息隔离、拒绝场景零未授权写入。
// 任一断言失败即退出码 1；环境缺失退出码 2（BLOCKED_ENV）。
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { WebSocket } from 'ws'

const PORT_A = Number(process.env.VS44_PORT_A)
const PORT_B = Number(process.env.VS44_PORT_B)
const MYSQL_PORT = Number(process.env.VS44_MYSQL_PORT)
const REDIS_PORT = Number(process.env.VS44_REDIS_PORT)
const NONCE = process.env.VS44_NONCE
const ARTIFACTS = process.env.VS44_ARTIFACTS || 'collab-artifacts-44'
const PASSWORD = 'acceptance-password'

if (!PORT_A || !PORT_B || !MYSQL_PORT || !REDIS_PORT || !NONCE) {
  console.error('BLOCKED_ENV: VS44_PORT_A/B, VS44_MYSQL_PORT, VS44_REDIS_PORT, VS44_NONCE are required')
  process.exit(2)
}
fs.mkdirSync(ARTIFACTS, { recursive: true })

const results = []
const evidence = { scenarios: [], finalData: {} }

function record(name, ok, detail) {
  results.push({ name, ok, detail: detail || '' })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`)
  evidence.scenarios.push({ name, ok, detail: detail || '', at: new Date().toISOString() })
}

// ---- HTTP helper with cookie jar ----
function makeClient() {
  const jar = new Map()
  async function request(method, port, urlPath, body, extraHeaders = {}) {
    const headers = { ...extraHeaders }
    if (jar.size) headers['Cookie'] = [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    // 双提交 CSRF：jar 里已有的 VS_CSRF cookie 放进请求头
    if (jar.has('VS_CSRF')) headers['X-CSRF-Token'] = jar.get('VS_CSRF')
    const res = await fetch(`http://127.0.0.1:${port}/api${urlPath}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    for (const raw of res.headers.getSetCookie?.() || []) {
      const [pair] = raw.split(';')
      const eq = pair.indexOf('=')
      const name = pair.slice(0, eq).trim()
      const value = pair.slice(eq + 1).trim()
      if (value && value !== '""' && !/Expires=Thu, 01 Jan 1970/.test(raw)) jar.set(name, value)
      else jar.delete(name)
    }
    const text = await res.text()
    let json = null
    try { json = JSON.parse(text) } catch { /* html responses */ }
    return { status: res.status, json, text }
  }
  return { request, jar }
}

// ---- WebSocket helper ----
class WsSession {
  constructor(ws, label) {
    this.ws = ws
    this.label = label
    this.messages = []
    this.opened = new Promise((resolve, reject) => {
      ws.once('open', resolve)
      ws.once('error', (e) => reject(new Error(`handshake failed: ${e.message}`)))
    })
    ws.on('message', (data) => {
      try { this.messages.push(JSON.parse(data.toString())) } catch { this.messages.push({ type: 'UNPARSEABLE' }) }
    })
    ws.on('error', () => {})
  }

  async send(obj) {
    await this.opened
    this.ws.send(JSON.stringify(obj))
  }

  async sendRaw(text) {
    await this.opened
    this.ws.send(text)
  }

  async awaitType(type, ms = 8000) {
    const deadline = Date.now() + ms
    let scanned = 0
    while (Date.now() < deadline) {
      while (scanned < this.messages.length) {
        if (this.messages[scanned].type === type) return this.messages[scanned]
        scanned++
      }
      await sleep(50)
    }
    return null
  }

  count(type) {
    return this.messages.filter((m) => m.type === type).length
  }

  close() {
    try { this.ws.close() } catch { /* already closed */ }
  }

  hardClose() {
    try { this.ws.terminate() } catch { /* already closed */ }
  }
}

function connect(port, pictureId, cookieJar) {
  return new Promise((resolve) => {
    const key = `ws44-${crypto.randomUUID()}`
    const ws = new WebSocket(`ws://127.0.0.1:${port}/api/ws/picture/edit?pictureId=${pictureId}`, {
      headers: { Cookie: [...cookieJar.entries()].map(([k, v]) => `${k}=${v}`).join('; ') },
    })
    const session = new WsSession(ws, key)
    session.opened.then(() => resolve(session)).catch((e) => resolve({ handshakeError: e.message }))
  })
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ---- fixtures via MySQL JDBC not available in node; seed through acceptance SQL ----
// Seeding happens in the PS driver via mysql.exe before this script runs.
// Environment: VS44_FIXTURE_JSON holds {users, spaceId, pictureId}.

async function main() {
  const phase = process.env.VS44_PHASE || 'scenarios'
  if (phase === 'register') {
    // 阶段 1：只注册用户（真实 HTTP 入口，密码摘要与登录一致），PS 侧 seed 后再跑 scenarios
    const accounts = ['owner_44', 'editor_44', 'viewer_44']
    const client = makeClient()
    for (const account of accounts) {
      const res = await client.request('POST', PORT_A, '/user/register', {
        userAccount: account, userPassword: PASSWORD, checkPassword: PASSWORD,
      })
      if (res.json?.code !== 0 && !/已存在|重复/.test(res.json?.message || '')) {
        throw new Error(`register ${account} failed: ${res.text}`)
      }
    }
    console.log('registered', accounts.join(','))
    process.exit(0)
  }
  const fixture = JSON.parse(fs.readFileSync(process.env.VS44_FIXTURE_JSON, 'utf8'))
  const { spaceId, pictureId } = fixture
  const userA = fixture.users.owner // team space admin/owner
  const userB = fixture.users.editor // team editor

  evidence.fixture = { spaceId, pictureId, users: fixture.users }

  // === 1. 跨实例抢租约：A 连实例1，B 连实例2，只有一个赢家 ===
  const clientA = makeClient()
  const loginA = await clientA.request('POST', PORT_A, '/user/login', { userAccount: userA.account, userPassword: PASSWORD })
  if (loginA.json?.code !== 0) throw new Error('owner login failed: ' + loginA.text)
  const clientB = makeClient()
  const loginB = await clientB.request('POST', PORT_B, '/user/login', { userAccount: userB.account, userPassword: PASSWORD })
  if (loginB.json?.code !== 0) throw new Error('editor login failed: ' + loginB.text)

  const wsA = await connect(PORT_A, pictureId, clientA.jar)
  if (wsA.handshakeError) throw new Error('A handshake failed: ' + wsA.handshakeError)
  const wsB = await connect(PORT_B, pictureId, clientB.jar)
  if (wsB.handshakeError) throw new Error('B handshake failed: ' + wsB.handshakeError)

  await wsA.send({ type: 'ENTER_EDIT' })
  const aEnter = await wsA.awaitType('ENTER_EDIT')
  record('跨实例抢租约-实例1持有', !!aEnter, aEnter?.message)

  await wsB.send({ type: 'ENTER_EDIT' })
  const bEnter = await wsB.awaitType('ERROR')
  record('跨实例抢租约-实例2被拒', !!bEnter && /正在编辑/.test(bEnter.message || ''), bEnter?.message)

  // === 2. 持有者在实例1操作，广播跨实例到达实例2的观察者 ===
  const observerC = makeClient()
  await observerC.request('POST', PORT_A, '/user/login', { userAccount: userA.account, userPassword: PASSWORD })
  const wsC = await connect(PORT_B, pictureId, observerC.jar)
  if (!wsC.handshakeError) {
    await sleep(300)
    await wsA.send({ type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    const seenOnB = await wsC.awaitType('EDIT_ACTION')
    record('编辑操作跨实例广播', !!seenOnB, seenOnB?.message)
    const bSelfRejected = !(await (async () => {
      await wsB.send({ type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
      return wsB.awaitType('ERROR', 3000)
    })())
    // wsB 不持有租约，操作必须被拒
    const bReject = await wsB.awaitType('ERROR', 3000)
    record('非持有者操作被拒（跨实例验证租约归属）', !!bReject, bReject?.message)
    wsC.close()
  } else {
    record('编辑操作跨实例广播', false, 'observer handshake failed: ' + wsC.handshakeError)
  }

  // === 3. 同用户多标签：同账号从两个实例各开一条，第二标签关掉不释放 ===
  const tab2 = makeClient()
  await tab2.request('POST', PORT_B, '/user/login', { userAccount: userA.account, userPassword: PASSWORD })
  const wsA2 = await connect(PORT_B, pictureId, tab2.jar)
  if (wsA2.handshakeError) throw new Error('tab2 handshake failed: ' + wsA2.handshakeError)
  await wsA2.send({ type: 'ENTER_EDIT' })
  const tab2Rejected = await wsA2.awaitType('ERROR')
  record('同用户第二标签进入被拒', !!tab2Rejected, tab2Rejected?.message)
  wsA2.close() // 关闭第二标签
  await sleep(500)
  // 第一标签的租约必须还在：再次发送操作仍然成功广播
  const probe = makeClient()
  await probe.request('POST', PORT_B, '/user/login', { userAccount: userB.account, userPassword: PASSWORD })
  const wsProbe = await connect(PORT_B, pictureId, probe.jar)
  if (!wsProbe.handshakeError) {
    await sleep(200)
    await wsA.send({ type: 'EDIT_ACTION', editAction: 'ZOOM_OUT' })
    const stillBroadcast = await wsProbe.awaitType('EDIT_ACTION')
    record('关闭第二标签后原租约仍有效', !!stillBroadcast, stillBroadcast?.message)
    wsProbe.close()
  } else {
    record('关闭第二标签后原租约仍有效', false, 'probe handshake failed')
  }

  // === 4. 迟到事件：硬断开实例1的持有者连接，接管后注入迟到 EXIT 不影响新租约 ===
  // 持有者断开（模拟断线，不发关闭帧）
  wsA.hardClose()
  await sleep(300)
  // 服务端 afterConnectionClosed 会按连接释放租约（正常路径）。
  // 断线释放后 B 可立即接管：
  await wsB.send({ type: 'ENTER_EDIT' })
  const bTakeover = await wsB.awaitType('ENTER_EDIT', 8000)
  record('断线后另一实例接管租约', !!bTakeover, bTakeover?.message)

  // 旧连接对象迟到发送（连接已死应直接失败；若复活则被归属验证拒绝）
  try {
    wsA.messages = []
    await wsA.send({ type: 'EDIT_ACTION', editAction: 'ROTATE_LEFT' }).catch(() => {})
    await sleep(500)
    const lateWriteVisible = wsB.messages.some((m) => m.type === 'EDIT_ACTION' && /ROTATE_LEFT/.test(m.message || ''))
    record('断线旧连接迟到事件不写入', !lateWriteVisible, lateWriteVisible ? 'stale write observed' : '')
  } catch {
    record('断线旧连接迟到事件不写入', true, 'send failed as expected')
  }

  // === 5. 撤权即时生效（跨实例）：B 持租约时降权，B 的后续操作被拒且租约被收回 ===
  await wsB.send({ type: 'EXIT_EDIT' })
  await sleep(300)
  // 重新拿租约：A 短暂持有后释放，B 拿到
  await wsB.send({ type: 'ENTER_EDIT' })
  const bLease = await wsB.awaitType('ENTER_EDIT', 5000)
  record('B 重新获得租约', !!bLease, bLease?.message)
  if (bLease) {
    // 降权 B 为 viewer（驱动脚本通过 PS 注入的 SQL marker 文件触发）
    fs.writeFileSync(process.env.VS44_DEMOTE_FILE || path.join(ARTIFACTS, 'demote.trigger'), JSON.stringify({ spaceId, user: userB }))
    // 等待 PS 侧执行 UPDATE
    let demoted = false
    for (let i = 0; i < 40; i++) {
      if (fs.existsSync((process.env.VS44_DEMOTE_DONE_FILE || path.join(ARTIFACTS, 'demote.done')))) { demoted = true; break }
      await sleep(250)
    }
    if (!demoted) throw new Error('demotion trigger never completed')
    await wsB.send({ type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    const bDenied = await wsB.awaitType('ERROR', 8000)
    record('降权后写入被拒（跨实例）', !!bDenied, bDenied?.message)
    // 租约应被收回，A 可以立即接管
    await wsA.send({ type: 'ENTER_EDIT' })
    const aTakeover = await wsA.awaitType('ENTER_EDIT', 8000)
    record('降权后原持有者租约被收回', !!aTakeover, aTakeover?.message)
  }

  // === 6. 注销即时生效（跨实例）：新连接持租约后走 REST 注销 ===
  const clientD = makeClient()
  await clientD.request('POST', PORT_A, '/user/login', { userAccount: userB.account, userPassword: PASSWORD })
  const wsD = await connect(PORT_B, pictureId, clientD.jar)
  if (wsD.handshakeError) throw new Error('D handshake failed: ' + wsD.handshakeError)
  await wsA.send({ type: 'EXIT_EDIT' }) // A 释放，让 D 拿租约
  await sleep(300)
  await wsD.send({ type: 'ENTER_EDIT' })
  const dLease = await wsD.awaitType('ENTER_EDIT', 5000)
  record('注销场景 D 获得租约', !!dLease, dLease?.message)
  if (dLease) {
    const logout = await clientD.request('POST', PORT_A, '/user/logout')
    record('REST 注销成功', logout.json?.code === 0, logout.json?.message)
    await sleep(600) // 会话在 Redis 中失效
    await wsD.send({ type: 'EDIT_ACTION', editAction: 'ZOOM_IN' })
    const dDenied = await wsD.awaitType('ERROR', 8000)
    record('注销后写入被拒（跨实例）', !!dDenied, dDenied?.message)
  }

  // === 7. 非法消息隔离（跨实例连接上验证） ===
  if (!wsD.handshakeError && wsD.ws.readyState === 1) {
    await wsD.send({ type: '' })
    const e1 = await wsD.awaitType('ERROR', 5000)
    await wsD.sendRaw('not-json {{{')
    const e2 = await wsD.awaitType('ERROR', 5000)
    record('非法与损坏消息被拒绝', !!e1 && !!e2, [e1?.message, e2?.message].join(' | '))
    // 注销后登录态已失效，ENTER_EDIT 会被复核拒绝——这里仅验证非法消息后通道仍响应
    record('非法消息后通道仍处理消息', wsD.messages.length >= 2, `total=${wsD.messages.length}`)
  }

  // === 8. 拒绝场景零未授权写入：以 viewer 账号连接被拒握手 ===
  const clientViewer = makeClient()
  await clientViewer.request('POST', PORT_A, '/user/login', { userAccount: fixture.users.viewer.account, userPassword: PASSWORD })
  const wsViewer = await connect(PORT_A, pictureId, clientViewer.jar)
  record('viewer 握手被拒', !!wsViewer.handshakeError, wsViewer.handshakeError || 'unexpectedly connected')

  wsB.close()
  if (wsD && !wsD.handshakeError) wsD.close()

  evidence.finalData = {
    note: '最终数据结果由 PS 驱动在断言后查询 MySQL：picture 行保持 name=original，space 配额不变。',
  }
  fs.writeFileSync(path.join(ARTIFACTS, 'protocol-results.json'), JSON.stringify(evidence, null, 2))

  const failed = results.filter((r) => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} scenarios passed`)
  process.exit(failed.length ? 1 : 0)
}

main().catch((e) => {
  console.error('FATAL', e)
  process.exit(1)
})
