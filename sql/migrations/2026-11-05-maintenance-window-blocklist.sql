-- #46 维护窗口跨领域脱敏阻断清单（只读报告）
--
-- 用途：把各业务票已经交付的冲突事实汇总成一份「迁移前必须人工处置」的清单。
-- 每份业务清单只覆盖本票的表；本清单把归属未知、重复有效空间、无法安全恢复状态、
-- 未收敛补偿与「不得复活」的安全状态放到同一张表里，供维护窗口一次性核对，
-- 并按维度给出阻断计数：任一阻断维度非零，编排必须保持停写，不得放行回填或回滚。
--
-- 脱敏规则（与各业务票一致，且更严格）：
--   · 不输出任何源站地址、存储对象键、兑换码或码摘要、账号/昵称/头像等资料；
--   · 地址只保留既有 SHA2 摘要的前 12 位；会话标识另做摘要后取前 12 位；
--   · 「不得复活」一节只输出计数，不输出任何行内值；
--   · 只输出内部 id、状态短码与 SHA2 摘要前 12 位；会话标识、空间归属字段都先做摘要，
--     清单本身不是新的访问入口，也不暴露可直接回查账号/会话的原始标识符；
--
-- 本脚本只读，不修改任何数据；不含秘密值；不自动认领、合并、删除或续跑任何条目。
-- 用法：在维护窗口内以只读账号执行，结果留档后再决定处置方式。

-- ---------- 1. 归属未知的图片与附件（阻断迁移，不自动认领） ----------

SELECT 'unknown-session-attachment' AS `dimension`,
       la.`id`                     AS `recordId`,
       LEFT(SHA2(la.`sessionId`, 256), 12) AS `sessionHashPrefix`,
       la.`status`                AS `status`,
       LEFT(la.`urlHash`, 12)     AS `urlHashPrefix`
FROM `agent_legacy_attachment` la
WHERE la.`status` = 0
ORDER BY la.`id` ASC;

SELECT 'blocked-legacy-feedback-attachment' AS `dimension`,
       a.`id`                              AS `recordId`,
       a.`feedbackId`                     AS `feedbackId`,
       a.`status`                         AS `status`,
       LEFT(a.`legacyUrlHash`, 12)        AS `urlHashPrefix`
FROM `feedback_attachment` a
WHERE a.`status` = 3 AND a.`platform` = 'legacy'
ORDER BY a.`id` ASC;

-- ---------- 2. 重复有效空间（阻断回填，不自动选择/合并/删除） ----------

SELECT 'duplicate-valid-space' AS `dimension`,
       s.`id`                 AS `recordId`,
       s.`spaceType`          AS `spaceType`
FROM `space` s
JOIN (
  SELECT `userId`, `spaceType`
  FROM `space`
  WHERE `isDelete` = 0
  GROUP BY `userId`, `spaceType`
  HAVING COUNT(*) > 1
) d ON d.`userId` = s.`userId` AND d.`spaceType` = s.`spaceType`
WHERE s.`isDelete` = 0
ORDER BY s.`id` ASC;

-- ---------- 3. 无法安全恢复的执行状态（阻断续跑，不自动认领） ----------

SELECT 'unrecoverable-run-state' AS `dimension`,
       r.`id`                    AS `recordId`,
       r.`runStatus`             AS `status`,
       LEFT(SHA2(r.`threadId`, 256), 12) AS `threadHashPrefix`
FROM `agent_run_state` r
WHERE r.`runStatus` = 'PAUSED_UNRECOVERABLE'
   OR r.`legacyArchiveOnly` = 1
ORDER BY r.`id` ASC;

SELECT 'unknown-external-action' AS `dimension`,
       a.`id`                    AS `recordId`,
       a.`status`                AS `status`,
       LEFT(SHA2(a.`threadId`, 256), 12) AS `threadHashPrefix`
FROM `agent_action_record` a
WHERE a.`status` = 'UNKNOWN_NEEDS_REVIEW'
ORDER BY a.`id` ASC;

-- ---------- 4. 未收敛的补偿与配额偏差（阻断回滚，不静默丢弃） ----------

SELECT 'unresolved-cleanup-task' AS `dimension`,
       t.`id`                    AS `recordId`,
       t.`status`                AS `status`
FROM `storage_cleanup_task` t
WHERE t.`status` IN (0, 2, 3)
ORDER BY t.`id` ASC;

SELECT 'quota-deviation' AS `dimension`,
       s.`id`            AS `recordId`,
       s.`totalCount`    AS `storedCount`,
       COALESCE(p.`actualCount`, 0) AS `expectedCount`
FROM `space` s
LEFT JOIN (
  SELECT `spaceId`, COUNT(*) AS `actualCount`
  FROM `picture`
  WHERE `isDelete` = 0 AND `spaceId` IS NOT NULL
  GROUP BY `spaceId`
) p ON p.`spaceId` = s.`id`
WHERE s.`isDelete` = 0
  AND (s.`totalCount` <> COALESCE(p.`actualCount`, 0) OR s.`totalCount` < 0)
ORDER BY s.`id` ASC;

-- ---------- 5. 不得复活的安全状态（只给计数，不给行内值） ----------

SELECT 'voided-or-used-codes' AS `dimension`,
       COUNT(*)               AS `holdCount`
FROM `vip_redemption_code`
WHERE `status` IN (1, 2);

SELECT 'revoked-grants' AS `dimension`,
       COUNT(*)         AS `holdCount`
FROM `resource_access_grant`
WHERE `status` = 2;

SELECT 'active-quarantine' AS `dimension`,
       COUNT(*)            AS `holdCount`
FROM `resource_isolation`
WHERE `status` = 1 AND `activeKey` IS NOT NULL;

SELECT 'cleared-preference' AS `dimension`,
       COUNT(*)             AS `holdCount`
FROM `user_preference`
WHERE `explicitContent` IS NULL AND `learnedContent` IS NULL;

-- ---------- 6. 阻断汇总：任一 blockingCount 非零都不得放行 ----------

SELECT 'BLOCKING' AS `kind`, 'unknown-ownership' AS `dimension`,
       (SELECT COUNT(*) FROM `agent_legacy_attachment` WHERE `status` = 0)
     + (SELECT COUNT(*) FROM `feedback_attachment` WHERE `status` = 3 AND `platform` = 'legacy')
       AS `blockingCount`
UNION ALL
SELECT 'BLOCKING', 'duplicate-valid-space',
       (SELECT COUNT(*) FROM (
         SELECT `userId`, `spaceType` FROM `space` WHERE `isDelete` = 0
         GROUP BY `userId`, `spaceType` HAVING COUNT(*) > 1) d)
UNION ALL
SELECT 'BLOCKING', 'unrecoverable-state',
       (SELECT COUNT(*) FROM `agent_run_state` WHERE `runStatus` = 'PAUSED_UNRECOVERABLE' OR `legacyArchiveOnly` = 1)
     + (SELECT COUNT(*) FROM `agent_action_record` WHERE `status` = 'UNKNOWN_NEEDS_REVIEW')
UNION ALL
SELECT 'BLOCKING', 'unresolved-compensation',
       (SELECT COUNT(*) FROM `storage_cleanup_task` WHERE `status` IN (0, 2, 3))
UNION ALL
SELECT 'HOLD', 'do-not-revive',
       (SELECT COUNT(*) FROM `vip_redemption_code` WHERE `status` IN (1, 2))
     + (SELECT COUNT(*) FROM `resource_access_grant` WHERE `status` = 2)
     + (SELECT COUNT(*) FROM `resource_isolation` WHERE `status` = 1 AND `activeKey` IS NOT NULL);
