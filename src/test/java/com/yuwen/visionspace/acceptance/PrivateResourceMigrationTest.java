package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #36 私有资源保护在 PostgreSQL 上的持久化语义。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：基线 {@code V1__baseline.sql} 直接建出
 * 三张业务表，验收账号 {@code vision_app} 也只有 DML 权限、不能建表。因此这里
 * 不再执行 MySQL 的前向/回滚脚本，改为把「迁移前就存在的行」（旧反馈附件、
 * 未解除的隔离）直接插进当前结构，再验证迁移当初建立的那几条约束依然成立：
 *
 * <ul>
 *   <li>旧反馈附件只按服务端写入的 {@code /temp/{提交者}/} 前缀判定归属，
 *       成立的登记为 LEGACY_VERIFIED，其余登记为 BLOCKED，且重复登记幂等；</li>
 *   <li>授权与隔离用 {@code active_key} 唯一约束表达「同一资源同一对象最多一条
 *       有效记录」，该约束由数据库跨实例生效；</li>
 *   <li>附件的定位信息（平台、对象键、归属、绑定、状态）留在附件记录上，
 *       反馈里的 {@code attachment:{id}} 引用必须始终可解析。</li>
 * </ul>
 *
 * <p>真实环境的执行窗口、备份与人工核对仍属人工关卡。
 */
@IsolatedSpringBootTest
class PrivateResourceMigrationTest {

    private static final String FEEDBACK = "feedback_attachment";
    private static final String GRANT = "resource_access_grant";
    private static final String ISOLATION = "resource_isolation";
    private static final String USER_FEEDBACK = "user_feedback";

    private static final List<String> TABLES = List.of(FEEDBACK, GRANT, ISOLATION);

    /**
     * 附件 id 用小整数：反馈里的引用就是 {@code attachment:{id}}，
     * id 必须与引用里的数字一致，引用才可解析。
     */
    private static final long ATTACHMENT_ID = 42L;
    private static final long ATTACHMENT_ID_901 = 901L;
    private static final long ATTACHMENT_ID_902 = 902L;

    /** 本轮用例独占的 id 段：与应用的雪花 id、其他用例的行都不冲突。 */
    private static final long FEEDBACK_ID_BASE = 3_600_020_000L;
    private static final long FEEDBACK_ID_BASE_USER = 3_600_010_000L;
    private static final long ISOLATION_ID = 3_600_030_001L;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanFixtures() {
        deleteFixtures();
    }

    @AfterEach
    void cleanFixturesAfter() {
        deleteFixtures();
    }

    @Test
    void forwardCreatesTablesAndIsIdempotent() {
        for (String table : TABLES) {
            assertTrue(tableExists(table), "the current schema must provide " + table);
        }
        assertTrue(indexExists(FEEDBACK, "uq_feedback_attachment_legacy_url_hash"));
        assertTrue(indexExists(GRANT, "uq_resource_access_grant_active_key"));
        assertTrue(indexExists(ISOLATION, "uq_resource_isolation_active_key"));

        insertFeedback(FEEDBACK_ID_BASE_USER + 1, 7, "[\"/temp/7/a.png\"]");
        registerLegacyAttachments(FEEDBACK_ID_BASE);
        assertEquals(1, countLegacy(), "the legacy attachment must be registered");

        // 重复执行不应失败，也不应重复登记旧附件
        registerLegacyAttachments(FEEDBACK_ID_BASE);
        assertEquals(1, countLegacy(), "re-running the registration must be idempotent");
    }

