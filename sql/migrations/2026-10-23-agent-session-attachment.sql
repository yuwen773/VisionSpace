-- #35 Agent 图片完整生命周期：会话附件绑定（前向迁移）
--
-- 背景：Agent 临时上传的图片目前没有「临时 → 已绑定」的记录。SSE 结束时前端把
-- 上传的图片全部删掉，而消息里引用的地址留在历史里，导致历史图片失效；未发送
-- 的临时图片也没有回收路径，长期占用存储与用户额度。
--
-- 本脚本做三件事：
--   1. 新建 agent_session_attachment：会话与图片资源的绑定关系。
--      绑定建立后图片保留到会话或图片被明确删除；同一会话重复绑定同一资源幂等。
--      「是否已绑定」只看这张表，不看 picture_file.status，因此绑定与回收并发时
--      双方都以行为锁定 + 重新核对为准，不需要在资源表上另设绑定态。
--   2. 新建 agent_legacy_attachment：存量会话的图片清单。归属无法确认的一律
--      登记为待核对（阻断），不自动认领、不自动删除、不重新公开私人直链。
--   3. picture_file 增加删除任务状态（deleteState / deleteRetryCount /
--      deleteNextRetryTime）：对象存储删除未确认时保留可重试状态，不静默丢弃；
--      agent_message 增加 attachmentIds：消息携带哪些图片资源，历史按它渲染
--      受控入口，不再把源站地址交给浏览器。
--
-- 存量数据的回填在同名 -legacy.sql 中单独执行，与结构迁移分开，便于单独回滚。
--
-- 执行前提：在维护窗口内、已有备份，且应用停止写入 picture_file /
-- agent_message / agent_session_attachment。
-- 回滚：执行同名 -rollback.sql；应用回滚到不依赖绑定表的版本。

-- 连接字符集显式设为 utf8mb4：本脚本含中文列注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_session_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '绑定标识',
  `sessionId` varchar(64) NOT NULL COMMENT '会话标识',
  `resourceId` bigint(20) NOT NULL COMMENT 'picture_file 资源标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '附件所有者用户 id',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=BOUND 已绑定 1=RELEASED 已解除',
  `legacy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=由存量迁移登记',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_session_resource` (`sessionId`, `resourceId`) USING BTREE,
  INDEX `idx_resource_status` (`resourceId`, `status`) USING BTREE,
  INDEX `idx_session_status` (`sessionId`, `status`) USING BTREE,
  INDEX `idx_owner_status` (`ownerUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 会话附件绑定';

CREATE TABLE IF NOT EXISTS `agent_legacy_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '清单标识',
  `sessionId` varchar(64) NOT NULL COMMENT '会话标识',
  `messageId` bigint(20) NOT NULL COMMENT '消息标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '会话所有者用户 id',
  `urlHash` varchar(64) NOT NULL COMMENT '源站地址的 SHA2 摘要（不存原始地址）',
  `resourceId` bigint(20) NULL DEFAULT NULL COMMENT '确认归属后的资源标识，归属不明时为空',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=待核对（阻断） 1=已确认归属 2=已确认无归属',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_message_hash` (`messageId`, `urlHash`(32)) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_session` (`sessionId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '存量会话图片清单';

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，因此按 information_schema 判断后
-- 动态执行；重复执行不会报错。
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

-- 删除任务状态：0=无删除任务 1=删除未确认待重试 2=删除已确认
CALL vs_add_column_if_missing('picture_file', 'deleteState',
    'int(11) NOT NULL DEFAULT 0 COMMENT ''0=无删除任务 1=待重试 2=已确认删除''');

CALL vs_add_column_if_missing('picture_file', 'deleteRetryCount',
    'int(11) NOT NULL DEFAULT 0 COMMENT ''删除重试次数''');

CALL vs_add_column_if_missing('picture_file', 'deleteNextRetryTime',
    'datetime NULL DEFAULT NULL COMMENT ''下次重试时间''');

-- 消息携带的图片资源标识，十进制逗号分隔。
CALL vs_add_column_if_missing('agent_message', 'attachmentIds',
    'varchar(1024) NULL DEFAULT NULL COMMENT ''消息携带的图片资源标识，十进制逗号分隔''');

DROP PROCEDURE IF EXISTS `vs_add_column_if_missing`;
