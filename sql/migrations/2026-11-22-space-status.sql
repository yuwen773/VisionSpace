-- 管理端空间列表需要一个真实的状态字段来承载「启用 / 禁用」。
-- 之前前端按钮会提交 status，但表和 DTO 都没有该字段，操作实际不生效。
-- 默认 1（正常），历史数据补默认值后行为不变。
ALTER TABLE `space`
  ADD COLUMN `status` int(11) NOT NULL DEFAULT 1 COMMENT '空间状态：0-禁用 1-正常' AFTER `spaceType`;
