-- #41 第二轮修复的回滚：只移除本轮新增的四列。
--
-- 与 2026-10-23-agent-run-state-rollback.sql 不同，这里不删表：
--   - agent_action_record 保留全部动作证据（状态、结果摘要、尝试次数），
--     回滚只丢认领凭据，已确认完成/已核对的动作不会被重新开放重放；
--   - agent_run_state 保留完整状态与版本，只丢 MCP 记录与续跑租约。
--
-- 回滚后旧版本应用不再认领动作（旧实现按状态判定），因此必须在维护窗口内、
-- 应用停止写入这两张表之后执行，否则并发写会绕过认领。
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

CALL vs_drop_column_if_present('agent_run_state', 'resumeLeaseUntil');
CALL vs_drop_column_if_present('agent_run_state', 'resumeLease');
CALL vs_drop_column_if_present('agent_run_state', 'mcpServerCodes');
CALL vs_drop_column_if_present('agent_action_record', 'claimToken');

DROP PROCEDURE IF EXISTS `vs_drop_column_if_present`;
