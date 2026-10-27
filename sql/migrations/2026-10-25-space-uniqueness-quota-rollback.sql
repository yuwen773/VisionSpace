-- #37 空间唯一性、配额与文件一致性：回滚
--
-- 只移除本工单新增的结构：
--   1. 删除 space 上的唯一索引 uk_space_active_key 与 activeKey 列；
--   2. 删除 storage_cleanup_task。
--
-- 回滚不改变任何空间、图片或资源记录：activeKey 只是唯一占位，删掉它不会
-- 让已被拒绝的重复创建重新出现，也不会解除任何已有的数据约束。
--
-- 回滚前必须先把 storage_cleanup_task 里仍待处理（status=0）与等待人工处理
-- （status=2）的任务处理完：删表等于丢掉「还没删掉的对象」这份事实，
-- 之后没有任何地方能证明这些对象需要清理。存在未收敛任务时脚本主动失败，
-- 与 #36 一样用带 CHECK 约束的守卫表阻断（MySQL 不支持在 PREPARE 里 SIGNAL）。
--
-- 脚本可重复执行；被阻断时守卫表留在库里（无数据），处置完成后重跑即可。

SET NAMES utf8mb4;

-- ---------- 0. 回滚守卫 ----------

SET @pending_tasks = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'storage_cleanup_task'
);
SET @unresolved_tasks = IF(@pending_tasks = 0, 0,
  (SELECT COUNT(*) FROM `storage_cleanup_task` WHERE `status` IN (0, 2)));

CREATE TABLE IF NOT EXISTS `_space_quota_rollback_guard` (
  `ok` tinyint NOT NULL,
  CONSTRAINT `BLOCKED_resolve_storage_cleanup_tasks_before_rollback` CHECK (`ok` = 1)
) ENGINE = InnoDB COMMENT = '#37 回滚守卫：仅脚本执行期间存在，成功回滚后自动删除';

INSERT INTO `_space_quota_rollback_guard` (`ok`) VALUES (IF(@unresolved_tasks > 0, 0, 1));

-- ---------- 1. storage_cleanup_task ----------

DROP TABLE IF EXISTS `storage_cleanup_task`;

-- ---------- 2. space.activeKey ----------

SET @has_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'space'
    AND INDEX_NAME = 'uk_space_active_key'
);
SET @ddl = IF(@has_index > 0,
  'ALTER TABLE `space` DROP INDEX `uk_space_active_key`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'space' AND COLUMN_NAME = 'activeKey'
);
SET @ddl = IF(@has_column > 0,
  'ALTER TABLE `space` DROP COLUMN `activeKey`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `_space_quota_rollback_guard`;
