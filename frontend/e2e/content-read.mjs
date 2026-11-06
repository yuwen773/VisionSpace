// Invoked by HttpPictureContentAuthTest against its verified, isolated HTTP server.
import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'

const base = process.env.VS_CONTENT_API
const picture = process.env.VS_CONTENT_PICTURE
const membership = process.env.VS_CONTENT_MEMBERSHIP
if (!base || !picture || !membership) process.exit(2)

const browser = await chromium.launch({ headless: true })
async function login(account) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`${base}/api/user/get/login`)
  const response = await post(page, '/api/user/login', { userAccount: account, userPassword: 'acceptance-password' })
  assert.equal(response.code, 0, 'fixture login must succeed through the real HTTP entry')
  return page
}
async function post(page, url, body) {
  return page.evaluate(async ({ url, body }) => {
    const token = document.cookie.split(';').map((part) => part.trim()).find((part) => part.startsWith('VS_CSRF='))
    const response = await fetch(url, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', ...(token ? { 'X-CSRF-Token': decodeURIComponent(token.slice(8)) } : {}) }, body: JSON.stringify(body) })
    return response.json()
  }, { url, body })
}
async function read(page, suffix) {
  return page.evaluate(async (url) => {
    const response = await fetch(url, { credentials: 'include', cache: 'no-store' })
    return { status: response.status, body: await response.text(), cache: response.headers.get('cache-control'), disposition: response.headers.get('content-disposition') }
  }, `/api/picture/content/${picture}/${suffix}`)
}

try {
  const owner = await login(process.env.VS_CONTENT_OWNER)
  const viewer = await login(process.env.VS_CONTENT_VIEWER)
  const stranger = await login(process.env.VS_CONTENT_STRANGER)
  const variants = ['original', 'thumbnail', 'preview', 'original?download=true']
  for (const variant of variants) {
    const content = await read(viewer, variant)
    assert.equal(content.status, 200, `authorized ${variant}`)
    assert.equal(content.body, 'acceptance-image-bytes', 'the browser must actually consume stored bytes')
    assert.match(content.cache, /no-store/)
    if (variant.includes('download')) assert.match(content.disposition, /attachment/)
  }
  for (const variant of variants) {
    const content = await read(stranger, variant)
    assert.equal(content.status, 403, `stranger ${variant}`)
    assert.equal(content.body, '', 'denied content must not expose bytes')
  }
  const removed = await post(owner, '/api/spaceUser/delete', { id: membership })
  assert.equal(removed.code, 0, 'real authorized member removal must succeed')
  for (const variant of variants) {
    const content = await read(viewer, variant)
    assert.equal(content.status, 403, `revoked ${variant}`)
    assert.equal(content.body, '', 'an existing browser session cannot read after revocation')
  }
  console.log('PASS: browser actual original/thumbnail/preview/download bytes, stranger denial and immediate revocation')
} finally {
  await browser.close()
}