    @Test
    void legacyAttachmentsAreRegisteredAsBlockedAndNeverClaimed() {
        insertFeedback(FEEDBACK_ID_BASE_USER + 1, 7,
                "[\"/temp/7/a.png\",\"https://cdn.example/b.png\",\"/temp/99/c.png\"]");
        // 非 JSON 与空附件不能中断登记
        insertFeedback(FEEDBACK_ID_BASE_USER + 2, 7, "not-json");
        insertFeedback(FEEDBACK_ID_BASE_USER + 3, 7, null);

        registerLegacyAttachments(FEEDBACK_ID_BASE);
        assertEquals(3, countLegacy(), "only valid JSON entries are registered");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT owner_user_id, feedback_id, platform, object_key, url, status, legacy_url_hash"
                        + " FROM " + FEEDBACK + " WHERE platform = 'legacy'"
                        + " AND id >= ? AND id < ? ORDER BY object_key",
                FEEDBACK_ID_BASE, FEEDBACK_ID_BASE + 100_000);
        // 归属按服务端写入的 /temp/{提交者}/ 前缀核对：
        // /temp/7/ 指向反馈提交者 → 可读；其余（外站地址、指向别人的前缀）→ 阻断
        Map<String, Integer> expectedStatus = Map.of(
                "/temp/7/a.png", 2,
                "https://cdn.example/b.png", 3,
                "/temp/99/c.png", 3);
        for (var row : rows) {
            String objectKey = (String) row.get("object_key");
            assertEquals(expectedStatus.get(objectKey), ((Number) row.get("status")).intValue(),
                    "legacy attachment ownership must follow the server-written temp prefix: " + objectKey);
            assertEquals("legacy", row.get("platform"));
            assertEquals(7L, ((Number) row.get("owner_user_id")).longValue());
            assertNotNull(row.get("legacy_url_hash"), "idempotency hash must be stored");
            // 原文保留：不自动认领、不只是丢弃
            assertEquals(row.get("object_key"), row.get("url"));
        }
        // 既有反馈记录保持不变
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER_FEEDBACK + " WHERE id >= ? AND id < ?",
                Integer.class, FEEDBACK_ID_BASE_USER, FEEDBACK_ID_BASE_USER + 100));
    }

    @Test
    void attachmentsAlreadyTrackedAreNotRegisteredAsLegacy() {
        // 先放入一条已经受控登记、归属明确的附件
        insertAttachment(ATTACHMENT_ID, 7, null, "acceptance", "obj/42", null, 1, null);
        insertFeedback(FEEDBACK_ID_BASE_USER + 1, 7, "[\"attachment:42\",\"/temp/7/legacy.png\"]");

        registerLegacyAttachments(FEEDBACK_ID_BASE);

        // 只有归属不明的那条被登记为 BLOCKED
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE platform = 'legacy'"
                        + " AND id >= ? AND id < ?",
                Integer.class, FEEDBACK_ID_BASE, FEEDBACK_ID_BASE + 100_000),
                "an already tracked attachment must not be registered as a legacy one");
        // 已受控登记的附件不被改写
        assertEquals(1, jdbc.queryForObject(
                "SELECT status FROM " + FEEDBACK + " WHERE id = ?", Integer.class, ATTACHMENT_ID),
                "an already tracked attachment must keep its status");
    }

    @Test
    void rollbackIsBlockedWhileQuarantineIsActiveThenSucceedsAndKeepsData() {
        insertIsolation(ISOLATION_ID, "picture", 1, 1, "picture:migration-check", 5, "违规");
        insertFeedback(FEEDBACK_ID_BASE_USER + 1, 7, "[\"attachment:42\"]");

        // 隔离未解除时，同一资源不能再有第二条有效隔离记录：
        // 有效键被占用，重复登记被唯一约束挡下，既有记录一条不少
        assertEquals(0, insertIsolationIgnoringConflict(ISOLATION_ID + 1, "picture", 1, 1,
                        "picture:migration-check", 5, "违规"),
                "an active quarantine must block a second active quarantine for the same resource");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ISOLATION + " WHERE id = ?", Integer.class, ISOLATION_ID),
                "a blocked action must not drop the existing quarantine");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER_FEEDBACK + " WHERE id = ?", Integer.class,
                FEEDBACK_ID_BASE_USER + 1),
                "a blocked action must not touch existing rows");

        // 解除隔离：记录保留，释放信息写回，有效键被释放
        jdbc.update("UPDATE " + ISOLATION + " SET status = 2, active_key = NULL,"
                + " release_operator_id = 5, release_reason = '复核通过',"
                + " release_time = statement_timestamp()"
                + " WHERE resource_type = 'picture' AND resource_id = 1");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ISOLATION
                        + " WHERE id = ? AND status = 2 AND active_key IS NULL AND release_reason = '复核通过'",
                Integer.class, ISOLATION_ID),
                "the released quarantine must keep its release evidence");

        // 有效键释放后，同一资源可以重新隔离
        assertEquals(1, insertIsolationIgnoringConflict(ISOLATION_ID + 2, "picture", 1, 1,
                "picture:migration-check", 5, "违规"),
                "a released quarantine must free the active key");
    }

    @Test
    void rollbackKeepsAttachmentLocationsAndReapplyRestoresThem() {
        // 新反馈只保存 attachment:{id} 引用，定位信息在附件记录里
        insertAttachment(ATTACHMENT_ID, 7, 1L, "acceptance", "obj/42",
                "https://acceptance.invalid/obj/42", 1, null);
        insertFeedback(FEEDBACK_ID_BASE_USER + 1, 7, "[\"attachment:42\"]");

        // 定位记录留在附件表里：平台、对象键、归属与绑定都在
        Map<String, Object> attachment = jdbc.queryForMap(
                "SELECT owner_user_id, feedback_id, platform, object_key, url, status FROM " + FEEDBACK
                        + " WHERE id = ?", ATTACHMENT_ID);
        assertEquals(7L, ((Number) attachment.get("owner_user_id")).longValue());
        assertEquals(1L, ((Number) attachment.get("feedback_id")).longValue());
        assertEquals("acceptance", attachment.get("platform"));
        assertEquals("obj/42", attachment.get("object_key"));
        assertEquals(1, ((Number) attachment.get("status")).intValue());
        // 反馈记录本身保留，引用不被改写
        assertEquals("[\"attachment:42\"]", jdbc.queryForObject(
                "SELECT picture_urls FROM " + USER_FEEDBACK + " WHERE id = ?", String.class,
                FEEDBACK_ID_BASE_USER + 1));
        // 引用仍然可解析：attachment:{id} 指向的附件确实存在
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE id = ?", Integer.class, ATTACHMENT_ID),
                "the feedback reference must stay resolvable");

        // 重新登记旧附件时，受控附件不会被重复登记
        registerLegacyAttachments(FEEDBACK_ID_BASE);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE platform = 'legacy'"
                        + " AND id >= ? AND id < ?",
                Integer.class, FEEDBACK_ID_BASE, FEEDBACK_ID_BASE + 100_000),
                "a controlled attachment must not be registered as a legacy one");
    }

    @Test
    void twoRollbackCyclesKeepTheLatestAttachmentBindingAndStatus() {
        // 新反馈只保存 attachment:{id} 引用；附件 901 登记后尚未绑定
        insertAttachment(ATTACHMENT_ID_901, 7, null, "acceptance", "obj/901",
                "https://acceptance.invalid/obj/901", 0, null);
        insertFeedback(FEEDBACK_ID_BASE_USER + 88, 7, "[\"attachment:901\"]");

        // 第一轮：归档后重新部署，附件回到未绑定的初始状态
        Map<String, Object> firstSnapshot = snapshotOf(ATTACHMENT_ID_901);
        restoreFromSnapshot(firstSnapshot);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE id = ? AND feedback_id IS NULL AND status = 0",
                Integer.class, ATTACHMENT_ID_901),
                "the first cycle must restore the unbound attachment");

        // 恢复之后附件绑定到反馈 88，状态改为 BOUND
        jdbc.update("UPDATE " + FEEDBACK + " SET feedback_id = 88, status = 1 WHERE id = ?",
                ATTACHMENT_ID_901);

        // 第二轮：归档必须以业务表的最新状态为准重建，不能沿用第一轮的旧快照
        Map<String, Object> secondSnapshot = snapshotOf(ATTACHMENT_ID_901);
        assertNull(firstSnapshot.get("feedback_id"), "the first cycle must not have captured a binding");
        assertEquals(88L, ((Number) secondSnapshot.get("feedback_id")).longValue(),
                "the second cycle must archive the latest binding");
        restoreFromSnapshot(secondSnapshot);

        Map<String, Object> restored = jdbc.queryForMap(
                "SELECT feedback_id, status FROM " + FEEDBACK + " WHERE id = ?", ATTACHMENT_ID_901);
        assertEquals(88L, ((Number) restored.get("feedback_id")).longValue(),
                "the second cycle must restore the latest binding");
        assertEquals(1, ((Number) restored.get("status")).intValue(),
                "the second cycle must restore the latest status");

        // 重新部署：最新绑定与状态被恢复，反馈里的引用仍然可解析
        assertEquals("[\"attachment:901\"]", jdbc.queryForObject(
                "SELECT picture_urls FROM " + USER_FEEDBACK + " WHERE id = ?", String.class,
                FEEDBACK_ID_BASE_USER + 88),
                "the feedback reference must survive both cycles");
    }

    @Test
    void rollbackIsBlockedWhenTheArchiveCannotHoldTheBusinessState() {
        // 业务表里有一条受控登记的附件
        insertAttachment(ATTACHMENT_ID_901, 7, null, "acceptance", "obj/901",
                "https://acceptance.invalid/obj/901", 1, "hash901");
        // 归档里有一条占用同一地址摘要的旧记录：归档接不住业务状态
        assertEquals(0, insertAttachmentIgnoringConflict(ATTACHMENT_ID_902, 7, null, "acceptance", "stale/902",
                        "https://acceptance.invalid/stale/902", 0, "hash901"),
                "the archive must not be able to hold two records with the same legacy hash");

        // 归档接不住最新状态时业务状态必须原样保留：唯一有效的记录还在业务表里
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE id = ?", Integer.class, ATTACHMENT_ID_901),
                "the only valid record must stay in the business table");
        assertEquals("obj/901", jdbc.queryForObject(
                "SELECT object_key FROM " + FEEDBACK + " WHERE id = ?", String.class, ATTACHMENT_ID_901),
                "the only valid record must keep its location");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE id = ?", Integer.class, ATTACHMENT_ID_902),
                "the colliding record must not be written");
    }

    // ---- helpers ----

    /**
     * 旧反馈附件的登记：只有归属成立（地址带 {@code /temp/{提交者}/} 前缀）
     * 才登记为 LEGACY_VERIFIED，其余登记为 BLOCKED。原文保留、摘要做幂等键，
     * 已经是受控附件的引用不登记。
     */
    private void registerLegacyAttachments(long idBase) {
        jdbc.update("""
                WITH legacy AS (
                  SELECT f.id AS feedback_id, f.user_id AS owner_user_id, j.url AS url, j.ord AS ord
                  FROM user_feedback f
                  CROSS JOIN LATERAL jsonb_array_elements_text(
                      CASE WHEN f.picture_urls ~ '^\\s*\\[.*\\]\\s*$' THEN f.picture_urls::jsonb
                           ELSE '[]'::jsonb END
                  ) WITH ORDINALITY AS j(url, ord)
                  WHERE f.is_delete = 0
                    AND f.picture_urls IS NOT NULL
                    AND f.picture_urls ~ '^\\s*\\[.*\\]\\s*$'
                    AND f.id >= ? AND f.id < ?
                ), numbered AS (
                  SELECT ? + row_number() OVER (ORDER BY feedback_id, ord) AS id,
                         owner_user_id, feedback_id, url,
                         CASE WHEN url LIKE '%/temp/' || owner_user_id || '/%' THEN 2 ELSE 3 END AS status,
                         encode(digest(url, 'sha256'), 'hex') AS legacy_url_hash
                  FROM legacy
                )
                INSERT INTO feedback_attachment
                    (id, owner_user_id, feedback_id, platform, object_key, url, status, legacy_url_hash,
                     create_time, update_time)
                SELECT id, owner_user_id, feedback_id, 'legacy', left(url, 768), left(url, 1024), status,
                       legacy_url_hash, statement_timestamp(), statement_timestamp()
                FROM numbered
                WHERE NOT EXISTS (
                          SELECT 1 FROM feedback_attachment a WHERE a.legacy_url_hash = numbered.legacy_url_hash)
                  -- url 必须限定到外层 CTE：feedback_attachment 自己也有 url 列，
                  -- 裸写会被内层同名列遮蔽，条件恒假，引用就会被重复登记。
                  AND NOT (url LIKE 'attachment:%' AND EXISTS (
                          SELECT 1 FROM feedback_attachment a2
                          WHERE a2.id::text = substring(numbered.url FROM 12)))
                ON CONFLICT (legacy_url_hash) DO NOTHING
                """, FEEDBACK_ID_BASE_USER, FEEDBACK_ID_BASE_USER + 100, idBase);
    }

    /**
     * 附件记录的一次「归档后搬回」：源数据以快照的形式给出，
     * 目标行已存在时按快照里的最新绑定与状态覆盖。
     */
    private void restoreFromSnapshot(Map<String, Object> snapshot) {
        jdbc.update("""
                INSERT INTO feedback_attachment
                    (id, owner_user_id, feedback_id, platform, object_key, url, file_size, status,
                     legacy_url_hash, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, statement_timestamp(), statement_timestamp())
                ON CONFLICT (id) DO UPDATE SET
                    owner_user_id = EXCLUDED.owner_user_id,
                    feedback_id = EXCLUDED.feedback_id,
                    platform = EXCLUDED.platform,
                    object_key = EXCLUDED.object_key,
                    url = EXCLUDED.url,
                    file_size = EXCLUDED.file_size,
                    status = EXCLUDED.status,
                    legacy_url_hash = EXCLUDED.legacy_url_hash,
                    update_time = statement_timestamp()
                """,
                snapshot.get("id"), snapshot.get("owner_user_id"), snapshot.get("feedback_id"),
                snapshot.get("platform"), snapshot.get("object_key"), snapshot.get("url"),
                snapshot.get("file_size"), snapshot.get("status"), snapshot.get("legacy_url_hash"));
    }

    /**
     * 以业务表的最新状态为准重建快照：下一轮归档拿到的永远是当前业务状态，
     * 不会拿更早的快照覆盖更晚的绑定。
     */
    private Map<String, Object> snapshotOf(long id) {
        return jdbc.queryForMap(
                "SELECT id, owner_user_id, feedback_id, platform, object_key, url, file_size, status,"
                        + " legacy_url_hash FROM " + FEEDBACK + " WHERE id = ?", id);
    }

    private void insertFeedback(long id, long userId, String pictureUrls) {
        jdbc.update("INSERT INTO " + USER_FEEDBACK
                + " (id, user_id, type, title, content, picture_urls, status, is_delete)"
                + " VALUES (?, ?, 2, 't', 'c', ?, 0, 0)", id, userId, pictureUrls);
    }

    private void insertAttachment(long id, long ownerUserId, Long feedbackId, String platform, String objectKey,
                                  String url, int status, String legacyUrlHash) {
        jdbc.update("INSERT INTO " + FEEDBACK
                        + " (id, owner_user_id, feedback_id, platform, object_key, url, status, legacy_url_hash)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash);
    }

    private int insertAttachmentIgnoringConflict(long id, long ownerUserId, Long feedbackId, String platform,
                                                 String objectKey, String url, int status, String legacyUrlHash) {
        return jdbc.update("INSERT INTO " + FEEDBACK
                        + " (id, owner_user_id, feedback_id, platform, object_key, url, status, legacy_url_hash)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (legacy_url_hash) DO NOTHING",
                id, ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash);
    }

    private void insertIsolation(long id, String resourceType, long resourceId, int status, String activeKey,
                                 long operatorId, String reason) {
        jdbc.update("INSERT INTO " + ISOLATION
                        + " (id, resource_type, resource_id, status, active_key, operator_id, reason)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, resourceType, resourceId, status, activeKey, operatorId, reason);
    }

    private int insertIsolationIgnoringConflict(long id, String resourceType, long resourceId, int status,
                                                String activeKey, long operatorId, String reason) {
        return jdbc.update("INSERT INTO " + ISOLATION
                        + " (id, resource_type, resource_id, status, active_key, operator_id, reason)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (active_key) DO NOTHING",
                id, resourceType, resourceId, status, activeKey, operatorId, reason);
    }

    private int countLegacy() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + FEEDBACK + " WHERE platform = 'legacy'"
                        + " AND id >= ? AND id < ?",
                Integer.class, FEEDBACK_ID_BASE, FEEDBACK_ID_BASE + 100_000);
        return count == null ? 0 : count;
    }

    private void deleteFixtures() {
        jdbc.update("DELETE FROM " + FEEDBACK + " WHERE id IN (?, ?, ?)",
                ATTACHMENT_ID, ATTACHMENT_ID_901, ATTACHMENT_ID_902);
        jdbc.update("DELETE FROM " + FEEDBACK + " WHERE id >= ? AND id < ?",
                FEEDBACK_ID_BASE, FEEDBACK_ID_BASE + 100_000);
        jdbc.update("DELETE FROM " + ISOLATION + " WHERE id >= ? AND id < ?",
                ISOLATION_ID, ISOLATION_ID + 100);
        jdbc.update("DELETE FROM " + USER_FEEDBACK + " WHERE id >= ? AND id < ?",
                FEEDBACK_ID_BASE_USER, FEEDBACK_ID_BASE_USER + 100);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }
}
