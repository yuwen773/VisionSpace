import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { startHttpEntry } from './fixture-server.mjs'
const [port, account, boardId, pictureId, artifacts] = process.argv.slice(2)
const server = await startHttpEntry(`http://127.0.0.1:${port}`)
await server.listen()
const origin = `http://127.0.0.1:${server.port}`
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await context.route(/^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//, r => r.fulfill({ contentType: 'text/css', body: '' }))
const page = await context.newPage()
const requests = []
page.on('request', r => requests.push({ method: r.method(), path: new URL(r.url()).pathname }))
async function post(url, body) {
  return page.evaluate(async ({ url, body }) => {
    const token = document.cookie.split(';').map(c => c.trim()).find(c => c.startsWith('VS_CSRF='))?.slice(8)
    const r = await fetch(`/api${url}`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token ? decodeURIComponent(token) : '' }, body: JSON.stringify(body) })
    return r.json()
  }, { url, body })
}
async function login() {
  await page.goto(`${origin}/user/login`); await page.waitForLoadState('networkidle')
  assert.equal((await post('/user/login', { userAccount: account, userPassword: 'acceptance-password' })).code, 0)
}
const panel = page.getByRole('region', { name: '候选板保存提案', exact: true })
const board = page.getByRole('region', { name: '个人候选素材板', exact: true })
async function generate() {
  await panel.getByLabel('整理要求').fill('取消选择并分为一组')
  await panel.getByRole('button', { name: '生成保存提案' }).click()
  await panel.getByText('等待你的决定', { exact: true }).waitFor()
  assert.ok(new URL(page.url()).searchParams.get('task'))
}
try {
  await login()
  await page.goto(`${origin}/agent?board=${boardId}`); await page.waitForLoadState('networkidle')
  await board.getByText('已保存 · 版本 1', { exact: true }).waitFor()
  await generate()
  const taskUrl = page.url()
  assert.equal(await board.getByLabel('选择素材', { exact: true }).isChecked(), true)
  assert.ok((await panel.innerText()).includes('已选'))
  assert.ok((await panel.innerText()).includes('未选'))
  assert.ok((await panel.innerText()).includes('<b>Agent精选</b>'))
  assert.equal(await panel.locator('b,script').count(), 0)
  await page.waitForFunction(() => { const button = document.querySelector('[data-action="confirm-proposal"]'); return button && !button.disabled })
  await page.screenshot({ path: path.join(artifacts, '01-review.png'), fullPage: true })
  // 提案通知断开只停止展示；刷新通过任务恢复，不能自动保存。
  await page.evaluate(async task => { const c = new AbortController(); const r = await fetch(`/api/agent/tasks/${task}/events`, { headers: { 'X-VS-AI-Event-Version': '1' }, signal: c.signal }); await r.text(); c.abort() }, new URL(taskUrl).searchParams.get('task'))
  await page.reload(); await page.waitForLoadState('networkidle')
  await panel.getByText('等待你的决定', { exact: true }).waitFor()
  await page.route('**/api/agent/board-proposals/*/decision', async route => { await route.fetch(); await route.abort('failed') }, { times: 1 })
  await panel.getByRole('button', { name: '确认保存个人草稿' }).click()
  await panel.getByRole('status').filter({ hasText: '决定结果未知' }).waitFor()
  await panel.getByRole('button', { name: '核对任务状态' }).click()
  await panel.getByText('已提交 · 版本 2', { exact: true }).waitFor()
  await board.getByText('已保存 · 版本 2', { exact: true }).waitFor()
  assert.equal(await board.getByLabel('选择素材', { exact: true }).isChecked(), false)
  await page.reload(); await page.waitForLoadState('networkidle')
  await panel.getByText('已提交 · 版本 2', { exact: true }).waitFor()
  await post('/user/logout', {})
  await login(); await page.goto(taskUrl); await page.waitForLoadState('networkidle')
  await panel.getByText('已提交 · 版本 2', { exact: true }).waitFor()
  assert.equal(requests.filter(r => r.method === 'POST' && r.path.endsWith('/decision')).length, 1)
  await panel.getByRole('button', { name: '整理新版' }).click()
  await generate()
  assert.equal((await post('/moderation/quarantine', { resourceType: 'picture', resourceId: pictureId, reason: 'controlled proposal isolation' })).code, 0)
  await panel.getByRole('button', { name: '核对任务状态' }).click()
  await panel.getByText('当前无法展示私人差异。本人有效会话仍可核对状态或取消未决提案。', { exact: true }).waitFor()
  assert.equal(await panel.locator('[data-action="confirm-proposal"]').count(), 0)
  assert.equal((await panel.innerText()).includes('Agent精选'), false)
  await panel.getByRole('button', { name: '取消提案', exact: true }).click()
  await panel.getByText('已取消', { exact: true }).waitFor()
  await page.screenshot({ path: path.join(artifacts, '02-cancelled-private.png'), fullPage: true })
  await page.reload(); await page.waitForLoadState('networkidle')
  await panel.getByText('已取消', { exact: true }).waitFor()
  fs.writeFileSync(path.join(artifacts, 'results.json'), JSON.stringify({ status: 'PASS', requests }, null, 2))
  console.log('PASS: review, notification disconnect, lost commit response, refresh, re-login, isolation and explicit cancellation')
} catch (error) {
  await page.screenshot({ path: path.join(artifacts, 'failure.png'), fullPage: true }).catch(() => {})
  fs.writeFileSync(path.join(artifacts, 'failure.txt'), await page.locator('body').innerText().catch(() => 'closed'))
  throw error
} finally { await browser.close(); await server.close() }
