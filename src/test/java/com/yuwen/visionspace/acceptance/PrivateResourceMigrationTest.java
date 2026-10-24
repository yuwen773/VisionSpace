package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #36 三张新表的前向迁移与回滚脚本。
 *
 * <p>脚本在临时表名上执行（验收账号只有本轮库的权限，不能另建 schema），
 * 不触碰本轮的表与数据；同时验证：旧反馈附件只登记为 BLOCKED、重复执行幂等、
 * 存在未解除隔离时回滚被阻断、回滚成功后守卫表清理干净。
 * 真实环境的执行窗口、备份与人工核对仍属人工关卡。
 */
@IsolatedSpringBootTest
class PrivateResourceMigrationTest {

    private static final String FEEDBACK = "scratch_feedback_36";
    private static final String GRANT = "scratch_grant_36";
    private static final String ISOLATION = "scratch_isolation_36";
    private static final String GUARD = "scratch_guard_36";
    private static final String ARCHIVE_GUARD = "scratch_archive_guard_36";
    private static final String USER_FEEDBACK = "scratch_user_feedback_36";
    private static final String FEEDBACK_ARCHIVE = "scratch_feedback_archive_36";

    private static final List<String> TABLES = List.of(FEEDBACK, GRANT, ISOLATION);

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-23-private-resource-protection.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-23-private-resource-protection-rollback.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardCreatesTablesAndIsIdempotent() throws Exception {
        try {
            createScratchTables();
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (1, 7, 2, 't', 'c', ?, 0, 0)", "[\"/temp/7/a.png\"]");
            run(FORWARD);
            for (String table : TABLES) {
                assertTrue(tableExists(table), "forward migration must create " + table);
            }
            assertTrue(indexExists(FEEDBACK, "uk_legacy_url_hash"));
            assertTrue(indexExists(GRANT, "uk_active_key"));
            assertTrue(indexExists(ISOLATION, "uk_active_key"));
            assertEquals(1, count(FEEDBACK), "forward migration must register the legacy attachment");

            // 第二次执行不应失败，也不应重复登记旧附件
            run(FORWARD);
            assertEquals(1, count(FEEDBACK), "re-running the migration must be idempotent");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void legacyAttachmentsAreRegisteredAsBlockedAndNeverClaimed() throws Exception {
        try {
            createScratchTables();
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (1, 7, 2, 't', 'c', ?, 0, 0)",
                    "[\"/temp/7/a.png\",\"https://cdn.example/b.png\",\"/temp/99/c.png\"]");
            // 非 JSON 与空附件不能中断迁移
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (2, 7, 2, 't', 'c', 'not-json', 0, 0)");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (3, 7, 2, 't', 'c', NULL, 0, 0)");

            run(FORWARD);
            assertEquals(3, count(FEEDBACK), "only valid JSON entries are registered");
            List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash"
                            + " FROM `" + FEEDBACK + "` ORDER BY id");
            // 归属按服务端写入的 /temp/{提交者}/ 前缀核对：
            // /temp/7/ 指向反馈提交者 → 可读；其余（外站地址、指向别人的前缀）→ 阻断
            Map<String, Integer> expectedStatus = Map.of(
                    "/temp/7/a.png", 2,
                    "https://cdn.example/b.png", 3,
                    "/temp/99/c.png", 3);
            for (var row : rows) {
                String objectKey = (String) row.get("objectKey");
                assertEquals(expectedStatus.get(objectKey), ((Number) row.get("status")).intValue(),
                        "legacy attachment ownership must follow the server-written temp prefix: " + objectKey);
                assertEquals("legacy", row.get("platform"));
                assertEquals(7L, ((Number) row.get("ownerUserId")).longValue());
                assertTrue(row.get("legacyUrlHash") != null, "idempotency hash must be stored");
                // 原文保留：不自动认领、不只是丢弃
                assertEquals(row.get("objectKey"), row.get("url"));
            }
            // 既有反馈记录保持不变
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + USER_FEEDBACK + "`", Integer.class));
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void attachmentsAlreadyTrackedAreNotRegisteredAsLegacy() throws Exception {
        try {
            createScratchTables();
            // 先建表，再放入一条已经受控登记、归属明确的附件
            run(FORWARD);
            jdbc.update("INSERT INTO `" + FEEDBACK + "`"
                    + " (id, ownerUserId, platform, objectKey, status) VALUES (42, 7, 'acceptance', 'obj/42', 1)");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (1, 7, 2, 't', 'c', ?, 0, 0)",
                    "[\"attachment:42\",\"/temp/7/legacy.png\"]");
            run(FORWARD);
            // 只有归属不明的那条被登记为 BLOCKED
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE platform = 'legacy'", Integer.class),
                    "an already tracked attachment must not be registered as a legacy one");
            // 已受控登记的附件不被改写
            assertEquals(1, jdbc.queryForObject(
                    "SELECT status FROM `" + FEEDBACK + "` WHERE id = 42", Integer.class),
                    "an already tracked attachment must keep its status");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void rollbackIsBlockedWhileQuarantineIsActiveThenSucceedsAndKeepsData() throws Exception {
        try {
            createScratchTables();
            run(FORWARD);
            jdbc.update("INSERT INTO `" + ISOLATION + "`"
                    + " (resourceType, resourceId, status, activeKey, operatorId, reason)"
                    + " VALUES ('picture', 1, 1, 'picture:migration-check', 5, '违规')");

            // 未解除隔离时回滚必须失败，避免回滚即重新公开违规内容
            assertThrows(Exception.class, () -> run(ROLLBACK),
                    "rollback must refuse while a quarantine is active");
            for (String table : TABLES) {
                assertTrue(tableExists(table), "failed rollback must not drop " + table);
            }
            assertTrue(tableExists(USER_FEEDBACK), "failed rollback must not touch existing tables");

            jdbc.update("UPDATE `" + ISOLATION + "` SET status = 2, activeKey = NULL,"
                    + " releaseOperatorId = 5, releaseReason = '复核通过', releaseTime = NOW()"
                    + " WHERE resourceType = 'picture' AND resourceId = 1");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (1, 7, 2, 't', 'c', ?, 0, 0)", "[\"attachment:42\"]");
            run(ROLLBACK);
            for (String table : TABLES) {
                assertFalse(tableExists(table), "rollback must drop " + table);
            }
            // 回滚保留既有业务数据，只删除本工单新增的表与守卫表
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + USER_FEEDBACK + "`", Integer.class),
                    "rollback must keep existing feedback rows");
            assertFalse(tableExists(GUARD), "successful rollback must clean up its guard table");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void rollbackKeepsAttachmentLocationsAndReapplyRestoresThem() throws Exception {
        try {
            createScratchTables();
            run(FORWARD);
            // 新反馈只保存 attachment:{id} 引用，定位信息在附件记录里
            jdbc.update("INSERT INTO `" + FEEDBACK + "`"
                    + " (id, ownerUserId, feedbackId, platform, objectKey, url, status)"
                    + " VALUES (42, 7, 1, 'acceptance', 'obj/42', 'https://acceptance.invalid/obj/42', 1)");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (1, 7, 2, 't', 'c', ?, 0, 0)", "[\"attachment:42\"]");

            run(ROLLBACK);

            // 业务表删掉了，定位记录仍在归档里：平台、对象键、归属与绑定都在
            assertFalse(tableExists(FEEDBACK), "rollback must drop the attachment table");
            assertTrue(tableExists(FEEDBACK_ARCHIVE), "rollback must keep an archive of the attachments");
            Map<String, Object> archived = jdbc.queryForMap(
                    "SELECT ownerUserId, feedbackId, platform, objectKey, url, status FROM `"
                            + FEEDBACK_ARCHIVE + "` WHERE id = 42");
            assertEquals(7L, ((Number) archived.get("ownerUserId")).longValue());
            assertEquals(1L, ((Number) archived.get("feedbackId")).longValue());
            assertEquals("acceptance", archived.get("platform"));
            assertEquals("obj/42", archived.get("objectKey"));
            assertEquals(1, ((Number) archived.get("status")).intValue());
            // 反馈记录本身保留，引用不被改写
            assertEquals("[\"attachment:42\"]", jdbc.queryForObject(
                    "SELECT pictureUrls FROM `" + USER_FEEDBACK + "` WHERE id = 1", String.class));

            // 重新部署本工单：记录从归档搬回业务表，引用重新可解析
            run(FORWARD);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE id = 42 AND feedbackId = 1 AND status = 1",
                    Integer.class), "re-applying the migration must restore the attachment record");
            // 引用不再被当成归属不明的旧附件重复登记
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE platform = 'legacy'", Integer.class));
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void twoRollbackCyclesKeepTheLatestAttachmentBindingAndStatus() throws Exception {
        try {
            createScratchTables();
            run(FORWARD);
            // 新反馈只保存 attachment:{id} 引用；附件 901 登记后尚未绑定
            jdbc.update("INSERT INTO `" + FEEDBACK + "`"
                    + " (id, ownerUserId, feedbackId, platform, objectKey, url, status)"
                    + " VALUES (901, 7, NULL, 'acceptance', 'obj/901', 'https://acceptance.invalid/obj/901', 0)");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (88, 7, 2, 't', 'c', ?, 0, 0)", "[\"attachment:901\"]");

            // 第一轮：回滚归档后重新部署，附件回到未绑定的初始状态
            run(ROLLBACK);
            assertFalse(tableExists(FEEDBACK), "rollback must drop the attachment table");
            run(FORWARD);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE id = 901 AND feedbackId IS NULL AND status = 0",
                    Integer.class), "the first cycle must restore the unbound attachment");

            // 恢复之后附件绑定到反馈 88，状态改为 BOUND
            jdbc.update("UPDATE `" + FEEDBACK + "` SET feedbackId = 88, status = 1 WHERE id = 901");

            // 第二轮：回滚必须归档最新的绑定与状态，不能沿用第一轮的旧快照
            run(ROLLBACK);
            Map<String, Object> archived = jdbc.queryForMap(
                    "SELECT feedbackId, status FROM `" + FEEDBACK_ARCHIVE + "` WHERE id = 901");
            assertEquals(88L, ((Number) archived.get("feedbackId")).longValue(),
                    "the second rollback must archive the latest binding");
            assertEquals(1, ((Number) archived.get("status")).intValue(),
                    "the second rollback must archive the latest status");

            // 重新部署：最新绑定与状态被恢复，反馈里的引用仍然可解析
            run(FORWARD);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE id = 901 AND feedbackId = 88 AND status = 1",
                    Integer.class), "the second cycle must restore the latest binding and status");
            assertEquals("[\"attachment:901\"]", jdbc.queryForObject(
                    "SELECT pictureUrls FROM `" + USER_FEEDBACK + "` WHERE id = 88", String.class),
                    "the feedback reference must survive both cycles");
            // 归档被消费：下一轮回滚从当前业务状态重新归档，不会拿旧快照覆盖新绑定
            assertEquals(0, count(FEEDBACK_ARCHIVE), "the archive payload must be consumed after restore");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void rollbackIsBlockedWhenTheArchiveCannotHoldTheBusinessState() throws Exception {
        try {
            createScratchTables();
            run(FORWARD);
            // 业务表里有一条受控登记的附件
            jdbc.update("INSERT INTO `" + FEEDBACK + "`"
                    + " (id, ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash)"
                    + " VALUES (901, 7, NULL, 'acceptance', 'obj/901',"
                    + " 'https://acceptance.invalid/obj/901', 1, 'hash901')");
            // 归档里有一条占用同一地址摘要的旧记录：归档接不住业务状态
            jdbc.update("INSERT INTO `" + FEEDBACK_ARCHIVE + "`"
                    + " (id, ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash)"
                    + " VALUES (902, 7, NULL, 'acceptance', 'stale/902',"
                    + " 'https://acceptance.invalid/stale/902', 0, 'hash901')");

            // 归档接不住最新状态时必须阻断回滚：唯一有效的记录还在业务表里
            assertThrows(Exception.class, () -> run(ROLLBACK),
                    "rollback must be blocked when the archive cannot hold the business state");
            assertTrue(tableExists(FEEDBACK), "a blocked rollback must not drop the attachment table");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + FEEDBACK + "` WHERE id = 901", Integer.class),
                    "the only valid record must stay in the business table");
            assertTrue(tableExists(ARCHIVE_GUARD), "the archive guard must be left behind for inspection");
        } finally {
            dropScratchTables();
        }
    }

    // ---- helpers ----

    private void createScratchTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS `" + USER_FEEDBACK + "` ("
                + " id bigint NOT NULL, userId bigint NOT NULL, type int NOT NULL, title varchar(50) NOT NULL,"
                + " content varchar(2000) NOT NULL, pictureUrls text NULL, status int NOT NULL DEFAULT 0,"
                + " isDelete tinyint NOT NULL DEFAULT 0, PRIMARY KEY (id))");
    }

    private void dropScratchTables() {
        for (String table : List.of(FEEDBACK, GRANT, ISOLATION, GUARD, ARCHIVE_GUARD,
                USER_FEEDBACK, FEEDBACK_ARCHIVE)) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
    }

    /**
     * 按脚本原文执行，只把表名换成临时表名；其余语义（含守卫与幂等条件）保持不变。
     */
    private void run(Path script) throws IOException {
        for (String statement : statements(Files.readString(script, StandardCharsets.UTF_8))) {
            jdbc.execute(rename(statement));
        }
    }

    private String rename(String sql) {
        return sql.replace("`user_feedback`", "`" + USER_FEEDBACK + "`")
                .replace("`feedback_attachment_archive_20261023`", "`" + FEEDBACK_ARCHIVE + "`")
                .replace("`feedback_attachment`", "`" + FEEDBACK + "`")
                .replace("`resource_access_grant`", "`" + GRANT + "`")
                .replace("`resource_isolation`", "`" + ISOLATION + "`")
                .replace("`_private_resource_archive_guard`", "`" + ARCHIVE_GUARD + "`")
                .replace("`_private_resource_rollback_guard`", "`" + GUARD + "`");
    }

    private List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        assertEquals(0, current.toString().trim().length(), "script must end every statement with a semicolon");
        return result;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
        return count == null ? 0 : count;
    }
}
