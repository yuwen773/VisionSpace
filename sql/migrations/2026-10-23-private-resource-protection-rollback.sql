-- #36 私有资源管理与反馈保护：回滚
--
-- 删除本工单新增的业务表，既有 user_feedback / picture 等数据不受影响。
-- 回滚不恢复已撤销的授权、不解除隔离：若仍有隔离中的资源，脚本主动失败，
-- 要求先按独立流程处理，避免"回滚即重新公开违规内容"或"回滚即恢复已撤权限"。
--
-- 反馈附件的定位记录不丢：新反馈在 user_feedback.pictureUrls 里只保存
-- attachment:{id} 引用，直接删表会让平台、对象键、归属与绑定记录一起消失，
-- 旧版本既定位不到附件也无法再核对。因此先把记录搬进归档表
-- feedback_attachment_archive_20261023，再删业务表；重新执行前向脚本时记录会搬回。
-- 归档表保留源站地址只是业务数据，不对外暴露，也不重新公开已关闭的私人直链。
--
-- 归档策略（每轮都以业务表的最新状态为准）：
--   1. 补齐：归档里没有的记录按业务表插入；
--   2. 更新：归档里已有的记录按 id 用业务表的最新绑定与状态覆盖；
--   3. 校验：归档必须逐行等于业务表，不一致就阻断，不删业务表。
-- 不能只用 INSERT IGNORE：那样归档里的旧快照会被静默保留，
-- 附件在两次回滚之间绑定或改状态后，下一轮回滚归档的仍是第一轮的旧状态。
--
-- 阻断方式：向带 CHECK 约束的守卫表插入一行，存在未解除隔离时插入 0 触发
-- 3819（Check constraint ... is violated）。MySQL 不支持在 PREPARE 中执行 SIGNAL，
-- 而 CHECK 在任意 sql_mode 下都会求值，因此这是可移植的单语句守卫。
-- 归档不一致用另一张守卫表阻断，语义相同。
-- 脚本必须在"遇错即停"的客户端执行（mysql 命令行默认行为、JDBC 逐条 execute），
-- 不能加 --force。被阻断时守卫表会留在库里（无数据），这是预期状态：
-- 完成人工处置后重跑本脚本会先复用该表，再在成功路径上删除它。
-- 无论归档是否成功，业务表都要到最后才删：归档不完整时唯一有效的记录仍在业务表里。

SET @active_quarantine = (
  SELECT COUNT(*) FROM `resource_isolation` WHERE `status` = 1 AND `activeKey` IS NOT NULL
);
CREATE TABLE IF NOT EXISTS `_private_resource_rollback_guard` (
  `ok` tinyint NOT NULL,
  CONSTRAINT `BLOCKED_resolve_active_quarantine_before_rollback` CHECK (`ok` = 1)
) ENGINE = InnoDB COMMENT = '#36 回滚守卫：仅脚本执行期间存在，成功回滚后自动删除';
INSERT INTO `_private_resource_rollback_guard` (`ok`) VALUES (IF(@active_quarantine > 0, 0, 1));

-- 归档表与业务表同结构：归档记录必须带着平台、对象键、归属与绑定一起搬过去
CREATE TABLE IF NOT EXISTS `feedback_attachment_archive_20261023` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '附件标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '上传者用户 id',
  `feedbackId` bigint(20) NULL DEFAULT NULL COMMENT '绑定后的反馈 id；未提交时为空',
  `platform` varchar(32) NOT NULL COMMENT '存储平台标识；旧附件为 legacy',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键；旧附件保留地址原文',
  `url` varchar(1024) NULL DEFAULT NULL COMMENT '上传时返回的地址，仅作记录，不对外返回',
  `fileSize` bigint(20) NULL DEFAULT NULL COMMENT '文件字节数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=UPLOADED 1=BOUND 2=LEGACY_VERIFIED 3=BLOCKED',
  `legacyUrlHash` char(64) NULL DEFAULT NULL COMMENT '旧附件地址摘要，仅用于幂等登记',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_legacy_url_hash`(`legacyUrlHash`) USING BTREE,
  INDEX `idx_feedback_status`(`feedbackId`, `status`) USING BTREE,
  INDEX `idx_owner_status`(`ownerUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '反馈附件回滚归档';

