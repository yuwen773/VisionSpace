-- #40 存储切换与旧资源可用性（前向迁移）
--
-- 新增 storage_switch_state：存储切换的持久版本状态（单行，id = 1）。
-- 持久配置状态（storage_config.isActive）与运行生效状态（各实例内存快照）由此行衔接：
--   * activeVersion     最近一次完整确认生效的快照版本；
--   * pendingVersion    已占位但尚未被全部实例确认的候选版本（NULL 表示无切换进行中）；
--   * pendingConfigId / pendingPlatform / pendingTime   待生效目标；
--   * confirmCount / expectedInstances  双实例确认进度。
--
-- 切换流程（对应父规格 D20）：
--   1. 候选快照完整构建并验证（任一配置失败即整体失败，保留旧快照）；
--   2. claimSwitch 原子占位（WHERE pendingVersion IS NULL，跨实例只允许一个进行中的切换）；
--   3. 各实例确认生效（confirmCount 原子累加），达到 expectedInstances 后在同一个
--      事务里完成版本晋升与 storage_config.isActive 的迁移；
--   4. 部分生效期间保持明确的 pending 状态，可执行回滚（abortSwitch，
--      activeVersion 不变，各实例从持久配置重建快照）。
--
-- 存量数据：无需回填。应用启动时会以 INSERT IGNORE 保证单行存在。
-- 本脚本幂等：表已存在时整体不做任何事。
--
-- 回滚：执行同名 -rollback.sql。回滚保留该表（运行中的切换状态不丢失），
-- 旧版本应用不读取该表，行为与升级前一致。

SET @table_exists = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'storage_switch_state'
);
SET @ddl = IF(
  @table_exists = 0,
  'CREATE TABLE `storage_switch_state` (\n' ||
  '  `id` bigint(20) NOT NULL COMMENT ''固定为 1 的单行'',\n' ||
  '  `activeVersion` bigint(20) NOT NULL DEFAULT 1 COMMENT ''最近完整生效的快照版本'',\n' ||
  '  `pendingVersion` bigint(20) NULL DEFAULT NULL COMMENT ''待确认的候选版本'',\n' ||
  '  `pendingConfigId` bigint(20) NULL DEFAULT NULL COMMENT ''待生效配置 id'',\n' ||
  '  `pendingPlatform` varchar(32) NULL DEFAULT NULL COMMENT ''待生效平台'',\n' ||
  '  `pendingTime` datetime NULL DEFAULT NULL COMMENT ''切换占位时间'',\n' ||
  '  `confirmCount` int(11) NOT NULL DEFAULT 0 COMMENT ''已确认实例数'',\n' ||
  '  `expectedInstances` int(11) NOT NULL DEFAULT 1 COMMENT ''期望确认实例数'',\n' ||
  '  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',\n' ||
  '  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',\n' ||
  '  PRIMARY KEY (`id`) USING BTREE\n' ||
  ') ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = ''存储切换版本状态（单行）''',
  'SELECT ''storage_switch_state already exists, nothing to do'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 单行初始化：仅在该行不存在时插入（幂等，可重复执行）。
INSERT IGNORE INTO `storage_switch_state` (`id`, `activeVersion`) VALUES (1, 1);
