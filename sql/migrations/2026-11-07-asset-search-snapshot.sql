-- #54：维护窗口中对备份后的目标库执行；只新增表，不改写旧消息、图片或配额。
-- 回退：停止新检索入口，保留本表及快照；不重新开放旧图片直链。
CREATE TABLE IF NOT EXISTS `asset_search_snapshot` (
  `id` varchar(36) NOT NULL,
  `ownerId` bigint NOT NULL,
  `spaceId` bigint NOT NULL,
  `threadId` varchar(64) DEFAULT NULL,
  `payload` longtext NOT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_owner_thread` (`ownerId`, `threadId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内检索结果快照';
