// 截图：node shot.mjs dir-1.html → shots/dir-1.png
import { createRequire } from 'node:module';
import path from 'node:path';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const require = createRequire('D:/develop-worktree-t3/VisionSpace/t3-e85ff7a3-0b2d60ae6df4cde1/.claude/skills/huashu-design/package.json');
const { chromium } = require('playwright');

const name = process.argv[2];
const out = path.resolve('shots');
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
page.on('pageerror', (e) => {
  const stack = e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e);
  errors.push(stack);
});
page.on('console', (m) => {
  if (m.type() === 'error') errors.push('console: ' + m.text().slice(0, 400));
});
await page.goto(pathToFileURL(path.resolve(name)).href);
await page.waitForTimeout(2500);
await page.screenshot({ path: path.join(out, name.replace(/\.html$/, '.png')), fullPage: true });
console.log('errors:', JSON.stringify(errors, null, 1));
await browser.close();
