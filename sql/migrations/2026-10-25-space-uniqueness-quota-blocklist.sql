-- #37 存量核对清单（只读报告）
--
-- 三份清单，都只读、不改数据、不含秘密值：
--   1. 存量重复有效空间：同一创建者的同一空间类型有多条未删除记录。
--      迁移与回填被阻断，直到人工处置；脚本不自动选择、合并或删除。
--   2. 存量空间配额：space.totalCount/totalSize 与 picture 表的实际条数/字节数
--      不一致的空间。偏差是历史遗留的累计错误，由维护窗口按清单校正，
--      不静默覆盖。
--   3. 未收敛的存储清理任务：还没确认删除、或引用复核不通过而等待人工处理的对象。
--
-- 用法：在维护窗口内以只读账号执行，结果留档后再决定处置方式。
-- 处置顺序建议：先 1（决定保留哪一个空间）→ 再回填 activeKey → 再 2（校正计数）
-- → 再 3（逐条核对对象是否真的可以删除）。

-- ---------- 1. 存量重复有效空间 ----------

-- 明细：每一组重复空间逐条列出，由人工决定保留哪一条
SELECT
  s.`userId`        AS `userId`,
  s.`spaceType`     AS `spaceType`,
  s.`id`            AS `spaceId`,
  s.`spaceName`     AS `spaceName`,
  s.`totalCount`    AS `totalCount`,
  s.`totalSize`     AS `totalSize`,
  s.`createTime`    AS `createTime`,
  (SELECT COUNT(*) FROM `picture` p WHERE p.`spaceId` = s.`id` AND p.`isDelete` = 0) AS `livePictures`,
  (SELECT COUNT(*) FROM `space_user` su WHERE su.`spaceId` = s.`id`) AS `memberCount`
FROM `space` s
JOIN (
  SELECT `userId`, `spaceType`
  FROM `space`
  WHERE `isDelete` = 0
  GROUP BY `userId`, `spaceType`
  HAVING COUNT(*) > 1
) d ON d.`userId` = s.`userId` AND d.`spaceType` = s.`spaceType`
WHERE s.`isDelete` = 0
ORDER BY s.`userId` ASC, s.`spaceType` ASC, s.`id` ASC;

-- 汇总：受影响的创建者数量。非零时不得回填 activeKey，也不得启用唯一约束。
SELECT
  COUNT(*) AS `duplicatedGroups`
FROM (
  SELECT `userId`, `spaceType`
  FROM `space`
  WHERE `isDelete` = 0
  GROUP BY `userId`, `spaceType`
  HAVING COUNT(*) > 1
) d;

-- ---------- 2. 存量空间配额偏差 ----------

-- 明细：只列不一致的空间；expected* 是按 picture 表现算出来的值。
-- 删除空间或删除图片都不会自动修复历史偏差，需按本清单人工校正。
SELECT
  s.`id`                       AS `spaceId`,
  s.`userId`                   AS `userId`,
  s.`spaceName`                AS `spaceName`,
  s.`totalCount`               AS `storedCount`,
  COALESCE(p.`actualCount`, 0) AS `expectedCount`,
  s.`totalSize`                AS `storedSize`,
  COALESCE(p.`actualSize`, 0)  AS `expectedSize`
FROM `space` s
LEFT JOIN (
  SELECT `spaceId`, COUNT(*) AS `actualCount`, COALESCE(SUM(`picSize`), 0) AS `actualSize`
  FROM `picture`
  WHERE `isDelete` = 0 AND `spaceId` IS NOT NULL
  GROUP BY `spaceId`
) p ON p.`spaceId` = s.`id`
WHERE s.`isDelete` = 0
  AND (s.`totalCount` <> COALESCE(p.`actualCount`, 0)
    OR s.`totalSize` <> COALESCE(p.`actualSize`, 0))
ORDER BY ABS(s.`totalCount` - COALESCE(p.`actualCount`, 0)) DESC, s.`id` ASC;

-- 负数：已用计数不应为负。出现即为必须优先处理的存量错误。
SELECT
  `id`         AS `spaceId`,
  `userId`     AS `userId`,
  `totalCount` AS `storedCount`,
  `totalSize`  AS `storedSize`
FROM `space`
WHERE `isDelete` = 0 AND (`totalCount` < 0 OR `totalSize` < 0)
ORDER BY `id` ASC;

-- ---------- 3. 未收敛的存储清理任务 ----------

-- 待重试与等待人工处理的对象。lastError 只记原因，不含地址或凭据；
-- 这里输出对象键是为了让运维能核对对象，不要把结果贴到公开渠道。
SELECT
  t.`id`           AS `taskId`,
  t.`status`       AS `status`,
  t.`attempts`     AS `attempts`,
  t.`platform`     AS `platform`,
  t.`kind`         AS `kind`,
  t.`objectKey`    AS `objectKey`,
  t.`reason`       AS `reason`,
  t.`lastError`    AS `lastError`,
  t.`nextRetryTime` AS `nextRetryTime`,
  t.`createTime`   AS `createTime`
FROM `storage_cleanup_task` t
WHERE t.`status` IN (0, 2)
ORDER BY t.`status` ASC, t.`id` ASC;

-- 汇总：回滚前必须为 0
SELECT
  SUM(CASE WHEN `status` = 0 THEN 1 ELSE 0 END) AS `pendingRetry`,
  SUM(CASE WHEN `status` = 2 THEN 1 ELSE 0 END) AS `blockedForReview`
FROM `storage_cleanup_task`;
