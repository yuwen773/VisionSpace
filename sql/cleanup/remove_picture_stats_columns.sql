-- 删除 picture 表中的冗余统计字段（迁移到 picture_stats 后执行）
-- 执行前请确认：
-- 1. 数据已迁移到 picture_stats
-- 2. 查询逻辑已更新为从 picture_stats 获取统计
-- 3. 无其他代码依赖 picture 表的统计字段

ALTER TABLE picture
DROP COLUMN viewCount,
DROP COLUMN likeCount,
DROP COLUMN collectCount,
DROP COLUMN downloadCount,
DROP COLUMN shareCount,
DROP COLUMN impressionCount,
DROP COLUMN ctr;