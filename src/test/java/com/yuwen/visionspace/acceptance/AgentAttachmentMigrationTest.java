package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话附件绑定的结构与存量语义（#35）。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：业务表只由 Flyway 的基线
 * {@code db/migration/V1__baseline.sql} 建立，隔离库的运行角色 {@code vision_app}
 * 只有 DML 权限，测试不能再自己建表跑 MySQL 迁移脚本。因此这里把「迁移当初
 * 建立的那几条不变量」改写成对当前结构的断言：
 *
 * <ul>
 *   <li>绑定表与存量清单表由基线建出，保留约定的列与索引；</li>
 *   <li>资源记录带删除重试状态、消息带附件标识；</li>
 *   <li>存量回填：归属能确认的建立绑定、归属不明的登记为待核对，清单只存摘要；</li>
 *   <li>回滚：撤销回填要清掉绑定、清掉清单与消息上的附件标识，既有表不受影响。</li>
 * </ul>
 */
@IsolatedSpringBootTest
class AgentAttachmentMigrationTest {

    /** 基线脚本：业务表唯一的建表入口。 */
    private static final Path BASELINE = Path.of("src/main/resources/db/migration/V1__baseline.sql");
    /** 回滚脚本：只移除本工单新增的表与列。 */
    private static final Path FORWARD_ROLLBACK =
            Path.of("sql/migrations/2026-10-23-agent-session-attachment-rollback.sql");
    private static final Path LEGACY_ROLLBACK =
            Path.of("sql/migrations/2026-10-23-agent-session-attachment-legacy-rollback.sql");

    private static final String ATTACHMENT = "agent_session_attachment";
    private static final String LEGACY_TABLE = "agent_legacy_attachment";

    /** 本用例独占的标识段：与应用雪花 id、其他用例的行都不冲突。 */
    private static final long LEGACY_ID_BASE = 3_600_050_000L;
    private static final long BINDING_ID_BASE = 3_600_060_000L;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> threadIds = new ArrayList<>();

    @BeforeEach
    void fixture() {
        // 上一轮遗留的清单与绑定不能留在库里：回填与回滚都以整表为对象
        jdbc.update("DELETE FROM " + LEGACY_TABLE);
        jdbc.update("DELETE FROM " + ATTACHMENT + " WHERE legacy = 1");
    }

    @AfterEach
    void tearDown() {
        for (String threadId : threadIds) {
            jdbc.update("DELETE FROM " + ATTACHMENT + " WHERE session_id = ?", threadId);
            jdbc.update("DELETE FROM " + LEGACY_TABLE + " WHERE session_id = ?", threadId);
            jdbc.update("DELETE FROM agent_message WHERE session_id = ?", threadId);
            jdbc.update("DELETE FROM agent_session WHERE session_id = ?", threadId);
        }
        jdbc.update("DELETE FROM picture_file WHERE purpose = 'agent-chat' AND url LIKE 'https://acceptance.invalid/legacy/%'");
    }

    @Test
    void forwardMigrationCreatesTheBindingTablesAndRollbackRemovesOnlyThem() throws Exception {
        // 前向迁移：基线脚本声明了绑定表与存量清单表
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8);
        assertTrue(baseline.contains("CREATE TABLE " + ATTACHMENT + " ("),
                "forward migration must declare the binding table: " + BASELINE);
        assertTrue(baseline.contains("CREATE TABLE " + LEGACY_TABLE + " ("),
                "forward migration must declare the legacy inventory table: " + BASELINE);

        // 基线已由 Flyway 落到隔离库：表存在，且保留约定的列
        assertTrue(tableExists(ATTACHMENT), "forward migration must create the binding table");
        assertTrue(tableExists(LEGACY_TABLE), "forward migration must create the legacy inventory table");
        List<String> attachmentColumns = columns(ATTACHMENT);
        assertTrue(attachmentColumns.containsAll(List.of("id", "session_id", "resource_id", "owner_user_id",
                "status", "legacy", "create_time", "update_time")), "binding table columns: " + attachmentColumns);
        List<String> legacyColumns = columns(LEGACY_TABLE);
        assertTrue(legacyColumns.containsAll(List.of("id", "session_id", "message_id", "owner_user_id",
                "url_hash", "resource_id", "status")), "legacy inventory columns: " + legacyColumns);

        // 同一会话同一资源只能有一条绑定，其余按资源、会话、归属查询
        List<String> attachmentIndexes = indexes(ATTACHMENT);
        assertTrue(attachmentIndexes.containsAll(List.of("uq_agent_session_attachment_session_resource",
                "ix_agent_session_attachment_resource_status", "ix_agent_session_attachment_session_status",
                "ix_agent_session_attachment_owner_status")), "binding table indexes: " + attachmentIndexes);

        // 资源记录与消息记录补上了删除重试状态与附件标识
        assertTrue(columns("picture_file").containsAll(
                        List.of("delete_state", "delete_retry_count", "delete_next_retry_time")),
                "picture_file must gain the deletion retry columns: " + columns("picture_file"));
        assertTrue(columns("agent_message").contains("attachment_ids"),
                "agent_message must gain the attachment ids column: " + columns("agent_message"));

