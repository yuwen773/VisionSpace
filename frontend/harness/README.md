# 图表渲染安全验收

真实浏览器里的回归入口。用**生产同一条渲染链**处理恶意与正常输入，检查最终 DOM、
脚本是否真的执行、以及浏览器实际发出的网络请求。

不需要公开 CDN、公共渲染服务或真实对话内容：页面由本地静态服务器托管，
输入是固定的测试样例。

## 为什么不能用单元测试代替

Mermaid 依赖浏览器布局 API（`getBBox`），jsdom 里渲染不出来；CSP、事件属性是否会被
执行、请求是否真的发出，也只有在真实浏览器里才有意义。单元测试覆盖清理逻辑和组件结构，
这里覆盖"浏览器实际做了什么"。

## 运行

```bash
cd frontend

# 1) 构建隔离入口（产物在 dist-harness/，已 gitignore）
npx vite build --config harness-src/vite.config.ts --configLoader runner

# 2) 起一个带 CDP 的 Chrome（端口任选一个空闲口）
chrome --headless=new --remote-debugging-port=9222 --user-data-dir=/tmp/cdp-harness about:blank

# 3) 跑检查
CDP_PORT=9222 node harness/browser-check.mjs
```

退出码 0 表示通过；失败时逐条打印原因（未授权外发、payload 被执行、危险链接残留等）。

## 验证两种部署形态

PlantUML 的行为取决于是否配置了受控服务，两种都要跑：

```bash
# 未配置：必须显示源码，且不产生任何图片请求
npx vite build --config harness-src/vite.config.ts --configLoader runner
CDP_PORT=9222 node harness/browser-check.mjs

# 已配置：预览地址只能指向该服务，内容不能改写目标
VITE_PLANTUML_SERVER=http://127.0.0.1:9/plantuml \
  npx vite build --config harness-src/vite.config.ts --configLoader runner
CDP_PORT=9222 node harness/browser-check.mjs
```

配置里的端口故意选一个连不上的地址：只要没有真实请求发出，就说明预览图地址
只是被生成而没有被内容改写去别的目标。

## 覆盖的样例

| 样例 | 期望 |
| --- | --- |
| 正常 Mermaid 流程图 / 时序图 | 渲染出 SVG |
| 正常 draw.io 图 | 渲染出 SVG（本地 mxgraph，非公共 CDN） |
| 正常 PlantUML | 未配置显示源码；配置后指向受控服务 |
| Mermaid 内的 HTML / `click` 回调 / `javascript:` 链接 | 不执行，DOM 中无事件属性 |
| Markdown 内联 SVG、`onerror`、`javascript:`/`data:` 链接 | 不执行，无可用的危险 href |
| draw.io 单元格内的 HTML payload | 不执行 |
| PlantUML 内容里的外部地址 | 不改变请求目标 |
| 外部图片引用 | 不生成 `<img>`，零外发请求 |
