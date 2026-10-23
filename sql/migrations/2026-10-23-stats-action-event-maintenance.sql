-- #38 行为统计与推荐链路正确性：存量事件冲突与积压清单（只读）
--
-- 本脚本只读，不修改任何数据。运维在维护窗口内执行并保存输出，作为
-- 「已知冲突形成维护清单」的交付物。清单必须人工处置，不允许脚本自动改写。
--
-- 判定口径：
--   A. 已标记处理、但目标图片没有任何统计行 —— 旧版按时间窗整体标记完成，
--      存在「标记完成却没有入账」的历史缺口，计数可能丢失，需人工核对。
--   B. 未处理、但目标图片已不存在 —— 孤儿事件。新版本不会把它们计入任何图片，
--      也不允许静默丢弃，需人工确认后清理。
--   C. 未处理、且领取尝试次数已达阈值 —— 反复失败的事件，属于失败积压。
--   D. 未处理、且目标图片存在 —— 正常待处理事件，新版本会按持久状态领取。

-- A. 可能丢失计数的已处理事件
SELECT 'A-lost-count' AS conflictType,
       a.pictureId AS pictureId,
       COUNT(*) AS eventCount,
       MIN(a.createdTime) AS firstEventTime,
       MAX(a.createdTime) AS lastEventTime
FROM `user_picture_action` a
LEFT JOIN `picture_stats` s ON s.pictureId = a.pictureId
WHERE a.isProcessed = 1 AND s.pictureId IS NULL
GROUP BY a.pictureId
ORDER BY eventCount DESC;

-- B. 目标图片已不存在的未处理事件
SELECT 'B-orphan-pending' AS conflictType,
       a.pictureId AS pictureId,
       COUNT(*) AS eventCount,
       MIN(a.createdTime) AS firstEventTime,
       MAX(a.createdTime) AS lastEventTime
FROM `user_picture_action` a
LEFT JOIN `picture` p ON p.id = a.pictureId AND p.isDelete = 0
WHERE a.isProcessed = 0 AND p.id IS NULL
GROUP BY a.pictureId
ORDER BY eventCount DESC;

-- C. 反复领取失败的事件积压
SELECT 'C-stuck-pending' AS conflictType,
       a.pictureId AS pictureId,
       COUNT(*) AS eventCount,
       MAX(a.attemptCount) AS maxAttemptCount,
       MAX(a.claimExpireAt) AS lastClaimExpireAt
FROM `user_picture_action` a
WHERE a.isProcessed = 0 AND a.attemptCount >= 3
GROUP BY a.pictureId
ORDER BY maxAttemptCount DESC, eventCount DESC;

-- D. 正常待处理事件（迁移后由新版本按持久状态领取）
SELECT 'D-pending-normal' AS conflictType,
       COUNT(*) AS eventCount,
       MIN(a.createdTime) AS firstEventTime,
       MAX(a.createdTime) AS lastEventTime
FROM `user_picture_action` a
JOIN `picture` p ON p.id = a.pictureId AND p.isDelete = 0
WHERE a.isProcessed = 0 AND a.attemptCount < 3;

-- E. 只剩幂等台账、载荷已被保留期清理掉的事件。
--    这些事件已经入账，靠台账挡住重放：台账不能被清理，否则同一个事件重放会
--    重新变成一条待处理事件并二次入账。需要核对数量时看这里，不允许改写。
SELECT 'E-dedupe-evidence-only' AS conflictType,
       COUNT(*) AS eventCount,
       MIN(d.createdTime) AS firstEventTime,
       MAX(d.createdTime) AS lastEventTime
FROM `picture_action_event_dedupe` d
LEFT JOIN `user_picture_action` a ON a.eventId = d.eventId
WHERE a.id IS NULL;
