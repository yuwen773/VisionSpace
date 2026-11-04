-- #42 用户可控制的持久长期偏好（前向迁移）
--
-- 偏好从进程内 MemoryStore 迁移到数据库：
--   * user_preference 每个用户一行（uk_userId），跨重启与双实例读取一致。
--   * explicitContent  显式设置（用户在界面上直接编辑），自动学习永远不改写它；
--                      加载时显式键覆盖同名的推断键——「显式设置优先于模型推断」。
--   * learnedContent   自动学习产物（反馈关键词推断），可被用户清除。
--   * feedbackHistory  反馈历史（条数上限由 vision-space.agent.maxFeedbackHistorySize 控制）。
--   * autoLearn        自动学习开关，默认 0（关闭）：自动学习只在用户明确开启后发生。
--   * preferenceVersion 单调递增版本号，学习写入按版本 CAS；
--   * dataEpoch        数据纪元：用户每一次控制操作（修改/清除/开关）都递增。
--                      在途学习任务持有读取时的 (version, epoch)，清除或关闭后
--                      两个条件都不再成立——旧任务写不回已清除的数据，也不会在
--                      重读后被「重放」为一次新的学习。
--   * learnError       最近一次自动学习放弃的原因（短码，不含正文）。学习写入
--                      连续版本冲突被放弃时写入；下一次学习成功或用户的任何控制
--                      操作都会清空。与 #41 agent_run_state.backupError 同一纪律：
--                      失败必须可观察、可清除，不静默吞掉。
--
-- 存量数据迁移的诚实声明：旧实现把偏好放在进程内 MemoryStore
-- （AgentStoreConfig#memoryStore，无任何磁盘形态），实例停止即丢失，
-- 不存在可从磁盘恢复的存量数据。本脚本不做任何数据回填，也
-- 不把进程内旧值虚构为已迁移；升级后所有用户从空偏好开始，
-- 显式设置由用户重新录入，自动学习在重新开启后按新规则积累。
--
-- 回滚：执行同名 -rollback.sql 后回滚应用。回滚只删本表；
-- 回滚后新积累的偏好留在数据库里不随应用降级丢失，但旧版本
-- 应用读不到它们（继续用进程内 MemoryStore）。

CREATE TABLE IF NOT EXISTS `user_preference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userId` bigint(20) NOT NULL COMMENT '偏好归属用户；全部读写条件都带它',
  `explicitContent` text NULL COMMENT '显式设置 JSON（用户直接编辑，优先于推断）',
  `learnedContent` text NULL COMMENT '自动学习 JSON（反馈推断，可清除）',
  `feedbackHistory` text NULL COMMENT '反馈历史 JSON（条数有上限）',
  `autoLearn` tinyint(4) NOT NULL DEFAULT 0 COMMENT '自动学习开关: 0-关闭（默认） 1-开启',
  `preferenceVersion` bigint(20) NOT NULL DEFAULT 0 COMMENT '偏好版本，单调递增，学习写入按它 CAS',
  `dataEpoch` bigint(20) NOT NULL DEFAULT 0 COMMENT '数据纪元，用户控制操作递增，旧学习任务据此失效',
  `learnError` varchar(200) NULL DEFAULT NULL COMMENT '最近一次学习放弃原因（短码）；学习成功或用户控制操作时清空',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_userId`(`userId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户长期偏好（显式设置 + 自动学习）';
