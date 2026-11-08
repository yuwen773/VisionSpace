-- #55：新增聚合和持久操作回执；不复制文件、不修改图片或会话。
CREATE TABLE IF NOT EXISTS `asset_board` (
  `id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ownerId` bigint NOT NULL,
  `spaceId` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `payload` longtext NOT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_owner_space` (`ownerId`, `spaceId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_board_operation` (
  `ownerId` bigint NOT NULL,
  `operationId` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operationType` varchar(24) NOT NULL,
  `boardId` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `requestDigest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint DEFAULT NULL,
  `proposalId` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ownerId`, `operationId`),
  KEY `idx_board` (`boardId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
