-- #35 Agent 图片完整生命周期：会话附件绑定（回滚）
--
-- 只移除本工单新增的表与列：agent_session_attachment、agent_legacy_attachment、
-- picture_file 的删除重试列、agent_message 的 attachmentIds。
--
-- 回滚不删除 picture_file 里的资源记录，也不删除任何对象：附件与图片资源本身
-- 保留，回滚后旧版本应用退回「按地址处理附件」的行为。
-- 回滚不会重新公开已关闭的私人直链，也不会恢复已删除的对象。
--
-- 执行顺序：先跑存量回填的回滚（-legacy-rollback.sql）再跑本脚本；本脚本会删除
-- 清单表，存量回滚的回滚依赖它。
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

CALL vs_drop_column_if_present('agent_message', 'attachmentIds');
CALL vs_drop_column_if_present('picture_file', 'deleteNextRetryTime');
CALL vs_drop_column_if_present('picture_file', 'deleteRetryCount');
CALL vs_drop_column_if_present('picture_file', 'deleteState');

DROP PROCEDURE IF EXISTS `vs_drop_column_if_present`;

DROP TABLE IF EXISTS `agent_session_attachment`;
DROP TABLE IF EXISTS `agent_legacy_attachment`;
