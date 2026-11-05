-- #42 用户长期偏好（回滚迁移）
--
-- 回滚到进程内 MemoryStore 版本时，旧版本应用不再需要 user_preference 表，
-- 但升级后已经积累的偏好数据不能随表一起删掉：
--   * 显式设置是用户直接编辑的内容；
--   * 学习产物、反馈历史、自动学习开关与版本/纪元是用户控制状态的唯一落库形态；
--   * 清除过的行是挡住在途学习写回的墓碑，删掉会让「已清除」失去凭据。
--
-- 因此本脚本把业务表重命名为归档表 `user_preference_rollback_archive` 的等价形态：
-- 先按业务表结构建归档表、把全部行搬过去、校验逐行覆盖，再删业务表。
-- 归档保留全部行与列（含已清除的 NULL 内容），归档表不被旧版本应用读取，
-- 也不重新公开任何已清除的数据。
--
-- 重新部署本工单时，前向脚本在归档表存在时把数据原样搬回业务表再删除归档；
-- 搬回后用户的显式设置、学习开关、版本与纪元保持原值，已清除的内容仍为空。
-- 归档/恢复期间应用必须停止写入偏好表（维护窗口）。
--
-- 存量数据迁移的诚实声明不变：旧实现的进程内 MemoryStore 没有磁盘形态，
-- 本脚本不虚构任何旧值；它保留的只是升级后确实已经落库的数据。
--
-- 脚本可重复执行：业务表不存在时（已回滚过）幂等退出，不碰已有归档。
-- 归档不完整时守卫表会阻断，业务表不会被删；处置后重跑即可。
-- 脚本必须在「遇错即停」的客户端执行（mysql 命令行默认行为、JDBC 逐条 execute），
-- 不能加 --force。

-- 连接字符集显式设为 utf8mb4：本脚本含中文注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `vs42_rollback_preference`;

DELIMITER $$
CREATE PROCEDURE `vs42_rollback_preference`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_preference'
  ) THEN
    -- 1. 归档表与业务表同结构；已存在时复用（上一次被阻断的脚本可能留下它）
    CREATE TABLE IF NOT EXISTS `user_preference_rollback_archive` LIKE `user_preference`;

    -- 2. 以业务表最新状态为准补齐归档：不能只用 INSERT IGNORE，那样两次回滚
    --    之间改过的行会在归档里保留旧快照
    INSERT INTO `user_preference_rollback_archive`
      (`id`, `userId`, `explicitContent`, `learnedContent`, `feedbackHistory`,
       `autoLearn`, `preferenceVersion`, `dataEpoch`, `learnError`, `createTime`, `updateTime`)
    SELECT `id`, `userId`, `explicitContent`, `learnedContent`, `feedbackHistory`,
           `autoLearn`, `preferenceVersion`, `dataEpoch`, `learnError`, `createTime`, `updateTime`
    FROM `user_preference` AS live
    ON DUPLICATE KEY UPDATE
      `explicitContent` = live.`explicitContent`,
      `learnedContent` = live.`learnedContent`,
      `feedbackHistory` = live.`feedbackHistory`,
      `autoLearn` = live.`autoLearn`,
      `preferenceVersion` = live.`preferenceVersion`,
      `dataEpoch` = live.`dataEpoch`,
      `learnError` = live.`learnError`,
      `createTime` = live.`createTime`,
      `updateTime` = live.`updateTime`;

    -- 3. 校验：业务表每一行都必须在归档里有同一 id，否则不删表
    CREATE TABLE IF NOT EXISTS `_user_preference_restore_guard` (
      `ok` tinyint NOT NULL,
      CONSTRAINT `BLOCKED_user_preference_archive_incomplete` CHECK (`ok` = 1)
    ) ENGINE = InnoDB COMMENT = '#42 回滚守卫：仅脚本执行期间存在，成功回滚后自动删除';

    INSERT INTO `_user_preference_restore_guard` (`ok`)
    VALUES (IF(EXISTS (
      SELECT 1 FROM `user_preference` AS live
      WHERE NOT EXISTS (
        SELECT 1 FROM `user_preference_rollback_archive` AS archived
        WHERE archived.`id` = live.`id`
      )
    ), 0, 1));

    -- 4. 归档完整后才移除旧版本不再使用的业务表
    DROP TABLE IF EXISTS `user_preference`;
    DROP TABLE IF EXISTS `_user_preference_restore_guard`;
  END IF;
END$$
DELIMITER ;

CALL `vs42_rollback_preference`();
DROP PROCEDURE IF EXISTS `vs42_rollback_preference`;
