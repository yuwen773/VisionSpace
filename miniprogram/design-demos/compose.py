"""把 src/dir-N.src.html 组装成可直接打开的单文件 dir-N.html。

- /*__FRAME__*/  -> 微信外壳（取自 _frame_snippet.txt，去掉重复的旧展示板）+ board.jsx
- /*__IMG__*/{}  -> assets/img-base64.json 的内嵌 payload
"""
import json
import pathlib
import sys

root = pathlib.Path(__file__).parent
snip = (root / "_frame_snippet.txt").read_text(encoding="utf-8")
frame = snip[:snip.index("/* ---------- 展示板")]
board = (root / "board.jsx").read_text(encoding="utf-8")
img = json.loads((root / "assets" / "img-base64.json").read_text(encoding="utf-8"))
payload = json.dumps(img, ensure_ascii=False, separators=(",", ":"))

targets = sys.argv[1:] or ["dir-2", "dir-3"]
for name in targets:
    src = root / "src" / f"{name}.src.html"
    text = src.read_text(encoding="utf-8")
    text = text.replace("/*__FRAME__*/", frame + "\n" + board)
    text = text.replace("/*__IMG__*/{}", payload)
    out = root / f"{name}.html"
    out.write_text(text, encoding="utf-8")
    print(f"{out.name}  {out.stat().st_size/1024:.0f} KB")
