// 量每台手机的滚动区：node measure.mjs final.html [dark]
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(
  fileURLToPath(new URL('../../.claude/skills/huashu-design/package.json', import.meta.url)),
);
const { chromium } = require('playwright');

const [name, mode] = process.argv.slice(2);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
await page.goto(pathToFileURL(path.resolve(name)).href);
await page.waitForTimeout(1500);
if (mode === 'dark') {
  await page.click('text=深色 · 极光');
  await page.waitForTimeout(900);
}
const rows = await page.evaluate(() => {
  const out = [];
  document.querySelectorAll('[data-mp-page]').forEach((el, i) => {
    const label = el.getAttribute('data-mp-page');
    const scrollers = [...el.querySelectorAll('*')].filter((n) => {
      const s = getComputedStyle(n);
      return /(auto|scroll)/.test(s.overflowY) && n.scrollHeight > 0;
    });
    const s = scrollers[0];
    out.push({ i, page: label, scroll: s ? s.scrollHeight : el.scrollHeight, client: s ? s.clientHeight : el.clientHeight, over: s ? s.scrollHeight - s.clientHeight : 0 });
  });
  return out;
});
rows.forEach((r) => console.log(`${r.i} ${r.page.padEnd(28)} scroll=${r.scroll} client=${r.client} over=${r.over}`));
await browser.close();
