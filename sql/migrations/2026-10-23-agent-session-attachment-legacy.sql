-- #35 Agent 图片完整生命周期：存量会话图片清单回填（数据迁移）
--
-- 用途：把存量 agent_message 里带 <image-analysis> 地址的历史图片登记成一份
-- 可核对的清单。归属能确认的（会话所有者就是该地址对应的 agent-chat 资源
-- 所有者）登记为已确认并建立绑定，历史消息按受控入口渲染；归属确认不了的登记为
-- 待核对（阻断）——不自动认领、不自动删除、不重新公开已关闭的私人直链。
--
-- 脱敏：清单只存地址的 SHA2 摘要，不存原始地址，也不输出账号、昵称、头像。
--
-- 可重复执行：所有写入都用 INSERT IGNORE，重复执行不会产生重复行，也不会覆盖
-- 人工核对后的结论（已确认无归属的条目不会被改回待核对）。
--
-- 前置：先执行 2026-10-23-agent-session-attachment.sql 建好清单表与绑定表。
-- 回滚：执行同名 -legacy-rollback.sql。

-- 连接字符集显式设为 utf8mb4：本脚本含中文列注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

-- 1. 登记清单：逐条抽取历史消息里的图片地址，标注能否确认归属。
--    REGEXP_SUBSTR 的 occurrence 参数直接取第 n 处匹配，不需要递归查询。
INSERT IGNORE INTO `agent_legacy_attachment`
  (`sessionId`, `messageId`, `ownerUserId`, `urlHash`, `resourceId`, `status`, `createTime`)
WITH `nums` AS (
  SELECT 1 AS `n` UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
), `hits` AS (
  SELECT msg.`id` AS `messageId`,
         msg.`sessionId` AS `sessionId`,
         nums.`n` AS `n`,
         REGEXP_SUBSTR(msg.`content`, '<image-analysis>[^<]*</image-analysis>', 1, nums.`n`) AS `tag`
  FROM `agent_message` msg
  JOIN `nums` ON 1 = 1
  WHERE msg.`role` = 'USER'
    AND msg.`content` LIKE '%<image-analysis>%'
), `parsed` AS (
  SELECT `messageId`,
         `sessionId`,
         SUBSTRING(`tag`, CHAR_LENGTH('<image-analysis>') + 1,
                   CHAR_LENGTH(`tag`) - CHAR_LENGTH('<image-analysis>') - CHAR_LENGTH('</image-analysis>')) AS `url`
  FROM `hits`
  WHERE `tag` IS NOT NULL
)
SELECT p.`sessionId`,
       p.`messageId`,
       s.`userId`,
       SHA2(p.`url`, 256),
       pf.`id`,
       CASE WHEN pf.`id` IS NOT NULL AND pf.`ownerUserId` = s.`userId` THEN 1 ELSE 0 END,
       NOW()
FROM `parsed` p
JOIN `agent_session` s ON s.`sessionId` = p.`sessionId` AND s.`isDelete` = 0
LEFT JOIN `picture_file` pf ON pf.`id` = (
  SELECT f.`id` FROM `picture_file` f
  WHERE BINARY f.`url` = BINARY p.`url` AND f.`purpose` = 'agent-chat' AND f.`status` <> 2
  ORDER BY f.`id` DESC LIMIT 1)
WHERE p.`url` IS NOT NULL AND p.`url` <> '';

-- 2. 为已确认归属的条目建立绑定。绑定后图片保留到会话或图片被明确删除。
INSERT IGNORE INTO `agent_session_attachment`
  (`sessionId`, `resourceId`, `ownerUserId`, `status`, `legacy`, `createTime`)
SELECT `sessionId`, `resourceId`, `ownerUserId`, 0, 1, NOW()
FROM `agent_legacy_attachment`
WHERE `status` = 1 AND `resourceId` IS NOT NULL;

-- 3. 已确认归属的消息登记它携带的资源标识，历史按受控入口渲染。
--    只在登记为空时写入，不覆盖应用自己写入的值。
UPDATE `agent_message` m
JOIN (
  SELECT la.`messageId` AS `messageId`, GROUP_CONCAT(la.`resourceId` ORDER BY la.`id` ASC) AS `ids`
  FROM `agent_legacy_attachment` la
  WHERE la.`status` = 1 AND la.`resourceId` IS NOT NULL
  GROUP BY la.`messageId`
) t ON t.`messageId` = m.`id`
SET m.`attachmentIds` = t.`ids`
WHERE m.`attachmentIds` IS NULL OR m.`attachmentIds` = '';
