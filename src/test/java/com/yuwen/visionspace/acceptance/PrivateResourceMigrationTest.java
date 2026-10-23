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
    private static final String USER_FEEDBACK = "scratch_user_feedback_36";

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
                    "[\"/temp/7/a.png\",\"https://cdn.example/b.png\"]");
            // 非 JSON 与空附件不能中断迁移
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (2, 7, 2, 't', 'c', 'not-json', 0, 0)");
            jdbc.update("INSERT INTO `" + USER_FEEDBACK + "`"
                    + " (id, userId, type, title, content, pictureUrls, status, isDelete)"
                    + " VALUES (3, 7, 2, 't', 'c', NULL, 0, 0)");

            run(FORWARD);
            assertEquals(2, count(FEEDBACK), "only valid JSON entries are registered");
            List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash"
                            + " FROM `" + FEEDBACK + "` ORDER BY id");
            for (var row : rows) {
                assertEquals(3, ((Number) row.get("status")).intValue(),
                        "unconfirmed legacy attachments must be BLOCKED");
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
            run(ROLLBACK);
            for (String table : TABLES) {
                assertFalse(tableExists(table), "rollback must drop " + table);
            }
            // 回滚保留既有业务数据，只删除本工单新增的表与守卫表
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + USER_FEEDBACK + "`", Integer.class),
                    "rollback must keep existing feedback rows");
            assertFalse(tableExists(GUARD), "successful rollback must clean up its guard table");
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
        for (String table : List.of(FEEDBACK, GRANT, ISOLATION, GUARD, USER_FEEDBACK)) {
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
                .replace("`feedback_attachment`", "`" + FEEDBACK + "`")
                .replace("`resource_access_grant`", "`" + GRANT + "`")
                .replace("`resource_isolation`", "`" + ISOLATION + "`")
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
