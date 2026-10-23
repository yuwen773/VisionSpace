# #41 Agent 完整恢复与副作用安全 — 设计与验收映射

工单：#41；父规格：#26；基线：`631c3af`（origin/main，含 #31 `12ef2c2`、#33 `e2d3049`）。

## 现有执行状态与故障窗口（实施前梳理）

| 位置 | 现状 | 故障窗口 |
| --- | --- | --- |
| `DualCheckpointSaver#put` | Redis 主写 + 异步 MySQL 轻量备份（每 3 步） | 备份无版本：**旧异步备份晚到会覆盖新状态**（25.3） |
| `DualCheckpointSaver#get` | Redis 缺失时用 MySQL 轻量归档**重建** Checkpoint | 轻量归档只有 messages，**被伪装成完整恢复点**（25.4）；且未校验归属 |
| `DualCheckpointSaver#release` | 直接转 RedisSaver | `stepCounters` 无界增长，**释放后不清理**（25.3） |
| 备份失败 | 仅 `log.warn` | **不可见、不可重试**（25.3） |
| 工具执行 | 无任何动作记录 | 崩溃后**无法判断外部动作是否已发生**；重复调用会重放（26.1/26.2） |
| 恢复入口 | 不存在（#31 已注明） | 无受限恢复流程（26.3/26.4） |
| 人工确认 | `HumanInTheLoopHook` 中断后仅存 messages | 待确认阶段/参数**未持久化**，重启后无法区分"待确认"与"已完成"（25.1） |

## 最小状态模型

### `agent_run_state`（完整执行状态，版本化）

- `threadId` + `userId`（归属，唯一键 `(threadId)`）
- `stateVersion` BIGINT：单调递增，**CAS 写入** `WHERE stateVersion < ?`，旧备份无法覆盖新版本
- `stateFormat` varchar：状态格式版本（当前 `v2-full`），用于兼容迁移
- `checkpointId` / `nodeId` / `nextNodeId`：完整恢复点（不是仅消息）
- `statePayload` mediumblob：完整 OverAllState 序列化（gzip），**不是**轻量消息
- `runStatus`：`RUNNING` / `AWAITING_CONFIRMATION` / `COMPLETED` / `FAILED` / `PAUSED_UNRECOVERABLE`
- `pendingConfirmation` text：人工确认信息（工具名、参数摘要、中断点），脱敏
- `backupError` / `backupAttempts`：备份失败**可见可重试**
- `legacyArchiveOnly`：旧轻量归档标记——可查看历史，**不可续跑**

### `agent_action_record`（动作台账）

- `actionId`（=`threadId` + `toolCallId` 派生，稳定可核对）、`threadId`、`userId`
- `toolName`、`argumentsDigest`（**摘要，不存原文**，审计不泄露私人正文）
- `status`：`EXECUTING` / `SUCCEEDED` / `FAILED` / `UNKNOWN_NEEDS_REVIEW`
- `idempotency`：`PROVEN` / `NOT_PROVEN`（只有 PROVEN 才允许在结果未知时重试）
- `attempt`、`resultSummary`（截断摘要）、`createdTime` / `updatedTime`
- 唯一键 `(threadId, actionId)`：重复登记是空操作

## 兼容与回滚

- `stateFormat` 未知/过旧 → **不重建**，标记 `PAUSED_UNRECOVERABLE` 并明确暂停（不伪造恢复成功）。
- 旧 `checkpointData` 轻量归档保留只读展示路径；恢复入口拒绝把它当完整状态。
- 回滚：新表独立，删除新表/回退代码即恢复旧行为；旧列 `checkpointData` 不删除。

## 逐项验收映射

| 验收项 | 实现 | 测试 |
| --- | --- | --- |
| 25.1 持久保存完整状态/版本/确认信息，读取恢复经归属校验 | `AgentRunStateStore` + `AgentSessionGuard` | `AgentRunStateStoreTest`、`HttpAgentRecoveryTest` |
| 25.2 确认前后/推进阶段故障恢复正确，跨实例一致 | 版本化全量状态 + CAS | `DualCheckpointSaverTest`、`HttpAgentRecoveryTest` |
| 25.3 旧备份晚到不覆盖；失败可见可重试；释放清理计数 | CAS `stateVersion`；`backupError`；`release` 清理 | `DualCheckpointSaverTest` |
| 25.4 旧轻量归档可查看但不可伪装续跑 | `legacyArchiveOnly` + 恢复入口拒绝 | `DualCheckpointSaverTest`、`HttpAgentRecoveryTest` |
| 25.5 格式兼容/脱敏迁移/安全回滚；不宣称恰好一次 | `stateFormat` + 脱敏摘要 | `AgentRunStateStoreTest` |
| 26.1 记录动作标识/状态/结果，已完成不重放 | `AgentActionLedger` + 拦截器 | `AgentActionLedgerTest` |
| 26.2 外部完成本地未提交窗口；仅可证明幂等才重试 | `EXECUTING`→`UNKNOWN_NEEDS_REVIEW`；`idempotency` 门 | `AgentActionInterceptorTest` |
| 26.3 UI 区分未知与明确失败，有权用户核对恢复 | `MessageType.ACTION_UNKNOWN` + 前端 | `agentRecovery.spec.ts` |
| 26.4 重复/过期/旧恢复请求不重复执行；他人不可续跑 | 乐观版本 + `AgentSessionGuard` + 动作状态机 | `HttpAgentRecoveryTest` |
| 26.5 审计无秘密/私人正文；以调用次数与最终状态验证 | 摘要存储 + `ExternalCallRecorder` | `HttpAgentRecoveryTest` |