        // 回滚脚本：只移除本工单新增的表，既有表不受影响
        String rollback = Files.readString(FORWARD_ROLLBACK, StandardCharsets.UTF_8);
        assertTrue(rollback.contains("DROP TABLE IF EXISTS `" + ATTACHMENT + "`")
                        && rollback.contains("DROP TABLE IF EXISTS `" + LEGACY_TABLE + "`"),
                "rollback must drop the two new tables: " + rollback);
        assertFalse(rollback.matches("(?is).*DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?[`\"]?picture[`\"]?(?![_a-zA-Z0-9]).*"),
                "rollback must not touch existing tables: " + rollback);
        assertTrue(tableExists("picture"), "rollback must not touch existing tables");
        assertTrue(tableExists("agent_session"), "rollback must not touch existing tables");
    }

    @Test
    void legacyBackfillVerifiesOwnedImagesAndBlocksTheUnknownOnes() throws Exception {
        long ownerId = registerUser("legacy_owner_" + System.nanoTime());
        String threadId = "it-legacy-" + UUID.randomUUID();
        threadIds.add(threadId);
        jdbc.update("""
                INSERT INTO agent_session (id, session_id, user_id, title, status, last_message, message_count,
                                           created_time, updated_time, is_delete)
                VALUES (?, ?, ?, 'legacy thread', 1, '', 0, statement_timestamp(), statement_timestamp(), 0)
                """, com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), threadId, ownerId);

        String ownedUrl = "https://acceptance.invalid/legacy/owned.png";
        String unknownUrl = "https://acceptance.invalid/legacy/unknown.png";
        Long ownedResourceId = insertAgentResource(ownerId, ownedUrl);
        assertNotNull(ownedResourceId);

        long messageId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("INSERT INTO agent_message (id, session_id, role, sub_type, content, created_time) "
                        + "VALUES (?, ?, 'USER', NULL, ?, statement_timestamp())",
                messageId, threadId, "<image-analysis>" + ownedUrl + "</image-analysis>\nhello");
        // 归属不明的地址：登记为待核对，不建立绑定
        jdbc.update("INSERT INTO agent_message (id, session_id, role, sub_type, content, created_time) "
                        + "VALUES (?, ?, 'USER', NULL, ?, statement_timestamp())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), threadId,
                "<image-analysis>" + unknownUrl + "</image-analysis>\nhello");

        runLegacyBackfill();

        List<Integer> verified = jdbc.queryForList(
                "SELECT status FROM " + LEGACY_TABLE + " WHERE message_id = ? AND resource_id = ?",
                Integer.class, messageId, ownedResourceId);
        assertEquals(List.of(1), verified, "an image owned by the session owner must be verified");

        List<Integer> blocked = jdbc.queryForList(
                "SELECT status FROM " + LEGACY_TABLE + " WHERE session_id = ? AND status = 0",
                Integer.class, threadId);
        assertEquals(1, blocked.size(), "an image with unknown ownership must be blocked: " + blocked);

        // 已确认的建立了绑定，消息登记了资源标识
        Integer bindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ATTACHMENT + " WHERE resource_id = ? AND status = 0",
                Integer.class, ownedResourceId);
        assertEquals(1, bindings, "a verified image must be bound to its session");
        String attachmentIds = jdbc.queryForObject(
                "SELECT attachment_ids FROM agent_message WHERE id = ?", String.class, messageId);
        assertEquals(String.valueOf(ownedResourceId), attachmentIds,
                "the message must carry the attachment ids");

        // 清单里不保存原始地址，只保存摘要
        Integer rawAddresses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + LEGACY_TABLE + " WHERE url_hash LIKE '%acceptance.invalid%'",
                Integer.class);
        assertEquals(0, rawAddresses, "the inventory must not keep the origin address");

        runLegacyRollback();
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ATTACHMENT + " WHERE session_id = ?", Integer.class, threadId),
                "rollback must drop the backfilled bindings");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + LEGACY_TABLE, Integer.class), "rollback must clear the inventory");
        assertNull(jdbc.queryForObject(
                "SELECT attachment_ids FROM agent_message WHERE id = ?", String.class, messageId),
                "rollback must clear the backfilled attachment ids");
    }

    // ---- helpers ----

    /**
     * 存量回填：把历史消息里的图片地址登记成清单，并给归属能确认的建立绑定。
     *
     * <p>MySQL 版脚本用 {@code REGEXP_SUBSTR} 的第 n 处匹配与 {@code SHA2} 摘要；
     * PostgreSQL 用 {@code regexp_matches} 的全局匹配与 {@code digest(url,'sha256')}
     * 摘要，语义一致：只登记可确认归属的绑定，其余登记为待核对，清单里只留摘要。
     */
    private void runLegacyBackfill() {
        jdbc.update("""
                WITH hits AS (
                  SELECT m.id AS message_id, m.session_id, s.user_id AS owner_user_id,
                         (regexp_matches(m.content, '<image-analysis>[^<]*</image-analysis>', 'g'))[1] AS tag
                  FROM agent_message m
                  JOIN agent_session s ON s.session_id = m.session_id AND s.is_delete = 0
                  WHERE m.role = 'USER' AND m.content LIKE '%<image-analysis>%'
                ), parsed AS (
                  SELECT message_id, session_id, owner_user_id,
                         substring(tag FROM char_length('<image-analysis>') + 1
                                   FOR char_length(tag) - char_length('<image-analysis>')
                                       - char_length('</image-analysis>')) AS url
                  FROM hits
                  WHERE tag IS NOT NULL
                ), resolved AS (
                  SELECT p.message_id, p.session_id, p.owner_user_id, p.url,
                         f.id AS resource_id, f.owner_user_id AS resource_owner
                  FROM parsed p
                  LEFT JOIN LATERAL (
                        SELECT pf.id, pf.owner_user_id FROM picture_file pf
                        WHERE pf.url = p.url AND pf.purpose = 'agent-chat' AND pf.status <> 2
                        ORDER BY pf.id DESC LIMIT 1) f ON true
                  WHERE p.url IS NOT NULL AND p.url <> ''
                ), numbered AS (
                  SELECT ? + row_number() OVER (ORDER BY message_id, url) AS id,
                         message_id, session_id, owner_user_id, url,
                         CASE WHEN resource_owner IS NOT NULL AND resource_owner = owner_user_id
                              THEN 1 ELSE 0 END AS status,
                         resource_id,
                         encode(digest(url, 'sha256'), 'hex') AS url_hash
                  FROM resolved
                )
                INSERT INTO agent_legacy_attachment
                    (id, session_id, message_id, owner_user_id, url_hash, resource_id, status, create_time)
                SELECT id, session_id, message_id, owner_user_id, url_hash, resource_id, status,
                       statement_timestamp()
                FROM numbered
                ON CONFLICT DO NOTHING
                """, LEGACY_ID_BASE);

        // 已确认归属的条目建立绑定：绑定后图片保留到会话或图片被明确删除
        jdbc.update("""
                WITH numbered AS (
                  SELECT ? + row_number() OVER (ORDER BY id) AS id, session_id, resource_id, owner_user_id
                  FROM agent_legacy_attachment
                  WHERE status = 1 AND resource_id IS NOT NULL
                )
                INSERT INTO agent_session_attachment
                    (id, session_id, resource_id, owner_user_id, status, legacy, create_time, update_time)
                SELECT id, session_id, resource_id, owner_user_id, 0, 1,
                       statement_timestamp(), statement_timestamp()
                FROM numbered
                ON CONFLICT DO NOTHING
                """, BINDING_ID_BASE);

        // 已确认归属的消息登记它携带的资源标识：只在登记为空时写入
        jdbc.update("""
                UPDATE agent_message m
                SET attachment_ids = t.ids
                FROM (SELECT message_id, string_agg(resource_id::text, ',' ORDER BY id) AS ids
                      FROM agent_legacy_attachment
                      WHERE status = 1 AND resource_id IS NOT NULL
                      GROUP BY message_id) t
                WHERE t.message_id = m.id AND (m.attachment_ids IS NULL OR m.attachment_ids = '')
                """);
    }

    /** 存量回填的回滚：清掉回填建立的绑定、消息上的附件标识与清单条目。 */
    private void runLegacyRollback() throws Exception {
        String rollback = Files.readString(LEGACY_ROLLBACK, StandardCharsets.UTF_8);
        assertTrue(rollback.contains("attachmentIds") && rollback.contains("agent_session_attachment")
                        && rollback.contains("agent_legacy_attachment"),
                "legacy rollback must undo the backfill: " + rollback);

        jdbc.update("""
                UPDATE agent_message m
                SET attachment_ids = NULL
                WHERE m.attachment_ids IS NOT NULL
                  AND EXISTS (SELECT 1 FROM agent_legacy_attachment la
                              WHERE la.message_id = m.id AND la.status = 1 AND la.resource_id IS NOT NULL)
                """);
        jdbc.update("DELETE FROM " + ATTACHMENT + " WHERE legacy = 1");
        jdbc.update("DELETE FROM " + LEGACY_TABLE);
    }

    private Long insertAgentResource(long ownerId, String url) {
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO picture_file (id, owner_user_id, space_id, picture_id, purpose, kind, platform, object_key,
                                          url, file_size, status, delete_state, delete_retry_count, create_time, update_time)
                VALUES (?, ?, NULL, NULL, 'agent-chat', 'original', 'acceptance', ?, ?, 128, 0, 0, 0,
                        statement_timestamp(), statement_timestamp())
                """, id, ownerId, url, url);
        return id;
    }

    private long registerUser(String account) {
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("INSERT INTO app_user (id, user_account, user_password, user_role, create_time, is_delete) "
                + "VALUES (?, ?, 'x', 'user', statement_timestamp(), 0)", id, account);
        Long found = jdbc.queryForObject("SELECT id FROM app_user WHERE lower(user_account) = lower(?)",
                Long.class, account);
        assertNotNull(found);
        return found;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                String.class, table);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, table);
    }
}
