-- #38 行为统计与推荐链路正确性：事件幂等键与持久领取状态（前向迁移）
--
-- 只改结构与索引，不回填存量事件，也不改动任何已有计数：
--   * eventId      事件幂等键。存量行保持 NULL，唯一索引允许多个 NULL，
--                  因此存量事件既不会被合并，也不会被丢弃。
--   * isAccumulated 已计入统计的标记。与 isProcessed 一起构成
--                  「待处理 -> 已领取 -> 已计入 -> 已完成」的可恢复状态机。
--   * claimToken / claimedAt / claimExpireAt 领取租约。租约到期后其他实例
--                  可以重新领取，崩溃的实例不会永久占住事件。
--   * attemptCount 领取尝试次数。反复失败的事件据此可见，形成积压清单。
--
-- 执行前提：维护窗口内、已有备份、且应用已停止写入 user_picture_action。
-- 回滚：执行同名 -rollback.sql，并把应用回滚到不依赖这些列的版本。
-- 存量事件的状态说明与冲突清单见同名 -maintenance.sql。

ALTER TABLE `user_picture_action`
  ADD COLUMN `eventId` varchar(128) NULL DEFAULT NULL COMMENT '事件幂等键: 唯一索引保证同一事件重放不重复入账' AFTER `source`,
  ADD COLUMN `isAccumulated` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否已计入统计: 0-未计入 1-已计入' AFTER `isProcessed`,
  ADD COLUMN `claimToken` varchar(64) NULL DEFAULT NULL COMMENT '领取令牌: 持有本次领取租约的聚合运行' AFTER `isAccumulated`,
  ADD COLUMN `claimedAt` datetime NULL DEFAULT NULL COMMENT '领取时间' AFTER `claimToken`,
  ADD COLUMN `claimExpireAt` datetime NULL DEFAULT NULL COMMENT '领取租约到期时间' AFTER `claimedAt`,
  ADD COLUMN `attemptCount` int(11) NOT NULL DEFAULT 0 COMMENT '领取尝试次数: 反复领取失败的事件据此可见' AFTER `claimExpireAt`,
  ADD UNIQUE INDEX `uk_eventId` (`eventId`) USING BTREE,
  ADD INDEX `idx_claim` (`isProcessed`, `isAccumulated`, `claimExpireAt`) USING BTREE;
