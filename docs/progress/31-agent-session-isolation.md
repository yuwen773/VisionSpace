# #31 Agent 全入口会话隔离

工单：[31](https://github.com/yuwen773/VisionSpace/issues/31)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`feat/31-agent-session-isolation`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `AgentSessionGuard` | 会话归属的唯一判定入口：首次创建原子绑定、后续读写回读核对、格式校验；拒绝时不区分「不存在」与「属于他人」 |
| `AgentSessionMapper#insertIfAbsent` | `INSERT ... ON DUPLICATE KEY UPDATE sessionId = sessionId`，在 `sessionId` 唯一索引上原子绑定，绝不改写已有会话的归属 |
| `AgentSessionMapper#updateMessageCount/updateCheckpointData/updateSummaryOwned` | 全部带 `userId` 条件；他人调用影响 0 行 |
| `AgentMessageMapper#insertOwned` | `INSERT ... SELECT FROM agent_session WHERE userId = ?`：归属判定与消息写入是同一条语句，没有先查后写的窗口 |
| `ImageAgent` | 同步聊天、SSE、反馈、恢复入口在进入模型之前调用归属判定；`RunnableConfig` 通过 metadata 携带 owner |
| `DualCheckpointSaver` | 无 owner 时跳过 MySQL 备份；备份写入按 `userId` 条件更新，0 行即拒绝 |
| `SummarizationHook` | 摘要落库同样按 owner 条件写入 |
| `AgentController` | 移除「未登录也放行」的流式分支；历史/反馈/聊天统一走守卫；请求体 `userId` 字段删除；SSE 入口在建立流之前完成归属判定，拒绝时显式写 JSON 错误体 |
| `useAgentStream` / `AgentChatPage` | SSE 建立时识别 JSON 拒绝响应并给出后端文案；本地缓存的失效会话自动清理并新建对话 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 06.1 首次创建原子绑定，已有会话写入与执行前核对归属，身份不接受客户端 userId | `AgentSessionGuard#bindOwner`（唯一索引 + 回读核对）；`AgentSessionGuardTest` 覆盖他人会话、缺身份、非法 threadId；`HttpAgentSessionIsolationTest#clientSuppliedUserIdDoesNotReplaceServerIdentity`；`AgentChatRequest`/`FeedbackRequest` 已删除 `userId` 字段 | 已覆盖 |
| 06.2 同步聊天、SSE、反馈、历史、状态读取及恢复拒绝他人与默认无权的平台管理员 | `HttpAgentSessionIsolationTest`：`historyAndSessionsRejectStrangersAndAdminsWithoutMembership`、`feedbackRejectsStrangers`、`syncChatRejectsStrangers`、`streamRejectsStrangers`、`anonymousStreamIsRejected` | 已覆盖 |
| 06.3 跨用户请求零消息插入、零 Checkpoint 变更、零模型或工具执行，并覆盖并发首次创建 | HTTP 用例断言 `ExternalCallRecorder` 调用数不变；`ChatHistoryCursorIntegrationTest#writesFromAnotherUserProduceNoRows`（真实库，零消息、摘要不变、归属不变）与 `#concurrentFirstCreationBindsOneOwner`；`HttpAgentSessionIsolationTest#concurrentFirstCreationBindsExactlyOneOwner`（两个 Cookie 会话并发） | 已覆盖（单实例进程） |
| 06.4 保留历史读取与反馈修复，客户端对拒绝与失效状态给出既有风格反馈 | `ChatHistoryServiceTest` / `ChatHistoryCursorIntegrationTest` 原有用例保持通过；`useAgentStream` 识别非 SSE 的 JSON 拒绝并抛 `AgentStreamError`，`AgentChatPage` 用 `message.error` 展示后端文案；历史加载遇到 40100/40101 时清空本地会话并新建对话 | 已覆盖（工程层） |

## 本次实际执行的验证

2026-10-22，Windows + JDK 21 / Maven 3.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

`target/acceptance/summary.json`：九个结果分类全部 PASS（含后端测试与打包、前端安装/类型检查/测试/构建、资源清理），退出 0。

- 后端共 164 条测试，其中本工单新增 `HttpAgentSessionIsolationTest` 8 条、`AgentSessionGuardTest` 6 条，
  并在 `ChatHistoryCursorIntegrationTest` 增加 2 条真实库用例（该文件共 8 条）。
- 前端 14 条测试、类型检查与构建通过。

## 人工关卡（未完成，不计入通过）

1. **双实例并发。** 并发首次创建只在单进程内验证。结论依赖 `sessionId` 唯一索引上的 `ON DUPLICATE KEY` 与「回读核对」，未起两个实例交叉验证。
2. **恢复入口。** 当前代码没有独立的恢复/续跑 HTTP 入口（`RunnableConfig.resume()` 未被控制器暴露）。本轮只保证「将来新增恢复入口必须复用 `AgentSessionGuard`」，没有可执行的恢复拒绝用例。
3. **浏览器 E2E。** 前端只做了类型检查、单测与构建；SSE 拒绝提示与会话失效清理是代码层改动，没有真实浏览器截图或交互证据。
4. **真实模型拒绝路径。** 验收环境的模型与工具地址指向本地记录器，因此「零模型/工具执行」是以「零外部调用」证明的，没有调用真实 DashScope。

## 实施中修正的两个真实缺陷

1. **SSE 拒绝变成 500。** 初版让归属异常冒泡到 `GlobalExceptionHandler`。流式端点的
   `Accept` 只有 `text/event-stream`，异常处理器协商不出 JSON，Spring 抛出
   `HttpMediaTypeNotAcceptableException`，客户端拿到 500 且读不到原因。
   现在在控制器里就地捕获，显式以 `application/json` 写回业务错误体；
   `ImageAgent.stream` 保留一次 `requireOwned` 作为服务层复核。
2. **验收环境里的模型重试。** Spring AI 默认对 503 做 1s→2s→4s→8s→3m 的退避重试，
   验收环境的模型地址指向记录器，失败的流式请求会挂上数分钟，使「零执行」断言
   看起来像超时。测试类用 `spring.ai.retry.max-attempts=1` 关闭重试——
   拒绝路径本来就不该发起调用，关闭后断言只会更严格。

## 已知取舍

- 归属判定放在 `ImageAgent` 而不是只放在 Controller：Controller 只是入口之一，服务层才是所有调用路径的汇合点。代价是 `ImageAgent` 的公开方法签名带上 `userId`。
- 异步写入被拒绝时只记日志、不抛异常：异步线程里的异常回不到请求方，且拒绝的语义是「没有副作用」。
- `threadId` 长度限制为 64（与 `agent_session.sessionId` 对齐），并拒绝空白与控制字符，避免同一会话在不同层被解析成不同键。
- 已存在的会话不会因新的首次创建请求而改变归属；客户端无法通过重发请求「接管」他人的会话。
