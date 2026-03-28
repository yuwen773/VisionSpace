CREATE TABLE IF NOT EXISTS user_picture_action (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    userId      BIGINT NOT NULL COMMENT '用户ID',
    pictureId   BIGINT NOT NULL COMMENT '图片ID',
    actionType  INT NOT NULL DEFAULT 0 COMMENT '行为类型: 0=impression 1=click 2=view 3=like 4=collect 5=download 6=share',
    actionValue INT NOT NULL DEFAULT 1 COMMENT '行为值',
    source      INT NOT NULL DEFAULT 0 COMMENT '来源: 0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER',
    createdTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_time (userId, createdTime DESC),
    INDEX idx_picture_time (pictureId, createdTime DESC),
    INDEX idx_action_type (actionType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
