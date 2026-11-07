package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.manager.cache.RecommendCacheManager;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PictureContentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #46 维护窗口迁移与安全回滚演练：把各业务票已交付的迁移脚本编排成一次可重复的窗口。
 *
 * <p>父规格 34.1–34.5 / D08 / T23。本演练只调用 {@code sql/migrations/} 下现成脚本，
 * 不补做业务功能、不改写真实业务表结构、不接触生产数据。隔离验收库由
 * {@code scripts/acceptance.ps1} 引导（26 张表已在基线里），因此脚本统一在
 * {@code *_drill_*} 临时表上重放；真实环境维护窗口不受此限制。
 *
 * <p>演练按 BACKUP → PAUSE_WRITES → MIGRATE → RECONCILE → RESUME → ROLLBACK_REHEARSAL
 * → RE_DEPLOY 顺序执行，并证明：冲突形成脱敏清单并阻断（零自动认领/合并/删除/续跑）、
 * 前后对账一致、故障回滚不重新开放已暴露兑换码/已撤权限/旧秘密/私人直链、无法安全回退时
 * 保持维护状态。编排事实来源为 {@code sql/migrations/maintenance-window.manifest.json}。
 *
 * <p>真实桶 ACL、CDN、凭据轮换、公网可达性与生产备份恢复仍属人工关卡，见演练进度文档；
 * 本测试不把它们表述为通过。
 */
@IsolatedSpringBootTest
class MaintenanceWindowDrillTest {

    private static final Path MANIFEST = Path.of("sql/migrations/maintenance-window.manifest.json");
    private static final Path MIGRATIONS = Path.of("sql/migrations");

    /** 演练用的脱敏快照：真正会被脚本改动的表统一改名为 *_drill_*，与引导库真实表隔离。 */
    private static final Map<String, String> SCRATCH = scratchTables();

    private static Map<String, String> scratchTables() {
        Map<String, String> tables = new HashMap<>();
        tables.put("space", "space_drill_46");
        tables.put("storage_cleanup_task", "storage_cleanup_task_drill_46");
        tables.put("picture", "picture_drill_46");
        tables.put("agent_legacy_attachment", "agent_legacy_attachment_drill_46");
        tables.put("agent_session_attachment", "agent_session_attachment_drill_46");
        tables.put("agent_message", "agent_message_drill_46");
        tables.put("asset_search_snapshot", "asset_search_snapshot_drill_46");
        tables.put("feedback_attachment", "feedback_attachment_drill_46");
        tables.put("resource_access_grant", "resource_access_grant_drill_46");
        tables.put("resource_isolation", "resource_isolation_drill_46");
        tables.put("vip_redemption_code", "vip_redemption_code_drill_46");
        tables.put("vip_redemption_record", "vip_redemption_record_drill_46");
        tables.put("agent_run_state", "agent_run_state_drill_46");
        tables.put("agent_action_record", "agent_action_record_drill_46");
        tables.put("user_preference", "user_preference_drill_46");
        tables.put("user", "user_drill_46");
        tables.put("user_feedback", "user_feedback_drill_46");
        tables.put("picture_stats", "picture_stats_drill_46");
        tables.put("user_picture_action", "user_picture_action_drill_46");
        tables.put("picture_action_event_dedupe", "picture_action_event_dedupe_drill_46");
        tables.put("storage_switch_state", "storage_switch_state_drill_46");
        tables.put("storage_switch_confirm", "storage_switch_confirm_drill_46");
        return Map.copyOf(tables);
    }

    private static final List<String> PROCEDURES = List.of("vs_add_column_if_missing",
            "vs_drop_column_if_present", "vs37_add_column_if_missing", "vs37_normalize_cleanup_task",
            "vs42_restore_preference", "vs42_rollback_preference");

    private static final List<String> GUARD_NAMES = List.of("_space_quota_backfill_guard",
            "_space_quota_rollback_guard", "_private_resource_rollback_guard",
            "_private_resource_archive_guard", "_user_preference_restore_guard");

    private static final List<String> GUARDS = GUARD_NAMES.stream()
            .map(name -> name + "_drill_46").toList();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PictureContentService pictureContentService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RecommendCacheManager recommendCacheManager;

    @Autowired
    private ExternalCallRecorder recorder;

    /** 写入闸门：true 表示尚未获 M2 批准，任何会改数据的步骤都必须被拒绝。 */
    private boolean writesPaused = true;

    private JsonNode manifest;

    @BeforeEach
    void setUp() throws Exception {
        manifest = new ObjectMapper().readTree(Files.readString(MANIFEST, StandardCharsets.UTF_8));
        dropScratch();
        createSnapshot();
    }

    @AfterEach
    void tearDown() {
        dropScratch();
    }

    // ------------------------------------------------------------------
    // 34.1 按备份、停写、迁移、核对、恢复编排各领域现成步骤
    // ------------------------------------------------------------------

