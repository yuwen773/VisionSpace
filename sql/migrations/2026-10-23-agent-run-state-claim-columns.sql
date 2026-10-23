-- #41 第二轮修复：为已建表的库补上认领与续跑所需的列（前向迁移，可重复执行）
--
-- 背景：2026-10-23-agent-run-state.sql 在本分支上先建过一次表，评审修复又新增了
-- claimToken / mcpServerCodes / resumeLease / resumeLeaseUntil。已经执行过旧版本的
-- 环境不会因为 CREATE TABLE IF NOT EXISTS 而得到新列，随后 mapper.claim 会直接抛错，
-- 台账被判成 UNAVAILABLE，所有工具调用都会被拦下。
--
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，因此按 information_schema 判断后
-- 动态执行；重复执行不会报错。
--
-- 只加列、不改已有数据，也不删除任何动作证据：已存在的动作记录状态保持不变。
-- 回滚见同名 -rollback.sql。

-- 连接字符集显式设为 utf8mb4：本脚本含中文列注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `vs_add_column_if_missing`;

DELIMITER $$
CREATE PROCEDURE `vs_add_column_if_missing`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL vs_add_column_if_missing('agent_run_state', 'mcpServerCodes',
    'text NULL COMMENT ''本次执行使用的 MCP 服务 code（JSON 数组，只存 code，不存凭据/地址）''');
CALL vs_add_column_if_missing('agent_run_state', 'resumeLease',
    'varchar(64) NULL DEFAULT NULL COMMENT ''当前续跑请求的租约凭据，防止重复续跑''');
CALL vs_add_column_if_missing('agent_run_state', 'resumeLeaseUntil',
    'datetime NULL DEFAULT NULL COMMENT ''续跑租约到期时间；仅用于回收崩溃遗留的租约''');
CALL vs_add_column_if_missing('agent_action_record', 'claimToken',
    'varchar(64) NULL DEFAULT NULL COMMENT ''本次认领凭据，只有持有者能推进状态''');

DROP PROCEDURE IF EXISTS `vs_add_column_if_missing`;
