package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #37 空间唯一性与配额迁移脚本（前向 / 回填 / 回滚 / 存量清单）。
 *
 * <p>脚本在临时表名上执行（验收账号只有本轮库的权限，不能另建 schema），
 * 不触碰本轮真实的 space 与 storage_cleanup_task。验证：加列与唯一索引可重复执行、
 * 回填遇到重复有效空间时在改数据之前失败、回滚在有未收敛清理任务时被阻断、
 * 回滚成功后守卫表清理干净、存量清单能报出重复空间。
 *
 * <p>真实维护窗口内的执行、备份与人工处置仍属人工关卡，不在这里冒充通过。
 */
@IsolatedSpringBootTest
class SpaceQuotaMigrationTest {

    private static final String SPACE = "scratch_space_37";
    private static final String CLEANUP = "scratch_cleanup_37";
    private static final String ROLLBACK_GUARD = "scratch_rollback_guard_37";
    private static final String BACKFILL_GUARD = "scratch_backfill_guard_37";

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-25-space-uniqueness-quota.sql");
    private static final Path BACKFILL = Path.of("sql/migrations/2026-10-25-space-uniqueness-quota-backfill.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-25-space-uniqueness-quota-rollback.sql");
    private static final Path BLOCKLIST = Path.of("sql/migrations/2026-10-25-space-uniqueness-quota-blocklist.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardAddsTheColumnAndIndexAndIsIdempotent() throws Exception {
        try {
            createScratchSpace();
            run(FORWARD);
            assertTrue(columnExists(SPACE, "activeKey"), "forward migration must add space.activeKey");
            assertTrue(indexExists(SPACE, "uk_space_active_key"), "forward migration must add the unique index");
            assertTrue(tableExists(CLEANUP), "forward migration must create storage_cleanup_task");
            assertTrue(indexExists(CLEANUP, "uk_pending_object"), "cleanup tasks need a pending-unique key");

            // 第二次执行不应失败
            run(FORWARD);
            assertTrue(columnExists(SPACE, "activeKey"));
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void backfillFillsOnlyLiveSpacesAndNeverTouchesDeletedOnes() throws Exception {
        try {
            createScratchSpace();
            run(FORWARD);
            // 同一用户的两类空间各一条有效记录；另有一条已删除的历史墓碑
            insertSpace(1, 7, 0, 0);
            insertSpace(2, 7, 1, 0);
            insertSpace(3, 7, 0, 1);

            run(BACKFILL);
            assertEquals("7:0", activeKeyOf(1), "a live private space gets its placeholder");
            assertEquals("7:1", activeKeyOf(2), "a live team space gets its placeholder");
            assertNull(activeKeyOf(3), "a deleted space must not hold a placeholder");

            // 可重复执行：第二次结果不变
            run(BACKFILL);
            assertEquals("7:0", activeKeyOf(1));
            assertFalse(tableExists(BACKFILL_GUARD), "a successful backfill must clean up its guard table");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void backfillRefusesToChooseBetweenDuplicateLiveSpaces() throws Exception {
        try {
            createScratchSpace();
            run(FORWARD);
            // 两条同类有效空间：脚本不能自动选一个，也不能删一个
            insertSpace(1, 7, 0, 0);
            insertSpace(2, 7, 0, 0);

            assertThrows(Exception.class, () -> run(BACKFILL),
                    "backfill must fail while duplicate live spaces exist");
            // 失败发生在改动数据之前：两条都还没有占位键，数据一行没少
            assertNull(activeKeyOf(1), "a blocked backfill must not write any placeholder");
            assertNull(activeKeyOf(2), "a blocked backfill must not write any placeholder");
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + SPACE + "` WHERE isDelete = 0", Integer.class),
                    "a blocked backfill must not delete or merge duplicates");
            assertTrue(tableExists(BACKFILL_GUARD), "the guard table stays behind for inspection");
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void rollbackIsBlockedWhileCleanupTasksAreUnresolvedThenSucceeds() throws Exception {
        try {
            createScratchSpace();
            run(FORWARD);
            insertSpace(1, 7, 0, 0);
            run(BACKFILL);
            jdbc.update("INSERT INTO `" + CLEANUP + "` (platform, objectKey, reason, status, attempts)"
                    + " VALUES ('acceptance', 'obj/pending', 'test', 0, 0)");

            // 还有对象没删掉时回滚必须失败：删表等于丢掉这份事实
            assertThrows(Exception.class, () -> run(ROLLBACK),
                    "rollback must refuse while a cleanup task is unresolved");
            assertTrue(tableExists(CLEANUP), "a blocked rollback must not drop the task table");
            assertTrue(columnExists(SPACE, "activeKey"), "a blocked rollback must not drop the column");

            // 任务收敛后回滚成功
            jdbc.update("UPDATE `" + CLEANUP + "` SET status = 1, pendingKey = NULL");
            run(ROLLBACK);
            assertFalse(tableExists(CLEANUP), "rollback must drop the task table");
            assertFalse(columnExists(SPACE, "activeKey"), "rollback must drop space.activeKey");
            assertFalse(indexExists(SPACE, "uk_space_active_key"), "rollback must drop the unique index");
            assertFalse(tableExists(ROLLBACK_GUARD), "a successful rollback must clean up its guard table");
            // 空间数据本身不受影响
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + SPACE + "`", Integer.class),
                    "rollback must keep the space rows");

            // 重新部署：结构恢复，且不影响已有数据
            run(FORWARD);
            assertTrue(columnExists(SPACE, "activeKey"));
            assertTrue(tableExists(CLEANUP));
        } finally {
            dropScratchTables();
        }
    }

    @Test
    void blocklistReportsDuplicateLiveSpacesWithoutChangingData() throws Exception {
        try {
            createScratchSpace();
            run(FORWARD);
            insertSpace(1, 7, 0, 0);
            insertSpace(2, 7, 0, 0);

            List<Map<String, Object>> duplicates = blocklistDuplicates();

            assertTrue(duplicates.stream().anyMatch(row -> ((Number) row.get("spaceId")).longValue() == 1L
                            && ((Number) row.get("userId")).longValue() == 7L),
                    "the blocklist must report the duplicated group: " + duplicates);
            assertTrue(duplicates.stream().anyMatch(row -> ((Number) row.get("spaceId")).longValue() == 2L));
            // 只读：数据与结构都没变
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + SPACE + "` WHERE isDelete = 0", Integer.class));
            assertNull(activeKeyOf(1), "the blocklist must not write placeholders");
        } finally {
            dropScratchTables();
        }
    }

