-- #36 旧反馈附件脱敏阻断清单（只读报告）
--
-- 用途：人工核对归属未确认的旧反馈附件。旧附件登记为 BLOCKED（status=3），
-- 不公开、不自动认领、不删除；本清单只回答"哪些附件还没有确认归属"。
--
-- 脱敏规则：清单本身不能变成新的直链来源，也不扩散个人信息。
--   · 不输出原始地址（/temp/{userId}/... 形式的旧直链）与存储对象键；
--   · 地址改用 SHA2 摘要前 12 位代替，核对时可与原始数据比对，又不泄漏地址；
--   · 不输出账号、昵称、头像等资料，只保留内部 id 用于核对。
--
-- 本脚本只读，不修改任何数据。

-- 明细：逐条待核对附件
SELECT
  a.`id`                AS `attachmentId`,
  a.`feedbackId`        AS `feedbackId`,
  a.`ownerUserId`       AS `ownerUserId`,
  a.`status`            AS `status`,
  LEFT(a.`legacyUrlHash`, 12) AS `urlHashPrefix`,
  a.`createTime`        AS `createTime`
FROM `feedback_attachment` a
WHERE a.`status` = 3
  AND a.`platform` = 'legacy'
ORDER BY a.`feedbackId` ASC, a.`id` ASC;

-- 汇总：按反馈聚合待核对数量，便于按反馈逐条处理
SELECT
  a.`feedbackId`        AS `feedbackId`,
  a.`ownerUserId`       AS `ownerUserId`,
  COUNT(*)              AS `blockedCount`
FROM `feedback_attachment` a
WHERE a.`status` = 3
  AND a.`platform` = 'legacy'
GROUP BY a.`feedbackId`, a.`ownerUserId`
ORDER BY `blockedCount` DESC, a.`feedbackId` ASC;
