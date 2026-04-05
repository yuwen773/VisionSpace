-- 创建库
create database if not exists yu_picture;

-- 切换库
use yu_picture;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;


ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512) NULL COMMENT '审核信息',
    ADD COLUMN reviewerId BIGINT NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime DATETIME NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url';

ALTER TABLE picture
    ADD COLUMN previewUrl varchar(512) NULL COMMENT '预览图 url';


-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    -- 索引设计
    index idx_userId (userId),        -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),  -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel) -- 提升按空间级别查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;

-- 添加新列
ALTER TABLE picture
    ADD COLUMN spaceId bigint  null comment '空间 id（为空表示公共空间）';

-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);

-- 添加新列
ALTER TABLE picture
    ADD COLUMN picColor varchar(16) null comment '图片主色调';

-- 支持空间类型，添加新列
ALTER TABLE space
    ADD COLUMN spaceType int default 0 not null comment '空间类型：0-私有 1-团队';

CREATE INDEX idx_spaceType ON space (spaceType);

-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' collate = utf8mb4_unicode_ci;

-- 扩展用户表：新增会员功能
ALTER TABLE user
    ADD COLUMN vipExpireTime datetime NULL COMMENT '会员过期时间',
    ADD COLUMN vipCode varchar(128) NULL COMMENT '会员兑换码',
    ADD COLUMN vipNumber bigint NULL COMMENT '会员编号';

-- 存储平台配置表
create table if not exists storage_config
(
    id           bigint auto_increment comment 'id' primary key,
    platform     varchar(32)                           not null comment '平台标识：minio/cos/oss/obs',
    platformName varchar(64)                           null comment '显示名称',
    endpoint     varchar(255)                          null comment '请求地址',
    region       varchar(64)                           null comment '区域',
    bucket       varchar(128)                          not null comment 'Bucket名称',
    accessKey    varchar(128)                          null comment 'AccessKey',
    secretKey    varchar(255)                          null comment 'SecretKey',
    domain       varchar(255)                          null comment '自定义域名',
    basePath     varchar(128)                          null comment '存储基础路径',
    isActive     tinyint   default 0                   not null comment '是否激活（1=激活）',
    isDefault    tinyint   default 0                   not null comment '是否默认平台',
    orderNum     int      default 0                    null comment '排序',
    status       tinyint   default 1                   not null comment '状态：0-禁用 1-启用',
    createTime   datetime default CURRENT_TIMESTAMP    not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP    not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint   default 0                   not null comment '是否删除',
    unique key uk_platform (platform),
    index idx_isActive (isActive),
    index idx_status (status)
) comment '存储平台配置' collate = utf8mb4_unicode_ci;

-- 图片聚合统计表
CREATE TABLE IF NOT EXISTS picture_stats (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    pictureId   BIGINT NOT NULL UNIQUE COMMENT '图片ID',
    viewCount   BIGINT NOT NULL DEFAULT 0 COMMENT '查看次数',
    likeCount   BIGINT NOT NULL DEFAULT 0 COMMENT '点赞次数',
    collectCount BIGINT NOT NULL DEFAULT 0 COMMENT '收藏次数',
    downloadCount BIGINT NOT NULL DEFAULT 0 COMMENT '下载次数',
    shareCount  BIGINT NOT NULL DEFAULT 0 COMMENT '分享次数',
    impressionCount BIGINT NOT NULL DEFAULT 0 COMMENT '曝光次数',
    clickCount  BIGINT NOT NULL DEFAULT 0 COMMENT '点击次数',
    ctr         DECIMAL(10,6) DEFAULT 0 COMMENT '点击率',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- Agent 会话持久化相关表
-- =====================================================

-- Agent 会话表
CREATE TABLE IF NOT EXISTS agent_session (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sessionId           VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID (threadId)',
    userId              BIGINT NOT NULL COMMENT '用户ID',
    title               VARCHAR(200) DEFAULT '新对话' COMMENT '会话标题',
    modelName           VARCHAR(50) COMMENT '使用的模型',
    status              TINYINT DEFAULT 1 COMMENT '状态: 0-结束 1-进行中',
    lastMessage         VARCHAR(200) COMMENT '最后一条消息摘要',
    messageCount        INT DEFAULT 0 COMMENT '消息条数',
    summaryContent      TEXT COMMENT '对话摘要',
    summaryAt           DATETIME COMMENT '最后一次总结时间',
    checkpointData      MEDIUMBLOB COMMENT 'Checkpoint JSON 备份（gzip压缩）',
    createdTime         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updatedTime         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expiredTime         DATETIME COMMENT '过期时间',
    isDelete            TINYINT DEFAULT 0 COMMENT '是否删除',

    INDEX idx_userId (userId),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

-- Agent 消息记录表
CREATE TABLE IF NOT EXISTS agent_message (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sessionId           VARCHAR(64) NOT NULL COMMENT '会话ID',
    role                VARCHAR(20) NOT NULL COMMENT '角色: USER/ASSISTANT',
    subType             VARCHAR(20) NULL COMMENT '子类型: text/reasoning/tool-call/tool-result/tool-confirm（USER为NULL）',
    content             TEXT NOT NULL COMMENT '消息内容',
    tokenCount          INT DEFAULT 0 COMMENT 'Token数量',
    isSummary           TINYINT DEFAULT 0 COMMENT '0=原始 1=摘要',
    createdTime         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_sessionId_time (sessionId, createdTime DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息记录表';