-- 归档前先保证业务表存在：回滚可以重复执行，业务表不存在时按空表处理
CREATE TABLE IF NOT EXISTS `feedback_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '附件标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '上传者用户 id',
  `feedbackId` bigint(20) NULL DEFAULT NULL COMMENT '绑定后的反馈 id；未提交时为空',
  `platform` varchar(32) NOT NULL COMMENT '存储平台标识；旧附件为 legacy',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键；旧附件保留地址原文',
  `url` varchar(1024) NULL DEFAULT NULL COMMENT '上传时返回的地址，仅作记录，不对外返回',
  `fileSize` bigint(20) NULL DEFAULT NULL COMMENT '文件字节数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=UPLOADED 1=BOUND 2=LEGACY_VERIFIED 3=BLOCKED',
  `legacyUrlHash` char(64) NULL DEFAULT NULL COMMENT '旧附件地址摘要，仅用于幂等登记',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_legacy_url_hash`(`legacyUrlHash`) USING BTREE,
  INDEX `idx_feedback_status`(`feedbackId`, `status`) USING BTREE,
  INDEX `idx_owner_status`(`ownerUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '反馈附件记录';

-- 归档补齐：把业务表里归档还没有的记录先放进去。
-- 唯一键冲突时跳过，随后的一致性校验会拦截并阻断回滚。
INSERT IGNORE INTO `feedback_attachment_archive_20261023`
  (`id`, `ownerUserId`, `feedbackId`, `platform`, `objectKey`, `url`, `fileSize`, `status`,
   `legacyUrlHash`, `createTime`, `updateTime`)
SELECT `id`, `ownerUserId`, `feedbackId`, `platform`, `objectKey`, `url`, `fileSize`, `status`,
       `legacyUrlHash`, `createTime`, `updateTime`
FROM `feedback_attachment`;

-- 归档更新：已有的归档记录按 id 用业务表的最新绑定与状态覆盖，
-- 保证下一轮恢复拿到的是最新可信业务状态，而不是第一轮的旧快照
UPDATE `feedback_attachment_archive_20261023` a
JOIN `feedback_attachment` f ON f.`id` = a.`id`
SET a.`ownerUserId` = f.`ownerUserId`,
    a.`feedbackId` = f.`feedbackId`,
    a.`platform` = f.`platform`,
    a.`objectKey` = f.`objectKey`,
    a.`url` = f.`url`,
    a.`fileSize` = f.`fileSize`,
    a.`status` = f.`status`,
    a.`legacyUrlHash` = f.`legacyUrlHash`,
    a.`updateTime` = f.`updateTime`;

-- 归档一致性校验：归档必须逐行等于业务表（定位、归属、绑定、状态都不差），
-- 不一致说明归档没有真正接住业务数据，此时不能删业务表
SET @archive_mismatch = (
  SELECT COUNT(*) FROM (
    SELECT f.`id` AS `id`
    FROM `feedback_attachment` f
    LEFT JOIN `feedback_attachment_archive_20261023` a ON a.`id` = f.`id`
    WHERE a.`id` IS NULL
       OR NOT (a.`ownerUserId` <=> f.`ownerUserId`
            AND a.`feedbackId` <=> f.`feedbackId`
            AND a.`platform` <=> f.`platform`
            AND a.`objectKey` <=> f.`objectKey`
            AND a.`url` <=> f.`url`
            AND a.`fileSize` <=> f.`fileSize`
            AND a.`status` <=> f.`status`
            AND a.`legacyUrlHash` <=> f.`legacyUrlHash`)
  ) AS `mismatched`
);
CREATE TABLE IF NOT EXISTS `_private_resource_archive_guard` (
  `ok` tinyint NOT NULL,
  CONSTRAINT `BLOCKED_archive_must_match_before_rollback` CHECK (`ok` = 1)
) ENGINE = InnoDB COMMENT = '#36 归档一致性守卫：仅脚本执行期间存在，成功回滚后自动删除';
INSERT INTO `_private_resource_archive_guard` (`ok`) VALUES (IF(@archive_mismatch > 0, 0, 1));

DROP TABLE IF EXISTS `resource_isolation`;
DROP TABLE IF EXISTS `resource_access_grant`;
DROP TABLE IF EXISTS `feedback_attachment`;
DROP TABLE IF EXISTS `_private_resource_rollback_guard`;
DROP TABLE IF EXISTS `_private_resource_archive_guard`;