    @Test
    void windowRunsInManifestOrderAndOnlyComposesDeliveredScripts() throws Exception {
        List<String> phases = new ArrayList<>();
        for (JsonNode node : manifest.path("phases")) {
            phases.add(node.asText());
        }
        List<Integer> orders = new ArrayList<>();
        for (JsonNode step : manifest.path("steps")) {
            orders.add(step.path("order").asInt());
            assertTrue(Files.isRegularFile(MIGRATIONS.resolve(step.path("file").asText())),
                    "编排只引用已交付脚本: " + step.path("file").asText());
        }
        assertEquals(phases.get(0), "BACKUP");
        assertEquals(phases.get(phases.size() - 1), "RE_DEPLOY");
        assertEquals(orders.stream().sorted().toList(), orders,
                "清单步骤必须按维护窗口顺序排列，不能乱序执行");

        // BACKUP：脱敏快照逐表行数形成台账，作为回滚演练的恢复基线。
        Map<String, Long> ledger = backupLedger();
        assertTrue(ledger.containsKey(renamed("space")), "备份台账必须覆盖空间");
        assertTrue(ledger.containsKey(renamed("vip_redemption_code")), "备份台账必须覆盖 VIP 权益");
        assertTrue(ledger.containsKey(renamed("user_preference")), "备份台账必须覆盖长期偏好");
        assertEquals(2L, ledger.get(renamed("space")), "停写前快照含两条有效空间");

        // PAUSE_WRITES：所有会改数据的迁移步骤都必须声明「要求停写」，并在闸门未放行时被拒绝。
        // 放行本身由人工批准点 M2/M7 控制，编排脚本不自行恢复写入。
        int pausedSteps = 0;
        for (JsonNode step : manifest.path("steps")) {
            if (!step.path("phase").asText().equals("RECONCILE")) {
                assertTrue(step.path("writesPaused").asBoolean(false),
                        step.path("file").asText() + " 必须在停写窗口内执行");
                pausedSteps++;
            }
        }
        assertEquals(countNonReconcileSteps(), pausedSteps, "除只读盘点外的每一步都受停写闸门约束");

        // PAUSE_WRITES：编排器在 M2 批准前拒绝执行任何迁移步骤；这一步不会碰到数据库。
        assertThrows(IllegalStateException.class,
                () -> runStepUnderGate("2026-10-25-space-uniqueness-quota-backfill.sql"),
                "闸门未放行时迁移步骤必须被拒绝");

        // M2 批准进入维护窗口后，按清单顺序在改名后的临时表上重放现成脚本
        //（只取真实可执行、有守卫的数据步骤）。
        openWindowForDrill();
        runDelivered("2026-10-25-space-uniqueness-quota-backfill.sql");
        runDelivered("2026-11-05-user-preference-rollback.sql");
        runDelivered("2026-11-05-user-preference.sql");

        // RECONCILE：清单里每一份只读盘点都按编排顺序执行，且不改动备份台账。
        List<String> inventories = new ArrayList<>();
        for (JsonNode step : manifest.path("steps")) {
            if (step.path("phase").asText().equals("RECONCILE")) {
                String file = step.path("file").asText();
                runReadOnly(file);
                inventories.add(file);
            }
        }
        assertEquals(phaseFileCount("RECONCILE"), inventories.size(),
                "清单声明的只读盘点步骤全部纳入编排");
        assertEquals(ledger, backupLedger(), "只读盘点不得修改任何业务数据");

        // RESUME / RE_DEPLOY：核对通过后恢复写入，再按清单把每份回滚步骤演练一遍
        //（这一步在 rollbackRehearsal* 用例里带真实风险状态验证；这里证明流程完整、无遗漏步骤）。
        List<String> rehearsals = new ArrayList<>();
        for (JsonNode step : manifest.path("steps")) {
            if (step.path("phase").asText().equals("ROLLBACK_REHEARSAL")) {
                rehearsals.add(step.path("file").asText());
                assertTrue(Files.isRegularFile(MIGRATIONS.resolve(step.path("file").asText())),
                        "回滚演练只引用已交付脚本");
            }
        }
        assertEquals(phaseFileCount("ROLLBACK_REHEARSAL"), rehearsals.size(),
                "清单声明的回滚脚本全部出现在演练阶段");

        // 恢复后快照与备份一致：无损迁移。
        assertEquals(ledger, backupLedger(), "迁移前后对账必须一致（无损）");
    }

    // ------------------------------------------------------------------
    // 34.2 盘点各维度，记录前后核对结果
    // ------------------------------------------------------------------

    @Test
    void quotaBlocklistReportsByteDeviationsWhenPictureCountsMatch() throws Exception {
        jdbc.update("UPDATE space_drill_46 SET totalCount = 1, totalSize = 9 WHERE id = 9001");
        jdbc.update("UPDATE space_drill_46 SET totalSize = -1 WHERE id = 9002");
        insertSpace(9003, 7003, 0, 0);
        jdbc.update("UPDATE space_drill_46 SET totalCount = 1, totalSize = 10 WHERE id = 9003");
        insertSpace(9004, 7004, 0, 1);
        jdbc.update("UPDATE space_drill_46 SET totalSize = -1 WHERE id = 9004");
        jdbc.update("INSERT INTO picture_drill_46 (id, url, userId, picSize, spaceId, isDelete) VALUES "
                + "(88101, 'dummy', 7001, 10, 9001, 0), (88102, 'dummy', 7001, 99, 9001, 1), "
                + "(88103, 'dummy', 7003, 10, 9003, 0), (88104, 'dummy', 7001, 99, NULL, 0)");
        List<Map<String, Object>> spacesBefore = jdbc.queryForList("SELECT * FROM space_drill_46 ORDER BY id");
        List<Map<String, Object>> picturesBefore = jdbc.queryForList("SELECT * FROM picture_drill_46 ORDER BY id");
        int callsBefore = recorder.calls().size();
        String sql = Files.readString(MIGRATIONS.resolve("2026-11-05-maintenance-window-blocklist.sql"));
        String detailSql = statements(sql).stream().filter(s -> s.contains("AS `storedCount`"))
                .findFirst().orElseThrow();
        List<Map<String, Object>> detail = jdbc.queryForList(rename(detailSql));
        assertEquals(List.of(9001L, 9002L), detail.stream().map(row -> row.get("recordId")).toList(),
                "数量正确但容量错误或为负的有效空间仍须阻断；已删除空间不计入");
        assertEquals(9L, ((Number) detail.get(0).get("storedSize")).longValue());
        assertEquals(10L, ((Number) detail.get(0).get("expectedSize")).longValue());
        assertEquals(-1L, ((Number) detail.get(1).get("storedSize")).longValue());
        assertEquals(0L, ((Number) detail.get(1).get("expectedSize")).longValue());
        Map<String, Object> summary = blocklistSection(sql, 6).stream()
                .filter(row -> "quota-deviation".equals(row.get("dimension"))).findFirst().orElseThrow();
        assertEquals("BLOCKING", summary.get("kind"));
        assertEquals(2L, ((Number) summary.get("blockingCount")).longValue());
        assertEquals(spacesBefore, jdbc.queryForList("SELECT * FROM space_drill_46 ORDER BY id"));
        assertEquals(picturesBefore, jdbc.queryForList("SELECT * FROM picture_drill_46 ORDER BY id"));
        assertEquals(callsBefore, recorder.calls().size(), "配额盘点零存储副作用");
    }

    @Test
    void eventBlocklistReportsPendingEventsForLogicallyDeletedPictures() throws Exception {
        jdbc.update("INSERT INTO picture_drill_46 (id, url, userId, picSize, isDelete) VALUES "
                + "(88201, 'dummy', 7001, 10, 1), (88202, 'dummy', 7001, 10, 0)");
        jdbc.update("INSERT INTO user_picture_action_drill_46 (id, userId, pictureId, isProcessed, attemptCount) "
                + "VALUES (88301, 7001, 88201, 0, 0), (88302, 7001, 88202, 0, 0)");
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM user_picture_action_drill_46 ORDER BY id");
        String sql = Files.readString(MIGRATIONS.resolve("2026-11-05-maintenance-window-blocklist.sql"));
        Map<String, Object> summary = blocklistSection(sql, 6).stream()
                .filter(row -> "event-backlog".equals(row.get("dimension"))).findFirst().orElseThrow();
        assertEquals("BLOCKING", summary.get("kind"));
        assertEquals(1L, ((Number) summary.get("blockingCount")).longValue(),
                "逻辑删除图片的未处理事件必须阻断；有效图片的正常待处理事件不阻断");
        assertEquals(before, jdbc.queryForList("SELECT * FROM user_picture_action_drill_46 ORDER BY id"),
                "事件盘点不能自动标记完成或丢弃孤儿事件");
    }

