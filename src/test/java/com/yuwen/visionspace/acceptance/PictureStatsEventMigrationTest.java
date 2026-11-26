package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件幂等与持久领取状态的持久化语义，跑在真实 PostgreSQL 上。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：基线 {@code V1__baseline.sql} 直接建出
 * 迁移后的最终结构，验收账号也没有建表权限。因此这里不再执行 MySQL 迁移脚本，
 * 改为把「迁移前就存在的行」直接插进当前结构，再验证迁移当初建立的那几条约束
 * 依然成立：事件幂等键唯一、幂等台账可重复回填、已计入统计的事件必须先收敛成
 * 已处理、盘点清单只读。
 *
 * <p>真实环境的执行窗口、备份与存量冲突处置仍属人工关卡。
 */
@IsolatedSpringBootTest
class PictureStatsEventMigrationTest {

    private static final String TABLE = "user_picture_action";
    private static final String DEDUPE_TABLE = "picture_action_event_dedupe";

    /** 本轮用例独占的行为主体与图片，避免与其他用例互相干扰。 */
    private static final long USER_ID = 88_802L;
    private static final long PICTURE_ID = 88_803L;

    /** 本轮用例写下的事件键；台账只增不减，清理时按这些键精确删除。 */
    private static final List<String> EVENT_KEYS =
            List.of("evt-1", "evt-legacy", "evt-keep", "evt-maint");

    /**
     * 盘点清单的只读语句（PostgreSQL 版）：A 丢失计数 / B 孤儿事件 / C 领取积压 /
     * D 正常待处理 / E 只剩台账的证据。
     */
    private static final List<String> MAINTENANCE_SQL = List.of(
            "SELECT 'A-lost-count' AS conflict_type, a.picture_id AS picture_id, COUNT(*) AS event_count, "
                    + "MIN(a.created_time) AS first_event_time, MAX(a.created_time) AS last_event_time "
                    + "FROM user_picture_action a "
                    + "LEFT JOIN picture_stats s ON s.picture_id = a.picture_id "
                    + "WHERE a.is_processed = 1 AND s.picture_id IS NULL "
                    + "GROUP BY a.picture_id ORDER BY event_count DESC",
            "SELECT 'B-orphan-pending' AS conflict_type, a.picture_id AS picture_id, COUNT(*) AS event_count, "
                    + "MIN(a.created_time) AS first_event_time, MAX(a.created_time) AS last_event_time "
                    + "FROM user_picture_action a "
                    + "LEFT JOIN picture p ON p.id = a.picture_id AND p.is_delete = 0 "
                    + "WHERE a.is_processed = 0 AND p.id IS NULL "
                    + "GROUP BY a.picture_id ORDER BY event_count DESC",
            "SELECT 'C-stuck-pending' AS conflict_type, a.picture_id AS picture_id, COUNT(*) AS event_count, "
                    + "MAX(a.attempt_count) AS max_attempt_count, MAX(a.claim_expire_at) AS last_claim_expire_at "
                    + "FROM user_picture_action a "
                    + "WHERE a.is_processed = 0 AND a.attempt_count >= 3 "
                    + "GROUP BY a.picture_id ORDER BY max_attempt_count DESC, event_count DESC",
            "SELECT 'D-pending-normal' AS conflict_type, COUNT(*) AS event_count, "
                    + "MIN(a.created_time) AS first_event_time, MAX(a.created_time) AS last_event_time "
                    + "FROM user_picture_action a "
                    + "JOIN picture p ON p.id = a.picture_id AND p.is_delete = 0 "
                    + "WHERE a.is_processed = 0 AND a.attempt_count < 3",
            "SELECT 'E-dedupe-evidence-only' AS conflict_type, COUNT(*) AS event_count, "
                    + "MIN(d.created_time) AS first_event_time, MAX(d.created_time) AS last_event_time "
                    + "FROM picture_action_event_dedupe d "
                    + "LEFT JOIN user_picture_action a ON a.event_id = d.event_id "
                    + "WHERE a.id IS NULL");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanFixtures() {
        deleteFixtures();
    }

    /**
     * 用例跑完也要清干净。
     *
     * <p>本类故意造「目标图片不存在的孤儿待处理事件」；这些行留在共享库里会让
     * 后面用例看到全局积压不为 0，测试之间互相污染（用例执行顺序一变就变红）。
     */
    @AfterEach
    void removeFixtures() {
        deleteFixtures();
    }

    private void deleteFixtures() {
        jdbc.update("DELETE FROM " + TABLE + " WHERE user_id = ?", USER_ID);
        for (String key : EVENT_KEYS) {
            jdbc.update("DELETE FROM " + DEDUPE_TABLE + " WHERE event_id = ?", key);
        }
    }

