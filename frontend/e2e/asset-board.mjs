import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { startHttpEntry } from './fixture-server.mjs'

const [backendPort, viewerAccount, ownerAccount, spaceId, memberId, artifacts] = process.argv.slice(2)
assert.ok(backendPort && artifacts)
const server = await startHttpEntry(`http://127.0.0.1:${backendPort}`)
await server.listen()
const origin = `http://127.0.0.1:${server.port}`
const browser = await chromium.launch({ headless: true })
const calls = []
async function newPage(context) {
  const page = await context.newPage()
  page.on('request', r => calls.push({ method: r.method(), path: new URL(r.url()).pathname }))
  page.on('dialog', d => d.accept())
  return page
}
async function context() {
  const c = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await c.route(/^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//, r => r.fulfill({ contentType: 'text/css', body: '' }))
  return c
}
async function post(page, pathname, body) {
  return page.evaluate(async ({ pathname, body }) => {
    const cookie = document.cookie.split(';').map(c => c.trim()).find(c => c.startsWith('VS_CSRF='))
    const r = await fetch(`/api${pathname}`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': cookie ? decodeURIComponent(cookie.slice(8)) : '' }, body: JSON.stringify(body) })
    return r.json()
  }, { pathname, body })
}
async function login(page, account) {
  await page.goto(`${origin}/user/login`)
  await page.waitForLoadState('networkidle')
  assert.equal((await post(page, '/user/login', { userAccount: account, userPassword: 'acceptance-password' })).code, 0)
}
try {
  const viewerContext = await context()
  const page = await newPage(viewerContext)
  await login(page, viewerAccount)
  await page.goto(`${origin}/agent`)
  await page.waitForLoadState('networkidle')
  await page.getByLabel('站内找图', { exact: true }).check()
  await page.getByLabel('选择团队空间').selectOption(spaceId)
  await page.getByRole('button', { name: '查找素材', exact: true }).click()
  const board = page.getByRole('region', { name: '个人候选素材板' })
  await board.getByRole('button', { name: '加入候选板' }).click()
  await board.getByLabel('选择素材', { exact: true }).uncheck()
  await board.getByLabel('板标题', { exact: true }).fill('<script>window.boardInjected=1</script>')
  await board.getByLabel('新分组名称').fill('<b>横图</b>')
  await board.getByRole('button', { name: '添加分组' }).click()
  await board.getByLabel('素材分组').selectOption({ label: '<b>横图</b>' })
  // 后端已提交，再丢弃响应：首次创建时浏览器仍不知道 boardId。
  await page.route('**/api/agent/boards', async route => { await route.fetch(); await route.abort('failed') }, { times: 1 })
  await board.getByRole('button', { name: '保存候选板', exact: true }).click()
  await board.getByRole('status').filter({ hasText: '保存结果未知' }).waitFor()
  await board.getByRole('button', { name: '核对保存结果' }).click()
  await board.getByText('已保存 · 版本 1', { exact: true }).waitFor()
  const savedUrl = page.url()
  const boardId = new URL(savedUrl).searchParams.get('board')
  assert.ok(boardId)
  assert.equal(await page.evaluate(() => window.boardInjected), undefined)
  assert.equal(await board.locator('b,script').count(), 0)
  assert.equal(await board.getByLabel('选择素材', { exact: true }).isChecked(), false)
  assert.equal(calls.filter(c => c.method === 'POST' && c.path === '/api/agent/boards').length, 1)
  await page.screenshot({ path: path.join(artifacts, '01-saved.png'), fullPage: true, animations: 'disabled' })
  const contentRef = await board.locator('img').getAttribute('src')
  const second = await newPage(viewerContext)
  await second.goto(savedUrl)
  await second.waitForLoadState('networkidle')
  const otherBoard = second.getByRole('region', { name: '个人候选素材板' })
  await otherBoard.getByText('已保存 · 版本 1', { exact: true }).waitFor()
  await board.getByLabel('板标题', { exact: true }).fill('第一窗口已保存')
  await board.getByRole('button', { name: '保存候选板', exact: true }).click()
  await board.getByText('已保存 · 版本 2', { exact: true }).waitFor()
  await otherBoard.getByLabel('板标题', { exact: true }).fill('第二窗口旧版本')
  await otherBoard.getByRole('button', { name: '保存候选板', exact: true }).click()
  await otherBoard.getByRole('status').filter({ hasText: '版本冲突' }).waitFor()
  await otherBoard.getByRole('button', { name: '读取最新版（放弃本页修改）', exact: true }).click()
  await otherBoard.getByText('已保存 · 版本 2', { exact: true }).waitFor()
  assert.equal(await otherBoard.getByLabel('板标题', { exact: true }).inputValue(), '第一窗口已保存')
  await second.screenshot({ path: path.join(artifacts, '02-conflict-recovered.png'), fullPage: true, animations: 'disabled' })
  await second.close()
  await page.reload()
  await page.waitForLoadState('networkidle')
  await board.getByText('已保存 · 版本 2', { exact: true }).waitFor()
  assert.equal((await post(page, '/user/logout', {})).code, 0)
  await login(page, viewerAccount)
  await page.goto(savedUrl)
  await page.waitForLoadState('networkidle')
  await board.getByText('已保存 · 版本 2', { exact: true }).waitFor()
  const owner = await newPage(await context())
  await login(owner, ownerAccount)
  assert.equal((await post(owner, '/spaceUser/delete', { id: memberId })).code, 0)
  const revoked = page.waitForResponse(async r => new URL(r.url()).pathname === `/api/agent/boards/${boardId}` && (await r.json().catch(() => null))?.code === 40101)
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await revoked
  await page.waitForFunction(() => !document.querySelector('.board-editor img'))
  assert.notEqual(await board.getByLabel('板标题', { exact: true }).inputValue(), '第一窗口已保存')
  assert.equal(await page.evaluate(async ref => (await fetch(ref)).status, contentRef), 403)
  await page.screenshot({ path: path.join(artifacts, '03-revoked.png'), fullPage: true, animations: 'disabled' })
  assert.ok(!calls.some(c => /delete|upload|generate/.test(c.path) && !/spaceUser|logout/.test(c.path)))
  fs.writeFileSync(path.join(artifacts, 'results.json'), JSON.stringify({ status: 'PASS', boardId, calls }, null, 2))
  console.log('PASS: explicit editing, lost creation response, readOperation, two-client conflict, reload, re-login and revocation')
} catch (error) {
  for (const [index, page] of browser.contexts().flatMap(c => c.pages()).entries()) {
    await page.screenshot({ path: path.join(artifacts, `failure-${index}.png`), fullPage: true }).catch(() => {})
    fs.writeFileSync(path.join(artifacts, `failure-${index}.txt`), await page.locator('body').innerText().catch(() => 'closed'))
  }
  fs.writeFileSync(path.join(artifacts, 'requests.json'), JSON.stringify(calls, null, 2))
  throw error
} finally { await browser.close(); await server.close() }
