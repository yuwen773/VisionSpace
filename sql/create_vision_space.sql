/*
 Navicat Premium Dump SQL

 Source Server         : localhost@5.7
 Source Server Type    : MySQL
 Source Server Version : 50719 (5.7.19)
 Source Host           : localhost:3306
 Source Schema         : vision_space

 Target Server Type    : MySQL
 Target Server Version : 50719 (5.7.19)
 File Encoding         : 65001

 Date: 31/03/2026 14:25:19
*/
create database if not exists vision_space default charset utf8mb4 collate utf8mb4_unicode_ci;
use vision_space;



SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for agent_message
-- ----------------------------
DROP TABLE IF EXISTS `agent_message`;
CREATE TABLE `agent_message`  (
                                  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `sessionId` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
                                  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色: USER/ASSISTANT/TOOL/SYSTEM',
                                  `subType` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息类型',
                                  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
                                  `attachmentIds` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息携带的图片资源标识，十进制逗号分隔',
                                  `tokenCount` int(11) NULL DEFAULT 0 COMMENT 'Token数量',
                                  `isSummary` tinyint(4) NULL DEFAULT 0 COMMENT '0=原始 1=摘要',
                                  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  PRIMARY KEY (`id`) USING BTREE,
                                  INDEX `idx_sessionId_time`(`sessionId`, `createdTime`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2039687491759828995 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Agent消息记录表' ROW_FORMAT = Dynamic;

--
-- ----------------------------
-- Table structure for agent_session
-- ----------------------------
DROP TABLE IF EXISTS `agent_session`;
CREATE TABLE `agent_session`  (
                                  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `sessionId` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID (threadId)',
                                  `userId` bigint(20) NOT NULL COMMENT '用户ID',
                                  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '新对话' COMMENT '会话标题',
                                  `modelName` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '使用的模型',
                                  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 0-结束 1-进行中',
                                  `lastMessage` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后一条消息摘要',
                                  `messageCount` int(11) NULL DEFAULT 0 COMMENT '消息条数',
                                  `summaryContent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '对话摘要',
                                  `summaryAt` datetime NULL DEFAULT NULL COMMENT '最后一次总结时间',
                                  `checkpointData` mediumblob NULL COMMENT 'Checkpoint JSON ???ݣ?gzipѹ????',
                                  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updatedTime` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `expiredTime` datetime NULL DEFAULT NULL COMMENT '过期时间',
                                  `isDelete` tinyint(4) NULL DEFAULT 0 COMMENT '是否删除',
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE INDEX `sessionId`(`sessionId`) USING BTREE,
                                  INDEX `idx_userId`(`userId`) USING BTREE,
                                  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2039687121910296579 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Agent会话表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for picture
-- ----------------------------
DROP TABLE IF EXISTS `picture`;
CREATE TABLE `picture`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍥剧墖 url',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍥剧墖鍚嶇О',
  `introduction` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绠?粙',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鍒嗙被',
  `tags` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鏍囩?锛圝SON 鏁扮粍锛',
  `picSize` bigint(20) NULL DEFAULT NULL COMMENT '鍥剧墖浣撶Н',
  `picWidth` int(11) NULL DEFAULT NULL COMMENT '鍥剧墖瀹藉害',
  `picHeight` int(11) NULL DEFAULT NULL COMMENT '鍥剧墖楂樺害',
  `picScale` double NULL DEFAULT NULL COMMENT '鍥剧墖瀹介珮姣斾緥',
  `picFormat` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鍥剧墖鏍煎紡',
  `userId` bigint(20) NOT NULL COMMENT '鍒涘缓鐢ㄦ埛 id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缂栬緫鏃堕棿',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `isDelete` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁鍒犻櫎',
  `reviewStatus` int(11) NOT NULL DEFAULT 0 COMMENT '瀹℃牳鐘舵?锛?-寰呭?鏍? 1-閫氳繃; 2-鎷掔粷',
  `reviewMessage` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '瀹℃牳淇℃伅',
  `reviewerId` bigint(20) NULL DEFAULT NULL COMMENT '瀹℃牳浜?ID',
  `reviewTime` datetime NULL DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `previewUrl` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预览图 url',
  `thumbnailUrl` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缂╃暐鍥?url',
  `spaceId` bigint(20) NULL DEFAULT NULL COMMENT '绌洪棿 id锛堜负绌鸿〃绀哄叕鍏辩┖闂达級',
  `picColor` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鍥剧墖涓昏壊璋',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE,
  INDEX `idx_introduction`(`introduction`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_tags`(`tags`) USING BTREE,
  INDEX `idx_userId`(`userId`) USING BTREE,
  INDEX `idx_reviewStatus`(`reviewStatus`) USING BTREE,
  INDEX `idx_spaceId`(`spaceId`) USING BTREE,
  INDEX `idx_picture_stats`(`reviewStatus`, `isDelete`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2038608856269361154 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '鍥剧墖' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for picture_stats
-- ----------------------------
DROP TABLE IF EXISTS `picture_stats`;
CREATE TABLE `picture_stats`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `pictureId` bigint(20) NOT NULL COMMENT '鍥剧墖ID',
  `viewCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鏌ョ湅娆℃暟',
  `likeCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鐐硅禐娆℃暟',
  `collectCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鏀惰棌娆℃暟',
  `downloadCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '涓嬭浇娆℃暟',
  `shareCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鍒嗕韩娆℃暟',
  `impressionCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鏇濆厜娆℃暟',
  `clickCount` bigint(20) NOT NULL DEFAULT 0 COMMENT '鐐瑰嚮娆℃暟',
  `ctr` decimal(10, 6) NULL DEFAULT 0.000000 COMMENT '鐐瑰嚮鐜',
  `createTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updateTime` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `pictureId`(`pictureId`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for space
-- ----------------------------
DROP TABLE IF EXISTS `space`;
CREATE TABLE `space`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceName` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绌洪棿鍚嶇О',
  `spaceLevel` int(11) NULL DEFAULT 0 COMMENT '绌洪棿绾у埆锛?-鏅??鐗?1-涓撲笟鐗?2-鏃楄埌鐗',
  `maxSize` bigint(20) NULL DEFAULT 0 COMMENT '绌洪棿鍥剧墖鐨勬渶澶ф?澶у皬',
  `maxCount` bigint(20) NULL DEFAULT 0 COMMENT '绌洪棿鍥剧墖鐨勬渶澶ф暟閲',
  `totalSize` bigint(20) NULL DEFAULT 0 COMMENT '褰撳墠绌洪棿涓嬪浘鐗囩殑鎬诲ぇ灏',
  `totalCount` bigint(20) NULL DEFAULT 0 COMMENT '褰撳墠绌洪棿涓嬬殑鍥剧墖鏁伴噺',
  `userId` bigint(20) NOT NULL COMMENT '鍒涘缓鐢ㄦ埛 id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缂栬緫鏃堕棿',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `isDelete` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁鍒犻櫎',
  `spaceType` int(11) NOT NULL DEFAULT 0 COMMENT '绌洪棿绫诲瀷锛?-绉佹湁 1-鍥㈤槦',
  `activeKey` varchar(64) NULL DEFAULT NULL COMMENT '有效空间唯一占位键 userId:spaceType，删除后置空',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_space_active_key`(`activeKey`) USING BTREE,
  INDEX `idx_userId`(`userId`) USING BTREE,
  INDEX `idx_spaceName`(`spaceName`) USING BTREE,
  INDEX `idx_spaceLevel`(`spaceLevel`) USING BTREE,
  INDEX `idx_spaceType`(`spaceType`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2037735393402068994 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '绌洪棿' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for space_user
-- ----------------------------
DROP TABLE IF EXISTS `space_user`;
CREATE TABLE `space_user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceId` bigint(20) NOT NULL COMMENT '绌洪棿 id',
  `userId` bigint(20) NOT NULL COMMENT '鐢ㄦ埛 id',
  `spaceRole` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'viewer' COMMENT '绌洪棿瑙掕壊锛歷iewer/editor/admin',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_spaceId_userId`(`spaceId`, `userId`) USING BTREE,
  INDEX `idx_userId`(`userId`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '绌洪棿鐢ㄦ埛鍏宠仈' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for storage_config
-- ----------------------------
DROP TABLE IF EXISTS `storage_config`;
CREATE TABLE `storage_config`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `platform` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '骞冲彴鏍囪瘑锛歮inio/cos/oss/obs',
  `platformName` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鏄剧ず鍚嶇О',
  `endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '璇锋眰鍦板潃',
  `region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鍖哄煙',
  `bucket` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Bucket鍚嶇О',
  `accessKey` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AccessKey',
  `secretKey` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SecretKey',
  `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鑷?畾涔夊煙鍚',
  `basePath` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '瀛樺偍鍩虹?璺?緞',
  `isActive` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁婵?椿锛?=婵?椿锛',
  `isDefault` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁榛樿?骞冲彴',
  `orderNum` int(11) NULL DEFAULT 0 COMMENT '鎺掑簭',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '鐘舵?锛?-绂佺敤 1-鍚?敤',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `isDelete` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁鍒犻櫎',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_platform`(`platform`) USING BTREE,
  INDEX `idx_isActive`(`isActive`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2037095872441008130 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '瀛樺偍骞冲彴閰嶇疆' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userAccount` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璐﹀彿',
  `userPassword` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瀵嗙爜',
  `userName` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鐢ㄦ埛鏄电О',
  `userAvatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鐢ㄦ埛澶村儚',
  `userProfile` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '鐢ㄦ埛绠?粙',
  `userRole` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '鐢ㄦ埛瑙掕壊锛歶ser/admin',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缂栬緫鏃堕棿',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `isDelete` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁鍒犻櫎',
  `vipExpireTime` datetime NULL DEFAULT NULL COMMENT '浼氬憳杩囨湡鏃堕棿',
  `vipCode` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '浼氬憳鍏戞崲鐮',
  `vipNumber` bigint(20) NULL DEFAULT NULL COMMENT '浼氬憳缂栧彿',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_userAccount`(`userAccount`) USING BTREE,
  INDEX `idx_userName`(`userName`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1911004288852279298 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '鐢ㄦ埛' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_feedback
-- ----------------------------
DROP TABLE IF EXISTS `user_feedback`;
CREATE TABLE `user_feedback`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `userId` bigint(20) NOT NULL COMMENT '鍙嶉?鐢ㄦ埛ID',
  `type` int(11) NOT NULL DEFAULT 1 COMMENT '鍙嶉?绫诲瀷锛?=浜у搧寤鸿? 2=鍐呭?涓炬姤 3=宸ュ崟鏀?寔',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙嶉?鏍囬?',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙嶉?鍐呭?',
  `pictureUrls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '闄勪欢鍥剧墖URLs锛圝SON鏁扮粍锛',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '澶勭悊鐘舵?锛?=寰呭?鐞?1=澶勭悊涓?2=宸茶В鍐',
  `handlerId` bigint(20) NULL DEFAULT NULL COMMENT '澶勭悊浜虹?鐞嗗憳ID',
  `handlerNote` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绠＄悊鍛樺?鐞嗗?娉',
  `handleTime` datetime NULL DEFAULT NULL COMMENT '澶勭悊鏃堕棿',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updateTime` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `isDelete` tinyint(4) NOT NULL DEFAULT 0 COMMENT '鏄?惁鍒犻櫎',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_userId`(`userId`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2038530553923678211 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '鐢ㄦ埛鍙嶉?琛' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_picture_action
-- ----------------------------
DROP TABLE IF EXISTS `user_picture_action`;
CREATE TABLE `user_picture_action`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `userId` bigint(20) NOT NULL COMMENT '用户ID',
  `pictureId` bigint(20) NOT NULL COMMENT '图片ID',
  `actionType` int(11) NOT NULL DEFAULT 0 COMMENT '行为类型: 0=impression 1=click 2=view 3=like 4=collect 5=download 6=share',
  `actionValue` int(11) NOT NULL DEFAULT 1 COMMENT '行为值',
  `source` int(11) NOT NULL DEFAULT 0 COMMENT '来源: 0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER',
  `eventId` varchar(128) NULL DEFAULT NULL COMMENT '事件幂等键: 唯一索引保证同一事件重放不重复入账',
  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `isProcessed` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否已完成聚合: 0-未处理 1-已处理',
  `isAccumulated` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否已计入统计: 0-未计入 1-已计入',
  `claimToken` varchar(64) NULL DEFAULT NULL COMMENT '领取令牌: 持有本次领取租约的聚合运行',
  `claimedAt` datetime NULL DEFAULT NULL COMMENT '领取时间',
  `claimExpireAt` datetime NULL DEFAULT NULL COMMENT '领取租约到期时间',
  `attemptCount` int(11) NOT NULL DEFAULT 0 COMMENT '领取尝试次数: 反复领取失败的事件据此可见',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_eventId`(`eventId`) USING BTREE,
  INDEX `idx_user_time`(`userId`, `createdTime`) USING BTREE,
  INDEX `idx_picture_time`(`pictureId`, `createdTime`) USING BTREE,
  INDEX `idx_action_type`(`actionType`) USING BTREE,
  INDEX `idx_isProcessed`(`isProcessed`) USING BTREE,
  INDEX `idx_processed_time`(`isProcessed`, `createdTime`) USING BTREE,
  INDEX `idx_claim`(`isProcessed`, `isAccumulated`, `claimExpireAt`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 909 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- 行为事件幂等台账：重复判定的可靠凭证，与事件载荷的保留期分开
DROP TABLE IF EXISTS `picture_action_event_dedupe`;
CREATE TABLE `picture_action_event_dedupe`  (
  `eventId` varchar(128) NOT NULL COMMENT '事件幂等键: 载荷清理后仍然挡住同一事件的重放',
  `createdTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次受理该事件的时间',
  PRIMARY KEY (`eventId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;


CREATE TABLE `mcp_server` (
                              `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
                              `mcpServerCode`  VARCHAR(64)  NOT NULL COMMENT '服务器编码',
                              `userId`         BIGINT      NOT NULL COMMENT '所属用户ID',
                              `name`           VARCHAR(128) NOT NULL COMMENT '服务器名称',
                              `description`    VARCHAR(512) COMMENT '描述',
                              `deployConfig`   TEXT         NOT NULL COMMENT '部署配置(JSON)',
                              `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用 1=启用 3=删除',
                              `installType`    VARCHAR(32)  NOT NULL DEFAULT 'SSE' COMMENT '安装类型',
                              `host`           VARCHAR(256) COMMENT 'MCP服务器地址',
                              `gmtCreate`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `gmtModified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              UNIQUE KEY `uk_mcp_server_code_user` (`mcpServerCode`, `userId`),
                              KEY `idx_user_id` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务器配置表';

-- ----------------------------
-- Table structure for picture_file（#34 资源记录）
-- ----------------------------
CREATE TABLE `picture_file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资源标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '所有者用户 id',
  `spaceId` bigint(20) NULL DEFAULT NULL COMMENT '空间 id（公共图库为空）',
  `pictureId` bigint(20) NULL DEFAULT NULL COMMENT '绑定后的图片 id（临时上传为空）',
  `purpose` varchar(32) NOT NULL COMMENT '用途：gallery / agent-chat',
  `kind` varchar(16) NOT NULL COMMENT '内容类型：original / thumbnail / preview',
  `platform` varchar(32) NOT NULL COMMENT '存储平台标识',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键（含 basePath）',
  `url` varchar(1024) NULL DEFAULT NULL COMMENT '上传时返回的地址，仅作记录',
  `fileSize` bigint(20) NULL DEFAULT NULL COMMENT '文件字节数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=TEMP 1=ACTIVE 2=DELETED 3=SUPERSEDED',
  `deleteState` int(11) NOT NULL DEFAULT 0 COMMENT '0=无删除任务 1=待重试 2=已确认删除',
  `deleteRetryCount` int(11) NOT NULL DEFAULT 0 COMMENT '删除重试次数',
  `deleteNextRetryTime` datetime NULL DEFAULT NULL COMMENT '下次重试时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_picture_kind`(`pictureId`, `kind`, `status`) USING BTREE,
  INDEX `idx_owner_purpose_status`(`ownerUserId`, `purpose`, `status`) USING BTREE,
  INDEX `idx_platform_key`(`platform`, `objectKey`(191)) USING BTREE,
  INDEX `idx_url`(`url`(191)) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图片资源记录';

-- ----------------------------
-- Table structure for storage_cleanup_task（#37 存储清理补偿）
-- ----------------------------
CREATE TABLE `storage_cleanup_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务标识',
  `pictureFileId` bigint(20) NULL DEFAULT NULL COMMENT '资源记录 id；上传失败时为空',
  `pictureId` bigint(20) NULL DEFAULT NULL COMMENT '图片 id；临时资源为空',
  `platform` varchar(32) NULL DEFAULT NULL COMMENT '存储平台标识',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键',
  `kind` varchar(16) NULL DEFAULT NULL COMMENT '内容类型：original / thumbnail / preview',
  `reason` varchar(128) NOT NULL COMMENT '触发清理的原因',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=待重试 1=已确认删除 2=引用复核不通过，等待人工处理',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `nextRetryTime` datetime NULL DEFAULT NULL COMMENT '下次重试时间（数据库时钟）',
  `lastError` varchar(255) NULL DEFAULT NULL COMMENT '最近一次失败原因，不含秘密值',
  `pendingKey` varchar(255) NULL DEFAULT NULL COMMENT '待处理唯一键 platform|kind|objectKey；进入终态后置空',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pending_object`(`pendingKey`) USING BTREE,
  INDEX `idx_status_retry`(`status`, `nextRetryTime`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对象存储清理任务';

-- ----------------------------
-- Table structure for agent_run_state (#41)
-- ----------------------------
CREATE TABLE `agent_run_state` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `threadId` varchar(64) NOT NULL COMMENT '会话 id',
  `userId` bigint(20) NOT NULL COMMENT '会话归属用户（读取与恢复的唯一依据）',
  `stateVersion` bigint(20) NOT NULL DEFAULT 0 COMMENT '状态版本，单调递增；写入按它做 CAS',
  `runEpoch` bigint(20) NOT NULL DEFAULT 0 COMMENT '执行世代：每次执行开始时递增，迟到旧载荷据此被拒',
  `stateFormat` varchar(32) NOT NULL COMMENT '状态格式版本，例如 v2-full',
  `checkpointId` varchar(128) NULL DEFAULT NULL COMMENT '完整恢复点 id',
  `nodeId` varchar(128) NULL DEFAULT NULL COMMENT '已完成的节点',
  `nextNodeId` varchar(128) NULL DEFAULT NULL COMMENT '下一步应执行的节点',
  `statePayload` mediumblob NULL COMMENT '完整状态（gzip 后的序列化数据），不是仅消息',
  `runStatus` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/AWAITING_CONFIRMATION/COMPLETED/FAILED/PAUSED_UNRECOVERABLE',
  `pendingConfirmation` text NULL COMMENT '待人工确认信息（已脱敏）',
  `mcpServerCodes` text NULL COMMENT '本次执行使用的 MCP 服务 code（JSON 数组，只存 code，不存凭据/地址）',
  `resumeLease` varchar(64) NULL DEFAULT NULL COMMENT '当前续跑请求的租约凭据，防止重复续跑',
  `resumeLeaseUntil` datetime NULL DEFAULT NULL COMMENT '续跑租约到期时间；仅用于回收崩溃遗留的租约',
  `legacyArchiveOnly` tinyint(4) NOT NULL DEFAULT 0 COMMENT '1=仅有旧轻量归档，可查看但不可续跑',
  `backupError` varchar(255) NULL DEFAULT NULL COMMENT '最近一次备份失败原因（不含正文/秘密）',
  `backupAttempts` int(11) NOT NULL DEFAULT 0 COMMENT '备份尝试次数',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_thread`(`threadId`) USING BTREE,
  INDEX `idx_user_status`(`userId`, `runStatus`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 完整执行状态';

-- ----------------------------
-- Table structure for agent_action_record (#41)
-- ----------------------------
CREATE TABLE `agent_action_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `threadId` varchar(64) NOT NULL COMMENT '会话 id',
  `userId` bigint(20) NOT NULL COMMENT '会话归属用户',
  `actionId` varchar(128) NOT NULL COMMENT '可核对的动作标识（threadId + toolCallId 派生）',
  `toolName` varchar(128) NOT NULL COMMENT '工具名',
  `argumentsDigest` varchar(128) NULL DEFAULT NULL COMMENT '参数摘要（sha-256），不保存正文',
  `status` varchar(32) NOT NULL COMMENT 'EXECUTING/SUCCEEDED/FAILED/UNKNOWN_NEEDS_REVIEW/DISMISSED',
  `idempotency` varchar(16) NOT NULL DEFAULT 'NOT_PROVEN' COMMENT 'PROVEN 才允许结果未知时重试',
  `claimToken` varchar(64) NULL DEFAULT NULL COMMENT '本次认领凭据，只有持有者能推进状态',
  `attempt` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `resultSummary` varchar(255) NULL DEFAULT NULL COMMENT '结果摘要（截断，不含正文）',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_thread_action`(`threadId`, `actionId`) USING BTREE,
  INDEX `idx_thread_status`(`threadId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 外部动作台账';

-- ----------------------------
-- Table structure for vip_redemption_code (#39)
-- ----------------------------
CREATE TABLE `vip_redemption_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `codeHash` char(64) NOT NULL COMMENT '兑换码摘要（sha-256），不保存明文',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态: 0-可用 1-已用 2-已作废',
  `usedBy` bigint(20) NULL DEFAULT NULL COMMENT '核销用户',
  `usedTime` datetime NULL DEFAULT NULL COMMENT '核销时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codeHash`(`codeHash`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'VIP 兑换码（只存摘要）';

-- ----------------------------
-- Table structure for vip_redemption_record (#39)
-- ----------------------------
CREATE TABLE `vip_redemption_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userId` bigint(20) NOT NULL COMMENT '兑换用户',
  `codeId` bigint(20) NOT NULL COMMENT '兑换码记录 id',
  `codeHash` char(64) NOT NULL COMMENT '兑换码摘要（sha-256），不保存明文',
  `previousExpireTime` datetime NULL DEFAULT NULL COMMENT '兑换前到期时间',
  `expireTime` datetime NOT NULL COMMENT '兑换后到期时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codeId`(`codeId`) USING BTREE,
  INDEX `idx_userId`(`userId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'VIP 兑换流水';
-- Table structure for agent_session_attachment (#35)
-- ----------------------------
CREATE TABLE `agent_session_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '绑定标识',
  `sessionId` varchar(64) NOT NULL COMMENT '会话标识',
  `resourceId` bigint(20) NOT NULL COMMENT 'picture_file 资源标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '附件所有者用户 id',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=BOUND 已绑定 1=RELEASED 已解除',
  `legacy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=由存量迁移登记',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_session_resource` (`sessionId`, `resourceId`) USING BTREE,
  INDEX `idx_resource_status` (`resourceId`, `status`) USING BTREE,
  INDEX `idx_session_status` (`sessionId`, `status`) USING BTREE,
  INDEX `idx_owner_status` (`ownerUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 会话附件绑定';

-- ----------------------------
-- Table structure for agent_legacy_attachment (#35)
-- ----------------------------
CREATE TABLE `agent_legacy_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '清单标识',
  `sessionId` varchar(64) NOT NULL COMMENT '会话标识',
  `messageId` bigint(20) NOT NULL COMMENT '消息标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '会话所有者用户 id',
  `urlHash` varchar(64) NOT NULL COMMENT '源站地址的 SHA2 摘要（不存原始地址）',
  `resourceId` bigint(20) NULL DEFAULT NULL COMMENT '确认归属后的资源标识，归属不明时为空',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=待核对（阻断） 1=已确认归属 2=已确认无归属',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_message_hash` (`messageId`, `urlHash`(32)) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_session` (`sessionId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '存量会话图片清单';

-- ----------------------------
-- #36 私有资源管理与反馈保护（反馈附件 / 限时只读授权 / 违规隔离）
-- ----------------------------
CREATE TABLE `feedback_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '附件标识',
  `ownerUserId` bigint(20) NOT NULL COMMENT '上传者用户 id',
  `feedbackId` bigint(20) NULL DEFAULT NULL COMMENT '绑定后的反馈 id；未提交时为空',
  `platform` varchar(32) NOT NULL COMMENT '存储平台标识；旧附件为 legacy',
  `objectKey` varchar(768) NOT NULL COMMENT '存储对象键；旧附件保留地址原文',
  `url` varchar(1024) NULL DEFAULT NULL COMMENT '上传时返回的地址，仅作记录，不对外返回',
  `fileSize` bigint(20) NULL DEFAULT NULL COMMENT '文件字节数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0=UPLOADED 1=BOUND 2=LEGACY_VERIFIED 3=BLOCKED',
  `legacyUrlHash` char(64) NULL DEFAULT NULL COMMENT '旧附件地址摘要，仅用于幂等登记',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_legacy_url_hash`(`legacyUrlHash`) USING BTREE,
  INDEX `idx_feedback_status`(`feedbackId`, `status`) USING BTREE,
  INDEX `idx_owner_status`(`ownerUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '反馈附件记录';

CREATE TABLE `resource_access_grant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '授权标识',
  `resourceType` varchar(32) NOT NULL COMMENT '资源类型：picture',
  `resourceId` bigint(20) NOT NULL COMMENT '资源 id',
  `granteeUserId` bigint(20) NOT NULL COMMENT '被授权查看者',
  `grantedByUserId` bigint(20) NOT NULL COMMENT '授权主体（个人资源所有者或团队管理员）',
  `reason` varchar(500) NULL DEFAULT NULL COMMENT '授权理由',
  `expiresTime` datetime NOT NULL COMMENT '到期时间，必填',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '1=ACTIVE 2=REVOKED 3=EXPIRED',
  `activeKey` varchar(128) NULL DEFAULT NULL COMMENT '有效授权唯一键；撤销或到期后置空',
  `revokedByUserId` bigint(20) NULL DEFAULT NULL COMMENT '撤销人',
  `revokeReason` varchar(500) NULL DEFAULT NULL COMMENT '撤销理由',
  `revokeTime` datetime NULL DEFAULT NULL COMMENT '撤销时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_active_key`(`activeKey`) USING BTREE,
  INDEX `idx_resource_status`(`resourceType`, `resourceId`, `status`) USING BTREE,
  INDEX `idx_grantee_status`(`granteeUserId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '单资源限时只读授权';

CREATE TABLE `resource_isolation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '隔离记录标识',
  `resourceType` varchar(32) NOT NULL COMMENT '资源类型：picture',
  `resourceId` bigint(20) NOT NULL COMMENT '资源 id',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '1=QUARANTINED 2=RELEASED',
  `activeKey` varchar(128) NULL DEFAULT NULL COMMENT '隔离中唯一键；解除后置空',
  `operatorId` bigint(20) NOT NULL COMMENT '隔离操作者',
  `reason` varchar(500) NOT NULL COMMENT '隔离理由',
  `releaseOperatorId` bigint(20) NULL DEFAULT NULL COMMENT '恢复操作者',
  `releaseReason` varchar(500) NULL DEFAULT NULL COMMENT '恢复理由',
  `releaseTime` datetime NULL DEFAULT NULL COMMENT '恢复时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '隔离时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_active_key`(`activeKey`) USING BTREE,
  INDEX `idx_resource_status`(`resourceType`, `resourceId`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '违规资源隔离与恢复';
