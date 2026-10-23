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
 * 事件幂等与持久领取状态的迁移脚本可执行，且前向与回滚互为逆操作。
 *
 * <p>脚本在临时表名上执行，不触碰本轮真实的 {@code user_picture_action}；
 * 真实环境的执行窗口、备份与存量冲突处置仍属人工关卡。
 */
@IsolatedSpringBootTest
class PictureStatsEventMigrationTest {

    private static final String TABLE = "user_picture_action";
    private static final String SCRATCH = "user_picture_action_migration_check";
    private static final String DEDUPE_TABLE = "picture_action_event_dedupe";
    private static final String DEDUPE_SCRATCH = "picture_action_event_dedupe_migration_check";

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-23-stats-action-event.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-23-stats-action-event-rollback.sql");
    private static final Path MAINTENANCE = Path.of("sql/migrations/2026-10-23-stats-action-event-maintenance.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationAddsTheEventAndClaimStateWithoutLosingLegacyRows() throws Exception {
        dropScratch();
        try {
            createLegacyTable();
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, createdTime, isProcessed)
                    VALUES (?, ?, ?, 2, 1, 0, NOW(), 0)
                    """.formatted(SCRATCH), 501L, 77L, 88_801L);

            run(FORWARD);

            List<String> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                    String.class, SCRATCH);
            assertTrue(columns.containsAll(List.of("id", "userId", "pictureId", "actionType", "actionValue",
                            "source", "eventId", "createdTime", "isProcessed", "isAccumulated", "claimToken",
                            "claimedAt", "claimExpireAt", "attemptCount")),
                    "the migrated table must keep the documented columns: " + columns);
            List<String> indexes = jdbc.queryForList(
                    "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, SCRATCH);
            assertTrue(indexes.containsAll(List.of("uk_eventId", "idx_claim")),
                    "the migrated table must keep the documented indexes: " + indexes);

            // 存量事件不丢失，也不被合并：eventId 保持为空，等待真实事件才写入
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + SCRATCH + "` WHERE id = 501", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT isAccumulated FROM `" + SCRATCH + "` WHERE id = 501", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT attemptCount FROM `" + SCRATCH + "` WHERE id = 501", Integer.class));
            assertEquals(null, jdbc.queryForObject(
                    "SELECT eventId FROM `" + SCRATCH + "` WHERE id = 501", String.class));

            // 存量行可以有多个空事件键，但同一个事件键只能有一条
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, createdTime, isProcessed)
                    VALUES (502, 77, 88801, 2, 1, 0, NOW(), 0)
                    """.formatted(SCRATCH));
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, eventId, createdTime, isProcessed)
                    VALUES (503, 77, 88801, 2, 1, 0, 'evt-1', NOW(), 0)
                    """.formatted(SCRATCH));
            assertThrows(Exception.class, () -> jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, eventId, createdTime, isProcessed)
                    VALUES (504, 77, 88801, 2, 1, 0, 'evt-1', NOW(), 0)
                    """.formatted(SCRATCH)), "the same event key must not be stored twice");
        } finally {
            dropScratch();
        }
    }

    /**
     * 幂等台账随前向迁移建立：重复判定与事件载荷的保留期分开。
     */
    @Test
    void forwardMigrationCreatesTheDedupeLedgerAndBackfillsExistingEventKeys() throws Exception {
        dropScratch();
        try {
            createLegacyTable();
            run(FORWARD);

            List<String> ledgerColumns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, DEDUPE_SCRATCH);
            assertTrue(ledgerColumns.containsAll(List.of("eventId", "createdTime")),
                    "the dedupe ledger must keep the documented columns: " + ledgerColumns);

            // 一条带事件键的事件：台账里必须有它，重复回填也不会多出一条
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, eventId, createdTime, isProcessed)
                    VALUES (801, 77, 88801, 2, 1, 0, 'evt-legacy', NOW(), 0)
                    """.formatted(SCRATCH));

            runBackfill();
            runBackfill();

            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + DEDUPE_SCRATCH + "` WHERE eventId = 'evt-legacy'", Integer.class),
                    "an existing event key must be backfilled into the dedupe ledger");
            assertThrows(Exception.class, () -> jdbc.update(
                    "INSERT INTO `" + DEDUPE_SCRATCH + "` (eventId, createdTime) VALUES ('evt-legacy', NOW())"),
                    "the same event key must not be stored twice in the ledger");
        } finally {
            dropScratch();
        }
    }

    @Test
    void rollbackRemovesTheNewStateAndDoesNotRecountAlreadyCountedEvents() throws Exception {
        dropScratch();
        try {
            createLegacyTable();
            run(FORWARD);
            // 一条已计入统计但没有完成标记的事件：回滚后必须被标记为已处理，
            // 否则旧版本会按时间窗再算一次
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, createdTime,
                                       isProcessed, isAccumulated)
                    VALUES (601, 77, 88801, 2, 1, 0, NOW(), 0, 1)
                    """.formatted(SCRATCH));
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, createdTime,
                                       isProcessed, isAccumulated)
                    VALUES (602, 77, 88801, 2, 1, 0, NOW(), 0, 0)
                    """.formatted(SCRATCH));

            run(ROLLBACK);

            List<String> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, SCRATCH);
            assertFalse(columns.contains("isAccumulated"), "rollback must drop the claim state");
            assertFalse(columns.contains("eventId"), "rollback must drop the event key");
            assertTrue(columns.contains("isProcessed"), "rollback must keep the legacy state");

            assertEquals(1, jdbc.queryForObject(
                    "SELECT isProcessed FROM `" + SCRATCH + "` WHERE id = 601", Integer.class),
                    "an already counted event must be marked processed so the legacy job will not count it again");
            assertEquals(0, jdbc.queryForObject(
                    "SELECT isProcessed FROM `" + SCRATCH + "` WHERE id = 602", Integer.class),
                    "an event that was never counted must stay pending");
        } finally {
            dropScratch();
        }
    }

    /**
     * 回滚不删除幂等台账：已经受理过的事件靠它挡住重放，删掉就等于让它们重新入账。
     */
    @Test
    void rollbackKeepsTheDedupeLedger() throws Exception {
        dropScratch();
        try {
            createLegacyTable();
            run(FORWARD);
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, eventId, createdTime,
                                       isProcessed, isAccumulated)
                    VALUES (901, 77, 88801, 2, 1, 0, 'evt-keep', NOW(), 1, 1)
                    """.formatted(SCRATCH));
            runBackfill();
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + DEDUPE_SCRATCH + "`", Integer.class),
                    "the accepted event must be in the dedupe ledger before the rollback");

            run(ROLLBACK);

            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + DEDUPE_SCRATCH + "`", Integer.class),
                    "the rollback must keep the dedupe evidence of already accepted events");
        } finally {
            dropScratch();
        }
    }

    @Test
    void maintenanceListIsExecutableAndOnlyReads() throws Exception {
        dropScratch();
        try {
            createLegacyTable();
            run(FORWARD);
            jdbc.update("""
                    INSERT INTO `%s` (id, userId, pictureId, actionType, actionValue, source, createdTime, isProcessed)
                    VALUES (701, 77, 88801, 2, 1, 0, NOW(), 0)
                    """.formatted(SCRATCH));
            // 冲突清单必须可执行，且只读：执行后数据不发生变化
            run(MAINTENANCE);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + SCRATCH + "` WHERE id = 701", Integer.class));
        } finally {
            dropScratch();
        }
    }

    private void dropScratch() {
        jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        jdbc.execute("DROP TABLE IF EXISTS `" + DEDUPE_SCRATCH + "`");
    }

    private void createLegacyTable() {
        jdbc.execute("""
                CREATE TABLE `%s` (
                  `id` bigint(20) NOT NULL AUTO_INCREMENT,
                  `userId` bigint(20) NOT NULL,
                  `pictureId` bigint(20) NOT NULL,
                  `actionType` int(11) NOT NULL DEFAULT 0,
                  `actionValue` int(11) NOT NULL DEFAULT 1,
                  `source` int(11) NOT NULL DEFAULT 0,
                  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                  `isProcessed` tinyint(4) NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`) USING BTREE
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
                """.formatted(SCRATCH));
    }

    private void run(Path script) throws IOException {
        for (String statement : statements(Files.readString(script, StandardCharsets.UTF_8))) {
            jdbc.execute(rewrite(statement));
        }
    }

    /**
     * 只执行幂等台账的回填语句：验证它能把已有事件键补进台账，且重复执行不会多写。
     */
    private void runBackfill() throws IOException {
        int executed = 0;
        for (String statement : statements(Files.readString(FORWARD, StandardCharsets.UTF_8))) {
            if (statement.contains("INSERT IGNORE INTO `" + DEDUPE_TABLE + "`")) {
                jdbc.execute(rewrite(statement));
                executed++;
            }
        }
        assertEquals(1, executed, "the forward migration must contain the ledger backfill");
    }

    private String rewrite(String statement) {
        return statement
                .replace("`" + TABLE + "`", "`" + SCRATCH + "`")
                .replace("`" + DEDUPE_TABLE + "`", "`" + DEDUPE_SCRATCH + "`");
    }

    /**
     * 去掉注释行后按分号切分；脚本本身不含字符串内的分号。
     */
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
}
