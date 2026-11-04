-- #40 存储切换（回滚迁移）
--
-- 回滚目标：让不依赖 storage_switch_state / storage_switch_confirm 的旧版本应用继续运行。
--   * 不删除这两张表：进行中或已完成的切换状态保留，供人工核对与再次前向迁移；
--   * 不改动表内任何行：activeVersion/pendingVersion 与确认集合原样保留；
--   * 旧版本应用不读取这两张表，回滚后行为与升级前一致；
--   * storage_config.isActive 是旧版本唯一读取的生效标记，保持与本表
--     activeVersion 一致（完成确认的切换已同步迁移过；pending 未确认的
--     切换从未改动 isActive，无需回退）。
--
-- 本脚本幂等：行缺失时补齐，已存在时不做任何事。

-- 状态单行兜底（幂等）
INSERT IGNORE INTO `storage_switch_state` (`id`, `activeVersion`) VALUES (1, 1);

-- 对称恢复：前向迁移会条件 DROP 早期计数器列 confirmCount；若本环境回滚到
-- 仍依赖该列的中间版本，可在此条件重建（DEFAULT 0 保证现行应用不写它也能插入）。
SET @confirm_count_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'storage_switch_state'
    AND COLUMN_NAME = 'confirmCount'
);
SET @ddl = IF(
  @confirm_count_exists = 0,
  'ALTER TABLE `storage_switch_state` ADD COLUMN `confirmCount` int(11) NOT NULL DEFAULT 0 COMMENT ''（已废弃）确认计数器，仅中间版本使用''',
  'SELECT ''storage_switch_state.confirmCount already present, nothing to do'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
