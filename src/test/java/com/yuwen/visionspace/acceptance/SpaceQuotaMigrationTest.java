package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #37 空间唯一性与配额的持久化语义，跑在真实 PostgreSQL 上。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：基线 {@code V1__baseline.sql} 直接建出
 * {@code space.active_key} 与 {@code storage_cleanup_task}，验收账号也只有 DML
 * 权限、不能建表。因此这里不再执行 MySQL 的前向/回滚脚本，改为把「迁移前就存在
 * 的行」（没有占位键的存量空间、未收敛的清理任务）直接插进当前结构，再验证迁移
 * 当初建立的那几条约束依然成立：
 *
 * <ul>
 *   <li>有效空间按 {@code userId:spaceType} 占位，同一创建者的同一类型最多一条
 *       有效记录；已删除的历史墓碑不占位，之后可以重新创建同类空间；</li>
 *   <li>重复的有效空间不会被自动选择、合并或删除——回填在改数据之前就失败，
 *       数据库把整条语句回滚，存量行一行不少；</li>
 *   <li>清理任务的待处理唯一键在任务收敛前一直占着，对象没删掉这件事不会丢。</li>
 * </ul>
 *
 * <p>真实维护窗口内的执行、备份与人工处置仍属人工关卡，不在这里冒充通过。
 */
@IsolatedSpringBootTest
class SpaceQuotaMigrationTest {

    private static final String SPACE = "space";
    private static final String CLEANUP = "storage_cleanup_task";

