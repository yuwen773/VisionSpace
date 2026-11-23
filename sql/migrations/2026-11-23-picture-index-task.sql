-- 公开图库索引的待重试任务；不改图片、权限与空间额度。
-- 回滚：索引同步失败将不再有持久待办，只留日志；已写入的索引文档不受影响。
CREATE TABLE IF NOT EXISTS `picture_index_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pictureId` bigint NOT NULL,
  `operation` varchar(16) NOT NULL,
  `status` int NOT NULL DEFAULT 1,
  `attempts` int NOT NULL DEFAULT 0,
  `nextRetryTime` datetime DEFAULT NULL,
  `lastError` varchar(500) DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_picture` (`pictureId`),
  KEY `idx_status_retry` (`status`, `nextRetryTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
