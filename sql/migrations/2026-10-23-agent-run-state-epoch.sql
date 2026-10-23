-- #41 第三轮修复：为已建表的库补上执行世代列（前向迁移，可重复执行）
--
-- 背景：完整检查点的写入只按 stateVersion 做 CAS 时，「被更新版本取代」后的
-- 对齐重试无法区分两种情况——合法接管后继续推进，与上一轮执行迟到的旧载荷
-- 重新编号后覆盖新状态。runEpoch 是执行世代（fencing token）：每个执行入口
-- 开始时原子递增，检查点写入必须匹配当前世代，旧执行世代的迟到写入一律拒绝。
--
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，因此按 information_schema 判断后
-- 动态执行；重复执行不会报错。
--
-- 只加列、不改已有数据，也不删除任何动作证据：已存在的动作记录与完整状态
-- 保持不变，存量行的世代默认 0，与新写入的世代约束兼容。
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

CALL vs_add_column_if_missing('agent_run_state', 'runEpoch',
    'bigint(20) NOT NULL DEFAULT 0 COMMENT ''执行世代：每次执行开始时递增，迟到旧载荷据此被拒''');

DROP PROCEDURE IF EXISTS `vs_add_column_if_missing`;
