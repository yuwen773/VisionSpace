-- 添加 isProcessed 标记字段
ALTER TABLE user_picture_action
ADD COLUMN isProcessed TINYINT NOT NULL DEFAULT 0 COMMENT '是否已聚合: 0-未处理 1-已处理';

-- 添加索引
ALTER TABLE user_picture_action
ADD INDEX idx_isProcessed (isProcessed),
ADD INDEX idx_createdTime (createdTime);

-- 组合索引优化查询
ALTER TABLE user_picture_action
ADD INDEX idx_processed_time (isProcessed, createdTime);