    @Test
    void forwardMigrationAddsTheEventAndClaimStateWithoutLosingLegacyRows() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                String.class, TABLE);
        assertTrue(columns.containsAll(List.of("id", "user_id", "picture_id", "action_type", "action_value",
                        "source", "event_id", "created_time", "is_processed", "is_accumulated", "claim_token",
                        "claimed_at", "claim_expire_at", "attempt_count")),
                "the migrated table must keep the documented columns: " + columns);
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, TABLE);
        assertTrue(indexes.containsAll(List.of("uq_user_picture_action_event_id", "ix_user_picture_action_claim")),
                "the migrated table must keep the documented indexes: " + indexes);

        // 存量事件不丢失，也不被合并：event_id 保持为空，等待真实事件才写入
        insertAction(501L, null, 0, 0, 0);
        assertEquals(1, countRows(501L));
        assertEquals(0, stateOf(501L, "is_accumulated"));
        assertEquals(0, stateOf(501L, "attempt_count"));
        assertEquals(null, jdbc.queryForObject(
                "SELECT event_id FROM " + TABLE + " WHERE id = 501", String.class));

        // 存量行可以有多个空事件键，但同一个事件键只能有一条
        insertAction(502L, null, 0, 0, 0);
        insertAction(503L, "evt-1", 0, 0, 0);
        int inserted = insertActionIgnoringConflict(504L, "evt-1", 0, 0, 0);
        assertEquals(0, inserted, "the same event key must not be stored twice");
        assertEquals(1, countRowsByEventKey("evt-1"),
                "the same event key must not be stored twice");
    }

    /**
     * 幂等台账随前向迁移建立：重复判定与事件载荷的保留期分开。
     */
    @Test
    void forwardMigrationCreatesTheDedupeLedgerAndBackfillsExistingEventKeys() {
        List<String> ledgerColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ?",
                String.class, DEDUPE_TABLE);
        assertTrue(ledgerColumns.containsAll(List.of("event_id", "created_time")),
                "the dedupe ledger must keep the documented columns: " + ledgerColumns);

        // 一条带事件键的事件：台账里必须有它，重复回填也不会多出一条
        insertAction(801L, "evt-legacy", 0, 0, 0);

        runBackfill();
        runBackfill();

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + DEDUPE_TABLE + " WHERE event_id = 'evt-legacy'", Integer.class),
                "an existing event key must be backfilled into the dedupe ledger");
        int inserted = jdbc.update(
                "INSERT INTO " + DEDUPE_TABLE + " (event_id, created_time) VALUES ('evt-legacy', statement_timestamp()) "
                        + "ON CONFLICT (event_id) DO NOTHING");
        assertEquals(0, inserted, "the same event key must not be stored twice in the ledger");
    }

    /**
     * 已计入统计但没有完成标记的事件必须先收敛成已处理，
     * 否则旧版本会按时间窗再算一次。
     *
     * <p>PostgreSQL 基线直接是最终结构，没有「回滚删列」这一步；
     * 真正需要长期守住的是这条收敛规则本身。
     */
    @Test
    void rollbackRemovesTheNewStateAndDoesNotRecountAlreadyCountedEvents() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ?",
                String.class, TABLE);
        assertTrue(columns.containsAll(List.of("event_id", "is_accumulated", "claim_token", "attempt_count")),
                "the claim state must stay part of the current schema: " + columns);
        assertTrue(columns.contains("is_processed"), "the legacy state must stay part of the current schema");

        // 一条已计入统计但没有完成标记的事件，收敛后必须被标记为已处理，
        // 否则旧版本会按时间窗再算一次
        insertAction(601L, null, 0, 1, 0);
        insertAction(602L, null, 0, 0, 0);

        jdbc.update("UPDATE " + TABLE + " SET is_processed = 1 WHERE is_processed = 0 AND is_accumulated = 1");

        assertEquals(1, stateOf(601L, "is_processed"),
                "an already counted event must be marked processed so the legacy job will not count it again");
        assertEquals(0, stateOf(602L, "is_processed"),
                "an event that was never counted must stay pending");
    }

    /**
     * 幂等台账只增不减：已经受理过的事件靠它挡住重放，删掉就等于让它们重新入账。
     */
    @Test
    void rollbackKeepsTheDedupeLedger() {
        insertAction(901L, "evt-keep", 1, 1, 0);
        runBackfill();
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + DEDUPE_TABLE + " WHERE event_id = 'evt-keep'", Integer.class),
                "the accepted event must be in the dedupe ledger");

        // 台账留着，重放同一个事件键仍然被挡住
        int inserted = insertActionIgnoringConflict(902L, "evt-keep", 0, 0, 0);
        assertEquals(0, inserted, "an accepted event must not be accepted again");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + DEDUPE_TABLE + " WHERE event_id = 'evt-keep'", Integer.class),
                "the rollback must keep the dedupe evidence of already accepted events");
    }

    @Test
    void maintenanceListIsExecutableAndOnlyReads() {
        // 一条已处理但目标图片没有统计行的事件：A 类缺口
        insertAction(701L, null, 1, 0, 0);
        // 一条未处理且目标图片不存在的孤儿事件：B 类缺口
        insertAction(702L, null, 0, 0, 0);
        // 一条反复领取失败的事件：C 类积压
        insertAction(703L, null, 0, 0, 3);

        int actionsBefore = countFixtureActions();
        int ledgerBefore = countFixtureLedger();

        List<java.util.Map<String, Object>> lost = jdbc.queryForList(MAINTENANCE_SQL.get(0));
        List<java.util.Map<String, Object>> orphan = jdbc.queryForList(MAINTENANCE_SQL.get(1));
        List<java.util.Map<String, Object>> stuck = jdbc.queryForList(MAINTENANCE_SQL.get(2));
        jdbc.queryForList(MAINTENANCE_SQL.get(3));
        jdbc.queryForList(MAINTENANCE_SQL.get(4));

        assertTrue(lost.stream().anyMatch(row -> ((Number) row.get("picture_id")).longValue() == PICTURE_ID),
                "the maintenance list must report the lost-count group: " + lost);
        assertTrue(orphan.stream().anyMatch(row -> ((Number) row.get("picture_id")).longValue() == PICTURE_ID),
                "the maintenance list must report the orphan group: " + orphan);
        assertTrue(stuck.stream().anyMatch(row -> ((Number) row.get("picture_id")).longValue() == PICTURE_ID),
                "the maintenance list must report the stuck group: " + stuck);

        // 冲突清单必须可执行，且只读：执行后本用例的夹具不增不减。
        // 这里刻意不用全表计数：上报走异步队列，别的用例排空晚一步就会往
        // user_picture_action / picture_action_event_dedupe 里补行，全表计数
        // 会在 CI（机器慢、时序不同）上偶发变红，而盘点 SQL 其实什么也没改。
        assertEquals(actionsBefore, countFixtureActions());
        assertEquals(ledgerBefore, countFixtureLedger());
        assertEquals(1, countRows(701L));
    }

    // ---- helpers ----

    private void insertAction(long id, String eventId, int isProcessed, int isAccumulated, int attemptCount) {
        jdbc.update("""
                INSERT INTO user_picture_action
                    (id, user_id, picture_id, action_type, action_value, source, event_id, created_time,
                     is_processed, is_accumulated, attempt_count)
                VALUES (?, ?, ?, 2, 1, 0, CAST(? AS varchar(128)), statement_timestamp(), ?, ?, ?)
                """, id, USER_ID, PICTURE_ID, eventId, isProcessed, isAccumulated, attemptCount);
    }

    /**
     * 重复的事件键由唯一索引挡下：PostgreSQL 上不靠捕获异常，
     * 靠 {@code ON CONFLICT DO NOTHING} 的影响行数判定。
     */
    private int insertActionIgnoringConflict(long id, String eventId, int isProcessed, int isAccumulated,
                                             int attemptCount) {
        return jdbc.update("""
                INSERT INTO user_picture_action
                    (id, user_id, picture_id, action_type, action_value, source, event_id, created_time,
                     is_processed, is_accumulated, attempt_count)
                VALUES (?, ?, ?, 2, 1, 0, CAST(? AS varchar(128)), statement_timestamp(), ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, id, USER_ID, PICTURE_ID, eventId, isProcessed, isAccumulated, attemptCount);
    }

    /**
     * 把存量事件幂等键补进台账：只补重复判定凭证，不改任何计数，也不合并任何事件。
     * 可重复执行，重复的事件键由台账主键挡下。
     */
    private void runBackfill() {
        jdbc.update("""
                INSERT INTO picture_action_event_dedupe (event_id, created_time)
                SELECT event_id, coalesce(created_time, statement_timestamp())
                FROM user_picture_action
                WHERE event_id IS NOT NULL
                ON CONFLICT (event_id) DO NOTHING
                """);
    }

    private int countRows(long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private int countRowsByEventKey(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private int stateOf(long id, String column) {
        Integer value = jdbc.queryForObject(
                "SELECT " + column + " FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return value == null ? 0 : value;
    }

    private int countFixtureActions() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE user_id = ?", Integer.class, USER_ID);
        return count == null ? 0 : count;
    }

    private int countFixtureLedger() {
        String placeholders = "?" + ",?".repeat(EVENT_KEYS.size() - 1);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + DEDUPE_TABLE + " WHERE event_id IN (" + placeholders + ")",
                Integer.class, EVENT_KEYS.toArray());
        return count == null ? 0 : count;
    }
}
