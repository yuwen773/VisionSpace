-- #38 行为统计与推荐链路正确性：事件幂等键与持久领取状态（回滚）
--
-- 回滚顺序有要求，先收敛状态再删列：
--   1. 已计入统计但未标记完成的事件（isProcessed=0 且 isAccumulated=1）必须先
--      标记为已处理。旧版本只认 isProcessed，若不先收敛，这些已经入账的事件
--      会被旧的时间窗聚合再算一次，造成重复计数。
--   2. 再删除本工单新增的列与索引。存量行与 picture_stats 的计数不受影响。
--
-- 回滚后的事件语义回到旧版时间窗：已领取但未入账、且早于旧时间窗的事件
-- 不会再被旧版本消费，属于旧版已知缺口，需要按维护清单人工处置。

UPDATE `user_picture_action` SET `isProcessed` = 1 WHERE `isProcessed` = 0 AND `isAccumulated` = 1;

ALTER TABLE `user_picture_action`
  DROP INDEX `uk_eventId`,
  DROP INDEX `idx_claim`,
  DROP COLUMN `eventId`,
  DROP COLUMN `isAccumulated`,
  DROP COLUMN `claimToken`,
  DROP COLUMN `claimedAt`,
  DROP COLUMN `claimExpireAt`,
  DROP COLUMN `attemptCount`;