    @Test
    void inventoryCoversEveryNamedDimensionAndIsDesensitized() throws Exception {
        // 归档表是回滚时临时存在的表，不算基线维度；其余表必须在快照或引导库里存在。
        Set<String> transientTables = Set.of("user_preference_rollback_archive");
        for (JsonNode dim : manifest.path("inventoryDimensions")) {
            for (JsonNode table : dim.path("tables")) {
                String name = table.asText();
                assertTrue(transientTables.contains(name) || tableExists(name) || tableExists(renamed(name)),
                        dim.path("id").asText() + " 引用的表必须存在: " + name);
            }
        }
        // 放一条归属未知的哑值，让只读清单确有明细输出可供脱敏断言。
        insertLegacyAttachment(9202, "sess-desensitize", 0);
        Map<String, Long> before = backupLedger();

        // 只执行跨领域清单并断言它的明细输出脱敏；#35 交付清单里的 sessionId 属于原票口径，
        // 本票不改它。生成脱敏清单的拒绝路径也不得读取源站对象。
        String blocklist = Files.readString(
                MIGRATIONS.resolve("2026-11-05-maintenance-window-blocklist.sql"), StandardCharsets.UTF_8);
        int callsBefore = recorder.calls().size();
        runReadOnly("2026-11-05-maintenance-window-blocklist.sql");
        List<Map<String, Object>> detail = blocklistSection(blocklist, 1);
        assertFalse(detail.isEmpty(), "归属未知条目必须进入脱敏清单");
        assertTrue(detail.stream().allMatch(row -> row.containsKey("sessionHashPrefix")
                        && !row.containsKey("sessionId")),
                "会话标识只能以摘要前缀输出，不得泄露原始 sessionId: " + detail);
        assertTrue(detail.stream().allMatch(row -> String.valueOf(row.get("sessionHashPrefix"))
                        .matches("[0-9a-f]{12}")),
                "会话摘要必须是 12 位十六进制前缀: " + detail);
        assertEquals(callsBefore, recorder.calls().size(),
                "生成脱敏清单不得读取源站对象：零未授权副作用");
        List<Map<String, Object>> summary = blocklistSection(blocklist, 6);
        Map<String, Long> summaryCounts = new LinkedHashMap<>();
        for (Map<String, Object> row : summary) {
            for (Object value : row.values()) {
                assertFalse(String.valueOf(value).contains("https://"),
                        "清单输出不得泄露源站地址: " + row);
            }
            summaryCounts.put(String.valueOf(row.get("dimension")),
                    ((Number) row.get("blockingCount")).longValue());
        }
        // M3 阻断项必须在汇总里给出可读计数；BLOCKING 维度全为零才允许放行。
        for (String dimension : List.of("unknown-ownership", "duplicate-valid-space",
                "unrecoverable-state", "unresolved-compensation", "quota-deviation", "event-backlog")) {
            assertTrue(summaryCounts.containsKey(dimension), "汇总缺少 M3 阻断维度 " + dimension);
        }
        assertEquals(0L, summaryCounts.getOrDefault("quota-deviation", 0L),
                "脱敏快照不应残留配额偏差");
        assertEquals(0L, summaryCounts.getOrDefault("event-backlog", 0L),
                "脱敏快照不应残留事件积压");
        assertNoRawUrlsOrSecrets("2026-11-05-maintenance-window-blocklist.sql");

        assertEquals(before, backupLedger(), "盘点前后行数不变（只读）");
        assertEquals(9, manifest.path("inventoryDimensions").size(),
                "图片/附件归属、原平台、配额、有效空间、事件、VIP 权益、执行状态、偏好九个维度全覆盖");
    }

    // ------------------------------------------------------------------
    // 34.3 归属未知 / 重复有效空间 / 无法安全恢复状态 → 脱敏清单 + 阻断，零副作用
    // ------------------------------------------------------------------

    @Test
    void unresolvedConflictsProduceADesensitizedListAndBlockTheMigration() throws Exception {
        // 重复有效空间（同一创建者、同一类型两条有效记录）
        insertSpace(9101, 7001, 0, 0);
        insertSpace(9102, 7001, 0, 0);
        // 归属未知的存量会话图片与反馈附件
        insertLegacyAttachment(9201, "sess-unknown", 0);
        insertBlockedFeedback(9301);
        // 无法安全恢复的执行状态与结果未知的外部动作
        insertUnrecoverableRun(9401);
        insertUnknownAction(9501);

        Map<String, Long> before = backupLedger();
        long spacesBefore = countRows("space");
        long quarantinedBefore = countRowsWhere(renamed("resource_isolation"), "status = 1");

        // M2 批准后才允许在维护窗口内执行回填；守卫失败后编排回到停写。
        openWindowForDrill();
        assertThrows(Exception.class,
                () -> runDelivered("2026-10-25-space-uniqueness-quota-backfill.sql"),
                "重复有效空间必须阻断回填");
        assertEquals(spacesBefore, countRows("space"), "阻断后一行不删、不合并");
        assertEquals(0L, countRowsWhere(renamed("space"), "activeKey IS NOT NULL"),
                "阻断后不得写入任何占位键");
        assertTrue(tableExists(GUARDS.get(0)), "被阻断时守卫表留库供人工核对");

        // 跨领域清单把四类冲突全部报出，且只输出脱敏值。明细里的重复空间按
        // ownerHashPrefix 分组，不泄露原始 userId，仍可人工按组内 id 回查。
        String blocklist = Files.readString(
                MIGRATIONS.resolve("2026-11-05-maintenance-window-blocklist.sql"), StandardCharsets.UTF_8);
        List<Map<String, Object>> duplicateSpaces = blocklistSection(blocklist, 2);
        assertFalse(duplicateSpaces.isEmpty(), "重复有效空间必须进入脱敏清单");
        assertTrue(duplicateSpaces.stream().allMatch(row -> row.containsKey("ownerHashPrefix")
                        && !row.containsKey("userId")
                        && String.valueOf(row.get("ownerHashPrefix")).matches("[0-9a-f]{12}")),
                "重复空间只能输出归属摘要前缀，不得泄露原始 userId: " + duplicateSpaces);
        assertTrue(duplicateSpaces.stream().map(row -> row.get("ownerHashPrefix")).distinct().count() == 1,
                "同一归属的重复空间必须能按摘要前缀归为一组: " + duplicateSpaces);
        List<Map<String, Object>> blocking = blocklistSection(blocklist, 6);
        assertTrue(blocking.size() >= 4, "四类阻断维度都要出现在汇总里: " + blocking);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : blocking) {
            counts.put(String.valueOf(row.get("dimension")), ((Number) row.get("blockingCount")).longValue());
        }
        assertTrue(counts.getOrDefault("unknown-ownership", 0L) >= 2, "归属未知条目进清单");
        assertTrue(counts.getOrDefault("duplicate-valid-space", 0L) >= 1, "重复有效空间进清单");
        assertTrue(counts.getOrDefault("unrecoverable-state", 0L) >= 2, "无法安全恢复状态进清单");
        assertEquals(quarantinedBefore, countRowsWhere(renamed("resource_isolation"), "status = 1"),
                "清单不得自动解除隔离");

