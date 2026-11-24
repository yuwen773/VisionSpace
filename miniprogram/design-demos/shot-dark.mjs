// 深色主题截图：node shot-dark.mjs final.html → shots/final-dark.png + final-dark-pN.png
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(
  fileURLToPath(new URL('../../.claude/skills/huashu-design/package.json', import.meta.url)),
);
const { chromium } = require('playwright');

const [name] = process.argv.slice(2);
const base = name.replace(/\.html$/, '');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 });
const errs = [];
page.on('pageerror', (e) => errs.push(String(e)));
await page.goto(pathToFileURL(path.resolve(name)).href);
await page.waitForTimeout(1500);
await page.click('text=深色 · 极光');
await page.waitForTimeout(1200);
await page.screenshot({ path: `shots/${base}-dark.png`, fullPage: true });
const screens = await page.$$('[data-mp-page]');
for (let i = 0; i < screens.length; i++) {
  await screens[i].screenshot({ path: `shots/${base}-dark-p${i}.png` });
}
console.log('errors:', errs);
await browser.close();
