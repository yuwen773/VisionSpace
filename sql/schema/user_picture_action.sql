CREATE TABLE IF NOT EXISTS user_picture_action (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    picture_id  BIGINT NOT NULL COMMENT '图片ID',
    action_type  VARCHAR(32) NOT NULL COMMENT '行为类型: impression/click/view/like/collect/download/share',
    action_value INT NOT NULL DEFAULT 1 COMMENT '行为值',
    source      VARCHAR(32) DEFAULT 'HOME' COMMENT '来源: HOME/SEARCH/DETAIL/OTHER',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_time (user_id, created_time DESC),
    INDEX idx_picture_time (picture_id, created_time DESC),
    INDEX idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
