-- 图片聚合统计表
CREATE TABLE IF NOT EXISTS picture_stats (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    picture_id  BIGINT NOT NULL UNIQUE COMMENT '图片ID',
    view_count   BIGINT DEFAULT 0 COMMENT '浏览次数',
    like_count   BIGINT DEFAULT 0 COMMENT '点赞次数',
    collect_count BIGINT DEFAULT 0 COMMENT '收藏次数',
    download_count BIGINT DEFAULT 0 COMMENT '下载次数',
    share_count  BIGINT DEFAULT 0 COMMENT '分享次数',
    impression_count BIGINT DEFAULT 0 COMMENT '曝光次数',
    ctr          DECIMAL(6,4) DEFAULT 0 COMMENT '点击率',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_picture_id (picture_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;