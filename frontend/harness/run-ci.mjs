// Run the existing DOM/network harness in an owned, headless browser.
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync, execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { chromium } from 'playwright-core'
import { fileURLToPath } from 'node:url'

process.chdir(fileURLToPath(new URL('..', import.meta.url)))

const results = {}
const out = path.resolve('../target/diagram-browser')
fs.mkdirSync(out, { recursive: true })
const candidate = spawnSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).stdout.trim()

for (const configured of [false, true]) {
  const env = { ...process.env, VITE_PLANTUML_SERVER: configured ? 'http://127.0.0.1:18089/plantuml' : '' }
  const build = spawnSync(process.execPath, ['node_modules/vite/bin/vite.js', 'build', '--config', 'harness-src/vite.config.ts', '--configLoader', 'runner'], { env, encoding: 'utf8' })
  fs.writeFileSync(path.join(out, `build-${configured}.log`), (build.stdout || '') + (build.stderr || ''))
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
      const port = fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]
      // Keep Playwright's transport alive while it attaches newly-created CDP targets.
      const run = await promisify(execFile)(process.execPath, ['harness/browser-check.mjs'], { env: { ...env, CDP_PORT: port, HARNESS_THEME: theme }, encoding: 'utf8', timeout: 90000, maxBuffer: 10 * 1024 * 1024 })
        .then((value) => ({ ...value, status: 0 }), (error) => ({ stdout: error.stdout, stderr: error.stderr, status: 1 }))
      fs.writeFileSync(path.join(out, `${name}.log`), (run.stdout || '') + (run.stderr || ''))
      results[name] = run.status === 0 ? 'PASS' : 'FAIL'
      console.log(`${results[name]}: diagram ${name}`)
    } catch {
      results[name] = 'BLOCKED_ENV'
    } finally {
      await browser?.close()
      fs.rmSync(profile, { recursive: true, force: true })
    }
  }
}
fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify({ head: candidate, runId: process.env.GITHUB_RUN_ID || 'local', results, productionAcceptance: 'NOT_RUN' }, null, 2))
process.exit(Object.values(results).every((result) => result === 'PASS') ? 0 : 1)
