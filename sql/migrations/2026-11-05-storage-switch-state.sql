-- #40 存储切换与旧资源可用性（前向迁移）
--
-- 新增两张表：
--
-- storage_switch_state（单行，id = 1）：切换全局状态。
--   * activeVersion      最近一次完整确认生效的快照版本；
--   * pendingVersion     已占位但尚未被全部实例确认的候选版本（NULL 表示无切换进行中）；
--   * pendingConfigId / pendingPlatform / pendingTime   待生效目标；
--   * expectedInstances  期望确认实例数（占位时写入）。
--
-- storage_switch_confirm：确认实例集合（不是次数计数器）。
--   主键 (version, instanceId)，INSERT IGNORE 保证同一实例对同一版本
--   重复确认（重试、重启后再次确认）只计一次，杜绝确认数虚增导致的提前晋升。
--   instanceId 为实例本地持久化的 UUID（vs.instance-id-file）：同一物理实例
--   跨重启复用同一标识，不会因重启在集合里占多席。
--   残留清理：占位事务内由占位赢家清空同版本号的全部确认行——回滚只清
--   pending 不清本表，重新发起时若不清残留行，流产尝试的确认会被继承，
--   导致其余实例未确认就被提前晋升。
--
-- 切换流程（对应父规格 D20）：
--   1. 候选快照完整构建并验证（任一配置失败即整体失败，保留旧快照）；
--   2. claimSwitch 原子占位（WHERE pendingVersion IS NULL，跨实例只允许一个进行中的切换）；
--   3. 各实例（含发起实例）走统一确认路径：应用候选快照后向确认集合插入
--      本实例标识，集合大小达到 expectedInstances 才在同一事务里完成版本
--      晋升与 storage_config.isActive 的迁移；
--   4. 部分生效期间保持明确的 pending 状态，可执行回滚（abortSwitch，
--      activeVersion 不变，各实例从持久配置重建快照或惰性自愈）。
--
-- 存量数据：无需回填。应用启动时会以 INSERT IGNORE 保证状态单行存在。
-- 本脚本幂等：表已存在时整体不做任何事。
--
-- 回滚：执行同名 -rollback.sql。回滚保留这两张表（运行中的切换状态不丢失），
-- 旧版本应用不读取这两张表，行为与升级前一致。

SET @state_exists = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'storage_switch_state'
);
SET @ddl = IF(
  @state_exists = 0,
  'CREATE TABLE `storage_switch_state` (\n' ||
  '  `id` bigint(20) NOT NULL COMMENT ''固定为 1 的单行'',\n' ||
  '  `activeVersion` bigint(20) NOT NULL DEFAULT 1 COMMENT ''最近完整生效的快照版本'',\n' ||
  '  `pendingVersion` bigint(20) NULL DEFAULT NULL COMMENT ''待确认的候选版本'',\n' ||
  '  `pendingConfigId` bigint(20) NULL DEFAULT NULL COMMENT ''待生效配置 id'',\n' ||
  '  `pendingPlatform` varchar(32) NULL DEFAULT NULL COMMENT ''待生效平台'',\n' ||
  '  `pendingTime` datetime NULL DEFAULT NULL COMMENT ''切换占位时间'',\n' ||
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

SET @confirm_exists = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'storage_switch_confirm'
);
SET @ddl = IF(
  @confirm_exists = 0,
  'CREATE TABLE `storage_switch_confirm` (\n' ||
  '  `version` bigint(20) NOT NULL COMMENT ''候选版本'',\n' ||
  '  `instanceId` varchar(64) NOT NULL COMMENT ''确认实例标识（进程启动生成）'',\n' ||
  '  `configId` bigint(20) NULL DEFAULT NULL COMMENT ''确认时的待生效配置 id（审计）'',\n' ||
  '  `confirmTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''确认时间'',\n' ||
  '  PRIMARY KEY (`version`, `instanceId`) USING BTREE\n' ||
  ') ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = ''存储切换确认实例集合''',
  'SELECT ''storage_switch_confirm already exists, nothing to do'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 单行初始化：仅在该行不存在时插入（幂等，可重复执行）。
INSERT IGNORE INTO `storage_switch_state` (`id`, `activeVersion`) VALUES (1, 1);

-- 前向清理：跑过早期版本迁移（确认进度为计数器）的环境会残留 NOT NULL 的
-- confirmCount 死列，现行代码不再写入。条件 DROP，幂等可重复执行。
SET @confirm_count_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'storage_switch_state'
    AND COLUMN_NAME = 'confirmCount'
);
SET @ddl = IF(
  @confirm_count_exists > 0,
  'ALTER TABLE `storage_switch_state` DROP COLUMN `confirmCount`',
  'SELECT ''storage_switch_state.confirmCount not present, nothing to do'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
