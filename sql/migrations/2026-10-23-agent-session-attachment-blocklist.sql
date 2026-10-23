-- #35 存量会话图片脱敏阻断清单（只读报告）
--
-- 用途：人工核对归属未确认的存量会话图片。清单里的条目登记为待核对（status=0），
-- 不公开、不自动认领、不删除；本清单只回答「哪些存量图片还没有确认归属」。
--
-- 脱敏规则：清单本身不能变成新的直链来源，也不扩散个人信息。
--   · 不输出原始地址（源站直链）与存储对象键；
--   · 地址改用 SHA2 摘要前 12 位代替，核对时可与原始数据比对，又不泄漏地址；
--   · 不输出账号、昵称、头像等资料，只保留内部 id 用于核对。
--
-- 本脚本只读，不修改任何数据。

-- 明细：逐条待核对的存量图片
SELECT
  la.`id`                 AS `legacyAttachmentId`,
  la.`sessionId`          AS `sessionId`,
  la.`messageId`          AS `messageId`,
  la.`ownerUserId`        AS `ownerUserId`,
  la.`status`             AS `status`,
  LEFT(la.`urlHash`, 12)  AS `urlHashPrefix`,
  la.`createTime`         AS `createTime`
FROM `agent_legacy_attachment` la
WHERE la.`status` = 0
ORDER BY la.`sessionId` ASC, la.`messageId` ASC, la.`id` ASC;

-- 汇总：按会话聚合待核对数量，便于按会话逐条处理
SELECT
  la.`sessionId`          AS `sessionId`,
  la.`ownerUserId`        AS `ownerUserId`,
  COUNT(*)                AS `blockedCount`
FROM `agent_legacy_attachment` la
WHERE la.`status` = 0
GROUP BY la.`sessionId`, la.`ownerUserId`
ORDER BY `blockedCount` DESC, la.`sessionId` ASC;

-- 汇总：整体进度，便于核对「还有多少没有处理」
SELECT
  SUM(CASE WHEN la.`status` = 0 THEN 1 ELSE 0 END) AS `pendingCount`,
  SUM(CASE WHEN la.`status` = 1 THEN 1 ELSE 0 END) AS `verifiedCount`,
  SUM(CASE WHEN la.`status` = 2 THEN 1 ELSE 0 END) AS `rejectedCount`
FROM `agent_legacy_attachment` la;
