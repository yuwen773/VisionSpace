# #32 账户认证与 HTTPS 访问安全

工单：[32](https://github.com/yuwen773/VisionSpace/issues/32)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`feat/32-account-auth-https`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `service/PasswordHasher` | 新密码用 BCrypt（每用户独立盐、成本可配）存 `$2a$<cost>$...`；旧固定盐 MD5 只做兼容验证；`needsUpgrade` 判定旧摘要或低成本摘要 |
| `service/impl/UserServiceImpl` | 登录成功后才升级摘要，条件 `UPDATE` 带「摘要仍是读到的那一个」；失败登录与升级失败都不改写数据；注销同时撤销 HTTP 会话与 Sa-Token 账户级令牌 |
| `config/WebSecurityProperties` | `vision-space.web.*`：可信来源、CSRF 开关与 Cookie/头名、令牌存活时间 |
| `manager/auth/RequestOriginPolicy` | 同源判定：比较 Origin 与请求自身的 Host（含代理透传）与协议，`Origin: null` 按跨站处理 |
| `manager/auth/CsrfProtectionFilter` | 状态修改必须同源 + 双提交令牌（会话 + Cookie + 请求头三者一致）；只对已登录请求强制令牌 |
| `manager/websocket/WsOriginHandshakeInterceptor` | WebSocket 握手复用同一套同源判定，替换原来「只有开发期白名单」的做法 |
| `config/CorsConfig` | 只放行显式可信来源；列表为空即只信同源，不再回退到开发期默认值 |
| `application.yml` / `application-prod.yml` | `forward-headers-strategy: framework`；`sa-token.cookie.secure/http-only/same-site`；`spring.session.cookie.same-site: strict` |
| `nginx.conf` / `visionspace.conf` / `ssl-visionspace.conf` | `/api` 一个入口承载 REST/SSE/上传/WSS：`proxy_http_version 1.1` + `Upgrade`/`Connection`、透传 `Host`/`X-Forwarded-*`、关闭缓冲 |
| `frontend/src/apiOrigin.ts` | REST、SSE、上传、WebSocket 的唯一来源；`webSocketUrl` 由页面协议推导 `ws`/`wss`；读取并附加 CSRF 令牌 |
| `frontend/src/request.ts`、`composables/useAgentStream.ts`、`api-custom/agent.ts`、`utils/pictureEditWebSocket.ts` | 全部改从 `apiOrigin` 取地址，删除固定公网 `ws://8.163.10.130` |
| `scripts/browser-acceptance.ps1` + `frontend/e2e/*` | 真实 Chromium 验收：生产构建 + 自签 TLS 同源入口 + 隔离后端 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 07.1 新密码独立盐自适应摘要；旧摘要只兼容验证且成功后升级；失败登录不改写 | `registrationStoresIndependentSaltedDigest`（同一密码两次注册摘要不同、`$2a$` 前缀）；`legacyDigestStillLogsInAndIsUpgradedOnlyAfterSuccess`（失败登录后摘要逐字节不变，成功登录后升级）；`PasswordHasherTest` 9 条 | 已覆盖 |
| 07.2 并发升级不破坏账户、会员与管理员角色；数据兼容与回滚文档 | `concurrentSuccessfulLoginsUpgradeExactlyOnceAndKeepAccountUsable`（6 线程并发登录全部成功，升级后 `userRole='vip'`、`vipExpireTime` 保留、仍可登录）；条件 `UPDATE` 使后到者影响 0 行；回滚说明见本文「数据兼容与安全回滚」 | 已覆盖 |
| 07.3 注销同步撤销会话与空间鉴权；原 Cookie、令牌与后续受保护请求不再授权 | `logoutRevokesSessionAndSpaceAuthenticationForOldCookies`（旧 Cookie 快照：`/user/get/login`、`/user/picture/recent` 返回 40100，空间权限入口 `/picture/delete` 返回 40100）；注销走 Sa-Token `ACCOUNT` 范围并 `invalidate()` 会话 | 已覆盖 |
| 07.4 登录/注销/错误响应不泄漏密码与摘要；成功、失败、升级、撤销的 HTTP 回归 | `loginAndErrorResponsesNeverLeakPasswordOrDigest`（登录成功/失败/未知账户的响应体都不含原文与摘要，错误文案不含内部字段名）；以上四类路径均有独立 HTTP 用例 | 已覆盖 |
| 30.1 REST、SSE、上传、WebSocket 走同一同源入口；无固定公网 ws 地址、无混合内容、配置变量不自相矛盾 | `apiOrigin.ts` 是唯一来源；`webSocketUrl` 由 `window.location` 推导 `wss`；`apiOrigin.spec.ts` 7 条；浏览器验收「页面发起的全部 API 请求都与页面同源」「wss 由 https 页面推导」 | 已覆盖 |
| 30.2 Cookie、错误与重连契约一致；生产切换入口不改业务代码 | Cookie 统一 `SameSite=Strict` + Sa-Token `Secure`；入口差异只体现在 `VITE_API_BASE_URL` 与反向代理配置；浏览器验收断言 https 入口下会话 Cookie 均为 `Secure` 且下发 `VS_CSRF` | 已覆盖 |
| 30.3 开发跨域只放行可信 Origin；状态修改有可验证的 CSRF 防护，不接受仅靠 CORS/Cookie 属性 | `CsrfProtectionFilter`（会话绑定 + 双提交 + 来源判定）；`CsrfProtectionFilter` 的 HTTP 用例：`stateChangingRequestFromUntrustedOriginIsRejectedWithoutWrites`（40300 且零写入）、`missingOrMismatchedCsrfTokenIsRejectedWithoutStateChange`、`csrfCookieIsVisibleToPageScriptsOutsideTheApiContextPath`；`RequestOriginPolicyTest` 9 条 | 已覆盖 |
| 30.4 真实浏览器链路验证 API、SSE、WSS 与注销后拒绝；恶意来源与无效防护信息零状态修改 | `scripts/browser-acceptance.ps1` + `frontend/e2e/browser-entry.mjs` 9 步真实 Chromium：Secure Cookie、真实登录页走通生成的客户端、同源 API、注销后 40100、wss 握手失败、跨站 Origin 403、缺令牌 403、SSE 同源可用且缺令牌 403、http 入口只服务当前源 | 已覆盖（详见下节） |

## 数据兼容与安全回滚（07.2）

摘要格式只有两种，按存储值可直接区分：

| 存储值 | 判定 | 行为 |
| --- | --- | --- |
| 32 位小写十六进制 | `PasswordHasher.isLegacy` = true | 旧固定盐 MD5，只用于验证；成功登录后升级 |
| `$2a$<cost>$...` | BCrypt | 正常验证；`cost` 低于当前配置时在成功登录后升级 |

- **只写一列。** 升级语句是 `UPDATE user SET userPassword = ? WHERE id = ? AND userPassword = ?`，不触碰 `userRole`、`vipExpireTime`、`isDelete` 等任何其它列。
- **不存在「半升级」状态。** 单条条件更新要么生效要么影响 0 行；并发登录中只有第一个写入生效，其余按原摘要继续登录。
- **回滚方式。** 把 `vision-space.security.password-bcrypt-cost` 调回原值不会让 BCrypt 摘要失效（验证只读存储值），但若需要退回旧实现：旧实现只能验证 MD5，**已升级的账户会全部无法登录**。因此回滚必须配合数据恢复（从备份还原 `user.userPassword`），不能只回滚代码。升级前请保留 `userPassword` 列备份。
- **不需要数据迁移脚本。** 旧值原样保留，登录时按需升级，上线不产生批量写。

## 本次实际执行的验证

2026-10-23，Windows + JDK 21（corretto-21.0.7）/ Maven / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，非容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

`target/acceptance/summary.json`：runner-tests、secret-scan、isolation、backend、frontend-install、frontend-typecheck、frontend-tests、frontend-build、cleanup 全部 PASS。

浏览器真实链路：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/browser-acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>' -SkipInstall
```

`target/browser-acceptance/summary.json`：isolation、backend、browser、cleanup 全部 PASS，9 个浏览器步骤全绿（`passed: 9, failed: 0`）。

这一轮浏览器验收抓到四个只在真实入口下才出现的问题，均已修复并补了回归用例：

1. **同源写请求被框架判成跨站。** nginx 终止 TLS 后容器看到的是 `http`，Spring 比对 `Origin: https://...` 失败，直接以**空响应体 403** 拒绝，业务代码根本没执行。修复：`forward-headers-strategy: framework`（`native` 只还原协议不还原主机，仍会失败）。回归：`sameOriginRequestBehindTlsTerminatingProxyIsNotTreatedAsCrossSite`。
2. **CSRF Cookie 挂在 `/api` 下。** 页面（`/user/login`）用 `document.cookie` 读不到，所有写请求都会缺令牌。修复：令牌 Cookie 走站点根路径 `/`。回归：`csrfCookieIsVisibleToPageScriptsOutsideTheApiContextPath`。
3. **Sa-Token 令牌 Cookie 没有 `Secure`。** 默认配置为 `null`，HTTPS 入口下仍会明文回传。修复：`sa-token.cookie.secure/http-only/same-site`，生产强制开启，明文验收配置显式关闭。
4. **生成的 API 客户端丢了 `/api` 前缀（main 上就存在）。** 生成器产出的是 `/user/login`（不含前缀），而手写客户端写的是 `/api/mcp-server`。`request.ts` 的 `baseURL` 原本是空串，生成客户端的请求会落到站点根路径，被 SPA 回落成 `index.html`——看起来同源、其实没到后端。修复：`baseURL = API_ROOT`，并在拦截器里去掉请求路径已写过的 `/api` 前缀。回归：`frontend/src/__tests__/request.spec.ts`（base 含 `/api`、重复前缀被去重），以及浏览器验收「真实登录页走通」这条。

第 4 条值得单独说明**为什么之前的断言没抓到**：最初的记录器只收 `pathname.startsWith('/api/')` 的请求，恰恰把「漏了前缀」的缺陷请求过滤掉了；再加上前序步骤已经在共享 context 里登录过，「登录成功」这个信号无条件成立。最终改成独立 BrowserContext + 只记录同源的 `fetch`/`xhr`（axios 在浏览器里走 XHR，不是 fetch），红色运行才打印出 `GET https://127.0.0.1:<port>/user/get/login`——没有 `/api`，缺陷当场可见。

另外收紧了 WebSocket：原来只在 `trusted-origins` 非空时才用白名单、否则固定 `localhost:5173`，生产同源反而被拒。改为与 REST 共用 `RequestOriginPolicy`。

**`SameSite` 的实际归属（一次复核更正）。** 代码审查曾判定 `spring.session.cookie.same-site` 在 Boot 3.5.8 下不生效、应改用 `server.servlet.session.cookie.*`。实测推翻了这条：把该键移走后再跑，`SESSION=...` 仍然输出 `SameSite=Strict`——Spring Session 的 `DefaultCookieSerializer` 确实绑定这个键。因此配置保持原样，没有做无谓的搬家。浏览器验收现在直接读 `Set-Cookie` 头断言（`^(SESSION|satoken)=` 必须出现 ≥ 2 条且都带 `SameSite=Strict`），而不是读 Playwright 解析后的 `cookie.sameSite`——属性缺失时浏览器会回报默认值，那种断言会空泛通过。真实头样例：

```
SESSION=...; Max-Age=2592000; Path=/api; Secure; HttpOnly; SameSite=Strict
VS_CSRF=...; Max-Age=7200; Path=/; Secure
satoken=...; Max-Age=2592000; Path=/; Secure; HttpOnly; SameSite=strict
```

## 人工关卡（未完成，不计入通过）

1. **真实生产域名与证书。** 浏览器验收用 `127.0.0.1` 自签证书，验证的是「同源入口 + TLS + wss 推导」这套形状，不等于 `visionspace.space` 的证书链、HSTS 与 CDN 配置已验证。
2. **SSE 的模型输出未验证。** 浏览器验收已覆盖「同源 POST 建立 `text/event-stream` 连接」与「缺令牌被拒绝」两个入口契约，但隔离后端把外部模型指向不可达地址，所以没有真实 token 流。模型侧的输出与重连语义不在本工单范围。
3. **多实例与滚动发布。** 注销撤销、并发升级与 CSRF 都只在单进程 + 单 Redis 内验证；滚动发布期间新旧实例并存的窗口没有实测。
4. **生产回滚演练。** 「回滚需配合数据恢复」只在文档层说明，没有在含存量数据的库上演练过。
5. **浏览器验收未接入 CI。** `scripts/browser-acceptance.ps1` 本轮只在 Windows + 原生 MySQL/Redis 上跑通。它的 Linux 分支（Docker + `Start-Process -WindowStyle`）没有实测过，所以没有加进 `.github/workflows/acceptance.yml`——宁可让它保持手动关卡，也不提交一条没验证过的流水线配置。接入前需要先在 Linux 上跑通一次，并处理 `playwright-core` 的浏览器安装与 BLOCKED_ENV 退出码。

## 已知取舍

- **令牌随会话轮换。** CSRF 令牌绑定服务端会话，会话重建（重新登录、会话过期）后旧令牌立即失效，前端下次状态修改会拿到 403 后再取新令牌，而不是静默重试。这是刻意的：静默重试会掩盖真实的来源判定失败。
- **匿名状态修改不强制令牌。** 例如公开的 POST 查询与注册/登录本身，没有可被借用的授权。已登录请求一律强制。
- **`forward-headers-strategy: framework` 的代价。** 框架会信任并消费 `X-Forwarded-*`，所以后端端口**必须**只对反向代理可达（当前 nginx 与后端同机、`server.address` 未对外）。若将来后端直接暴露，需改为白名单代理地址。
- **旧摘要无法回滚到旧代码。** 见「数据兼容与安全回滚」：升级是单向的，回滚必须还原数据。
