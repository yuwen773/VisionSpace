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

## 审查缺陷的回归用例

| 用例 id                   | 证明什么                                                                  |
| ------------------------- | ------------------------------------------------------------------------- |
| `drawio-image-request`    | draw.io 的 `shape=image` 在渲染期不发请求（惰性文档 + 销毁）              |
| `mermaid-escaped-css`     | `themeCSS` 里的 `u\72l(...)` 不落到最终 DOM                               |
| `mermaid-style-url`       | Mermaid `style` 语句的 `url(...)` 在渲染前就被拒绝                        |
| `mermaid-image-shape`     | `A@{ img: "https://…" }` 在渲染前被拒绝（渲染库自己取图，剥离指令拦不住） |
| `mermaid-backslash-img`   | `img` 形状里的 `/\host`（反斜杠伪装的 `//host`）同样被拒绝                |
| `mermaid-loose-directive` | 内容里的 `securityLevel: loose` 被剥离，标签只能是文本                    |
| 生命周期探针              | 渲染 → 更新 → 更新 → 卸载，每一步的 document 监听器不增                   |

判据是"**不产生违规请求尝试**"，不是"尝试了但被拦下"——后者说明地址已经离开了页面。

## 正向对照：证明策略真的在执行

"没有观测到违规"有两种可能——策略拦住了，或者压根没在观测。harness 因此做这些事：

1. 安装 `securitypolicyviolation` 监听器，并把 `__cspListenerInstalled` 暴露给验收脚本；
2. 主动制造三次应被拦截的行为（外域脚本、内联脚本、外域 fetch），确认真的收到违规，
   且对照请求出现在网络记录里、被 CSP 拦下（而不是真的送达对方）；
3. 监听器探针自检：先挂再解一个已知监听器，确认增减都被记录——
   否则"监听器没有增长"既可能是真的没泄漏，也可能是探针没生效；
4. 中间 DOM 观测器自检：注入已知的 foreignObject 与外部 `url()`，确认会被记录；
5. 监听器基线在**任何用例渲染之前**取：否则用例泄漏的监听器会被算进基线，
   "没有增长"永远成立。

验收对以上每一项都要求"已安装/已自检"，否则判失败。这样每半个证据都是实打实的，
而不是一个恒为空的数组。

## 验证两种部署形态

PlantUML 的行为取决于是否配置了受控服务，两种都要跑：

```bash
# 未配置：必须显示源码，且不产生任何图片请求
npx vite build --config harness-src/vite.config.ts --configLoader runner
CDP_PORT=9222 node harness/browser-check.mjs

# 已配置：预览地址只能指向该服务，内容不能改写目标
VITE_PLANTUML_SERVER=http://127.0.0.1:18089/plantuml \
  npx vite build --config harness-src/vite.config.ts --configLoader runner
CDP_PORT=9222 node harness/browser-check.mjs
```

配置里的端口故意选一个没有服务在听的本地地址。这一形态下浏览器**会**尝试加载
预览图（`<img>` 一旦进入 DOM 就会发请求），所以验收的判据不是"零请求"，而是
**所有请求都指向该受控目标**——`unexpectedRequests` 必须为空，`orchestratedOtherHosts`
必须为空。内容里的 `https://evil.example/steal` 只能落在编码后的路径段里，改变不了目标。
端口不要用 9 这类浏览器屏蔽端口，否则请求根本不会发出，"目标正确"就无从验证。

## 覆盖的样例

| 样例                                                     | 期望                                                |
| -------------------------------------------------------- | --------------------------------------------------- |
| 正常 Mermaid 流程图 / 时序图                             | 渲染出 SVG                                          |
| 正常 draw.io 图                                          | 渲染出 SVG 且含真实形状（本地 mxgraph，非公共 CDN） |
| 正常 PlantUML                                            | 未配置显示源码；配置后指向受控服务                  |
| Mermaid 内的 HTML / `click` 回调 / `javascript:` 链接    | 不执行，DOM 中无事件属性                            |
| Markdown 内联 SVG、`onerror`、`javascript:`/`data:` 链接 | 不执行，无可用的危险 href                           |
| draw.io 单元格内的 HTML payload                          | 不执行                                              |
| PlantUML 内容里的外部地址                                | 不改变请求目标                                      |
| 外部图片引用                                             | 不生成 `<img>`，零外发请求                          |
| draw.io `shape=image` 指向外部主机                       | 渲染期不发请求，产物里无外部引用                    |
| Mermaid `themeCSS` 用 CSS 转义写 `url()`                 | 最终 DOM 里无该地址，图表照常渲染                   |
| Mermaid 内容试图改 `securityLevel` 为 loose              | 指令被剥离；图表渲染出来，但 HTML 标签只是文本      |
| Mermaid `A@{ img: "https://…" }` 形状                    | 渲染前就拒绝（请求根本不会发出），并说明原因        |
| 渲染 → 更新 → 更新 → 卸载                                | 每步 document 监听器不增加，卸载后回到基线          |

## 渲染期中间 DOM

只看最终 DOM 是不够的：清理器在最后一步把危险内容摘掉，于是"渲染期确实生成过
`foreignObject` 或外部 `url()`"这件事会被抹平，而请求在那时已经发出去了。

harness 因此用 `MutationObserver` 观测渲染期间的 DOM 变化，单独记录：

- `foreignObjects`：HTML 回到 SVG 的通道有没有被打开过；
- `externalRefs` / `externalCss`：中间态里出现的跨源引用（CSS 按语义解码后再匹配，
  `u\72l(...)` 这类转义写法也算）。

观测器自己也要被验证，否则"没有记录"和"没在工作"长得一样：启动时会插入一个
已知的中间态节点（带 `foreignObject` 与外部 `url()` 的样式），确认它被记录后清空计数。
验收脚本要求 `observerSelfTest === true`。

## 双主题

主题由查询参数选择，harness 在渲染之前把 class 挂到 `<html>` 上：

```bash
HARNESS_THEME=theme-aurora CDP_PORT=9222 node harness/browser-check.mjs
HARNESS_THEME=theme-pop    CDP_PORT=9222 node harness/browser-check.mjs
```

带主题运行时，脚本会读取主题令牌 `--color-bg-tertiary` 的期望色值，与图表节点的
实际计算色值比较。期望值来自令牌本身而不是写死的色号——Mermaid 必须用 `theme: 'base'`
才会应用 `themeVariables`，用 `default` 等内置主题时图表会固定成浅色，这条断言就是拦它的。
