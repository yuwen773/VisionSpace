-- #37 空间唯一性、配额与文件一致性（前向迁移）
--
-- 三件事，都不回填业务数据、不自动处置存量冲突：
--   1. space 增加 activeKey（userId:spaceType）与唯一索引 uk_space_active_key。
--      唯一索引只约束非空值：空间被删除时把 activeKey 置空，因此「删除后再创建」
--      和反复重建都不会被历史墓碑挡住。不能用 (userId, spaceType) 唯一索引代替：
--      逻辑删除只改 isDelete，行还在表里，第二次删除或重建必然冲突。
--   2. 新建 storage_cleanup_task：数据库与对象存储没有共同事务，删除未确认时
--      保留可重试、可观察的任务，由清理任务按退避重试；引用复核不通过的任务
--      转为等待人工处理，不按猜测删除对象。
--   3. 存量有效空间不回填 activeKey。回填会让存量重复数据在唯一索引上冲突，
--      也可能替人工"选中"某一个空间。存量清单见同名 -blocklist.sql，
--      回填与处置在维护窗口内由人工执行（见 docs/progress/37-*.md）。
--
-- 执行前提：维护窗口内、已有备份、应用停止写入 space 与 storage_cleanup_task。
-- 脚本可重复执行：加列与建表都先判断存在性。
-- 回滚：执行同名 -rollback.sql。

SET NAMES utf8mb4;

-- ---------- 1. space.activeKey ----------

DROP PROCEDURE IF EXISTS `vs37_add_column_if_missing`;

DELIMITER $$
CREATE PROCEDURE `vs37_add_column_if_missing`(
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

CALL vs37_add_column_if_missing('space', 'activeKey',
    'varchar(64) NULL DEFAULT NULL COMMENT ''有效空间唯一占位键 userId:spaceType，删除后置空''');

DROP PROCEDURE IF EXISTS `vs37_add_column_if_missing`;

-- 唯一索引同样先判断存在性，重复执行不报错。
SET @has_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'space'
    AND INDEX_NAME = 'uk_space_active_key'
);
SET @ddl = IF(@has_index = 0,
  'ALTER TABLE `space` ADD UNIQUE INDEX `uk_space_active_key` (`activeKey`) USING BTREE',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. storage_cleanup_task ----------

CREATE TABLE IF NOT EXISTS `storage_cleanup_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务标识',
  `pictureFileId` bigint(20) NULL DEFAULT NULL COMMENT '资源记录 id；上传失败时为空',
  `pictureId` bigint(20) NULL DEFAULT NULL COMMENT '图片 id；临时资源为空',
  `platform` varchar(32) NULL DEFAULT NULL COMMENT '存储平台标识',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键',
  `kind` varchar(16) NULL DEFAULT NULL COMMENT '内容类型：original / thumbnail / preview',
  `reason` varchar(128) NOT NULL COMMENT '触发清理的原因',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=待重试 1=已确认删除 2=引用复核不通过，等待人工处理',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `nextRetryTime` datetime NULL DEFAULT NULL COMMENT '下次重试时间（数据库时钟）',
  `lastError` varchar(255) NULL DEFAULT NULL COMMENT '最近一次失败原因，不含秘密值',
  `pendingKey` varchar(255) NULL DEFAULT NULL COMMENT '待处理唯一键 platform|kind|objectKey；进入终态后置空',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pending_object`(`pendingKey`) USING BTREE,
  INDEX `idx_status_retry`(`status`, `nextRetryTime`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对象存储清理任务';
