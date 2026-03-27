-- 为 picture 表添加统计计数字段
-- 执行时间: 2026-03-27
-- 说明: 为支持首页推荐系统，需要在 picture 表添加互动计数字段
-- 注意: 如果字段已存在，请手动注释掉对应的 ALTER 语句

-- 添加浏览次数
ALTER TABLE picture ADD COLUMN view_count BIGINT DEFAULT 0 COMMENT '浏览次数' AFTER pic_color;

-- 添加点赞次数
ALTER TABLE picture ADD COLUMN like_count BIGINT DEFAULT 0 COMMENT '点赞次数' AFTER view_count;

-- 添加收藏次数
ALTER TABLE picture ADD COLUMN collect_count BIGINT DEFAULT 0 COMMENT '收藏次数' AFTER like_count;

-- 添加下载次数
ALTER TABLE picture ADD COLUMN download_count BIGINT DEFAULT 0 COMMENT '下载次数' AFTER collect_count;

-- 添加分享次数
ALTER TABLE picture ADD COLUMN share_count BIGINT DEFAULT 0 COMMENT '分享次数' AFTER download_count;

-- 添加曝光次数
ALTER TABLE picture ADD COLUMN impression_count BIGINT DEFAULT 0 COMMENT '曝光次数' AFTER share_count;

-- 添加索引用于推荐查询
ALTER TABLE picture ADD INDEX idx_picture_stats (review_status, is_delete, view_count);
