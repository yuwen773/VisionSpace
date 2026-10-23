-- #41 Agent 完整恢复与副作用安全：执行状态与动作台账（前向迁移）
--
-- 只创建结构，不回填存量 agent_session.checkpointData。旧轻量归档继续按归属
-- 只读展示，但不会被当作完整恢复点（见 docs/progress/41-agent-safe-recovery.md）。
--
-- 两件事分开表达：
--   agent_run_state     —— 可继续执行的完整状态（版本化，CAS 写入）
--   agent_action_record —— 外部动作台账（已确认动作不重放，未知结果待核对）
--
-- 不使用全局 isDelete：这两张表需要保留历史用于核对与重试，查询一律显式带状态条件。
--
-- 执行前提：维护窗口内、已有备份、应用停止写入 agent_run_state / agent_action_record。
-- 回滚：执行同名 -rollback.sql；应用回滚到不含恢复入口的版本。
-- 旧 agent_session 表与 checkpointData 列保持不变，回滚不需要数据迁移。

CREATE TABLE IF NOT EXISTS `agent_run_state` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `threadId` varchar(64) NOT NULL COMMENT '会话 id',
  `userId` bigint(20) NOT NULL COMMENT '会话归属用户（读取与恢复的唯一依据）',
  `stateVersion` bigint(20) NOT NULL DEFAULT 0 COMMENT '状态版本，单调递增；写入按它做 CAS',
  `stateFormat` varchar(32) NOT NULL COMMENT '状态格式版本，例如 v2-full',
  `checkpointId` varchar(128) NULL DEFAULT NULL COMMENT '完整恢复点 id',
  `nodeId` varchar(128) NULL DEFAULT NULL COMMENT '已完成的节点',
  `nextNodeId` varchar(128) NULL DEFAULT NULL COMMENT '下一步应执行的节点',
  `statePayload` mediumblob NULL COMMENT '完整状态（gzip 后的序列化数据），不是仅消息',
  `runStatus` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/AWAITING_CONFIRMATION/COMPLETED/FAILED/PAUSED_UNRECOVERABLE',
  `pendingConfirmation` text NULL COMMENT '待人工确认信息（已脱敏）',
  `legacyArchiveOnly` tinyint(4) NOT NULL DEFAULT 0 COMMENT '1=仅有旧轻量归档，可查看但不可续跑',
  `backupError` varchar(255) NULL DEFAULT NULL COMMENT '最近一次备份失败原因（不含正文/秘密）',
  `backupAttempts` int(11) NOT NULL DEFAULT 0 COMMENT '备份尝试次数',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_thread`(`threadId`) USING BTREE,
  INDEX `idx_user_status`(`userId`, `runStatus`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 完整执行状态';

CREATE TABLE IF NOT EXISTS `agent_action_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `threadId` varchar(64) NOT NULL COMMENT '会话 id',
  `userId` bigint(20) NOT NULL COMMENT '会话归属用户',
  `actionId` varchar(128) NOT NULL COMMENT '可核对的动作标识（threadId + toolCallId 派生）',
  `toolName` varchar(128) NOT NULL COMMENT '工具名',
  `argumentsDigest` varchar(128) NULL DEFAULT NULL COMMENT '参数摘要（sha-256），不保存正文',
  `status` varchar(32) NOT NULL COMMENT 'EXECUTING/SUCCEEDED/FAILED/UNKNOWN_NEEDS_REVIEW',
  `idempotency` varchar(16) NOT NULL DEFAULT 'NOT_PROVEN' COMMENT 'PROVEN 才允许结果未知时重试',
  `attempt` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `resultSummary` varchar(255) NULL DEFAULT NULL COMMENT '结果摘要（截断，不含正文）',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_thread_action`(`threadId`, `actionId`) USING BTREE,
  INDEX `idx_thread_status`(`threadId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 外部动作台账';
