-- #40 存储切换（回滚迁移）
--
-- 回滚目标：让不依赖 storage_switch_state 的旧版本应用继续运行。
--   * 不删除该表：进行中或已完成的切换状态保留，供人工核对与再次前向迁移；
--   * 不改动表内任何行：activeVersion/pendingVersion 原样保留；
--   * 旧版本应用不读取该表，回滚后行为与升级前一致；
--   * storage_config.isActive 是旧版本唯一读取的生效标记，保持与本表
--     activeVersion 一致（完成确认的切换已同步迁移过；pending 未确认的
--     切换从未改动 isActive，无需回退）。
--
-- 本脚本幂等：列/行缺失时补齐，已存在时不做任何事。

-- 单行兜底（幂等）
INSERT IGNORE INTO `storage_switch_state` (`id`, `activeVersion`) VALUES (1, 1);
