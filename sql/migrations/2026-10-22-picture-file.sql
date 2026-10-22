-- #34 图片归属与实际内容鉴权：资源记录表（前向迁移）
--
-- 只创建结构与索引，不回填存量 picture 的 url/thumbnailUrl/previewUrl。
-- 未知归属的旧文件不自动认领：旧调用兼容仅在存在精确归属记录时生效，
-- 存量直链的失效与真实桶 ACL 由维护窗口及人工关卡闭环（见 docs/progress/34-*.md）。
--
-- 生命周期用 status 表达（TEMP/ACTIVE/DELETED/SUPERSEDED），不使用全局 isDelete
-- 逻辑删除：资源记录需要保留"已删除/已替换"的历史用于排查与重试，而 isDelete
-- 只有两态，无法区分这两种终态。查询一律显式带 status 条件。
--
-- 执行前提：在维护窗口内、已有备份，且应用停止写入 picture_file。
-- 回滚：执行同名 -rollback.sql；应用回滚到不含资源记录读取的版本。

CREATE TABLE IF NOT EXISTS `picture_file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资源标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '所有者用户 id',
  `spaceId` bigint(20) NULL DEFAULT NULL COMMENT '空间 id（公共图库为空）',
  `pictureId` bigint(20) NULL DEFAULT NULL COMMENT '绑定后的图片 id（临时上传为空）',
  `purpose` varchar(32) NOT NULL COMMENT '用途：gallery / agent-chat',
  `kind` varchar(16) NOT NULL COMMENT '内容类型：original / thumbnail / preview',
  `platform` varchar(32) NOT NULL COMMENT '存储平台标识',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键（含 basePath）',
  `url` varchar(1024) NULL DEFAULT NULL COMMENT '上传时返回的地址，仅作记录',
  `fileSize` bigint(20) NULL DEFAULT NULL COMMENT '文件字节数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=TEMP 1=ACTIVE 2=DELETED 3=SUPERSEDED',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_picture_kind`(`pictureId`, `kind`, `status`) USING BTREE,
  INDEX `idx_owner_purpose_status`(`ownerUserId`, `purpose`, `status`) USING BTREE,
  INDEX `idx_platform_key`(`platform`, `objectKey`(191)) USING BTREE,
  INDEX `idx_url`(`url`(191)) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图片资源记录';
