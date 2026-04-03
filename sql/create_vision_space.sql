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
  PRIMARY KEY (`id`) USING BTREE,
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
  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `isProcessed` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否已聚合: 0-未处理 1-已处理',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_time`(`userId`, `createdTime`) USING BTREE,
  INDEX `idx_picture_time`(`pictureId`, `createdTime`) USING BTREE,
  INDEX `idx_action_type`(`actionType`) USING BTREE,
  INDEX `idx_isProcessed`(`isProcessed`) USING BTREE,
  INDEX `idx_processed_time`(`isProcessed`, `createdTime`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 909 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