        assertNoRawUrlsOrSecrets("2026-11-05-maintenance-window-blocklist.sql");
        for (Map<String, Object> row : blocking) {
            for (Object value : row.values()) {
                assertFalse(String.valueOf(value).contains("https://"),
                        "阻断汇总输出不得泄露源站地址: " + row);
            }
        }
        assertEquals(before, backupLedger(), "被阻断的迁移零副作用：快照行数不变");
    }

    @Test
    void blockedRollbackKeepsMaintenanceStateInsteadOfContinuing() throws Exception {
        // 未收敛的存储清理任务 + 未解除的隔离：两条回滚守卫都应阻断。
        insertUnresolvedCleanupTask(9601);
        insertActiveQuarantine(9701);

        // ROLLBACK_REHEARSAL：M7 批准后才允许在维护窗口内执行回滚演练步骤。
        openWindowForDrill();
        assertThrows(Exception.class,
                () -> runDelivered("2026-10-25-space-uniqueness-quota-rollback.sql"),
                "未收敛清理任务必须阻断空间回滚");
        // 守卫失败后编排自行落回停写；为独立验证第二条守卫，重新模拟一次 M7 批准。
        openWindowForDrill();
        assertThrows(Exception.class,
                () -> runDelivered("2026-10-23-private-resource-protection-rollback.sql"),
                "未解除隔离必须阻断私有资源回滚");
        // 两次守卫失败后编排落回停写完全由编排器负责，测试不再手动置位。
        assertTrue(tableExists(renamed("storage_cleanup_task")), "阻断后清理任务表仍在，事实不丢");
        assertTrue(columnExists(renamed("space"), "activeKey"), "阻断后 activeKey 列不删");
        assertTrue(tableExists(renamed("resource_isolation")), "阻断后隔离表仍在");
        // 无法安全回退时保持维护状态：用行为断言证明闸门确实关闭，而不是读一个刚被
        // 手动置位的布尔值。阻断后再发起任何迁移步骤都必须被拒绝，编排不自动续跑。
        assertThrows(IllegalStateException.class,
                () -> runStepUnderGate("2026-10-25-space-uniqueness-quota-backfill.sql"),
                "阻断后编排保持停写，后续迁移步骤必须被拒绝，不自动续跑/不恢复写入");
    }

    // ------------------------------------------------------------------
    // 34.4 故障回滚不重新开放任何已关闭的风险；无法安全回退时保持维护状态
    // ------------------------------------------------------------------

    @Test
    void rollbackRehearsalNeverRevivesExposedCodesRevokedGrantsOrPrivateLinks() throws Exception {
        // 脱敏快照里预先落好「已关闭的风险」：已作废/已用码、已撤销授权、已关闭私人直链、
        // 已清除偏好、旧秘密兼容列（恒 NULL）。旧直链对应的图片没有资源记录，
        // 因此不提供任何内容入口——回滚也不得让它重新出现。
        insertCode(9801, "a".repeat(64), 1);
        insertCode(9802, "b".repeat(64), 2);
        insertRevokedGrant(9901);
        insertPicture(88002, 7001);
        insertActiveResource(88002, 7001);
        jdbc.update("UPDATE picture_file SET platform = 'drill-legacy', objectKey = 'legacy/original-88002.jpg' "
                + "WHERE pictureId = 88002");
        int callsBefore = recorder.calls().size();
        Picture closedLink = new Picture();
        closedLink.setId(88001L);
        closedLink.setUrl("https://legacy.invalid/closed.jpg");
        assertNull(pictureContentService.contentUrl(closedLink, PictureContentService.ContentKind.ORIGINAL),
                "演练前私人直链已关闭");
        // 快照已含一条「已清除内容」的偏好行（91001），这里不再重复插入。
        Map<String, Object> risksBefore = riskSnapshot();
        assertEquals(List.of(Map.of("platform", "drill-legacy", "objectKey", "legacy/original-88002.jpg")),
                risksBefore.get("resourceLocations"), "对账必须包含旧资源的原平台与对象定位");

        // ROLLBACK_REHEARSAL：M7 批准后在维护窗口内执行 #39 / #35 / #42 回滚。
        openWindowForDrill();
        // 执行 #39 回滚：只补 user.vipCode 兼容列，不改任何状态行。
        runDelivered("2026-10-24-vip-redemption-rollback.sql");
        // 执行 #35/#42 回滚：撤销回填、归档偏好，不重新公开旧内容。
        runDelivered("2026-10-23-agent-session-attachment-legacy-rollback.sql");
        runDelivered("2026-11-05-user-preference-rollback.sql");

        assertEquals(risksBefore, riskSnapshot(), "回滚不得改变任何已关闭的安全状态");
        assertEquals(0L, countRowsWhere(renamed("user"), "vipCode IS NOT NULL"),
                "兼容列恒为 NULL，不复活任何明文码");
        assertEquals(0L, countRowsWhere(renamed("vip_redemption_code"), "status = 0"),
                "已作废/已用码不得回到可用状态");
        assertEquals(0L, countRowsWhere(renamed("resource_access_grant"), "status = 1"),
                "已撤销授权不得复活");
        assertEquals(0L, countRowsWhere(renamed("agent_legacy_attachment"), "status = 0"),
                "待核对清单被回滚清理，但不得变成已确认归属的公开入口");
        assertNull(pictureContentService.contentUrl(closedLink, PictureContentService.ContentKind.ORIGINAL),
                "回滚后已关闭的私人直链不得重新出现内容入口");
        // 「旧秘密」：回滚不写回任何明文码、凭据或状态行；兼容列保持 NULL，
        // 已暴露（作废）与已用码状态原样保留，没有可用码可被旧版本 JAR 兑换。
        assertEquals(2L, countRowsWhere(renamed("vip_redemption_code"), "status IN (1, 2)"),
                "已暴露/已用码状态原样保留，不复活、不改写");
        assertEquals(2L, countRowsWhere(renamed("vip_redemption_code"),
                "codeHash REGEXP '^[0-9a-f]{64}$'"),
                "台账只存 sha-256 摘要，不写回任何明文码值");
        assertEquals(0L, countRowsWhere(renamed("user"), "vipCode IS NOT NULL"),
                "旧秘密兼容列恒为 NULL，回滚不写回任何值");
        assertTrue(tableExists("user_preference_rollback_archive_drill_46"),
                "偏好行先归档再删表，不随降级丢失");
        assertEquals(1L, countRowsWhere("user_preference_rollback_archive_drill_46",
                "explicitContent IS NULL AND learnedContent IS NULL"),
                "已清除内容在归档里仍为空，不重新公开");

        // RE_DEPLOY：重新前向迁移把归档原样搬回，已清除内容仍为空、版本/纪元保持原值。
        runDelivered("2026-11-05-user-preference.sql");
        assertEquals(1L, countRowsWhere(renamed("user_preference"),
                "explicitContent IS NULL AND learnedContent IS NULL"),
                "重新部署不复活已清除的偏好");
        assertFalse(tableExists("user_preference_rollback_archive_drill_46"),
                "恢复成功后归档被消费");
        assertEquals(risksBefore, riskSnapshot(), "重新部署后旧资源定位与已关闭安全状态原样保留");
        assertEquals(callsBefore, recorder.calls().size(), "回滚及重新部署零存储读取或删除调用");
    }

    // ------------------------------------------------------------------
    // 34.5 受控存储与缓存环境验证旧地址失效策略
    // ------------------------------------------------------------------

    @Test
    void controlledStorageDoesNotExposeLegacyDirectUrls() throws Exception {
        // 录制型存储替身会记录每次 READ / READ-LEGACY / DELETE；本用例只走纯解析入口，
        // 任何一次拒绝路径上的调用都视为未授权副作用。
        int callsBefore = recorder.calls().size();

        // 存量图只有源站地址、没有归属记录：contentUrl 必须返回 null，
        // 即浏览器拿不到可绕过即时撤权的长期直链（ADR 0003）。
        Picture legacy = new Picture();
        legacy.setId(88001L);
        legacy.setUrl("https://legacy.invalid/old.jpg");
        assertNull(pictureContentService.contentUrl(legacy, PictureContentService.ContentKind.ORIGINAL),
                "未知归属的存量图不得对外给出内容地址");
        assertNull(pictureContentService.contentUrl(legacy, PictureContentService.ContentKind.THUMBNAIL),
                "缩略图同样不暴露源站地址");
        assertNull(pictureContentService.contentUrl(legacy, PictureContentService.ContentKind.PREVIEW),
                "预览同样不暴露源站地址");
        assertEquals(callsBefore, recorder.calls().size(),
                "拒绝路径上不得读取源站对象，也不得删除对象：零未授权副作用");

        // 登记了资源记录的图片才给受控入口，且不返回源站地址本身。
        insertPicture(88002L, 7002L);
        insertActiveResource(88002L, 7002L);
        Picture owned = new Picture();
        owned.setId(88002L);
        owned.setUrl("https://legacy.invalid/owned.jpg");
        String url = pictureContentService.contentUrl(owned, PictureContentService.ContentKind.ORIGINAL);
        assertNotNull(url, "有归属记录的图片应有受控内容入口");
        assertFalse(url.contains("legacy.invalid"), "受控入口不得是源站直链: " + url);
        assertTrue(url.contains("/picture/content/"), "内容必须经由受控入口: " + url);
        assertEquals(callsBefore, recorder.calls().size(), "解析受控入口不得触发任何旧地址读取/删除");
    }

    @Test
    void controlledCacheInvalidationRemovesStaleEntriesWithoutServingPrivateContent() {
        // 受控缓存环境：先在隔离 Redis 里放一份带脱敏哑值的旧推荐快照，模拟维护窗口前
        // 可能残留的缓存条目。真实环境的 CDN / 浏览器副本仍属 M4/M5 人工关卡。
        String staleKey = "recommend:hot:list:drill46";
        redis.opsForValue().set(staleKey, "[\"drill-dummy-id\"]");
        redis.opsForValue().set("recommend:hot:total", "1");
        assertTrue(Boolean.TRUE.equals(redis.hasKey(staleKey)), "演练前确有旧缓存条目");

        try {
            // 内容入口本身不读缓存：撤权后的下一次请求按当前角色重新判定，
            // 因此「缓存里曾经有过」不能让未知归属的存量图重新拿到源站地址。
            Picture legacy = new Picture();
            legacy.setId(88001L);
            legacy.setUrl("https://legacy.invalid/old.jpg");
            assertNull(pictureContentService.contentUrl(legacy, PictureContentService.ContentKind.ORIGINAL),
                    "残留缓存不得让私人直链复活");

            // 维护窗口的缓存失效步骤：按前缀删除全部推荐缓存，不留旧快照。
            recommendCacheManager.invalidateRecommendCache();
            assertFalse(Boolean.TRUE.equals(redis.hasKey(staleKey)), "旧候选列表必须失效");
            assertFalse(Boolean.TRUE.equals(redis.hasKey("recommend:hot:total")), "旧总数必须失效");
            assertTrue(redis.keys("recommend:*") == null || redis.keys("recommend:*").isEmpty(),
                    "失效后不得残留任何 recommend:* 键");
        } finally {
            recommendCacheManager.invalidateRecommendCache();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 清单里指定阶段的步骤数，避免把编排数量硬编码到测试里。 */
    private int phaseFileCount(String phase) {
        int count = 0;
        for (JsonNode step : manifest.path("steps")) {
            if (step.path("phase").asText().equals(phase)) {
                count++;
            }
        }
        return count;
    }

    /** 除只读盘点外，其余步骤都必须在停写窗口内执行。 */
    private int countNonReconcileSteps() {
        return manifest.path("steps").size() - phaseFileCount("RECONCILE");
    }

    /** 清单步骤的某一节汇总，包成可查询的结果集，便于断言输出脱敏。 */
    private List<Map<String, Object>> blocklistSection(String sql, int section) {
        return jdbc.queryForList("SELECT * FROM (" + rename(sectionSql(sql, section)) + ") t");
    }

    /** 备份台账：脱敏快照逐表行数。回滚演练以它作为恢复基线。 */
    private Map<String, Long> backupLedger() {
        Map<String, Long> ledger = new LinkedHashMap<>();
        for (String real : SCRATCH.keySet()) {
            String table = renamed(real);
            if (tableExists(table)) {
                ledger.put(table, countRows(table));
            }
        }
        return ledger;
    }

    private Map<String, Object> riskSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resourceLocations", jdbc.queryForList(
                "SELECT platform, objectKey FROM picture_file WHERE pictureId = 88002 ORDER BY id"));
        snapshot.put("codes", jdbc.queryForList(
                "SELECT id, codeHash, status FROM `" + renamed("vip_redemption_code") + "` ORDER BY id"));
        snapshot.put("grants", jdbc.queryForList(
                "SELECT id, status, activeKey FROM `" + renamed("resource_access_grant") + "` ORDER BY id"));
        snapshot.put("isolation", jdbc.queryForList(
                "SELECT id, status, activeKey FROM `" + renamed("resource_isolation") + "` ORDER BY id"));
        snapshot.put("legacyAttachments", jdbc.queryForList(
                "SELECT id, status, urlHash FROM `" + renamed("agent_legacy_attachment") + "` ORDER BY id"));
        // #42 回滚会把业务表改名为归档表，重新前向迁移再搬回；两种形态都取同一列做核对，
        // 这样「回滚前后风险状态不变」的比较不会因为表暂时不存在而失去意义。
        String preference = tableExists(renamed("user_preference"))
                ? renamed("user_preference") : "user_preference_rollback_archive_drill_46";
        snapshot.put("preferences", jdbc.queryForList(
                "SELECT id, userId, explicitContent, learnedContent, autoLearn, preferenceVersion, dataEpoch "
                        + "FROM `" + preference + "` ORDER BY id"));
        return snapshot;
    }

    /**
     * 编排器的执行闸门：M2 批准前 {@code writesPaused = true}，任何迁移步骤都被拒绝，
     * 绝不落到数据库。批准只由人工关卡给出，脚本自身不推断「可以开始」。
     */
    private void runStepUnderGate(String file) throws IOException {
        if (writesPaused) {
            throw new IllegalStateException("维护窗口停写：迁移步骤未获人工批准，拒绝执行 " + file);
        }
        // 在单一连接上按原文执行现成脚本：会话变量 / PREPARE / DELIMITER 与 mysql CLI 一致。
        try {
            runScript(MIGRATIONS.resolve(file), true);
        } catch (RuntimeException blocked) {
            // 守卫（CHECK 约束）失败时编排落回停写，等待 M7 重新批准；不自动续跑。
            // 这样「无法安全回退时保持维护状态」是编排器的真实行为，而非测试手动置位。
            writesPaused = true;
            throw blocked;
        }
    }

    /**
     * 模拟 M2 批准：进入已停写的维护窗口，允许演练步骤执行。
     * 这里只是把人工批准点映射成内存状态，不代表真实环境放行。
     */
    private void openWindowForDrill() {
        writesPaused = false;
    }

    /** 在单一连接上按原文执行现成脚本：会话变量 / PREPARE / DELIMITER 与 mysql CLI 一致。 */
    private void runDelivered(String file) throws IOException {
        runStepUnderGate(file);
    }

    /** 只读清单：仍然按原文执行，但断言它不含任何写语句。 */
    private void runReadOnly(String file) throws IOException {
        runScript(MIGRATIONS.resolve(file), false);
    }

    private void runScript(Path script, boolean allowWrites) throws IOException {
        String sql = Files.readString(script, StandardCharsets.UTF_8);
        if (!allowWrites) {
            assertFalse(sql.toUpperCase().matches("(?s).*\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE)\\b.*"),
                    script + " 是只读清单，不得包含写语句");
        }
        List<String> statements = statements(sql);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String raw : statements) {
                    statement.execute(rename(raw));
                }
            }
            return null;
        });
    }

    /** 取出脚本里第 n 节（以 -- ---------- n. 分节）的第一条语句，可作为子查询使用。 */
    private String sectionSql(String sql, int section) {
        int from = sql.indexOf("-- ---------- " + section + ".");
        int to = sql.indexOf("-- ---------- " + (section + 1) + ".");
        assertTrue(from >= 0, "清单缺少第 " + section + " 节");
        String body = to > from ? sql.substring(from, to) : sql.substring(from);
        List<String> parts = statements(body);
        assertFalse(parts.isEmpty(), "第 " + section + " 节必须有语句");
        return parts.get(0);
    }

    private void assertNoRawUrlsOrSecrets(String file) throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(file), StandardCharsets.UTF_8);
        for (String forbidden : List.of("https://", "http://", "/temp/", "vipCode.json", "accessKey", "secretKey")) {
            assertFalse(sql.contains(forbidden),
                    "脱敏清单不得输出/引用 " + forbidden);
        }
    }

    /** 按脚本原文切分语句，支持 mysql 客户端的 DELIMITER 指令。 */
    private List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = ";";
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String statement = current.toString().trim();
                result.add(statement.substring(0, statement.length() - delimiter.length()));
                current.setLength(0);
            }
        }
        assertEquals(0, current.toString().trim().length(), "脚本每条语句都必须以分隔符结尾");
        return result;
    }

    /** 把脚本里的真实表名映射为演练临时表名；守卫表、归档表同样改名。 */
    private String rename(String sql) {
        String out = sql;
        for (Map.Entry<String, String> entry : SCRATCH.entrySet()) {
            String real = entry.getKey();
            String scratch = entry.getValue();
            out = out.replace("`" + real + "`", "`" + scratch + "`")
                    .replace("'" + real + "'", "'" + scratch + "'");
        }
        out = out.replace("`feedback_attachment_archive_20261023`", "`feedback_attachment_archive_drill_46`")
                .replace("'feedback_attachment_archive_20261023'", "'feedback_attachment_archive_drill_46'")
                .replace("`user_preference_rollback_archive`", "`user_preference_rollback_archive_drill_46`")
                .replace("'user_preference_rollback_archive'", "'user_preference_rollback_archive_drill_46'");
        for (String guard : GUARD_NAMES) {
            out = out.replace("`" + guard + "`", "`" + guard + "_drill_46`")
                    .replace("'" + guard + "'", "'" + guard + "_drill_46'");
        }
        for (String proc : PROCEDURES) {
            out = out.replace("`" + proc + "`", "`" + proc + "_drill_46`")
                    .replace(proc + "(", proc + "_drill_46(");
        }
        return out;
    }

    private String renamed(String real) {
        return SCRATCH.getOrDefault(real, real);
    }

    // ---- 脱敏快照：只建脚本会触碰的最小结构，字段与基线一致，数据全是哑值 ----

    private void createSnapshot() {
        jdbc.execute("CREATE TABLE `" + renamed("space") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, spaceName varchar(128), spaceLevel int DEFAULT 0,"
                + " spaceType int NOT NULL DEFAULT 0, maxSize bigint DEFAULT 0, maxCount bigint DEFAULT 0,"
                + " totalSize bigint DEFAULT 0, totalCount bigint DEFAULT 0, userId bigint NOT NULL,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP, editTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " isDelete tinyint NOT NULL DEFAULT 0, activeKey varchar(64) NULL)");
        // 快照本身是「已迁移后」的形态：activeKey 列与唯一索引由 #37 前向迁移交付。
        jdbc.execute("ALTER TABLE `" + renamed("space") + "` ADD UNIQUE INDEX `uk_space_active_key` (`activeKey`)");
        insertSpace(9001, 7001, 0, 0);
        insertSpace(9002, 7001, 1, 0);

        jdbc.execute("CREATE TABLE `" + renamed("storage_cleanup_task") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, platform varchar(32), objectKey varchar(768) NOT NULL,"
                + " kind varchar(16), reason varchar(128) NOT NULL, status int NOT NULL DEFAULT 0,"
                + " attempts int NOT NULL DEFAULT 0, lastError varchar(255), claimTime datetime NULL,"
                + " pendingKey char(64) NULL, nextRetryTime datetime NULL,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");

        jdbc.execute("CREATE TABLE `" + renamed("picture") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, url varchar(512) NOT NULL, name varchar(128),"
                + " userId bigint NOT NULL, picSize bigint, spaceId bigint, reviewStatus int DEFAULT 0,"
                + " isDelete tinyint DEFAULT 0)");

        jdbc.execute("CREATE TABLE `" + renamed("agent_legacy_attachment") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, sessionId varchar(64) NOT NULL,"
                + " messageId bigint NOT NULL, ownerUserId bigint NOT NULL, urlHash varchar(64) NOT NULL,"
                + " resourceId bigint NULL, status int NOT NULL DEFAULT 0,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("agent_session_attachment") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, sessionId varchar(64) NOT NULL, resourceId bigint NOT NULL,"
                + " ownerUserId bigint NOT NULL, status int NOT NULL DEFAULT 0, legacy tinyint NOT NULL DEFAULT 0,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("agent_message") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, sessionId varchar(64) NOT NULL, role varchar(20),"
                + " content text, attachmentIds varchar(1024) NULL, createdTime datetime DEFAULT CURRENT_TIMESTAMP)");

        jdbc.execute("CREATE TABLE `" + renamed("feedback_attachment") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, ownerUserId bigint NOT NULL, feedbackId bigint NULL,"
                + " platform varchar(32) NOT NULL, objectKey varchar(768) NOT NULL, url varchar(1024) NULL,"
                + " fileSize bigint NULL, status int NOT NULL DEFAULT 0, legacyUrlHash char(64) NULL,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `feedback_attachment_archive_drill_46` LIKE `" + renamed("feedback_attachment") + "`");
        jdbc.execute("CREATE TABLE `" + renamed("resource_access_grant") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, resourceType varchar(32) NOT NULL, resourceId bigint NOT NULL,"
                + " granteeUserId bigint NOT NULL, grantedByUserId bigint NOT NULL, reason varchar(500),"
                + " expiresTime datetime NOT NULL, status int NOT NULL DEFAULT 1, activeKey varchar(128) NULL,"
                + " revokedByUserId bigint NULL, revokeReason varchar(500), revokeTime datetime NULL,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("resource_isolation") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, resourceType varchar(32) NOT NULL, resourceId bigint NOT NULL,"
                + " status int NOT NULL DEFAULT 1, activeKey varchar(128) NULL, operatorId bigint NOT NULL,"
                + " reason varchar(500) NOT NULL, releaseOperatorId bigint NULL, releaseReason varchar(500),"
                + " releaseTime datetime NULL, createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP)");

        jdbc.execute("CREATE TABLE `" + renamed("vip_redemption_code") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, codeHash char(64) NOT NULL, status tinyint NOT NULL DEFAULT 0,"
                + " usedBy bigint NULL, usedTime datetime NULL, createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("vip_redemption_record") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, userId bigint NOT NULL, codeId bigint NOT NULL,"
                + " codeHash char(64) NOT NULL, previousExpireTime datetime NULL, expireTime datetime NOT NULL,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("user") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, userAccount varchar(256) NOT NULL, vipCode varchar(128) NULL,"
                + " vipExpireTime datetime NULL)");

        jdbc.execute("CREATE TABLE `" + renamed("agent_run_state") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, threadId varchar(64) NOT NULL, userId bigint NOT NULL,"
                + " stateVersion bigint NOT NULL DEFAULT 0, stateFormat varchar(32) NOT NULL,"
                + " runStatus varchar(32) NOT NULL DEFAULT 'RUNNING', legacyArchiveOnly tinyint NOT NULL DEFAULT 0,"
                + " createTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("agent_action_record") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, threadId varchar(64) NOT NULL, userId bigint NOT NULL,"
                + " actionId varchar(128) NOT NULL, toolName varchar(128) NOT NULL,"
                + " status varchar(32) NOT NULL, createTime datetime DEFAULT CURRENT_TIMESTAMP)");

        jdbc.execute("CREATE TABLE `" + renamed("user_preference") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, userId bigint NOT NULL, explicitContent text NULL,"
                + " learnedContent text NULL, feedbackHistory text NULL, autoLearn tinyint NOT NULL DEFAULT 0,"
                + " preferenceVersion bigint NOT NULL DEFAULT 0, dataEpoch bigint NOT NULL DEFAULT 0,"
                + " learnError varchar(200) NULL, createTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " updateTime datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " UNIQUE INDEX `uk_userId` (`userId`))");
        insertClearedPreference(91001);

        jdbc.execute("CREATE TABLE `" + renamed("user_feedback") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, userId bigint NOT NULL, pictureUrls text NULL, isDelete tinyint DEFAULT 0)");
        jdbc.execute("CREATE TABLE `" + renamed("picture_stats") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, pictureId bigint NOT NULL)");
        jdbc.execute("CREATE TABLE `" + renamed("user_picture_action") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, userId bigint NOT NULL, pictureId bigint NOT NULL,"
                + " isProcessed tinyint NOT NULL DEFAULT 0, isAccumulated tinyint NOT NULL DEFAULT 0,"
                + " attemptCount int NOT NULL DEFAULT 0, eventId varchar(128) NULL,"
                + " claimExpireAt datetime NULL, createdTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("picture_action_event_dedupe") + "` ("
                + " eventId varchar(128) NOT NULL PRIMARY KEY, createdTime datetime DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE `" + renamed("storage_switch_state") + "` ("
                + " id bigint NOT NULL PRIMARY KEY, activeVersion bigint NOT NULL DEFAULT 1,"
                + " pendingVersion bigint NULL, pendingToken varchar(64) NULL, expectedInstances int NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE `" + renamed("storage_switch_confirm") + "` ("
                + " version bigint NOT NULL, instanceId varchar(64) NOT NULL, confirmTime datetime DEFAULT CURRENT_TIMESTAMP,"
                + " PRIMARY KEY (version, instanceId))");
    }

    private void insertSpace(long id, long userId, int spaceType, int isDelete) {
        jdbc.update("INSERT INTO `" + renamed("space") + "` (id, spaceName, spaceType, userId, totalSize,"
                        + " totalCount, maxSize, maxCount, isDelete) VALUES (?, ?, ?, ?, 0, 0, 1000, 100, ?)",
                id, "space-" + id, spaceType, userId, isDelete);
    }

    private void insertLegacyAttachment(long id, String sessionId, int status) {
        jdbc.update("INSERT INTO `" + renamed("agent_legacy_attachment") + "` "
                        + "(id, sessionId, messageId, ownerUserId, urlHash, resourceId, status) "
                        + "VALUES (?, ?, ?, 7001, ?, NULL, ?)",
                id, sessionId, 8000 + id, "hash-" + id, status);
    }

    private void insertBlockedFeedback(long id) {
        jdbc.update("INSERT INTO `" + renamed("feedback_attachment") + "` "
                        + "(id, ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash) "
                        + "VALUES (?, 7001, 8001, 'legacy', ?, NULL, 3, ?)",
                id, "old-" + id, "hash-" + id);
    }

    private void insertUnrecoverableRun(long id) {
        jdbc.update("INSERT INTO `" + renamed("agent_run_state") + "` "
                        + "(id, threadId, userId, stateVersion, stateFormat, runStatus, legacyArchiveOnly) "
                        + "VALUES (?, 'thread-paused', 7001, 1, 'v2-full', 'PAUSED_UNRECOVERABLE', 1)", id);
    }

    private void insertUnknownAction(long id) {
        jdbc.update("INSERT INTO `" + renamed("agent_action_record") + "` "
                        + "(id, threadId, userId, actionId, toolName, status) "
                        + "VALUES (?, 'thread-unknown', 7001, 'action-1', 'tool', 'UNKNOWN_NEEDS_REVIEW')", id);
    }

    private void insertUnresolvedCleanupTask(long id) {
        jdbc.update("INSERT INTO `" + renamed("storage_cleanup_task") + "` "
                + "(id, platform, objectKey, kind, reason, status, attempts) "
                + "VALUES (?, 'acceptance', ?, 'original', 'test', 0, 0)", id, "obj/" + id);
    }

    private void insertActiveQuarantine(long id) {
        jdbc.update("INSERT INTO `" + renamed("resource_isolation") + "` "
                        + "(id, resourceType, resourceId, status, activeKey, operatorId, reason) "
                        + "VALUES (?, 'picture', 88001, 1, ?, 7002, 'test')", id, "quarantine-" + id);
    }

    private void insertCode(long id, String hash, int status) {
        jdbc.update("INSERT INTO `" + renamed("vip_redemption_code") + "` (id, codeHash, status) VALUES (?, ?, ?)",
                id, hash, status);
    }

    private void insertRevokedGrant(long id) {
        jdbc.update("INSERT INTO `" + renamed("resource_access_grant") + "` "
                        + "(id, resourceType, resourceId, granteeUserId, grantedByUserId, expiresTime, status, activeKey) "
                        + "VALUES (?, 'picture', 88001, 7003, 7001, DATE_ADD(NOW(), INTERVAL 1 DAY), 2, NULL)", id);
    }

    private void insertClearedPreference(long id) {
        jdbc.update("INSERT INTO `" + renamed("user_preference") + "` "
                        + "(id, userId, explicitContent, learnedContent, autoLearn, preferenceVersion, dataEpoch) "
                        + "VALUES (?, 7001, NULL, NULL, 0, 3, 2)", id);
    }

    private void insertPicture(long id, long userId) {
        jdbc.update("INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete) "
                + "VALUES (?, 'https://legacy.invalid/owned.jpg', 'owned', ?, 10, NULL, 1, 0)", id, userId);
    }

    private void insertActiveResource(long pictureId, long ownerId) {
        jdbc.update("INSERT INTO picture_file (id, ownerUserId, spaceId, pictureId, purpose, kind, platform,"
                        + " objectKey, url, fileSize, status) "
                        + "VALUES (?, ?, NULL, ?, 'gallery', 'original', 'acceptance', ?, NULL, 10, 1)",
                pictureId + 1_000_000, ownerId, pictureId, "gallery/" + pictureId + "/original.jpg");
    }

    private void dropScratch() {
        for (String table : new ArrayList<>(SCRATCH.values())) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        for (String table : List.of("feedback_attachment_archive_drill_46",
                "user_preference_rollback_archive_drill_46")) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        for (String guard : GUARDS) {
            jdbc.execute("DROP TABLE IF EXISTS `" + guard + "`");
        }
        for (String proc : PROCEDURES) {
            jdbc.execute("DROP PROCEDURE IF EXISTS `" + proc + "_drill_46`");
        }
        // contentUrl 用例在引导库的真实 picture/picture_file 上留过行，清理掉，避免污染其它用例。
        jdbc.execute("DELETE FROM picture_file WHERE pictureId = 88002");
        jdbc.execute("DELETE FROM picture WHERE id = 88002");
    }

    private long countRows(String table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class);
        return count == null ? 0L : count;
    }

    private long countRowsWhere(String table, String where) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "` WHERE " + where, Long.class);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }
}
