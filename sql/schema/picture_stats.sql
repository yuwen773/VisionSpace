-- 图片聚合统计表
-- 注意: 使用 camelCase 命名以匹配项目数据库规范
CREATE TABLE IF NOT EXISTS picture_stats (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    pictureId   BIGINT NOT NULL UNIQUE COMMENT '图片ID',
    viewCount   BIGINT DEFAULT 0 COMMENT '浏览次数',
    likeCount   BIGINT DEFAULT 0 COMMENT '点赞次数',
    collectCount BIGINT DEFAULT 0 COMMENT '收藏次数',
    downloadCount BIGINT DEFAULT 0 COMMENT '下载次数',
    shareCount  BIGINT DEFAULT 0 COMMENT '分享次数',
    impressionCount BIGINT DEFAULT 0 COMMENT '曝光次数',
    ctr         DECIMAL(6,4) DEFAULT 0 COMMENT '点击率',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pictureId (pictureId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
