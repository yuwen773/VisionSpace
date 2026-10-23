# #41 Agent 完整恢复与副作用安全

工单：[41](https://github.com/yuwen773/VisionSpace/issues/41)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
基线：`631c3af`（origin/main，含 #31 `12ef2c2`、#33 `e2d3049`）。分支：`codex/41-agent-safe-recovery`。
设计文档：`docs/plans/2026-10-23-41-agent-safe-recovery-design.md`。

本文只记录本次实际执行到的证据；没有执行的部分单列在「未验证与阻断项」，不计入通过。

## 状态与恢复契约

| 概念 | 存储 | 契约 |
| --- | --- | --- |
| 完整执行状态 | `agent_run_state`（唯一键 `threadId`） | `stateVersion` 单调递增，写入 `WHERE stateVersion < ?`；迟到的旧备份影响 0 行 |
| 状态格式 | `stateFormat` | `v2-full` 可续跑；`v1-lightweight`（仅消息）与未知格式**不可续跑** |
| 恢复点 | `checkpointId` / `nodeId` / `nextNodeId` | 缺 `nextNodeId` 时 `toCheckpoint` 返回空，不把「有消息」当「能续跑」 |
| 运行状态 | `runStatus` | `RUNNING` / `AWAITING_CONFIRMATION` / `COMPLETED` / `FAILED` / `PAUSED_UNRECOVERABLE` |
| 人工确认信息 | `pendingConfirmation` | 只存工具名与脱敏描述；随完整状态一起持久化 |
| 备份失败 | `backupError` / `backupAttempts` | 可见、可重试；下次成功写入即清零 |
| 外部动作 | `agent_action_record`（唯一键 `(threadId, actionId)`） | 状态机见下；`SUCCEEDED` 是终态，后续写入不改写 |
| 幂等能力 | `idempotency` | 只有 `PROVEN` 才允许「结果未知」时退回可重试 |
| 审计 | `argumentsDigest` / `resultSummary` | 只存 sha-256 与截断摘要，不含正文 |

动作状态机：

```
(无) --begin--> EXECUTING
EXECUTING --成功--> SUCCEEDED            （终态，不再执行）
EXECUTING --工具明确报错--> FAILED        （外部未成功，可安全重试）
EXECUTING --异常/结果未知 + PROVEN--> FAILED       （可安全重试）
EXECUTING --异常/结果未知 + NOT_PROVEN--> UNKNOWN_NEEDS_REVIEW
UNKNOWN_NEEDS_REVIEW --人工确认已发生--> SUCCEEDED
UNKNOWN_NEEDS_REVIEW --人工确认未发生--> FAILED
```

本工单**不承诺「恰好一次」**。它保证的是：不会因为重放而重复产生外部副作用，
且结果未知时不会把「可能已发生」当成「没发生」去重试。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `agent/recovery/AgentRunStateStore` | 完整状态的 gzip 序列化、版本 CAS 写入、归属条件读写、恢复点还原、备份失败记录 |
| `agent/recovery/AgentActionLedger` | 动作台账状态机；`actionId`/参数摘要派生；`reconcileStaleExecuting` 收敛上次中断的 `EXECUTING` |
| `agent/recovery/AgentActionInterceptor` | 工具执行边界：已完成跳过、成功/失败/未知分别落账；非幂等工具结果未知时抛 `PendingReviewException` 暂停执行 |
| `agent/recovery/AgentRecoveryService` | 受限恢复：归属校验、版本校验、待核对阻断、人工核对入口 |
| `agent/config/DualCheckpointSaver` | `get` 不再用 MySQL 轻量归档伪造恢复点；`put` 同步写完整状态（带单调版本）；`release` 清理计数与载荷 |
| `agent/ImageAgent` | 持久化人工确认中断；续跑入口 `resume`；`PendingReviewException` → `ACTION_UNKNOWN` 事件 |
| `controller/AgentController` | `GET /agent/recovery/{threadId}`、`POST .../resume`、`POST .../action/confirm`、`POST .../action/dismiss` |
| `frontend` | `RecoveryBanner`（核对/续跑/不可恢复）、`ActionUnknownMessage`、`useAgentStream` 的 `action-unknown` 分支 |
| `sql/migrations/2026-10-23-agent-run-state.sql` | 前向迁移（两张新表）与同名 `-rollback.sql` |

## 逐项验收

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 25.1 持久保存完整状态、版本、确认信息；读取与恢复经归属校验 | `AgentRunStateStoreTest`（11 条：CAS、归属条件、格式不可识别、缺恢复点）；`HttpAgentRecoveryTest#recoveryStateRejectsStrangersAndAdmins`、`#strangerCannotResumeAndNothingExecutes`；`AgentRunStateMapper` 所有语句带 `userId` | PASS |
| 25.2 确认前后、外部执行前后、本地提交前后的故障窗口；跨实例一致 | `reconcileStaleExecuting`（`EXECUTING` 遗留 → 可证幂等退回 `FAILED`，否则 `UNKNOWN_NEEDS_REVIEW`）；`DualCheckpointSaverTest#putPersistsFullStateSynchronouslyWithIncreasingVersions`；`HttpAgentRecoveryTest#concurrentWritersFromTwoInstancesProduceOneWinner`、`#staleExecutingActionIsReconciledNotReplayed` | PASS（单机双连接，未起第二实例进程） |
| 25.3 旧异步备份晚到不覆盖；备份失败可见可重试；释放后清理计数 | `AgentRunStateMapper#updateIfNewer`（`WHERE stateVersion < ?`）；`DualCheckpointSaverTest#releaseClearsRunStateAndPerThreadCounters`；`AgentRunStateStoreTest#backupFailureIsRecordedSoItIsVisibleAndRetryable`；`HttpAgentRecoveryTest#lateOlderBackupLosesTheVersionRace`、`#backupFailureIsVisibleAndRetryClearsIt`、`#releaseClearsPayloadAndRecoveryPoint` | PASS |
| 25.4 旧轻量归档可继续按权限查看，但不得伪装成可恢复 | `DualCheckpointSaver#get` 移除 MySQL 回填；`loadLegacyArchiveMessages` 只读；`AgentRunStateStoreTest#legacyLightweightArchiveIsNotResumable`、`#fullStateWithoutRecoveryPointCannotBeTurnedIntoCheckpoint`；`DualCheckpointSaverTest#normalStartDoesNotResurrectLegacyArchiveAsResumePoint`；`HttpAgentRecoveryTest#legacyArchiveOnlyIsReadableButNotResumable`、`#resumeWithoutAnyStateIsPausedInSteadOfFakingRecovery` | PASS |
| 25.5 格式兼容迁移与安全回滚；不宣称恰好一次 | `AgentStateFormat.fromCode` 未知格式 → 不可续跑并明确暂停；迁移 SQL 独立建表、回滚 SQL 只删新表、`checkpointData` 不改；`.get` 文档与本文明确「不承诺恰好一次」 | PASS（迁移脚本只在验收临时库执行，未在真实库运行） |
| 26.1 记录动作标识/状态/结果；已确认完成不得重放 | `AgentActionLedgerTest`（8 条）；`AgentActionInterceptorTest#alreadyCompletedActionProducesNoExternalCall`（外部调用次数 = 0） | PASS（受控工具替身） |
| 26.2 外部完成、本地未提交窗口；只有可证明幂等才允许安全重试 | `AgentActionInterceptorTest#exceptionOnNonIdempotentToolPausesForReviewInsteadOfRetrying`、`#exceptionOnIdempotentToolPropagatesAsRetryableFailure`、`#toolLevelErrorIsExplicitFailureAndRetryable` | PASS |
| 26.3 前端区分明确失败与结果未知，有权用户可核对并恢复 | `agentRecovery.spec.ts`（8 条：待核对不给一键重试、可续跑才显示续跑、旧归档明确不可续跑、备份失败可见、`action-unknown` 不并入助手消息、JSON 拒绝视为失败）；`RecoveryBanner` 逐条「已执行/未执行」 | PASS（单元/组件层，无浏览器 E2E） |
| 26.4 重复核对、过期请求、旧版本请求不重复执行；他人/管理员不可续跑 | `HttpAgentRecoveryTest#repeatedReviewIsRejectedAndOnlyTheFirstOneCounts`、`#staleResumeVersionIsRejected`、`#pendingReviewBlocksResumeUntilReviewed`、`#strangerCannotReviewActionsOwnedBySomeoneElse`、`#recoveryStateRejectsStrangersAndAdmins` | PASS |
| 26.5 审计与错误不泄露私人正文或秘密；以受控外部调用次数与最终状态为证 | 参数只存 sha-256（`AgentActionLedgerTest#argumentsAreStoredAsDigestNotAsRawText`）；结果摘要截断 80/200 字符；失败原因只存异常类型（`AgentRunStateStoreTest#failureReasonDoesNotCarryRawContent`、`HttpAgentRecoveryTest#backupFailureIsVisibleAndRetryClearsIt` 断言不含路径）；拒绝路径用 `ExternalCallRecorder` 断言零外部调用 | PASS |

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

- 后端 314 条测试 0 失败，其中本工单新增 53 条：
  `AgentRunStateStoreTest` 13、`AgentActionLedgerTest` 9、`AgentActionInterceptorTest` 7、
  `DualCheckpointSaverTest` 8、`HttpAgentRecoveryTest` 16。
- 前端 33 条测试（本工单新增 `agentRecovery.spec.ts` 9 条），类型检查与生产构建通过。

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

## 未验证与阻断项

1. **双实例竞争是单机双连接验证的。** `concurrentWritersFromTwoInstancesProduceOneWinner`
   用两个线程各持独立版本打同一行，证明 CAS 语义；没有启动第二个 Spring Boot 进程，
   因此 Redis/MySQL 的跨进程时序未实测。
2. **真实模型下的工具重放验证未做。** 验收环境的模型与工具地址指向本地记录器，
   Agent 循环跑不到工具节点。26.1/26.2 由 `AgentActionInterceptor` 的受控工具替身证明，
   没有在真实 DashScope + 真实图片生成上验证「确认完成后不重放」。
3. **迁移脚本未在真实库执行。** 只随验收临时库建过表；生产迁移与回滚未执行（本会话不授权）。
4. **浏览器 E2E 未做。** `RecoveryBanner` 与 `ActionUnknownMessage` 有组件级测试，
   没有真实浏览器截图或交互证据。
5. **旧轻量归档的展示路径未接 UI。** `loadLegacyArchiveMessages` 已提供只读入口，
   当前前端仍从 `agent_message` 读历史，该方法的调用方尚未接入。
6. **双实例续跑未做真实进程验证。** 版本认领的并发用例在单进程内用两个连接验证；
   没有启动第二个 Spring Boot 进程交叉续跑。
7. **`reconcileStaleExecuting` 的宽限期（10 分钟）是经验值。** 超过该时长的
   长耗时工具调用仍可能被误判为中断；这类工具本身不可证明幂等，误判只会
   进入待核对，不会重放，但会给用户多一次核对。
