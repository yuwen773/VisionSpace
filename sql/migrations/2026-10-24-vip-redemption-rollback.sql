-- #39 VIP 兑换（回滚迁移）
--
-- 回滚目标：让依赖 classpath JSON 兑换码的旧版本应用可以继续运行，
-- 同时不重新开放任何已失效的兑换码：
--   * 不删除 vip_redemption_code / vip_redemption_record 两张表：
--     已兑权益与已用状态必须保留，供人工核对与再次前向迁移。
--   * 不改动表内任何状态行：已作废（status = 2）与已用（status = 1）的码
--     不会被改回可用，也不写回任何明文码值。
--   * 仅恢复 user.vipCode 列（旧版本实体上有该字段映射）。列已存在时本脚本
--     幂等退出。该列回滚后保持 NULL，不会泄露或恢复任何码。
--
-- 兼容性要求（切换流程中已完成，见前向脚本注释）：
-- 运行时的 biz/vipCode.json 已被清空为 []，因此旧版本回滚后没有任何可兑换码，
-- 「失败不吞码」的已用码也不会因回滚变成可重试。

-- 仅在列缺失时补列；已存在则整体不做任何事（幂等）。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'vipCode'
);
SET @ddl = IF(
  @col_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `vipCode` varchar(128) NULL DEFAULT NULL COMMENT ''会员兑换码（兼容回滚保留，恒为 NULL）'' AFTER `vipExpireTime`',
  'SELECT ''user.vipCode already exists, nothing to do'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
