-- 将 picture 表中的统计数据迁移到 picture_stats
INSERT INTO picture_stats (pictureId, viewCount, likeCount, collectCount, downloadCount, shareCount, impressionCount, ctr, createTime, updateTime)
SELECT
    id AS pictureId,
    IFNULL(viewCount, 0) AS viewCount,
    IFNULL(likeCount, 0) AS likeCount,
    IFNULL(collectCount, 0) AS collectCount,
    IFNULL(downloadCount, 0) AS downloadCount,
    IFNULL(shareCount, 0) AS shareCount,
    IFNULL(impressionCount, 0) AS impressionCount,
    IFNULL(ctr, 0) AS ctr,
    NOW() AS createTime,
    NOW() AS updateTime
FROM picture
WHERE reviewStatus = 1
  AND isDelete = 0
  AND (
      viewCount > 0 OR likeCount > 0 OR collectCount > 0
      OR downloadCount > 0 OR shareCount > 0 OR impressionCount > 0
  )
ON DUPLICATE KEY UPDATE
    viewCount = VALUES(viewCount),
    likeCount = VALUES(likeCount),
    collectCount = VALUES(collectCount),
    downloadCount = VALUES(downloadCount),
    shareCount = VALUES(shareCount),
    impressionCount = VALUES(impressionCount),
    ctr = VALUES(ctr),
    updateTime = NOW();