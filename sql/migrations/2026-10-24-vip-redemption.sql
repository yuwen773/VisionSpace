-- #39 VIP 权益独立与原子兑换（前向迁移）
--
-- 兑换码离开 classpath JSON 文件：
--   * vip_redemption_code     兑换码台账。只存 sha-256 摘要，明文码不再落库，
--                             也不再进入用户行（user.vipCode 列在兼容窗口内保留）。
--                             唯一索引 uk_codeHash 保证同一码只存在一条记录，
--                             核销语句以「UPDATE ... WHERE codeHash = ? AND status = 0」
--                             的单语句原子性保证跨实例只成功一次。
--   * vip_redemption_record   兑换流水。每次成功核销一条，codeId 唯一，
--                             记录兑换前后的到期时间，便于核对「重试不重复延长」。
--
-- 状态语义：0-可用 1-已用 2-已作废。曾进入 Git 历史的未用码必须由
-- 导入工具置为 2（作废），不允许重新激活；已用码保持 1，保留既有权益痕迹。
--
-- 存量数据迁移（历史 JSON 兑换码）由 VipCodeImportTool 离线执行：
--   * 已用码  -> status = 1，usedBy/usedTime 未知则留空（权益以流水为准）；
--   * 未用码  -> status = 2（作废），并输出脱敏补发清单（不含码值），
--                经人工核验后用该工具补发新码。
-- 本脚本不回填任何码值或码摘要：存量码的明文只在旧文件里，导入时即时哈希。
--
-- 执行前提：维护窗口内、已有备份、且应用已停止兑换请求。
--
-- 切换流程的强制步骤（与导入工具一起构成安全边界，缺一不得放行回滚）：
--   1. 先运行 VipCodeImportTool 导出存量码摘要（已用保留、未用作废），
--      并按其脱敏清单完成人工核验与补发；
--   2. 删除运行时环境中的一切 vipCode.json 副本（外部挂载/配置目录里的
--      旧明文文件整体删除，不是置空）：本票之后源码已不再携带该文件，
--      任何残留副本都会让旧版本 JAR 在回滚时重新暴露未用码。
--   完成这两步后，旧版本 JAR 即使回滚也没有任何可兑换的明文码，
--   「回滚不重新激活已暴露码」由数据物理缺失保证，而非约定。
--
-- 回滚：执行同名 -rollback.sql，并把应用回滚到旧版本；
-- 回滚不会删除这两张表，也不会把已作废的码改回可用。

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
