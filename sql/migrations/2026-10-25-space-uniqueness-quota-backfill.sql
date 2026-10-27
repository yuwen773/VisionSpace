-- #37 存量有效空间 activeKey 回填（维护窗口内执行）
--
-- 前向迁移只加列与唯一索引，不碰存量数据：存量行 activeKey 为 NULL，唯一索引
-- 只约束非空值，因此加索引本身不会替人工"选中"任何一个空间。
--
-- 回填才是会改变数据的那一步，所以单独成脚本：
--   1. 存在同一 (userId, spaceType) 的多条有效空间时主动失败，不自动选择、合并
--      或删除其中任何一条。处置方式与顺序见同名 -blocklist.sql 的第 1 节。
--   2. 没有重复时，把每条有效空间的 activeKey 写成 userId:spaceType；已删除的
--      空间保持 NULL，历史墓碑不占位，之后可以重新创建同类空间。
--
-- 执行前提：维护窗口内、已有备份、应用停止写入 space。
-- 脚本可重复执行；被阻断时守卫表留在库里（无业务数据），处置完成后重跑即可。
-- 回滚：本脚本只改 activeKey，随同名 -rollback.sql 一起撤销（回滚会删掉该列）。

SET NAMES utf8mb4;

-- ---------- 0. 回填守卫 ----------

CREATE TABLE IF NOT EXISTS `_space_quota_backfill_guard` (
  `ok` tinyint NOT NULL,
  CONSTRAINT `BLOCKED_resolve_duplicate_valid_spaces_before_backfill` CHECK (`ok` = 1)
) ENGINE = InnoDB COMMENT = '#37 回填守卫：仅脚本执行期间存在，成功后自动删除';

SET @duplicated_groups = (
  SELECT COUNT(*) FROM (
    SELECT `userId`, `spaceType`
    FROM `space`
    WHERE `isDelete` = 0
    GROUP BY `userId`, `spaceType`
    HAVING COUNT(*) > 1
  ) d
);

-- 有重复时写入违反 CHECK 约束的值，脚本在改动任何数据之前失败
INSERT INTO `_space_quota_backfill_guard` (`ok`) VALUES (IF(@duplicated_groups > 0, 0, 1));

-- ---------- 1. 回填 ----------

UPDATE `space`
SET `activeKey` = CONCAT(`userId`, ':', `spaceType`)
WHERE `isDelete` = 0
  AND (`activeKey` IS NULL OR `activeKey` <> CONCAT(`userId`, ':', `spaceType`));

-- ---------- 2. 收尾 ----------

DROP TABLE IF EXISTS `_space_quota_backfill_guard`;
