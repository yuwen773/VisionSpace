-- 为 picture 表添加统计计数字段
-- 执行时间: 2026-03-27
-- 说明: 为支持首页推荐系统，需要在 picture 表添加互动计数字段
-- 注意: 使用 camelCase 命名以匹配项目数据库规范
-- 注意: 如果字段已存在，请手动注释掉对应的 ALTER 语句

-- 添加浏览次数
ALTER TABLE picture ADD COLUMN viewCount BIGINT DEFAULT 0 COMMENT '浏览次数' AFTER picColor;

-- 添加点赞次数
ALTER TABLE picture ADD COLUMN likeCount BIGINT DEFAULT 0 COMMENT '点赞次数' AFTER viewCount;

-- 添加收藏次数
ALTER TABLE picture ADD COLUMN collectCount BIGINT DEFAULT 0 COMMENT '收藏次数' AFTER likeCount;

-- 添加下载次数
ALTER TABLE picture ADD COLUMN downloadCount BIGINT DEFAULT 0 COMMENT '下载次数' AFTER collectCount;

-- 添加分享次数
ALTER TABLE picture ADD COLUMN shareCount BIGINT DEFAULT 0 COMMENT '分享次数' AFTER downloadCount;

-- 添加曝光次数
ALTER TABLE picture ADD COLUMN impressionCount BIGINT DEFAULT 0 COMMENT '曝光次数' AFTER shareCount;

-- 添加索引用于推荐查询
ALTER TABLE picture ADD INDEX idx_picture_stats (reviewStatus, isDelete, viewCount);
