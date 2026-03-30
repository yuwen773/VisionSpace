-- 用户反馈表
CREATE TABLE IF NOT EXISTS user_feedback (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    userId        BIGINT NOT NULL COMMENT '反馈用户ID',
    type          INT NOT NULL DEFAULT 1 COMMENT '反馈类型：1=产品建议 2=内容举报 3=工单支持',
    title         VARCHAR(50) NOT NULL COMMENT '反馈标题',
    content       VARCHAR(2000) NOT NULL COMMENT '反馈内容',
    pictureUrls   TEXT NULL COMMENT '附件图片URLs（JSON数组）',
    status        INT NOT NULL DEFAULT 0 COMMENT '处理状态：0=待处理 1=处理中 2=已解决',
    handlerId     BIGINT NULL COMMENT '处理人管理员ID',
    handlerNote   VARCHAR(500) NULL COMMENT '管理员处理备注',
    handleTime    DATETIME NULL COMMENT '处理时间',
    createTime    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime    DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表';
