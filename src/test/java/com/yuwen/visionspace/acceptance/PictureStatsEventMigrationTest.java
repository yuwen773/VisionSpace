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

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-23-stats-action-event.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-23-stats-action-event-rollback.sql");
    private static final Path MAINTENANCE = Path.of("sql/migrations/2026-10-23-stats-action-event-maintenance.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationAddsTheEventAndClaimStateWithoutLosingLegacyRows() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
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
            jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        }
    }

    @Test
    void rollbackRemovesTheNewStateAndDoesNotRecountAlreadyCountedEvents() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
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
            jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        }
    }

    @Test
    void maintenanceListIsExecutableAndOnlyReads() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
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
            jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        }
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
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
                """.formatted(SCRATCH));
    }

    private void run(Path script) throws IOException {
        for (String statement : statements(Files.readString(script, StandardCharsets.UTF_8))) {
            jdbc.execute(statement.replace("`" + TABLE + "`", "`" + SCRATCH + "`"));
        }
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
