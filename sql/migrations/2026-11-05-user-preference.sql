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
-- 不存在可从磁盘恢复的存量数据。首次升级不回填任何旧值，也不把进程内
-- 旧值虚构为已迁移；用户从空偏好开始，显式设置由用户重新录入。
--
-- 回滚与重新部署：同名 -rollback.sql 把本表的数据搬进归档表
-- `user_preference_rollback_archive` 后删除业务表，旧版本应用退回进程内
-- MemoryStore，但已经落库的偏好不随应用降级丢失。再次执行本脚本时，
-- 若归档表存在，会把归档行原样搬回新建的业务表（用户设置、开关、版本与
-- 纪元保持原值，已清除的内容仍为空），再删除归档表。归档/恢复期间应用
-- 必须停止写入偏好表（维护窗口）。脚本可重复执行。
--
-- 脚本必须在「遇错即停」的客户端执行（mysql 命令行默认行为、JDBC 逐条 execute），
-- 不能加 --force。

-- 连接字符集显式设为 utf8mb4：本脚本含中文注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

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

DROP PROCEDURE IF EXISTS `vs42_restore_preference`;

DELIMITER $$
CREATE PROCEDURE `vs42_restore_preference`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_preference_rollback_archive'
  ) THEN
    -- 只在业务表为空时搬回：非空说明应用已经开始写入，归档与在线数据不能合并。
    -- 这种情况下守卫表阻断，归档留在库里供人工核对，不影响在线业务。
    CREATE TABLE IF NOT EXISTS `_user_preference_restore_guard` (
      `ok` tinyint NOT NULL,
      CONSTRAINT `BLOCKED_user_preference_live_table_not_empty` CHECK (`ok` = 1)
    ) ENGINE = InnoDB COMMENT = '#42 恢复守卫：仅脚本执行期间存在，成功恢复后自动删除';

    INSERT INTO `_user_preference_restore_guard` (`ok`)
    VALUES (IF((SELECT COUNT(*) FROM `user_preference`) = 0, 1, 0));

    INSERT INTO `user_preference`
      (`id`, `userId`, `explicitContent`, `learnedContent`, `feedbackHistory`,
       `autoLearn`, `preferenceVersion`, `dataEpoch`, `learnError`, `createTime`, `updateTime`)
    SELECT `id`, `userId`, `explicitContent`, `learnedContent`, `feedbackHistory`,
           `autoLearn`, `preferenceVersion`, `dataEpoch`, `learnError`, `createTime`, `updateTime`
    FROM `user_preference_rollback_archive`;

    -- 校验：归档的每一行都必须已经回到业务表，否则不删归档
    INSERT INTO `_user_preference_restore_guard` (`ok`)
    VALUES (IF(EXISTS (
      SELECT 1 FROM `user_preference_rollback_archive` AS archived
      WHERE NOT EXISTS (
        SELECT 1 FROM `user_preference` AS live WHERE live.`id` = archived.`id`
      )
    ), 0, 1));

    DROP TABLE IF EXISTS `user_preference_rollback_archive`;
    DROP TABLE IF EXISTS `_user_preference_restore_guard`;
  END IF;
END$$
DELIMITER ;

CALL `vs42_restore_preference`();
DROP PROCEDURE IF EXISTS `vs42_restore_preference`;