    // ---- helpers ----

    /**
     * 清单第 1 节的第一条语句（重复有效空间明细）。
     *
     * <p>其余小节引用的是本轮真实的业务表，第 1 节的第二条是汇总计数，
     * 列名不同，这里只取明细那一条。
     */
    private List<Map<String, Object>> blocklistDuplicates() throws IOException {
        String sql = Files.readString(BLOCKLIST, StandardCharsets.UTF_8);
        String section = sql.substring(sql.indexOf("-- ---------- 1."), sql.indexOf("-- ---------- 2."));
        return jdbc.queryForList(rename(statements(section).get(0)));
    }

    private void createScratchSpace() {        // 与生产 space 一致的最小结构：迁移脚本只加列与索引
        jdbc.execute("CREATE TABLE IF NOT EXISTS `" + SPACE + "` ("
                + " id bigint NOT NULL, spaceName varchar(128) NULL, spaceLevel int NOT NULL DEFAULT 0,"
                + " spaceType int NOT NULL DEFAULT 0, maxSize bigint NULL, maxCount bigint NULL,"
                + " totalSize bigint NULL, totalCount bigint NULL, userId bigint NOT NULL,"
                + " createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " editTime datetime NULL, updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " isDelete tinyint NOT NULL DEFAULT 0, PRIMARY KEY (id))");
    }

    private void insertSpace(long id, long userId, int spaceType, int isDelete) {
        jdbc.update("INSERT INTO `" + SPACE + "` (id, spaceName, spaceType, userId, totalSize, totalCount,"
                + " maxSize, maxCount, isDelete) VALUES (?, ?, ?, ?, 0, 0, 1000, 100, ?)",
                id, "space-" + id, spaceType, userId, isDelete);
    }

    private Object activeKeyOf(long id) {
        return jdbc.queryForObject("SELECT activeKey FROM `" + SPACE + "` WHERE id = ?", Object.class, id);
    }

    private void dropScratchTables() {
        for (String table : List.of(CLEANUP, ROLLBACK_GUARD, BACKFILL_GUARD, SPACE)) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        jdbc.execute("DROP PROCEDURE IF EXISTS `vs37_add_column_if_missing`");
    }

    /**
     * 在**同一条连接**上执行整份脚本。
     *
     * <p>脚本用 {@code SET @ddl = ...} / {@code PREPARE} 做条件 DDL：会话变量与
     * 预备语句都只存在于一条连接上。用 {@code JdbcTemplate} 逐条执行会被连接池
     * 分配到不同连接，第二条就看不到第一条设的变量——脚本在生产里由 mysql
     * 客户端单连接执行，这里必须与之一致。
     */
    private void run(Path script) throws IOException {
        List<String> statements = statements(Files.readString(script, StandardCharsets.UTF_8));
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(rename(sql));
                }
            }
            return null;
        });
    }

    private String rename(String sql) {
        // 同时替换标识符与 information_schema 判断里的表名常量
        return sql.replace("`space`", "`" + SPACE + "`")
                .replace("'space'", "'" + SPACE + "'")
                .replace("`storage_cleanup_task`", "`" + CLEANUP + "`")
                .replace("'storage_cleanup_task'", "'" + CLEANUP + "'")
                .replace("`_space_quota_rollback_guard`", "`" + ROLLBACK_GUARD + "`")
                .replace("`_space_quota_backfill_guard`", "`" + BACKFILL_GUARD + "`");
    }

    /**
     * 按脚本原文切分语句，同时支持 mysql 客户端的 {@code DELIMITER} 指令。
     *
     * <p>{@code DELIMITER} 只是客户端指令，服务端不认识；这里用它切分后把
     * 存储过程体作为一整条语句发给服务端。
     */
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
        assertEquals(0, current.toString().trim().length(), "script must end every statement with a delimiter");
        return result;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }
}
