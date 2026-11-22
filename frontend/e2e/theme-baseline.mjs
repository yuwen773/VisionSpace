// 主题基线截图：把两个主题在同一组页面上的样子固定下来，供改样式前后对照。
//
// 为什么需要它：`global.css` / `global-ziyan.css` / `antd-theme*.css` 的组件覆盖段
// 都没有主题作用域，后加载的紫漾会盖住极光（详见架构评审 c8）。要收作用域，就必然
// 让极光换回自己的规则——这会产生可见的外观变化。没有改前/改后的同条件截图，
// 就无法判断变化是「修好了」还是「弄坏了」。
//
// 用法（在 frontend/ 下）：
//   node e2e/theme-baseline.mjs --base http://127.0.0.1:8081 --out ../theme-baselines/before
//   node e2e/theme-baseline.mjs --base http://127.0.0.1:8081 --out ../theme-baselines/after \
//     --account admin --password '…' --space 12345
//
// 参数：
//   --base            站点根地址（默认 http://127.0.0.1:8081）
//   --out             输出目录（必填；已存在也照写，同名文件会被覆盖）
//   --account/--password  登录用的账号；不给就只拍匿名可看的页面
//   --space           展厅页的空间 id；不给就跳过展厅
//   --viewport        宽x高（默认 1440x1000）
//   --with-web-fonts  允许加载远程网页字体（默认拦截，保证离线可重复）
//
// 产出：`<out>/<theme>-<page>.png` 与 `<out>/manifest.json`。
// manifest 记录每张图的最终地址、`<html>` 上的主题类与截图时间——拍摄条件可复查，
// 不会出现「以为拍的是极光、其实是紫漾」这种静默错误。
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright-core'

const THEMES = ['aurora', 'pop']

/** @param {string[]} argv */
function parseArgs(argv) {
  /** @type {Record<string, string | boolean>} */
  const options = {
    base: 'http://127.0.0.1:8081',
    out: '',
    account: '',
    password: '',
    space: '',
    viewport: '1440x1000',
    'with-web-fonts': false,
    help: false,
  }
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    if (key === 'help' || key === 'with-web-fonts') {
      options[key] = true
      continue
    }
    const value = argv[i + 1]
    assert.ok(value && !value.startsWith('--'), `--${key} 需要一个值`)
    options[key] = value
    i += 1
  }
  return options
}

const options = parseArgs(process.argv.slice(2))
if (options.help || !options.out) {
  console.log(`主题基线截图

  node e2e/theme-baseline.mjs --out <目录> [选项]

  --base <url>          站点根地址，默认 http://127.0.0.1:8081
  --account <账号>      登录账号；不给则只拍匿名页面
  --password <密码>     登录密码
  --space <空间id>      展厅页的空间；不给则跳过展厅
  --viewport <宽x高>    默认 1440x1000
  --with-web-fonts      允许加载远程网页字体（默认拦截以保证可重复）`)
  process.exit(options.help ? 0 : 1)
}

const base = String(options.base).replace(/\/$/, '')
const outDir = path.resolve(String(options.out))
const account = String(options.account || '')
const password = String(options.password || '')
const spaceId = String(options.space || '')
const [width, height] = String(options.viewport).split('x').map((part) => Number(part))
assert.ok(width > 0 && height > 0, '--viewport 需要写成 宽x高，例如 1440x1000')

fs.mkdirSync(outDir, { recursive: true })

/** 要拍的页面。展厅与后台是否可拍取决于参数与账号权限。 */
const pages = [
  { key: 'landing', pathname: '/' },
  { key: 'explore', pathname: '/explore' },
]
if (spaceId) pages.push({ key: 'gallery', pathname: `/space/${spaceId}` })
if (account) pages.push({ key: 'admin', pathname: '/admin/dashboard' })

const browser = await chromium.launch({ headless: true })

/** @type {Array<Record<string, unknown>>} */
const manifest = []

try {
  const context = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: 1,
    // 截图只反映版面与配色，不掺入系统的深浅偏好
    colorScheme: 'light',
  })
  if (!options['with-web-fonts']) {
    // 远程字体按离线处理：基线要可重复，不能因为公网字体服务抖动而变形
    await context.route(/^https:\/\/fonts\.(googleapis|gstatic)\.(com|cn)\//, (route) =>
      route.fulfill({ contentType: 'text/css', body: '' }),
    )
  }
  const page = await context.newPage()

  if (account) {
    await page.goto(`${base}/user/login`)
    await page.waitForLoadState('networkidle')
    const result = await page.evaluate(
      async ({ account, password }) => {
        const csrf = document.cookie
          .split(';')
          .map((part) => part.trim())
          .find((part) => part.startsWith('VS_CSRF='))
        const response = await fetch('/api/user/login', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRF-Token': csrf ? decodeURIComponent(csrf.slice(8)) : '',
          },
          body: JSON.stringify({ userAccount: account, userPassword: password }),
        })
        return response.json()
      },
      { account, password },
    )
    assert.equal(result.code, 0, `登录失败：${JSON.stringify(result)}`)
  }

  for (const theme of THEMES) {
    // 主题在应用启动时从 localStorage 读取并写到 <html> 的类上
    await page.goto(`${base}/`)
    await page.evaluate((value) => localStorage.setItem('ycloud-theme', value), theme)

    for (const target of pages) {
      const entry = {
        theme,
        page: target.key,
        requested: `${base}${target.pathname}`,
        finalUrl: '',
        htmlClass: '',
        file: '',
        capturedAt: new Date().toISOString(),
        skipped: '',
      }
      try {
        await page.goto(`${base}${target.pathname}`)
        await page.waitForLoadState('networkidle')
        await page.evaluate(() => document.fonts.ready)
        await page.waitForTimeout(300)

        entry.finalUrl = page.url()
        entry.htmlClass = await page.evaluate(() => document.documentElement.className)

        // 拍摄条件必须自证：主题类不对就说明拍错了，宁可不产出图
        assert.ok(
          entry.htmlClass.split(/\s+/).includes(`theme-${theme}`),
          `${target.pathname} 实际主题类为「${entry.htmlClass}」，期望 theme-${theme}`,
        )
        // 被路由守卫改写地址（例如非管理员访问后台）时，拍下来的不是要看的页面
        const expected = new URL(`${base}${target.pathname}`)
        const actual = new URL(String(entry.finalUrl))
        if (actual.pathname !== expected.pathname) {
          entry.skipped = `重定向到 ${actual.pathname}`
          console.warn(`跳过 ${theme}-${target.key}：${entry.skipped}`)
          manifest.push(entry)
          continue
        }

        entry.file = `${theme}-${target.key}.png`
        await page.screenshot({
          path: path.join(outDir, String(entry.file)),
          fullPage: true,
          animations: 'disabled',
        })
        console.log(`已拍摄 ${entry.file}`)
      } catch (error) {
        entry.skipped = error instanceof Error ? error.message : String(error)
        console.warn(`跳过 ${theme}-${target.key}：${entry.skipped}`)
      }
      manifest.push(entry)
    }
  }

  fs.writeFileSync(
    path.join(outDir, 'manifest.json'),
    `${JSON.stringify({ base, viewport: { width, height }, account: account || null, entries: manifest }, null, 2)}\n`,
    'utf-8',
  )
  const captured = manifest.filter((entry) => entry.file).length
  console.log(`\n共 ${captured} 张，写入 ${outDir}`)
  assert.ok(captured > 0, '一张图都没拍到，检查 --base 是否指向了运行中的站点')
} finally {
  await browser.close()
}
