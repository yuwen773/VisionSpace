# #33 MCP 与 URL 下载的外部能力安全

工单：[33](https://github.com/yuwen773/VisionSpace/issues/33)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`feat/33-mcp-url-security`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `manager/outbound/OutboundPolicy` | 用户输入出站的唯一策略入口：协议白名单、禁止 userinfo、主机字面量判定、解析结果逐条判定；`trustedHosts` 为运维声明的内部依赖（完整主机名精确匹配），与用户任意目标分开 |
| `manager/outbound/OutboundAddressRules` | 纯判定，不发起网络：IPv4/IPv6 回环、私网、链路本地、多播、保留段、NAT64/6to4 内嵌地址 |
| `manager/outbound/OutboundDownloader` | 有界下载：不依赖 HEAD，内容类型/实际字节/总时限在 GET 上判定；客户端关闭自动重定向，逐跳重新解析并重新判定；`DnsResolver` 让「判定结果」与「实际连接地址」是同一份数据 |
| `manager/upload/UrlPictureUpload` | 删除 HEAD 预检，改由 `OutboundDownloader` 完成全部检查；`PictureUploadTemplate` 让业务拒绝原样上抛，临时文件在 `finally` 清理 |
| `agent/mcp/McpServerAccess` | 归属 + 启用状态的唯一回读入口：`findExecutable`（可执行）、`findOwned`（可见）、`findEnabled`（批量）、`requireExecutable`（统一拒绝） |
| `agent/mcp/McpCapabilityGuard` | 生产判定（active profiles 含 `prod`/`production`）与 STDIO 封禁；拒绝发生在构造进程参数之前 |
| `agent/mcp/McpToolCallback` | 回调只保存 `(code, userId)`，不保存实体快照；执行前回读归属与启用状态 |
| `agent/mcp/McpToolCallbackProvider` / `TextMcpModelInterceptor` | 显式 code、默认启用、会话覆盖三条来源都按当前用户回读；被禁能力不进入工具列表 |
| `agent/manager/MCPManager` | 创建客户端前复核 STDIO 与出站目标；每个 HTTP 请求（含重定向、缓存客户端重连）经 `McpAsyncHttpClientRequestCustomizer` 再判定一次；工具缓存键带 `userId` |
| `agent/service/impl/McpServerServiceImpl` | 保存前拒绝被禁能力与受限目标；预览只在可执行时做工具发现；`getTools`/`callTool` 走 `requireExecutable` |
| `controller/McpServerController` + `GET /mcp-server/capabilities` | UI 与服务端共用同一份能力判定，界面不宣称被禁能力可用；前端走生成的 `api/mcpController.ts#capabilitiesUsingGet` |
| 前端 `McpServerForm` / `McpServicePanel` / `api-custom/mcp.ts` | STDIO 被禁用时不提供选项、默认落到 SSE、JSON 模板换成远程示例；能力接口失败时按禁用处理（fail-closed） |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 08.1 显式 code、默认选择、缓存命中、预览、工具发现均验证归属；执行前再查启用与授权 | `McpServerAccess` 全部查询带 `userId + status=1`；`McpToolCallback#call` 先 `requireExecutable` 再连接；`McpToolCallbackAuthorizationTest` 5 条；`HttpMcpAndUrlSecurityTest#anotherUserCannotPreviewOrDiscoverToolsAndNothingIsContacted`、`#ownerDiscoversToolsFromEnabledServer` | 已覆盖 |
| 08.2 停用/撤权后已缓存回调不能执行，缓存不跨用户串用 | `McpToolCallback` 不持实体快照；`MCPManager#cacheKey` 带 `userId`；`McpToolCallbackAuthorizationTest#disabledServerIsRejectedWithoutAnyExternalCall`、`#deletedServerIsRejectedWithoutAnyExternalCall`；`MCPManagerOutboundTest#toolsCacheIsKeyedByOwnerSoUsersDoNotShareDefinitions`；`HttpMcpAndUrlSecurityTest#disabledServerStopsDiscoveryAndCacheDoesNotResurrectIt`、`#toolsCacheIsIsolatedPerOwner` | 已覆盖 |
| 08.3 生产自定义 STDIO 全链路拒绝，记录器证明零进程启动 | 配置保存（`validateDeployConfig`）、客户端创建（`getMcpSyncClient`）、工具发现（`getTools`）、执行（`callTool`）四处封禁；`isStdio` 把「声明 SSE 但配置带 command」也按 STDIO 处理；`MCPManagerStdioBanTest#rejectedStdioLeavesNoProcessAndNoMarkerFile` 用标记文件 + 子进程集合证明零进程；`MCPManagerOutboundTest#productionRejectsStdioBeforeAnyProcessOrConnection`、`#stdioDeclaredAsRemoteIsStillRejectedInProduction` | 已覆盖（单进程） |
| 08.4 普通远程 MCP 仍可用；UI 不宣称被禁能力可用；拒绝后零未授权外部副作用 | `MCPManagerOutboundTest#authorizedRemoteToolsAreDiscoveredAndExecuted`（受控替身发现并执行 echo 工具）；`GET /mcp-server/capabilities` + `McpServerForm` 隐藏 STDIO 选项（`mcpServerForm.spec.ts` 3 条）；拒绝路径断言替身 `requestCount()`/`toolCalls()` 不变 | 已覆盖 |
| 09.1 解析与实际连接目标、IPv4/IPv6、每次重定向、DNS 变化均校验；禁止受限协议与地址 | `OutboundPolicy`（协议/主机/解析三段）+ `OutboundAddressRules`（v4/v6 段表）；`OutboundDownloader#buildClient` 用 `PinningResolver` 让连接使用已判定地址；重定向逐跳 `requireAllowed`；MCP 侧每请求 `assertConnectTarget`；`OutboundAddressRulesTest` 5 条、`OutboundPolicyTest` 8 条、`OutboundDownloaderTest#redirectToRestrictedTargetIsRejectedPerHop`、`#redirectToPrivateAddressIsRejected` | 已覆盖 |
| 09.2 HEAD 不支持或异常不跳过检查；字节、时间、重定向次数有上限 | HEAD 预检已删除，全部检查在 GET 上完成；`copyBounded` 按实际读到字节截断（不信 Content-Length）、`deadline` 覆盖重定向与正文、`maxRedirects` 取配置与硬上限的较小值；`OutboundDownloaderTest#headSupportIsIrrelevantBecauseChecksRunOnTheActualGet`、`#declaredLengthIsNotTrustedWhenBodyExceedsLimit`、`#redirectLoopIsBounded` | 已覆盖 |
| 09.3 正常图片上传保留体验；超量/不安全目标明确失败并清理临时副作用 | `UrlPictureUploadTest#validUrlIsAcceptedAndDerivesFilename`（受控替身正常上传）、`#downloadWritesActualBytesAndCleansUpOnFailure`、`#unsafeTargetFailsWithoutCreatingContent`；`PictureUploadTemplate` 的 `finally` 删除临时文件，业务拒绝不再被包成「上传失败」；`HttpMcpAndUrlSecurityTest#urlUploadFromControlledTargetStillWorks`、`#urlUploadRejectsRestrictedTargetWithoutSideEffects` | 已覆盖 |
| 09.4 受控替身验证，不探测真实内网/元数据/第三方；对外提供可复用的出站策略 | 所有用例的目标是 `ControlledMcpServer`（仅监听回环）或直接构造的受限地址（元数据地址只在字符串层面判定，不发起连接）；`OutboundPolicy` 为 URL 下载与 MCP 共用 | 已覆盖 |
| 10.1 用户提供的 URL/host/重定向/连接地址复用已验证策略，工具发现与缓存重连不能绕过 | 保存（`extractHost` → `requireAllowed`）、客户端创建、每请求 customizer 三处复用同一实例；`MCPManagerOutboundTest#restrictedRemoteTargetIsRejectedWithoutConnecting`；`HttpMcpAndUrlSecurityTest#savingRestrictedRemoteTargetIsRejectedAndNothingPersists` | 已覆盖 |
| 10.2 正常授权远程工具可发现并执行；错误目标、禁用配置、跨用户请求零连接零执行 | `authorizedRemoteToolsAreDiscoveredAndExecuted` 正向；拒绝路径断言替身计数为 0；`McpToolCallbackProviderTest#anotherUsersCodeProducesNoCallbacksAndNoConnection`、`#disabledCapabilityIsSkippedWithoutConnecting` | 已覆盖 |
| 10.3 受控内部依赖与用户任意目标明确区分，不靠宽泛内部例外重开攻击面 | `trustedHosts` 只接受完整主机名精确匹配（去尾点、去方括号），且必须落在 `trustedPorts`（默认 443/8443）内，只能由部署配置写入；`OutboundPolicyTest#trustedHostsAreMatchedExactly`、`#trustedHostOnlyOpensItsDeclaredPorts`；`allowLoopback` 默认 false，且生产 profile 下即使被环境变量打开也不生效（`#loopbackSwitchIsIgnoredInProduction`） | 已覆盖 |
| 10.4 UI 与服务端错误不暴露秘密；受控远程 MCP 替身记录调用次数 | `MCPManager#callTool` 失败返回固定文案，异常只进服务端日志；`parseConfig` 失败日志不再打印配置内容；`ImageAgent` 同步聊天与反馈的错误文案不再拼接异常原文；`McpToolCallbackProvider` 日志只记 `serverCode`；`ControlledMcpServer` 记录 `requests()`/`toolCalls()` | 已覆盖 |

## 本次实际执行的验证

2026-10-23，Windows + JDK 21 / Maven 3.9.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker engine 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin' `
  -RedisBin 'D:\Work\Backend\Redis-x64-3.2.100'
```

`target/acceptance/summary.json`：九个结果分类全部 PASS（runner-tests、secret-scan、isolation、backend、frontend-install、frontend-typecheck、frontend-tests、frontend-build、cleanup），退出 0，run id `a99ad96802c14932bfc8093181fe07da`，`productionAcceptance: NOT_RUN`。

- 后端共 261 条测试、0 失败 0 错误。本工单新增：`HttpMcpAndUrlSecurityTest` 8、`OutboundAddressRulesTest` 5、`OutboundDownloaderTest` 10、`OutboundPolicyTest` 10、`McpCapabilityGuardTest` 4、`McpToolCallbackAuthorizationTest` 7、`McpToolCallbackProviderTest` 4、`MCPManagerOutboundTest` 5、`MCPManagerStdioBanTest` 2、`UrlPictureUploadTest` 6；重写并扩充 `McpServerServiceCacheTest` 8 条。
- 前端 24 条测试（新增 `mcpServerForm.spec.ts` 3 条）、类型检查与构建通过。
- 首次完整运行 `frontend-tests` FAIL：新增的前端用例在 vitest 的 jsdom 环境下拿不到 `window.matchMedia`，且 runtime-only Vue 不编译字符串模板桩。改为 `shallowMount` + 渲染函数桩后通过；未放宽断言。
- 中途有一次直接 `mvn test` 的失败（89 个 error）是隔离验收用例缺少 `VS_ACCEPTANCE_NONCE` 等环境变量导致的 `BLOCKED_ENV`，由 `scripts/acceptance.ps1` 提供；不是本工单的回归。
- 另有一次 `backend` FAIL（`ClassNotFoundException: JsonContentType`，67 failures / 44 errors）是操作失误：验收脚本正在执行 `mvn clean verify` 时手工并发跑了 `mvn compile`，`clean` 把 `target/classes` 删掉，运行中的测试随即找不到类。代码未改动，按原命令重跑即全部通过；上面记录的是重跑结果。

## 人工关卡（未完成，不计入通过）

1. **双实例与多实例一致性。** 「停用后缓存回调不能执行」依赖每次执行前回读数据库，本轮只在单进程内验证，没有起两个实例交叉验证缓存与连接行为。
2. **真实第三方 MCP 服务。** 正向路径只用本机替身（回环 SSE），没有连接真实的远程 MCP 服务；TLS、证书、服务端重定向到受限地址等真实网络行为未覆盖。
3. **真实云元数据/内网探测。** 按工单要求没有探测真实内网或云元数据；受限地址的拒绝是在解析/字面量判定层面证明的，没有发起真实连接去观察拒绝。
4. **生产环境 STDIO 封禁的运行时确认。** 封禁逻辑由 active profiles 判定，测试用 `MockEnvironment` 设置 `prod`。没有以真实 `prod` profile 启动应用验证。
5. **DNS 变化（rebinding）的真实时序。** URL 下载侧由 `PinningResolver` 让判定与连接使用同一份解析结果；远程 MCP 侧只能在每个请求发出前重新判定（JDK `HttpClient` 自己解析 DNS，SDK 不提供注入解析器的入口），因此「判定」与「连接」之间仍有一个很短的窗口。本轮没有构造会在两次解析间切换地址的真实 DNS 来观察时序，这一项不表述为已消除。
6. **浏览器 E2E。** 前端只做了类型检查、单测与构建；「界面不宣称被禁能力可用」是组件层断言，没有真实浏览器截图。
7. **`trustedHosts` 的运维填写。** 该配置只从部署配置读取，本轮没有在真实部署里填写过内部依赖。

## 实施中修正的真实缺陷

1. **策略拒绝被吞成「没有工具」。** `MCPManager#getTools` 原本捕获 `CompletableFuture.get()` 的全部异常并返回空列表，生产 STDIO 封禁与受限目标拒绝都不会传到调用方。现在 `ExecutionException` 的 `BusinessException` 原因会原样上抛。
2. **受控替身的 SSE 握手超时。** JDK `HttpServer` 默认单线程执行器，长连接的事件流会把 POST 请求堵住。改为 `Executors.newCachedThreadPool()` 并在 `close()` 关闭。
3. **`validPicture` 的 HEAD 预检。** 原实现对 URL 先发 HEAD，HEAD 不被支持时整条上传失败，且 HEAD 与 GET 之间没有任何一致性保证。删除预检，全部检查移到实际 GET。
4. **业务拒绝被包装成系统错误。** `PictureUploadTemplate` 的 `catch (Exception)` 会把「地址不安全」包装成 `SYSTEM_ERROR: 上传失败`。增加 `BusinessException` 直通分支。
5. **异常原文回显给用户。** `MCPManager#callTool` 与 `ImageAgent` 的两处错误文案拼接了 `e.getMessage()`，可能带目标地址、请求头或服务端回显的凭据。改为固定文案，细节只进服务端日志。

## 已知取舍

- 出站策略把「解析失败」和「被策略拒绝」合并成同一条错误（`目标地址不在允许范围内` / `目标主机无法解析` 均不区分内网与不存在），避免用错误信息探测内网。
- `trustedHosts` 命中后跳过地址判定，这是「运维声明的内部依赖」与「用户任意目标」的边界；端口仍受 `trustedPorts` 限制，但该主机上这些端口的任何路径都被放行，运维必须保证只填写受控依赖。
- 远程 MCP 的出站判定发生在每个 HTTP 请求发出前，但 JDK `HttpClient` 自己解析 DNS 且 SDK 未暴露解析器注入点，因此连接阶段仍有一次独立解析；URL 下载侧没有这个问题（`DnsResolver` 让判定与连接共用同一份结果）。
- MCP 的工具缓存仍以「用户 + code」为键，没有把 `installType` 或配置指纹纳入；配置变更走 `invalidateToolsCache` 主动失效。
- STDIO 判定看 `installType` 与部署配置里的 `command`/`type`，任一指即按 STDIO 处理；这在非生产环境会让「声明 SSE 但配置带 command」的配置被当成 STDIO 启动。
- 前端能力接口失败时按「STDIO 禁用」处理：宁可让非生产部署暂时看不到 STDIO 选项，也不让生产界面宣称一个会被拒绝的能力可用。
