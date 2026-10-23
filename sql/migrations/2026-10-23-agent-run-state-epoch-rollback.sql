-- #41 第三轮修复的回滚：只移除本轮新增的执行世代列。
--
-- 回滚不删表、不动 agent_action_record：完整状态与动作证据全部保留。
-- 回滚后旧版本应用不再按世代拒绝迟到写入，退回纯版本 CAS 的行为，
-- 必须在应用停止写入 agent_run_state 之后执行。
--
-- MySQL 8.0 不支持 DROP COLUMN IF EXISTS，按 information_schema 判断后动态执行。

-- 连接字符集显式设为 utf8mb4：本脚本含中文列注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `vs_drop_column_if_present`;

DELIMITER $$
CREATE PROCEDURE `vs_drop_column_if_present`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL vs_drop_column_if_present('agent_run_state', 'runEpoch');

DROP PROCEDURE IF EXISTS `vs_drop_column_if_present`;
