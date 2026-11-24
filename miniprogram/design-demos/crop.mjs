// 单屏放大：node crop.mjs dir-3.html 1 4 → shots/dir-3-p1.png ...
import { createRequire } from 'node:module';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(
  fileURLToPath(new URL('../../.claude/skills/huashu-design/package.json', import.meta.url)),
);
const { chromium } = require('playwright');

const [name, ...idx] = process.argv.slice(2);
const out = path.resolve('shots');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 });
await page.goto(pathToFileURL(path.resolve(name)).href);
await page.waitForTimeout(1800);
const screens = await page.$$('[data-mp-page]');
const pick = idx.length ? idx.map(Number) : screens.map((_, i) => i);
for (const i of pick) {
  const s = screens[i];
  if (!s) continue;
  const file = path.join(out, `${name.replace(/\.html$/, '')}-p${i}.png`);
  await s.screenshot({ path: file });
  console.log(file);
}
await browser.close();
