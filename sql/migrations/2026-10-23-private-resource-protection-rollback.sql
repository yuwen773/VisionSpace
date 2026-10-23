-- #36 私有资源管理与反馈保护：回滚
--
-- 只删除本工单新增的三张表，既有 user_feedback / picture 等数据不受影响。
-- 回滚不恢复已撤销的授权、不解除隔离：若仍有隔离中的资源，脚本主动失败，
-- 要求先按独立流程处理，避免"回滚即重新公开违规内容"或"回滚即恢复已撤权限"。
--
-- 阻断方式：向带 CHECK 约束的守卫表插入一行，存在未解除隔离时插入 0 触发
-- 3819（Check constraint ... is violated）。MySQL 不支持在 PREPARE 中执行 SIGNAL，
-- 而 CHECK 在任意 sql_mode 下都会求值，因此这是可移植的单语句守卫。
-- 脚本必须在"遇错即停"的客户端执行（mysql 命令行默认行为、JDBC 逐条 execute），
-- 不能加 --force。被阻断时守卫表会留在库里（无数据），这是预期状态：
-- 完成人工处置后重跑本脚本会先复用该表，再在成功路径上删除它。

SET @active_quarantine = (
  SELECT COUNT(*) FROM `resource_isolation` WHERE `status` = 1 AND `activeKey` IS NOT NULL
);
CREATE TABLE IF NOT EXISTS `_private_resource_rollback_guard` (
  `ok` tinyint NOT NULL,
  CONSTRAINT `BLOCKED_resolve_active_quarantine_before_rollback` CHECK (`ok` = 1)
) ENGINE = InnoDB COMMENT = '#36 回滚守卫：仅脚本执行期间存在，成功回滚后自动删除';
INSERT INTO `_private_resource_rollback_guard` (`ok`) VALUES (IF(@active_quarantine > 0, 0, 1));

DROP TABLE IF EXISTS `resource_isolation`;
DROP TABLE IF EXISTS `resource_access_grant`;
DROP TABLE IF EXISTS `feedback_attachment`;
DROP TABLE IF EXISTS `_private_resource_rollback_guard`;
