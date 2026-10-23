-- #36 私有资源管理与反馈保护：反馈附件、限时只读授权、违规隔离（前向迁移）
--
-- 只新增业务表与一张回滚归档表，不修改既有表结构，不回填或删除既有业务数据：
--   feedback_attachment   反馈附件的可信上传记录（上传者、平台、对象键、状态）
--   resource_access_grant 单资源限时只读授权（被授权人、授权人、到期、撤销、理由）
--   resource_isolation    违规资源隔离与恢复（操作者、理由、时间、解除信息）
--   feedback_attachment_archive_20261023
--                         回滚归档：新反馈只保存 attachment:{id} 引用，回滚时若直接删表
--                         就会丢掉平台、对象键、归属与绑定记录；归档表保留这些记录，
--                         再次执行本脚本时把记录搬回 feedback_attachment。
--
-- 旧反馈附件（user_feedback.pictureUrls）无法从可信资源记录确认归属：
-- 上传时写入 /temp/{userId}/ 前缀，但前缀不是归属证据，因此旧附件先登记为
-- BLOCKED（status=3）并保留原 JSON 原文在 user_feedback 中，不公开、不自动认领、
-- 不删除。确认归属需要人工核对，核对后可用 LEGACY_VERIFIED（status=2）单独登记。
--
-- 授权与隔离都用 activeKey 唯一约束表达"同一资源同一对象最多一条有效记录"，
-- 该约束由数据库跨实例生效，不依赖应用进程内状态。
--
-- 执行前提：维护窗口内、已有备份。脚本可重复执行。
-- 回滚：执行同名 -rollback.sql；当存在未解除的隔离时会主动报错阻断回滚。

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

-- 回滚归档表：回滚时新反馈附件的定位记录先搬到这里，再删业务表；
-- 再次执行本脚本时从归档表搬回，业务数据不丢。
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

-- 归档记录搬回业务表：回滚过再部署本工单时，新反馈的附件定位、归属与绑定仍然可用
INSERT IGNORE INTO `feedback_attachment`
  (`id`, `ownerUserId`, `feedbackId`, `platform`, `objectKey`, `url`, `fileSize`, `status`,
   `legacyUrlHash`, `createTime`, `updateTime`)
SELECT `id`, `ownerUserId`, `feedbackId`, `platform`, `objectKey`, `url`, `fileSize`, `status`,
       `legacyUrlHash`, `createTime`, `updateTime`
FROM `feedback_attachment_archive_20261023`;

CREATE TABLE IF NOT EXISTS `resource_access_grant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '授权标识',
  `resourceType` varchar(32) NOT NULL COMMENT '资源类型：picture',
  `resourceId` bigint(20) NOT NULL COMMENT '资源 id',
  `granteeUserId` bigint(20) NOT NULL COMMENT '被授权查看者',
  `grantedByUserId` bigint(20) NOT NULL COMMENT '授权主体（个人资源所有者或团队管理员）',
  `reason` varchar(500) NULL DEFAULT NULL COMMENT '授权理由',
  `expiresTime` datetime NOT NULL COMMENT '到期时间，必填',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '1=ACTIVE 2=REVOKED 3=EXPIRED',
  `activeKey` varchar(128) NULL DEFAULT NULL COMMENT '有效授权唯一键；撤销或到期后置空',
  `revokedByUserId` bigint(20) NULL DEFAULT NULL COMMENT '撤销人',
  `revokeReason` varchar(500) NULL DEFAULT NULL COMMENT '撤销理由',
  `revokeTime` datetime NULL DEFAULT NULL COMMENT '撤销时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_active_key`(`activeKey`) USING BTREE,
  INDEX `idx_resource_status`(`resourceType`, `resourceId`, `status`) USING BTREE,
  INDEX `idx_grantee_status`(`granteeUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '单资源限时只读授权';

CREATE TABLE IF NOT EXISTS `resource_isolation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '隔离记录标识',
  `resourceType` varchar(32) NOT NULL COMMENT '资源类型：picture',
  `resourceId` bigint(20) NOT NULL COMMENT '资源 id',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '1=QUARANTINED 2=RELEASED',
  `activeKey` varchar(128) NULL DEFAULT NULL COMMENT '隔离中唯一键；解除后置空',
  `operatorId` bigint(20) NOT NULL COMMENT '隔离操作者',
  `reason` varchar(500) NOT NULL COMMENT '隔离理由',
  `releaseOperatorId` bigint(20) NULL DEFAULT NULL COMMENT '恢复操作者',
  `releaseReason` varchar(500) NULL DEFAULT NULL COMMENT '恢复理由',
  `releaseTime` datetime NULL DEFAULT NULL COMMENT '恢复时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '隔离时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_active_key`(`activeKey`) USING BTREE,
  INDEX `idx_resource_status`(`resourceType`, `resourceId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '违规资源隔离与恢复';

-- 旧反馈附件：仅登记为 BLOCKED（不可读取），保留原文并在重复执行时幂等跳过。
-- 已经是受控附件记录的引用（attachment:{id} 且该附件存在）不登记为旧附件：
-- 它的归属已经确认，重复登记只会让附件列表出现重复条目。
INSERT INTO `feedback_attachment` (`ownerUserId`, `feedbackId`, `platform`, `objectKey`, `url`, `status`, `legacyUrlHash`)
SELECT f.`userId`, f.`id`, 'legacy', LEFT(j.`url`, 768), LEFT(j.`url`, 1024), 3, SHA2(j.`url`, 256)
FROM `user_feedback` f
JOIN JSON_TABLE(f.`pictureUrls`, '$[*]' COLUMNS (`url` varchar(1024) PATH '$')) j
WHERE f.`isDelete` = 0
  AND f.`pictureUrls` IS NOT NULL
  AND JSON_VALID(f.`pictureUrls`)
  AND NOT EXISTS (
    SELECT 1 FROM `feedback_attachment` a WHERE a.`legacyUrlHash` = SHA2(j.`url`, 256)
  )
  AND NOT (
    j.`url` LIKE 'attachment:%'
    AND EXISTS (
      SELECT 1 FROM `feedback_attachment` a2
      WHERE CAST(a2.`id` AS CHAR) = SUBSTRING(j.`url`, 12)
    )
  );