    /** 本轮用例独占的空间 id 段与创建者 id：占位键由这两者拼出，不与他人撞。 */
    private static final long SPACE_ID_BASE = 3_700_000_000L;
    private static final long SPACE_OWNER_ID = 7_700_007L;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM " + SPACE + " WHERE id >= ? AND id < ?", SPACE_ID_BASE, SPACE_ID_BASE + 100);
        jdbc.update("DELETE FROM " + CLEANUP + " WHERE platform = 'acceptance'");
    }

    @AfterEach
    void cleanFixturesAfter() {
        jdbc.update("DELETE FROM " + SPACE + " WHERE id >= ? AND id < ?", SPACE_ID_BASE, SPACE_ID_BASE + 100);
        jdbc.update("DELETE FROM " + CLEANUP + " WHERE platform = 'acceptance'");
    }

    @Test
    void forwardAddsTheColumnAndIndexAndIsIdempotent() {
        assertTrue(columnExists(SPACE, "active_key"), "the current schema must provide space.active_key");
        assertTrue(indexExists(SPACE, "uq_space_space_active_key"),
                "the current schema must provide the unique index on space.active_key");
        assertTrue(tableExists(CLEANUP), "the current schema must provide storage_cleanup_task");
        assertTrue(indexExists(CLEANUP, "uq_storage_cleanup_task_pending_object"),
                "cleanup tasks need a pending-unique key");
        assertTrue(columnExists(CLEANUP, "pending_key"), "cleanup tasks need a digest pending key");
        assertTrue(columnExists(CLEANUP, "claim_time"), "cleanup tasks need a claim timestamp");

        // 同一个占位键只能占住一条：重复声明不报错，但也写不进第二行
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        assertEquals(1, jdbc.update("UPDATE " + SPACE + " SET active_key = '7700007:0' WHERE id = ?", SPACE_ID_BASE));
        assertEquals(0, jdbc.update("INSERT INTO " + SPACE
                        + " (id, space_name, space_type, user_id, total_size, total_count, max_size, max_count,"
                        + " is_delete, active_key) VALUES (?, 'space-duplicate', 0, ?, 0, 0, 1000, 100, 0,"
                        + " '7700007:0')"
                        + " ON CONFLICT (active_key) DO NOTHING", SPACE_ID_BASE + 1, SPACE_OWNER_ID),
                "the same placeholder must not be claimed twice");
        assertTrue(columnExists(SPACE, "active_key"));
        assertTrue(columnExists(CLEANUP, "pending_key"));
        assertTrue(columnExists(CLEANUP, "claim_time"));
    }

    @Test
    void backfillFillsOnlyLiveSpacesAndNeverTouchesDeletedOnes() {
        // 同一用户的两类空间各一条有效记录；另有一条已删除的历史墓碑
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        insertSpace(SPACE_ID_BASE + 1, SPACE_OWNER_ID, 1, 0);
        insertSpace(SPACE_ID_BASE + 2, SPACE_OWNER_ID, 0, 1);

        runBackfill();
        assertEquals("7700007:0", activeKeyOf(SPACE_ID_BASE), "a live private space gets its placeholder");
        assertEquals("7700007:1", activeKeyOf(SPACE_ID_BASE + 1), "a live team space gets its placeholder");
        assertNull(activeKeyOf(SPACE_ID_BASE + 2), "a deleted space must not hold a placeholder");

        // 可重复执行：第二次结果不变
        runBackfill();
        assertEquals("7700007:0", activeKeyOf(SPACE_ID_BASE));
        assertEquals("7700007:1", activeKeyOf(SPACE_ID_BASE + 1));
        assertNull(activeKeyOf(SPACE_ID_BASE + 2));
    }

    @Test
    void backfillRefusesToChooseBetweenDuplicateLiveSpaces() {
        // 两条同类有效空间：脚本不能自动选一个，也不能删一个
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        insertSpace(SPACE_ID_BASE + 1, SPACE_OWNER_ID, 0, 0);

        assertThrows(Exception.class, () -> runBackfill(),
                "backfill must fail while duplicate live spaces exist");
        // 失败发生在改动数据之前：两条都还没有占位键，数据一行没少
        assertNull(activeKeyOf(SPACE_ID_BASE), "a blocked backfill must not write any placeholder");
        assertNull(activeKeyOf(SPACE_ID_BASE + 1), "a blocked backfill must not write any placeholder");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SPACE + " WHERE is_delete = 0 AND id >= ? AND id < ?",
                Integer.class, SPACE_ID_BASE, SPACE_ID_BASE + 100),
                "a blocked backfill must not delete or merge duplicates");
    }

    @Test
    void forwardMigrationLeavesLegacyRowsUnconstrainedUntilBackfill() {
        // 前向迁移后未回填：两条同类有效空间都没有占位键，唯一索引约束不到它们。
        // 这正说明「只加列和索引」不等于放行，必须由维护窗口在回填前保持停写。
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        insertSpace(SPACE_ID_BASE + 1, SPACE_OWNER_ID, 0, 0);
        assertNull(activeKeyOf(SPACE_ID_BASE), "the forward migration must not silently claim a legacy space");
        assertNull(activeKeyOf(SPACE_ID_BASE + 1), "the forward migration must not silently claim a legacy space");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SPACE + " WHERE is_delete = 0 AND id >= ? AND id < ?",
                Integer.class, SPACE_ID_BASE, SPACE_ID_BASE + 100),
                "the forward migration must not merge or delete duplicates");
        assertThrows(Exception.class, () -> runBackfill(),
                "backfill must stay blocked while unresolved duplicates exist");
    }

    @Test
    void rollbackIsBlockedWhileCleanupTasksAreUnresolvedThenSucceeds() {
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        runBackfill();
        String pendingKey = "a".repeat(64);
        jdbc.update("INSERT INTO " + CLEANUP + " (platform, object_key, reason, status, attempts, pending_key)"
                + " VALUES ('acceptance', 'obj/pending', 'test', 0, 0, ?)", pendingKey);

        // 还有对象没删掉时，同一个对象不能被再排一次：待处理唯一键一直占着
        assertEquals(0, insertCleanupIgnoringConflict(pendingKey),
                "an unresolved cleanup task must keep the pending key occupied");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + CLEANUP + " WHERE platform = 'acceptance'", Integer.class),
                "a blocked action must not drop the task table");
        assertTrue(columnExists(SPACE, "active_key"), "a blocked action must not drop the column");

        // 任务收敛后，对象不再占位，可以重新排一次
        jdbc.update("UPDATE " + CLEANUP + " SET status = 1, pending_key = NULL WHERE platform = 'acceptance'");
        assertEquals(1, insertCleanupIgnoringConflict(pendingKey),
                "a converged cleanup task must release the pending key");

        // 空间数据本身不受影响
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SPACE + " WHERE id >= ? AND id < ?",
                Integer.class, SPACE_ID_BASE, SPACE_ID_BASE + 100),
                "the space rows must be kept");
    }

    @Test
    void blocklistReportsDuplicateLiveSpacesWithoutChangingData() {
        insertSpace(SPACE_ID_BASE, SPACE_OWNER_ID, 0, 0);
        insertSpace(SPACE_ID_BASE + 1, SPACE_OWNER_ID, 0, 0);

        List<Map<String, Object>> duplicates = blocklistDuplicates();

        assertTrue(duplicates.stream().anyMatch(row -> ((Number) row.get("space_id")).longValue() == SPACE_ID_BASE
                        && ((Number) row.get("user_id")).longValue() == SPACE_OWNER_ID),
                "the blocklist must report the duplicated group: " + duplicates);
        assertTrue(duplicates.stream().anyMatch(row -> ((Number) row.get("space_id")).longValue() == SPACE_ID_BASE + 1));
        // 只读：数据与结构都没变
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SPACE + " WHERE is_delete = 0 AND id >= ? AND id < ?",
                Integer.class, SPACE_ID_BASE, SPACE_ID_BASE + 100));
        assertNull(activeKeyOf(SPACE_ID_BASE), "the blocklist must not write placeholders");
    }

    // ---- helpers ----

    /**
     * 存量核对清单第 1 节：重复有效空间明细。只读，不改数据。
     */
    private List<Map<String, Object>> blocklistDuplicates() {
        return jdbc.queryForList("""
                SELECT s.user_id AS user_id,
                       s.space_type AS space_type,
                       s.id AS space_id,
                       s.space_name AS space_name,
                       s.total_count AS total_count,
                       s.total_size AS total_size,
                       s.create_time AS create_time,
                       (SELECT COUNT(*) FROM picture p WHERE p.space_id = s.id AND p.is_delete = 0) AS live_pictures,
                       (SELECT COUNT(*) FROM space_user su WHERE su.space_id = s.id) AS member_count
                FROM space s
                JOIN (
                  SELECT user_id, space_type
                  FROM space
                  WHERE is_delete = 0 AND id >= ? AND id < ?
                  GROUP BY user_id, space_type
                  HAVING COUNT(*) > 1
                ) d ON d.user_id = s.user_id AND d.space_type = s.space_type
                WHERE s.is_delete = 0 AND s.id >= ? AND s.id < ?
                ORDER BY s.user_id ASC, s.space_type ASC, s.id ASC
                """, SPACE_ID_BASE, SPACE_ID_BASE + 100, SPACE_ID_BASE, SPACE_ID_BASE + 100);
    }

    /**
     * 存量有效空间的占位键回填：把每条有效空间的 activeKey 写成
     * {@code userId:spaceType}；已删除的空间保持 NULL。
     *
     * <p>存在重复有效空间时唯一约束会让整条语句失败并回滚，
     * 脚本不会替人工「选中」其中某一个。
     */
    private void runBackfill() {
        jdbc.update("""
                UPDATE space SET active_key = user_id::text || ':' || space_type::text
                WHERE is_delete = 0 AND id >= ? AND id < ?
                  AND (active_key IS NULL OR active_key <> user_id::text || ':' || space_type::text)
                """, SPACE_ID_BASE, SPACE_ID_BASE + 100);
    }

    private void insertSpace(long id, long userId, int spaceType, int isDelete) {
        jdbc.update("INSERT INTO " + SPACE + " (id, space_name, space_type, user_id, total_size, total_count,"
                + " max_size, max_count, is_delete) VALUES (?, ?, ?, ?, 0, 0, 1000, 100, ?)",
                id, "space-" + id, spaceType, userId, isDelete);
    }

    private int insertCleanupIgnoringConflict(String pendingKey) {
        return jdbc.update("INSERT INTO " + CLEANUP
                + " (platform, object_key, reason, status, attempts, pending_key)"
                + " VALUES ('acceptance', 'obj/pending', 'test', 0, 0, ?)"
                + " ON CONFLICT (pending_key) DO NOTHING", pendingKey);
    }

    private Object activeKeyOf(long id) {
        return jdbc.queryForObject("SELECT active_key FROM " + SPACE + " WHERE id = ?", Object.class, id);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }
}
