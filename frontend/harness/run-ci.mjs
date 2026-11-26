// Run the existing DOM/network harness in an owned, headless browser.
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync, execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { chromium } from 'playwright-core'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import http from 'node:http'

process.chdir(fileURLToPath(new URL('..', import.meta.url)))

const results = {}
const out = path.resolve('../target/diagram-browser')
fs.mkdirSync(out, { recursive: true })
// 现场日志放 diagnostics/ 才会随证据发布（publish-evidence 只发布路径里带
// diagnostics 目录的 .log）。放在上一级时 CI 红只剩一个 FAIL，无法定位是哪条断言。
const diagnostics = path.join(out, 'diagnostics')
fs.mkdirSync(diagnostics, { recursive: true })
const candidate = spawnSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).stdout.trim()
const plantuml = http.createServer((_request, response) => {
  response.writeHead(200, { 'Content-Type': 'image/svg+xml' })
  response.end('<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="blue"/></svg>')
})
await new Promise((resolve, reject) => { plantuml.once('error', reject); plantuml.listen(0, '127.0.0.1', resolve) })
plantuml.unref()

for (const configured of [false, true]) {
  const env = { ...process.env, VITE_PLANTUML_SERVER: configured ? `http://127.0.0.1:${plantuml.address().port}/plantuml` : '' }
  const build = spawnSync(process.execPath, ['node_modules/vite/bin/vite.js', 'build', '--config', 'harness-src/vite.config.ts', '--configLoader', 'runner'], { env, encoding: 'utf8' })
  fs.writeFileSync(path.join(diagnostics, `build-${configured}.log`), (build.stdout || '') + (build.stderr || ''))
  if (build.status !== 0) {
    results[`build-${configured}`] = 'FAIL'
    continue
  }
  for (const theme of ['theme-aurora', 'theme-pop']) {
    const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'vs47-chromium-'))
    let browser
    const name = `${configured ? 'configured' : 'unconfigured'}-${theme}`
    try {
      browser = await chromium.launchPersistentContext(profile, { headless: true, args: ['--remote-debugging-port=0'] })
      // 端口文件由 Chromium 落盘，launch 返回不等于它已经写好；机器繁忙时立刻读会
      // ENOENT，把一次正常启动记成 BLOCKED_ENV（2026-11-26 CI 实测）。
      let port
      for (let attempt = 0; attempt < 60 && port === undefined; attempt++) {
        try {
          port = fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]
        } catch {
          await new Promise((resolve) => setTimeout(resolve, 250))
        }
      }
      if (!port) throw new Error(`DevToolsActivePort missing in ${profile} after 15s`)
      // Keep Playwright's transport alive while it attaches newly-created CDP targets.
      const run = await promisify(execFile)(process.execPath, ['harness/browser-check.mjs'], { env: { ...env, CDP_PORT: port, HARNESS_THEME: theme }, encoding: 'utf8', timeout: 90000, maxBuffer: 10 * 1024 * 1024 })
        .then((value) => ({ ...value, status: 0 }), (error) => ({
          stdout: error.stdout,
          stderr: error.stderr,
          status: 1,
          // 进程被杀/超时同样是现场：不记下来，日志结尾 PASS 却判 FAIL 时无从解释。
          note: `[harness] exit status=1 killed=${error.killed} signal=${error.signal} code=${error.code}\n`,
        }))
      fs.writeFileSync(path.join(diagnostics, `${name}.log`), (run.note || '') + (run.stdout || '') + (run.stderr || ''))
      results[name] = run.status === 0 ? 'PASS' : 'FAIL'
      console.log(`${results[name]}: diagram ${name}`)
    } catch (error) {
      results[name] = 'BLOCKED_ENV'
      // 启动失败同样是现场：不留下这一行，CI 上只剩一个 BLOCKED_ENV。
      fs.writeFileSync(path.join(diagnostics, `${name}.log`), `${error?.stack ?? error}\n`)
    } finally {
      await browser?.close()
      fs.rmSync(profile, { recursive: true, force: true })
    }
  }
}
fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify({ head: candidate, runId: process.env.VS_SRF_RUN_ID || randomUUID(), results, productionAcceptance: 'NOT_RUN' }, null, 2))
await new Promise((resolve) => plantuml.close(resolve))
process.exit(Object.values(results).every((result) => result === 'PASS') ? 0 : 1)
