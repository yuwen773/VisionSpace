import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { startHttpEntry } from './fixture-server.mjs'

const [backendPort, viewerAccount, ownerAccount, spaceId, memberId, artifacts] = process.argv.slice(2)
assert.ok(backendPort && viewerAccount && ownerAccount && spaceId && memberId && artifacts, 'isolated fixture arguments required')
const server = await startHttpEntry(`http://127.0.0.1:${backendPort}`)
await server.listen()
const origin = `http://127.0.0.1:${server.port}`
const browser = await chromium.launch({ headless: true })
const calls = []
const fontOrigins = new Set(['https://fonts.googleapis.cn', 'https://fonts.googleapis.com', 'https://fonts.gstatic.cn', 'https://fonts.gstatic.com'])
async function offlinePage(options = {}) {
  const context = await browser.newContext(options)
  // 项目原有远程字体在本测试中离线替代，浏览器验收不依赖公网字体服务。
  await context.route(/^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//,
    (route) => route.fulfill({ contentType: 'text/css', body: '' }))
  return context.newPage()
}
async function post(page, pathname, body) {
  return page.evaluate(async ({ pathname, body }) => {
    const token = document.cookie.split(';').map((part) => part.trim()).find((part) => part.startsWith('VS_CSRF='))
    const response = await fetch(`/api${pathname}`, { method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token ? decodeURIComponent(token.slice(8)) : '' }, body: JSON.stringify(body) })
    return response.json()
  }, { pathname, body })
}
async function login(page, account) {
  await page.goto(`${origin}/user/login`)
  await page.waitForLoadState('networkidle')
  assert.equal((await post(page, '/user/login', { userAccount: account, userPassword: 'acceptance-password' })).code, 0)
}
try {
  const page = await offlinePage({ viewport: { width: 1440, height: 1000 } })
  page.on('request', (request) => calls.push({ method: request.method(), path: new URL(request.url()).pathname, origin: new URL(request.url()).origin }))
  await login(page, viewerAccount)
  assert.equal((await post(page, '/agent/asset-search', { spaceId, criteria: { dominantColorFamily: 'BLUE_OR_PURPLE' } })).message, 'RULE_NOT_READY')
  await page.goto(`${origin}/agent`)
  await page.waitForLoadState('networkidle')
  await page.getByLabel('站内找图', { exact: true }).check()
  await page.getByLabel('选择团队空间').selectOption(spaceId)
  await page.getByRole('button', { name: '查找素材', exact: true }).click()
  await page.waitForFunction(() => document.querySelectorAll('[data-result-id]').length === 2)
  const resultId = await page.locator('[data-result-id]').first().getAttribute('data-result-id')
  assert.equal(await page.locator(`[data-result-id="${resultId}"]`).count(), 2)
  await page.waitForFunction(() => [...document.querySelectorAll('[data-result-id] img')].every((img) => img.naturalWidth > 0))
  assert.equal(calls.filter((call) => call.method === 'POST' && call.path === '/api/agent/asset-search').length, 2)
  const contentRef = await page.locator('[data-result-id] img').first().getAttribute('src')
  await page.waitForFunction(() => document.querySelector('.resource-panel')?.getBoundingClientRect().width > 200)
  await page.screenshot({ path: path.join(artifacts, '01-shared-result.png'), fullPage: true, animations: 'disabled' })
  await page.reload()
  await page.waitForLoadState('networkidle')
  await page.waitForFunction((id) => document.querySelectorAll(`[data-result-id="${id}"]`).length === 2, resultId)
  assert.equal(calls.filter((call) => call.method === 'POST' && call.path === '/api/agent/asset-search').length, 2, 'reload must read the existing snapshot')

  // 真实 SSE 模式：确定性模型替身只规划条件，最终卡片仍通过同一个 resultId 读取。
  await page.getByLabel('站内找图', { exact: true }).check()
  await page.getByLabel('选择团队空间').selectOption(spaceId)
  await page.locator('textarea').fill('找横图')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.waitForFunction(() => new Set([...document.querySelectorAll('[data-result-id]')].map((el) => el.getAttribute('data-result-id'))).size >= 2)
  const agentId = await page.locator('[data-result-id]').first().getAttribute('data-result-id')
  assert.notEqual(agentId, resultId)
  assert.equal(await page.locator(`[data-result-id="${agentId}"]`).count(), 2)

  const owner = await offlinePage()
  await login(owner, ownerAccount)
  assert.equal((await post(owner, '/spaceUser/delete', { id: memberId })).code, 0)
  await page.bringToFront()
  await page.getByRole('button', { name: '刷新状态', exact: true }).first().click().catch(() => {})
  await page.waitForFunction(() => document.querySelectorAll('[data-result-id] img').length === 0)
  assert.equal(await page.locator('[data-result-id]').count(), 0)
  assert.equal(await page.evaluate(async (url) => (await fetch(url, { credentials: 'include' })).status, contentRef), 403)
  await page.screenshot({ path: path.join(artifacts, '02-revoked.png'), fullPage: true, animations: 'disabled' })
  fs.writeFileSync(path.join(artifacts, 'requests.json'), JSON.stringify(calls, null, 2))
  assert.ok(calls.every((call) => call.origin === origin || call.origin === 'null' || fontOrigins.has(call.origin)),
    `unexpected browser destination: ${JSON.stringify(calls.filter((call) => call.origin !== origin && call.origin !== 'null' && !fontOrigins.has(call.origin)))}`)
  fs.writeFileSync(path.join(artifacts, 'results.json'), JSON.stringify({ status: 'PASS', resultId, agentId, calls }, null, 2))
  console.log('PASS: shared snapshot, actual preview, reload, SSE and revoked access')
} finally {
  await browser.close()
  await server.close()
}
