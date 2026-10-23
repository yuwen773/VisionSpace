# #41 Agent 完整恢复与副作用安全

工单：[41](https://github.com/yuwen773/VisionSpace/issues/41)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
基线：`631c3af`（origin/main，含 #31 `12ef2c2`、#33 `e2d3049`）。分支：`codex/41-agent-safe-recovery`。
设计文档：`docs/plans/2026-10-23-41-agent-safe-recovery-design.md`。

本文只记录本次实际执行到的证据；没有执行的部分单列在「未验证与阻断项」，不计入通过。

## 状态与恢复契约

| 概念 | 存储 | 契约 |
| --- | --- | --- |
| 完整执行状态 | `agent_run_state`（唯一键 `threadId`） | `stateVersion` 单调递增，写入 `WHERE stateVersion < ?`；迟到的旧备份影响 0 行 |
| 执行世代 | `runEpoch` | 每次执行开始（`beginExecution` / 续跑认领）原子递增；完整状态写入须匹配当前世代，被取代执行的迟到载荷无论版本多高都写不进（`LOST_OWNERSHIP`，不对齐重试） |
| 工具环境 | `mcpServerCodes` | 与载荷、版本在同一条 UPDATE 原子落库（执行配置声明，`[]` 表示内置环境）；新请求首个检查点前不改既有配对；续跑不带新环境时列原样保留 |
| 状态格式 | `stateFormat` | `v2-full` 可续跑；`v1-lightweight`（仅消息）与未知格式**不可续跑** |
| 恢复点 | `checkpointId` / `nodeId` / `nextNodeId` | 缺 `nextNodeId` 时 `toCheckpoint` 返回空，不把「有消息」当「能续跑」 |
| 运行状态 | `runStatus` | `RUNNING` / `AWAITING_CONFIRMATION` / `COMPLETED` / `FAILED` / `PAUSED_UNRECOVERABLE`；`RUNNING` 另报 `activeExecution`（租约有效或 10 分钟内有推进），失活遗留的 RUNNING 露出恢复入口 |
| 人工确认信息 | `pendingConfirmation` | 只存工具名与脱敏描述；随完整状态一起持久化 |
| 备份失败 | `backupError` / `backupAttempts` | 可见、可重试；下次成功写入即清零 |
| 外部动作 | `agent_action_record`（唯一键 `(threadId, actionId)`） | 状态机见下；执行互斥靠原子认领 + `claimToken`，不靠等待时间 |
| 幂等能力 | `idempotency` | 只有 `PROVEN` 才允许「结果未知」时退回可重试 |
| 续跑互斥 | `resumeLease` / `resumeLeaseUntil` | 版本 + 租约双重条件；租约到期只用于回收崩溃遗留，不用于判断动作安全 |
| 续跑工具环境 | `mcpServerCodes` | 只存 MCP 服务 code，续跑时按当前用户重新授权；`[]` = 确认没用 MCP，`NULL` = 没有记录（拒绝续跑）；凭据、地址、回调对象一律不落库 |
| 审计 | `argumentsDigest` / `resultSummary` | 只存 sha-256 与截断摘要，不含正文 |

动作状态机（每次推进都必须带本次认领的 `claimToken`）：

```
(无) --claim--> EXECUTING
EXECUTING --成功--> SUCCEEDED                    （终态，不再执行）
EXECUTING --工具明确报错--> FAILED                （外部未成功，可安全重试）
EXECUTING --异常/结果未知 + PROVEN--> FAILED       （可安全重试）
EXECUTING --异常/结果未知 + NOT_PROVEN--> UNKNOWN_NEEDS_REVIEW
UNKNOWN_NEEDS_REVIEW --人工确认已发生--> SUCCEEDED （终态）
UNKNOWN_NEEDS_REVIEW --人工确认未发生--> DISMISSED （终态，不会被重新认领）
FAILED --claim（新 token）--> EXECUTING
```

认领结果分五种，调用方必须分别处理，**只有 `ALREADY_SUCCEEDED` 能说「已完成」**：

| 认领结果 | 含义 | 拦截器行为 |
| --- | --- | --- |
| `ACQUIRED` | 本次持有 | 调用外部工具 |
| `ALREADY_SUCCEEDED` | 有可靠完成证据 | 返回「已确认完成」，不调用外部 |
| `IN_FLIGHT` | 另一个执行方正在跑 | 非幂等工具同样抛 `PendingReviewException` 暂停；幂等工具回错误文本 |
| `NEEDS_REVIEW` | 结果未知 | 非幂等工具抛 `PendingReviewException` 暂停本次运行 |
| `UNAVAILABLE` | 台账不可用，什么都证明不了 | 返回错误：无法确认是否已执行 |

认领是否成功**以读回的 `claimToken` 为准，不看影响行数**：Connector/J 默认按
「匹配行数」返回，`ON DUPLICATE KEY UPDATE` 命中但值没变时也是非零，
四个并发认领会全部以为自己是赢家（真实 MySQL 验收用例复现过）。

本工单**不承诺「恰好一次」**。它保证的是：不会因为重放而重复产生外部副作用，
且结果未知时不会把「可能已发生」当成「没发生」去重试。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `agent/recovery/AgentRunStateStore` | 完整状态的 gzip 序列化、版本 CAS 写入、归属条件读写、恢复点还原、备份失败记录 |
| `agent/recovery/AgentActionLedger` | 动作台账状态机；原子认领（`claim`）+ `claimToken` 结算；`actionId`/参数摘要派生；`reconcileStaleExecuting` 单语句收敛上次中断的 `EXECUTING` |
| `agent/recovery/AgentActionClaim` | 认领结果五态（`ACQUIRED`/`ALREADY_SUCCEEDED`/`IN_FLIGHT`/`NEEDS_REVIEW`/`DISMISSED`/`UNAVAILABLE`） |
| `agent/recovery/AgentActionInterceptor` | 工具执行边界：按认领结果分别处置；只有可靠完成证据才说「已完成」；非幂等工具待核对时抛 `PendingReviewException` 暂停执行 |
| `agent/recovery/AgentRecoveryService` | 受限恢复：归属校验、版本 + 租约认领、所有执行入口共用的待核对闸门、人工核对入口 |
| `agent/recovery/ResumeToolEnvironment` | 续跑时按持久化的 MCP code 用当前用户重新授权并重建工具环境；服务撤销则拒绝续跑 |
| `agent/config/DualCheckpointSaver` | `get` 不再用 MySQL 轻量归档伪造恢复点；`put` 同步写完整状态（带单调版本）；`release` 清理计数与载荷 |
| `agent/config/AgentToolExceptionPolicy` | 工具异常策略：`ImageGenerationException` 原样重抛给台账，其余按框架默认折成错误文本 |
| `sql/migrations/2026-10-23-agent-run-state-claim-columns.sql` | 第二轮新增列的前向迁移（可重复执行）；回滚只删列、保留全部动作证据 |
| `agent/ImageAgent` | 持久化人工确认中断；续跑入口 `resume`；`PendingReviewException` → `ACTION_UNKNOWN` 事件 |
| `controller/AgentController` | `GET /agent/recovery/{threadId}`、`POST .../resume`、`POST .../action/confirm`、`POST .../action/dismiss` |
| `frontend` | `RecoveryBanner`（核对/续跑/不可恢复）、`ActionUnknownMessage`、`useAgentStream` 的 `action-unknown` 分支 |
| `sql/migrations/2026-10-23-agent-run-state.sql` | 前向迁移（两张新表）与同名 `-rollback.sql` |

## 逐项验收

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 25.1 持久保存完整状态、版本、确认信息；读取与恢复经归属校验 | `AgentRunStateStoreTest`（11 条：CAS、归属条件、格式不可识别、缺恢复点）；`HttpAgentRecoveryTest#recoveryStateRejectsStrangersAndAdmins`、`#strangerCannotResumeAndNothingExecutes`；`AgentRunStateMapper` 所有语句带 `userId` | PASS |
| 25.2 确认前后、外部执行前后、本地提交前后的故障窗口；跨实例一致 | `reconcileStaleExecuting`（单条 SQL 收敛 `EXECUTING` 遗留 → 可证幂等退回 `FAILED`，否则 `UNKNOWN_NEEDS_REVIEW`）；版本顺序以共享持久状态为准（`AgentRunStateStore#currentVersion`）；`DualCheckpointSaverTest#olderStartedInstanceAdoptsTheStoredVersionInsteadOfBeingRejectedForever`、`#putPersistsFullStateSynchronouslyWithIncreasingVersions`；`HttpAgentRecoveryTest#concurrentWritersFromTwoInstancesProduceOneWinner`、`#takingOverInstanceWritesProgressAboveTheStoredVersion`、`#staleExecutingActionIsReconciledNotReplayed` | PASS（单机多连接，未起第二实例进程） |
| 25.3 旧异步备份晚到不覆盖；备份失败可见可重试；释放后清理计数 | `AgentRunStateMapper#updateIfNewer`（`WHERE stateVersion < ?`）；`DualCheckpointSaverTest#releaseClearsRunStateAndPerThreadCounters`；`AgentRunStateStoreTest#backupFailureIsRecordedSoItIsVisibleAndRetryable`；`HttpAgentRecoveryTest#lateOlderBackupLosesTheVersionRace`、`#backupFailureIsVisibleAndRetryClearsIt`、`#releaseClearsPayloadAndRecoveryPoint` | PASS |
| 25.4 旧轻量归档可继续按权限查看，但不得伪装成可恢复 | `DualCheckpointSaver#get` 移除 MySQL 回填；`loadLegacyArchiveMessages` 只读；`AgentRunStateStoreTest#legacyLightweightArchiveIsNotResumable`、`#fullStateWithoutRecoveryPointCannotBeTurnedIntoCheckpoint`；`DualCheckpointSaverTest#normalStartDoesNotResurrectLegacyArchiveAsResumePoint`；`HttpAgentRecoveryTest#legacyArchiveOnlyIsReadableButNotResumable`、`#resumeWithoutAnyStateIsPausedInSteadOfFakingRecovery` | PASS |
| 25.5 格式兼容迁移与安全回滚；不宣称恰好一次 | `AgentStateFormat.fromCode` 未知格式 → 不可续跑并明确暂停；迁移 SQL 独立建表、回滚 SQL 只删新表、`checkpointData` 不改；`.get` 文档与本文明确「不承诺恰好一次」 | PASS（迁移脚本只在验收临时库执行，未在真实库运行） |
| 26.1 记录动作标识/状态/结果；已确认完成不得重放 | `AgentActionLedgerTest`（15 条：认领五态、token 结算、收敛）；`AgentActionConcurrencyTest#twoConcurrentCallsForTheSameActionRunTheExternalToolOnce`（外部调用次数 = 1）、`#completedActionIsReportedAsCompletedRatherThanExecutedAgain`；`HttpAgentRecoveryTest#concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn`（真实 MySQL，4 并发只有 1 个赢家）、`#failedActionCanBeReclaimedWithANewToken`、`#succeededActionCannotBeReclaimed`、`#dismissedActionIsTerminalAndNotSilentlyReExecuted` | PASS |
| 26.2 外部完成、本地未提交窗口；只有可证明幂等才允许安全重试 | `AgentActionConcurrencyTest#crashAfterExternalSideEffectDoesNotAllowImmediateReplay`（崩溃后立即重来，外部调用 0 次、最终状态仍为待核对）；`AgentActionInterceptorTest#exceptionOnNonIdempotentToolPausesForReviewInsteadOfRetrying`、`#exceptionOnIdempotentToolPropagatesAsRetryableFailure`、`#toolLevelErrorIsExplicitFailureAndRetryable`、`#actionWaitingForReviewPausesTheRunInsteadOfFakingCompletion`；`AgentGenerationFailureTest`（真实生成工具超时 → 待核对） | PASS |
| 26.3 前端区分明确失败与结果未知，有权用户可核对并恢复 | `agentRecovery.spec.ts`（12 条：待核对不给一键重试、核对后仍保留续跑入口、失败可续跑、不可恢复不给续跑、旧归档明确不可续跑、备份失败可见、`action-unknown` 不并入助手消息、JSON 拒绝视为失败）；`RecoveryBanner` 逐条「已执行/未执行」 | PASS（单元/组件层，无浏览器 E2E） |
| 26.4 重复核对、过期请求、旧版本请求不重复执行；他人/管理员不可续跑 | `HttpAgentRecoveryTest#repeatedReviewIsRejectedAndOnlyTheFirstOneCounts`、`#staleResumeVersionIsRejected`、`#duplicateResumeRequestsCannotBothClaimTheSameVersion`、`#refreshedVersionCannotBypassARunningResume`（刷新版本也不能绕过租约）、`#releasingTheLeaseLetsTheUserResumeAgain`、`#pendingReviewBlocksResumeUntilReviewed`、`#strangerCannotReviewActionsOwnedBySomeoneElse`、`#recoveryStateRejectsStrangersAndAdmins` | PASS |
| 26.5 审计与错误不泄露私人正文或秘密；以受控外部调用次数与最终状态为证 | 参数只存 sha-256（`AgentActionLedgerTest#argumentsAreStoredAsDigestNotAsRawText`）；结果摘要只存长度；失败原因只存异常类型（`AgentRunStateStoreTest#failureReasonDoesNotCarryRawContent`、`HttpAgentRecoveryTest#backupFailureIsVisibleAndRetryClearsIt` 断言不含路径）；续跑配置只存 MCP code（`AgentRunStateStoreTest#mcpServerCodesArePersistedAsCodesOnlyAndCreateTheRowOnFirstRun`）；拒绝路径用 `ExternalCallRecorder` 断言零外部调用 | PASS |

## 本次实际执行的验证

2026-10-23，Windows 11 + JDK 23（`--release 21`）+ Maven 3.9.9 + Node/npm + PowerShell 7，
原生隔离 MySQL 8.0.26 + Redis 3.2.100（无 Docker）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin' `
  -RedisBin 'D:\Work\Backend\Redis-x64-3.2.100'
```

`target/acceptance/summary.json`：九个分类全部 PASS（`runner-tests`、`secret-scan`、`isolation`、
`backend`、`frontend-install`、`frontend-typecheck`、`frontend-tests`、`frontend-build`、`cleanup`），
退出 0。

- 后端 375 条测试 0 失败（真实 MySQL + Redis，含全部 HTTP 验收用例）。
- 前端 36 条测试（`agentRecovery.spec.ts` 12 条），类型检查与生产构建通过。

本工单相关的测试分布：`AgentRunStateStoreTest` 21、`AgentActionLedgerTest` 16、
`AgentActionInterceptorTest` 11、`AgentActionConcurrencyTest` 5、`DualCheckpointSaverTest` 12、
`HttpAgentRecoveryTest` 21、`ImageGeneratorToolTest` 5、`AgentGenerationFailureTest` 3、
`AgentToolChainFailureTest` 2、`ResumeToolEnvironmentTest` 4、
`TextMcpModelInterceptorResolveTest` 4、`ImageAgentPendingReviewGateTest` 5、
`ImageAgentResumeToolEnvironmentTest` 3。

### 审查后修复的六项缺陷（2026-10-23 第二轮）

外部审查在 `2fc1c80` 上发现六项问题，均先写失败回归再修复：

| # | 缺陷 | 根因 | 修复 | 回归 |
| --- | --- | --- | --- | --- |
| 1 | 同一动作可重复执行 | `begin()` 把已存在的 `EXECUTING` 判为可执行，判定与占用分两步，没有互斥 | 用单条 `INSERT ... ON DUPLICATE KEY UPDATE` 原子认领，只有「不存在」或「已明确失败」能抢到；认领结果以读回的 `claimToken` 为准 | `AgentActionConcurrencyTest#twoConcurrentCallsForTheSameActionRunTheExternalToolOnce`（外部调用 1 次）、`#inFlightExecutingRowIsNotReclaimableEvenWithoutAnyGracePeriod`、`#onlyTheClaimOwnerCanSettleTheAction`；`HttpAgentRecoveryTest#concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn`、`#failedActionCanBeReclaimedWithANewToken` |
| 2 | 跨实例接管后状态写不进去 | `DualCheckpointSaver` 用「进程启动时间 + 本地计数器」生成版本，从不吸收库里的版本 | 每次以 `currentVersion()` 为下界生成版本；`saveFullState` 返回 `SAVED`/`SUPERSEDED`/`FAILED` 三态，被取代时对齐重试一次，真正的写失败记录 `backupError`；`release` 清计数器不再回退版本 | `DualCheckpointSaverTest#olderStartedInstanceAdoptsTheStoredVersionInsteadOfBeingRejectedForever`、`#supersededWriteIsRetriedAgainstTheNewerStoredVersion`、`#releaseDoesNotRegressVersionsBecauseOrderComesFromTheDatabase`；`AgentRunStateStoreTest#unwritableRowIsReportedAsFailureNotAsSuperseded`；`HttpAgentRecoveryTest#takingOverInstanceWritesProgressAboveTheStoredVersion` |
| 3 | 实际图片生成超时被记为成功 | `ImageGeneratorTool` 吞掉 `ImageSynthesis.call` 的异常后返回空列表，拦截器把非 error 返回一律记成功；**框架默认的 `ToolExecutionExceptionProcessor(alwaysThrow=false)` 还会把工具异常折成一条非 error 响应** | 生成工具把调用失败与「无可用产物」都抛 `ImageGenerationException`；`AgentToolExceptionPolicy` 让它原样重抛到拦截器；拦截器不再把空结果当成功 | `ImageGeneratorToolTest`（5 条）、`AgentGenerationFailureTest`（3 条）、`AgentToolChainFailureTest`（2 条，真实 `ReactAgent` + 真实工具 + 真实台账） |
| 4 | 未知结果与台账故障被伪装成已完成 | 拦截器把「没抢到」一律返回「该动作已确认完成」；待核对暂停只在续跑入口判断 | 认领五态分别处置，只有 `ALREADY_SUCCEEDED` 说「已完成」；`IN_FLIGHT` 对非幂等工具同样暂停；`requireNoPendingReview` 闸门接入普通对话、流式、反馈、可中断入口与续跑，`PendingReviewException` 在同步入口也不再被吞成「稍后重试」 | `AgentActionInterceptorTest#inFlightActionPausesTheRunInsteadOfLettingTheModelRetry`、`#inFlightIdempotentActionReportsExecutingWithoutPausing`、`#ledgerFailureIsReportedAsUnavailableNotAsCompleted`、`#actionWaitingForReviewPausesTheRunInsteadOfFakingCompletion`；`AgentActionConcurrencyTest#twoConcurrentCallsForTheSameActionRunTheExternalToolOnce`；`ImageAgentPendingReviewGateTest`（5 条） |
| 5 | 人工核对后恢复按钮消失 | `RecoveryBanner` 在 `resumable=true` 且无待核对时直接隐藏，中断/失败态因此没有续跑入口 | 只在「正常运行中」隐藏；已完成不给任何入口；中断/失败/可续跑一律显示并给出续跑按钮 | `agentRecovery.spec.ts#keeps the resume entry after the user has reviewed the actions`、`#offers resume for a failed but resumable run`、`#shows a paused state without offering resume when it is not resumable` |
| 6 | MCP 恢复丢失原工具环境 | `resume` 一律用内置 agent；MCP 选择没有持久化 | 只持久化 MCP 服务 code（`mcpServerCodes`），续跑时按当前用户重新回读授权并重建 MCP agent；任一服务停用/撤销/删除则拒绝续跑；**没有记录（NULL）同样拒绝**，只有显式 `[]` 才走内置 agent | `ResumeToolEnvironmentTest`（4 条）、`TextMcpModelInterceptorResolveTest`（4 条）、`ImageAgentResumeToolEnvironmentTest`（3 条）、`AgentRunStateStoreTest#mcpCodesRecordedBeforeTheRowExistsAreFlushedWithTheFirstCheckpoint`、`#recordedMcpServerCodesRoundTripAndUnparsableValuesAreNotGuessed` |

### 代码审查（第二轮）追加修复

两轴审查在修复轮上又发现四处会在真实链路上漏掉的问题，均已先写失败回归再修：

| 缺陷 | 根因 | 修复 | 回归 |
| --- | --- | --- | --- |
| 真实链路仍把生成超时记成成功 | 框架 `AgentToolNode.executeSyncTool` 用 `DefaultToolExecutionExceptionProcessor(alwaysThrow=false)` 把工具异常折成非 error 响应，拦截器看不到异常 | 新增 `AgentToolExceptionPolicy`，把 `ImageGenerationException` 列为原样重抛；内置与 MCP agent 都挂上 | `AgentToolChainFailureTest`（去掉策略后 2 条全失败，恢复后通过） |
| 崩溃窗口内的 `IN_FLIGHT` 只回错误文本 | 模型收到「正在执行中」后会换一个 toolCallId 再来一次，同一副作用产生两次；这等于依赖 10 分钟宽限期才安全 | 非幂等工具 `IN_FLIGHT` 与 `NEEDS_REVIEW` 同路径抛 `PendingReviewException`；幂等工具仍只回错误文本 | `AgentActionInterceptorTest#inFlightActionPausesTheRunInsteadOfLettingTheModelRetry`、`#inFlightIdempotentActionReportsExecutingWithoutPausing`；`AgentActionConcurrencyTest#twoConcurrentCallsForTheSameActionRunTheExternalToolOnce` |
| 版本读失败被当成「被更新版本取代」 | `currentVersion` 吞掉读异常返回 0，`saveFullState` 于是把「读不到」判成 `SUPERSEDED`，只 debug 一句就丢掉这一版进度 | 读版本区分「没有行」与「读不到」；读不到一律按写入失败记录 `backupError`；`DualCheckpointSaver` 对 `FAILED` 改为告警 | `AgentRunStateStoreTest#unwritableRowWithUnreadableVersionIsAFailureNotASupersede` |
| 没有 MCP 记录被当成「没用 MCP」 | `recordMcpServerCodes` 把空列表写成 NULL，续跑时与「旧数据没记录」无法区分，于是在少了工具的情况下继续跑 | 空环境写 `[]`；`NULL` 保留给「没有记录」，续跑直接拒绝；首次执行行未建时先缓冲、第一个检查点后补写（不提前建行） | `AgentRunStateStoreTest#emptyMcpServerCodesAreRecordedAsAnEmptyListNotAsMissing`、`#mcpCodesRecordedBeforeTheRowExistsAreFlushedWithTheFirstCheckpoint`、`#recordedMcpServerCodesRoundTripAndUnparsableValuesAreNotGuessed`；`ImageAgentResumeToolEnvironmentTest`（3 条） |

审查同时指出迁移脚本是就地改的：已经执行过旧版建表脚本的环境拿不到新列，
`mapper.claim` 会抛错、台账判成 `UNAVAILABLE`、所有工具调用被永久拦下。
现补了独立的前向迁移 `2026-10-23-agent-run-state-claim-columns.sql`（按
`information_schema` 判断，可重复执行）与只删列的回滚脚本；两者都在
MySQL 8.0.26 上实测：旧表 → 加列 → 重跑无错 → 回滚 → 重跑无错，
且回滚后动作证据（状态、尝试次数）完整保留。

修复过程中由真实 MySQL 又暴露两个问题，一并修正：

- **影响行数不能判断认领归属。** Connector/J 默认按「匹配行数」返回，
  `ON DUPLICATE KEY UPDATE` 命中但值没变也是非零，四个并发认领全部"成功"。
  验收用例 `concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn` 第一次运行就是
  `expected: <1> but was: <4>`。改为读回 `claimToken` 判定。
- **`ON DUPLICATE KEY UPDATE` 的赋值顺序。** 从左到右求值且后续表达式看到已更新的值，
  先写 `status` 会让后面几行的 `status = 'FAILED'` 判断失效，重新认领拿不到新 token。
  现把 `status` 放到最后一个赋值。

### 实施中修正的真实缺陷

1. **正常开始被旧归档污染。** `CompiledGraph.getInitialState` 每次正常开始都会调用
   `saver.get()`，旧实现此时用 MySQL 轻量归档重建一个只含 messages 的 Checkpoint，
   于是「仅消息」被当成完整恢复点混进新执行。现已移除该回填，恢复只走显式入口。
2. **备份失败原因带路径。** 初版把异常消息截断后写进 `backupError`，可能携带文件路径或
   SQL 参数。现只保存异常类型，细节留在服务端日志。
3. **恢复不可用时返回通用系统错误。** `requireResumable` 初版抛 `IllegalStateException`，
   经全局处理器变成「系统错误」。现改为 `BusinessException(OPERATION_ERROR)`，
   客户端能拿到可操作的文案。
4. **完整状态把消息序列化成了 Map。** 初版用普通 `ObjectMapper` 读写状态 Map，
   `messages` 里的 Spring AI `Message` 读回来会退化成 `LinkedHashMap`，续跑时模型节点
   拿到的不是消息对象。现改走框架同一份 `StateSerializer`（`dataToBytes`/`dataFromBytes`），
   并补了一条断言消息类型得以保留的用例。
5. **状态行从未创建，所有写入静默丢失。** 代码评审发现 `agent_run_state` 没有任何
   INSERT 调用，而 `updateIfNewer` 是 `UPDATE`：生产里第一次执行影响 0 行，
   完整状态、待确认信息、备份失败记录全都写不进去，`/resume` 永远失败。
   测试此前用原始 JDBC 预插行掩盖了它。现由 `saveFullState` 自己
   `insertIfAbsent`（唯一键 + 空操作，不改写归属），并把验收夹具改成只走真实保存路径。
6. **正在执行的动作被判成「上次遗留」。** `reconcileStaleExecuting` 原来收敛所有
   `EXECUTING`，会把另一个实例正在跑的动作标成中断，进而放行重放。现只收敛
   超过 10 分钟没有更新的记录，并补了「进行中的动作不动」的用例。
7. **重复续跑请求都能通过。** `expectedVersion` 只比对、从不推进，两个并发
   `/resume` 都会执行。现按版本认领（`UPDATE ... WHERE stateVersion = ?`），
   只有一个成功，另一个明确被拒。
8. **续跑失败被包装成成功回复。** `resume` 把异常吞成 `ResultUtils.success("抱歉…")`，
   界面会把它显示成正常助手消息。现改为抛 `BusinessException`，让界面能区分
   「明确失败」与「结果未知」。
9. **审计列可能写入正文。** 工具结果摘要原来保存返回内容的前 80 个字符，
   人工确认信息保存中断描述的前 120 个字符——两者都可能含提示词或生成参数。
   现只记录结果长度与工具名。
10. **恢复点缺少字段时靠 NPE 兜底。** `Checkpoint.builder().build()` 对
   `id`/`nodeId`/`nextNodeId` 做非空校验，初版 `toCheckpoint` 只检查了 `nextNodeId`，
   缺 `checkpointId` 或 `nodeId` 时会抛 NPE 被吞成「无法还原」。现显式判空，
   并补了针对性用例。

## 第二轮复审修复（2026-10-23，`a55cac1` 复审五项）

复审报告指出四项 Spec 问题与一项 Standards 问题，本轮逐项修复；每项先补能在旧实现上失败的回归，再修实现。

### F1 [P1] 旧检查点重新编号后覆盖新状态

- 根因：`saveWithVersionAlignment` 在 SUPERSEDED 后读取共享版本、给同一旧载荷发更高版本重试，无法区分「合法接管」与「迟到旧载荷」。
- 修复：引入**执行世代** `agent_run_state.runEpoch`（fencing token）。每个执行入口经 `AgentRunStateStore.beginExecution` 原子递增世代并放进 `RunnableConfig` metadata；续跑由 `claimForResume` 同语句递增。`updateIfNewer` 增加世代条件；0 行时读回世代，不匹配判为新结果 `LOST_OWNERSHIP`——**不对齐重试**；世代匹配下的版本落后才保留一次对齐重试（合法接管能力不变）。不带世代的旧调用方退回纯版本 CAS。
- 回归：`DualCheckpointSaverTest`（LOST_OWNERSHIP 不重试、世代+环境从配置透传、缺世代退回版本语义）、`AgentRunStateStoreTest`（世代不匹配/匹配两种 0 行的区分、`beginExecution` 递增/无行/竞争放弃）、`HttpAgentRecoveryTest`（真实 MySQL 时序：接管后迟到载荷 `LOST_OWNERSHIP` 且**最终恢复点内容**仍是新执行的；合法接管持续推进；并发交错）。

### F2 [P1] 通用非幂等 MCP 超时仍记为成功

- 根因：MCPManager 把客户端异常折成 isError 文本响应，`McpToolCallback` 只提取文本，错误语义在适配层丢失，台账记 `SUCCEEDED`，模型继续执行。
- 修复：结果语义分层保留——MCPManager 捕获异常时置新字段 `outcomeUnknown`（传输超时/响应丢失 ≠ 服务明确报错）；`McpToolCallback` 把结果未知包成 `ToolExecutionException`（cause 为新类型 `McpToolOutcomeUnknownException`）抛出，把服务明确报错包成普通 `McpToolBusinessException` 抛出；`AgentToolExceptionPolicy` 改为按**工具幂等性**（`ToolExecutionException.getToolDefinition().name()` 查 `AgentActionInterceptor` 的可证明幂等集合）判定：非幂等的结果未知原样重抛 → 台账 `UNKNOWN_NEEDS_REVIEW` + `PendingReviewException` 暂停本轮；幂等只读工具与既有 `ImageGenerationException` 语义不变。明确业务失败以普通异常抛出，被框架折成 error 响应 → 台账 `FAILED`（可安全重试）。
- 回归：新增 `McpToolChainFailureTest`（真实 ReactAgent → 真实 McpToolCallback → 受控 MCPManager 替身 → 真实拦截器/台账）：外部写入后超时 ⇒ 外部调用 1 次、模型调用停在 1 次（不再给下一轮）、台账待核对；业务失败 ⇒ 台账 `FAILED` 且模型可见错误继续作答；人工核对入口（confirm → 后续认领 `ALREADY_SUCCEEDED`；dismiss → `DISMISSED`）；幂等工具结果未知不中断执行。既有图片生成异常传播与原子认领回归保持原样通过。

### F3 [P2] 硬崩溃遗留 RUNNING 时恢复入口隐藏

- 根因：检查点统一写 RUNNING，硬崩溃不经过 catch 改 FAILED；`RecoveryBanner` 对 `RUNNING && resumable` 直接隐藏。
- 修复：`AgentRecoveryService.describe` 计算 `activeExecution`（租约未过期 **或** `updateTime` 距今 10 分钟内 ⇒ 仍有活跃执行；阈值与动作收敛宽限一致）；前端 `RecoveryBanner` 对 RUNNING 只在 `activeExecution` 时隐藏，失活遗留显示「上次执行可能已中断」并给出继续执行按钮。正常执行、待核对、不可恢复、完成的行为不变。
- 回归：`HttpAgentRecoveryTest`（新状态 active、固定远古时间戳模拟失活后可恢复、有效租约保持 active）；`agentRecovery.spec.ts`（活跃 RUNNING 隐藏、失活 RUNNING 露出横幅与续跑按钮、租约优先于更新时间的语义）。

### F4 [P2] MCP 环境与对应检查点不是原子版本

- 根因：`recordMcpServerCodes` 在新请求开始时独立 UPDATE `mcpServerCodes`，早于首个新检查点；此间崩溃留下「旧载荷 + 新环境」的错配。
- 修复：删除提前直写与 `pendingMcpCodes` 缓冲。工具环境改由本次执行的 `RunnableConfig` 声明（`DualCheckpointSaver.MCP_SERVER_CODES_METADATA_KEY`），`DualCheckpointSaver` 把它与载荷、版本放进**同一条 UPDATE**（`COALESCE` 保留未声明时的旧值）。环境记录失败不再可能静默拼上另一轮的环境——它们本来就是同一次写入；新请求首个检查点前崩溃时既有配对原样保留；续跑不带新环境，沿用与恢复点配对的记录。
- 回归：`AgentRunStateStoreTest`（环境随检查点同语句落库、null 环境不动旧值）、`HttpAgentRecoveryTest`（首个检查点前崩溃旧配对原样、续跑推进载荷但环境不变、两个执行交错时最终是「载荷 + 匹配环境」的配对）、`DualCheckpointSaverTest`（配置中的环境透传到持久写入）。

### F5 [P3] 恢复接口生成客户端未同步

- 从当前后端契约重新生成：起隔离后端（临时原生 MySQL/Redis + `vs_openapi` 库，替身 AI key，**未调用任何付费服务**），`npm run openapi2ts`。产物：`agentController.ts` 新增 `getRecoveryStateUsingGet` / `resumeUsingPost` / `confirmActionUsingPost` / `dismissActionUsingPost`；`typings.d.ts` 新增 `RecoveryStateVO`（含 `activeExecution`）、`ActionReviewRequest` 等类型；顺带修正了生成器对 SSE 端点的响应类型（`ServerSentEventString[]` → `Record<string, any>`）并补齐了后端已存在但此前未生成的 `pictureContentController.ts`。手写 `api-custom/agent.ts` 补 `activeExecution` 字段。type-check / vitest / build 全过。

### 第二轮验收结果（本次实际执行）

- `scripts/acceptance.ps1`（原生 MySQL 8.0.26 + Redis 3.2.100，临时实例，datadir/nonce 身份核对，清理 PASS）：9 个 gate 全 PASS。
- 后端 **396/396**（基线 375，净增 21：`McpToolChainFailureTest` 4、`HttpAgentRecoveryTest` +9、`AgentRunStateStoreTest` +5、`DualCheckpointSaverTest` +3）。
- 前端 **38/38**（基线 36，净增 2 个 `RecoveryBanner` 失活场景断言）；type-check 与 build PASS。
- 隔离层以上的验证分级：单进程多线程（台账认领、版本/世代并发）= 单 JVM 多连接打真实 MySQL；**双 JVM 未做**；受控模型/MCP（脚本化 ChatModel、受控 MCPManager/McpServerAccess 替身 + 真实 ReactAgent 适配链）；真实外部 MCP / 付费模型 / 浏览器 E2E / 生产验收未执行。

## 未验证与阻断项

1. **双实例竞争是单机双连接验证的。** `concurrentWritersFromTwoInstancesProduceOneWinner`、
   `concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn`、`refreshedVersionCannotBypassARunningResume`
   都是同一进程内的多线程打同一行真实 MySQL，证明的是 SQL 层面的原子性；
   **没有启动第二个 Spring Boot 进程**，因此跨进程的 Redis/MySQL 时序、
   两个 JVM 各自的本地计数器、租约续期都未实测。
2. **真实模型下的工具重放验证未做。** 验收环境的模型与工具地址指向本地记录器，
   Agent 循环跑不到工具节点。26.1/26.2 由 `AgentActionInterceptor` + 有状态 Mapper 替身、
   `AgentGenerationFailureTest`（真实 `ImageGeneratorTool` + 替身 `ImageSynthesis`）
   与 `AgentToolChainFailureTest`（真实 `ReactAgent` + 真实工具 + 真实台账，模型用脚本化
   `ChatModel` 替身）证明，**没有在真实 DashScope + 真实图片生成上验证**「确认完成后不重放」。
   本会话不授权调用付费模型，这一项保持未验证。
3. **迁移脚本未在真实库执行。** 只随验收临时库建过表；生产迁移与回滚未执行（本会话不授权）。
4. **浏览器 E2E 未做。** `RecoveryBanner` 与 `ActionUnknownMessage` 有组件级测试，
   没有真实浏览器截图或交互证据。
5. **旧轻量归档的展示路径未接 UI。** `loadLegacyArchiveMessages` 已提供只读入口，
   当前前端仍从 `agent_message` 读历史，该方法的调用方尚未接入。
6. **双实例续跑未做真实进程验证。** 版本 + 租约认领的并发用例在单进程内用多个连接验证；
   没有启动第二个 Spring Boot 进程交叉续跑。
7. **`reconcileStaleExecuting` 的宽限期（10 分钟）是经验值。** 超过该时长的
   长耗时工具调用仍可能被误判为中断；这类工具本身不可证明幂等，误判只会
   进入待核对，不会重放，但会给用户多一次核对。**执行互斥不依赖这个宽限期**：
   即便收敛没发生，执行中的动作也不会被第二次认领。
8. **续跑租约（5 分钟）不会续期。** 单次续跑超过 5 分钟时，另一个请求理论上可以
   再次认领。此时动作层面的认领仍然互斥，所以不会重复产生外部副作用，
   但可能出现两个续跑同时推进状态。这一窗口未实测，也未做租约续期。
9. **MCP 续跑只在组件层验证。** `ResumeToolEnvironment` 与
   `TextMcpModelInterceptor.resolveForCodes` 有单元测试，`ImageAgent.resume`
   的重建分支（`ImageAgentResumeToolEnvironmentTest`）用替身验证了「记录缺失 /
   服务撤销 / 显式空环境」三条判定，但**没有被真实 MCP 服务端到端跑过**。
10. **迁移脚本未在真实库执行。** 新增列的迁移与回滚只在一次性 MySQL 8.0.26
   实例上验证（旧表 → 加列 → 重跑 → 回滚 → 重跑，动作证据保留）；
   生产迁移与回滚仍未执行（本会话不授权）。
11. **工具异常语义按「明确失败 vs 结果未知」与幂等性分层，边界如下。**
   结果未知（传输超时/响应丢失）的非幂等工具一律重抛进待核对；幂等只读工具
   与其他明确失败走框架默认（错误文本/error 响应）。两个已知精度损失：
   幂等只读工具「结果未知」被转成文本后台账记 `SUCCEEDED`（只读无副作用，
   重试靠新 toolCallId，无重放风险，仅审计口径不精确）；MCP 业务失败与
   传输失败的区分依赖 MCPManager 的 `outcomeUnknown` 标记，协议层无法证明
   「服务端收到请求但没执行」的中间态。
12. **执行世代的两个理论窗口。** ①首个执行的执行开始时状态行还不存在，
   `beginExecution` 返回 0，若两个「首次执行」在同一毫秒内并发开始，
   两者都持有世代 0，靠版本 CAS 仲裁（与修复前同级，且首个执行通常没有
   可竞争的既有恢复点）；②世代推进持续竞争 5 次失败的执行放弃持久化资格
   （返回 -1），只留 warn 日志，恢复点会缺失但不写错归属。
13. **RUNNING 失活阈值（10 分钟）是经验值。** 纯模型阶段超过 10 分钟且
   期间没有任何检查点/状态写入时，活跃运行会被误报为失活，界面给出
   续跑入口；误点续跑由世代 + 续跑认领兜底（旧执行后续写入被世代拒绝），
   不会写坏状态，但可能出现一段重复劳动。租约刷新只在续跑认领时发生，
   普通对话/流式执行不持租约，靠 `updateTime` 推进判定活性。
14. **openapi2ts 的生成来源是本分支后端**（`git diff` 基线：agent 恢复四端点、
   `RecoveryStateVO.activeExecution`、`ActionReviewRequest`、SSE 类型修正、
   `pictureContentController.ts` 补齐）。生成时后端跑在临时隔离库上，
   未调用任何付费服务；未验证生成客户端在真实部署契约（application-prod）下的差异。
