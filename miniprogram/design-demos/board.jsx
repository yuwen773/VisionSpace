/* ---------- 展示板（hero / flow / closeup 三种摆法） ---------- */
function ShowboardLabel({ s, theme, width }) {
  return (
    <div style={{ width, marginBottom: 10 }}>
      <div style={{ fontSize: 15, fontWeight: 700, color: theme.fg, lineHeight: 1.3 }}>{s.label}</div>
      {s.note && <div style={{ fontSize: 14, color: theme.muted, lineHeight: 1.5, marginTop: 2 }}>{s.note}</div>}
    </div>
  );
}

function ShowboardPhone({ s, theme, x, y, scale, phoneW, phoneH }) {
  return (
    <div style={{ position: 'absolute', left: x, top: y, width: phoneW * scale }}>
      <ShowboardLabel s={s} theme={theme} width={phoneW * scale} />
      <div style={{ width: phoneW * scale, height: phoneH * scale }}>
        <div style={{ transform: `scale(${scale})`, transformOrigin: '0 0', width: phoneW }}>{s.node}</div>
      </div>
    </div>
  );
}

function Showboard({ layout = 'hero', theme = {}, title, titleFont, kicker, lede, screens = [], aside, phoneW = 395, phoneH = 832, footnote }) {
  const t = { bg: '#F2F1EC', fg: '#1B1B1B', muted: '#6B6B66', accent: '#C2410C', font: 'inherit', ...theme };
  const W = 1440, M = 56, LABEL = 56;
  const mainIdx = Math.max(0, screens.findIndex((s) => s.main));
  const main = screens[mainIdx], rest = screens.filter((_, i) => i !== mainIdx);
  const placed = [];
  let asidePos = null, height = 0;

  if (layout === 'hero') {
    const big = 0.95, small = 0.7, my = 250;
    placed.push({ s: main, x: M, y: my, scale: big });
    const colX = [M + phoneW * big + 56, M + phoneW * big + 56 + phoneW * small + 36];
    rest.forEach((s, i) => {
      const col = i % 2, row = Math.floor(i / 2);
      placed.push({ s, x: colX[col], y: my - 40 + (col ? 140 : 0) + row * (phoneH * small + LABEL + 36), scale: small });
    });
    asidePos = { x: colX[1] + phoneW * small + 56, y: my + 40, w: W - M - (colX[1] + phoneW * small + 56) };
  } else if (layout === 'flow') {
    const s0 = 0.72, gap = 32, x0 = 104, y0 = 230, rowStep = LABEL + phoneH * s0 + 56;
    const rows = [];
    screens.forEach((s) => { const r = s.row || 0; (rows[r] = rows[r] || []).push(s); });
    let lastRight = 0, lastY = 0;
    rows.forEach((items, r) => {
      const y = y0 + r * rowStep;
      items.forEach((s, i) => placed.push({ s, x: x0 + i * (phoneW * s0 + gap), y, scale: s0 }));
      lastRight = x0 + items.length * phoneW * s0 + (items.length - 1) * gap;
      lastY = y;
    });
    if (lastRight + 48 + 236 <= W - M) asidePos = { x: lastRight + 48, y: lastY, w: 236 };
    else asidePos = { x: W - M - 236, y: 300, w: 236 };
  } else if (layout === 'closeup') {
    const big = 0.98, small = 0.6, my = 230;
    placed.push({ s: main, x: M, y: my, scale: big });
    const colX = [M + phoneW * big + 40, M + phoneW * big + 40 + phoneW * small + 24];
    rest.forEach((s, i) => {
      const col = i % 2, row = Math.floor(i / 2);
      placed.push({ s, x: colX[col], y: my + row * (LABEL + phoneH * small + 26), scale: small });
    });
    const maxBottom = Math.max(...placed.map((q) => q.y + LABEL + phoneH * q.scale));
    asidePos = { x: M, y: Math.max(my + LABEL + phoneH * big + 40, maxBottom - 380), w: phoneW * big };
  }

  placed.forEach((p) => { height = Math.max(height, p.y + LABEL + phoneH * p.scale + M); });
  if (asidePos && aside) height = Math.max(height, asidePos.y + 470 + M);

  return (
    <div data-showboard={layout} style={{ position: 'relative', width: W, height, background: t.bg, color: t.fg, fontFamily: t.font, overflow: 'hidden' }}>
      <div style={{ position: 'absolute', left: M, top: 56, width: 760 }}>
        {kicker && <div style={{ fontSize: 14, color: t.accent, fontWeight: 700, letterSpacing: '0.04em', marginBottom: 10 }}>{kicker}</div>}
        <div style={{ fontSize: 56, fontWeight: 800, lineHeight: 1.05, fontFamily: titleFont || 'inherit' }}>{title}</div>
        {lede && <div style={{ fontSize: 16, lineHeight: 1.65, color: t.fg, opacity: 0.86, marginTop: 18, width: 640 }}>{lede}</div>}
      </div>
      {placed.map((p, i) => <ShowboardPhone key={i} {...p} theme={t} phoneW={phoneW} phoneH={phoneH} />)}
      {aside && asidePos && <div style={{ position: 'absolute', left: asidePos.x, top: asidePos.y, width: asidePos.w }}>{aside}</div>}
      {footnote && <div style={{ position: 'absolute', left: M, bottom: 28, fontSize: 12, color: t.muted }}>{footnote}</div>}
    </div>
  );
}
