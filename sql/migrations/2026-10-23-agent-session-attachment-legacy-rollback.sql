-- #35 Agent 图片完整生命周期：存量会话图片清单回填的回滚
--
-- 只撤销本工单的回填结果：
--   · 清掉由回填建立的历史绑定（legacy = 1）；
--   · 清空由回填写入的消息附件标识；
--   · 删除清单表里的全部登记条目。
--
-- 回滚不删除 picture_file 里的资源记录，也不删除任何对象：图片与附件本身保留。
-- 回滚不会重新公开已关闭的私人直链，也不会恢复已删除的对象。
-- 已经在回填之后由应用新建的绑定（legacy = 0）不受影响。

-- 连接字符集显式设为 utf8mb4：本脚本含中文列注释，不能依赖客户端默认字符集。
SET NAMES utf8mb4;

UPDATE `agent_message` m
SET m.`attachmentIds` = NULL
WHERE m.`attachmentIds` IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM `agent_legacy_attachment` la
    WHERE la.`messageId` = m.`id` AND la.`status` = 1 AND la.`resourceId` IS NOT NULL);

DELETE FROM `agent_session_attachment` WHERE `legacy` = 1;

DELETE FROM `agent_legacy_attachment`;
