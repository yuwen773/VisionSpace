package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
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
 * 会话附件绑定的迁移脚本可执行且互为逆操作。
 *
 * <p>结构迁移在临时表名上执行；存量回填在隔离库里用真实数据演练，验证
 * 「归属能确认的建立绑定、归属不明的登记为待核对」，以及回滚把回填结果撤销。
 */
@IsolatedSpringBootTest
class AgentAttachmentMigrationTest {

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-23-agent-session-attachment.sql");
    private static final Path FORWARD_ROLLBACK =
            Path.of("sql/migrations/2026-10-23-agent-session-attachment-rollback.sql");
    private static final Path LEGACY = Path.of("sql/migrations/2026-10-23-agent-session-attachment-legacy.sql");
    private static final Path LEGACY_ROLLBACK =
            Path.of("sql/migrations/2026-10-23-agent-session-attachment-legacy-rollback.sql");

    private static final String ATTACHMENT = "agent_session_attachment_scratch";
    private static final String LEGACY_TABLE = "agent_legacy_attachment_scratch";
    private static final String FILES = "picture_file_scratch";
    private static final String MESSAGES = "agent_message_scratch";

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> threadIds = new ArrayList<>();

    @BeforeEach
    void fixture() {
        dropScratch();
        // 临时表按迁移前的结构建立：新增列必须由迁移脚本补上
        jdbc.execute("""
                CREATE TABLE `picture_file_scratch` (
                  `id` bigint(20) NOT NULL AUTO_INCREMENT,
                  `ownerUserId` bigint(20) NOT NULL,
                  `purpose` varchar(32) NOT NULL,
                  `kind` varchar(16) NOT NULL,
                  `platform` varchar(32) NOT NULL,
                  `objectKey` varchar(768) NOT NULL,
                  `url` varchar(1024) NULL DEFAULT NULL,
                  `fileSize` bigint(20) NULL DEFAULT NULL,
                  `status` int(11) NOT NULL DEFAULT 0,
                  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`)
                ) ENGINE = InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE `agent_message_scratch` (
                  `id` bigint(20) NOT NULL AUTO_INCREMENT,
                  `sessionId` varchar(64) NOT NULL,
                  `role` varchar(20) NOT NULL,
                  `subType` varchar(20) NULL DEFAULT NULL,
                  `content` text NOT NULL,
                  `tokenCount` int(11) NULL DEFAULT 0,
                  `isSummary` tinyint(4) NULL DEFAULT 0,
                  `createdTime` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`)
                ) ENGINE = InnoDB
                """);
    }

    @AfterEach
    void tearDown() {
        for (String threadId : threadIds) {
            jdbc.update("DELETE FROM agent_session_attachment WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_legacy_attachment WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_message WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_session WHERE sessionId = ?", threadId);
        }
        jdbc.update("DELETE FROM picture_file WHERE purpose = 'agent-chat' AND url LIKE 'https://acceptance.invalid/legacy/%'");
        dropScratch();
    }

    @Test
    void forwardMigrationCreatesTheBindingTablesAndRollbackRemovesOnlyThem() throws Exception {
        run(FORWARD, true);
        assertTrue(tableExists(ATTACHMENT), "forward migration must create the binding table");
        assertTrue(tableExists(LEGACY_TABLE), "forward migration must create the legacy inventory table");

        List<String> attachmentColumns = columns(ATTACHMENT);
        assertTrue(attachmentColumns.containsAll(List.of("id", "sessionId", "resourceId", "ownerUserId",
                "status", "legacy", "createTime", "updateTime")), "binding table columns: " + attachmentColumns);
        List<String> attachmentIndexes = indexes(ATTACHMENT);
        assertTrue(attachmentIndexes.containsAll(List.of("uk_session_resource", "idx_resource_status",
                "idx_session_status", "idx_owner_status")), "binding table indexes: " + attachmentIndexes);

        List<String> legacyColumns = columns(LEGACY_TABLE);
        assertTrue(legacyColumns.containsAll(List.of("id", "sessionId", "messageId", "ownerUserId",
                "urlHash", "resourceId", "status")), "legacy inventory columns: " + legacyColumns);

        // 资源记录与消息记录补上了删除重试状态与附件标识
        assertTrue(columns(FILES).containsAll(List.of("deleteState", "deleteRetryCount", "deleteNextRetryTime")),
                "picture_file must gain the deletion retry columns: " + columns(FILES));
        assertTrue(columns(MESSAGES).contains("attachmentIds"),
                "agent_message must gain the attachment ids column: " + columns(MESSAGES));

        run(FORWARD_ROLLBACK, true);
        assertFalse(tableExists(ATTACHMENT), "rollback must drop the binding table");
        assertFalse(tableExists(LEGACY_TABLE), "rollback must drop the legacy inventory table");
        // 回滚只动本工单新增的表与列，既有表不受影响
        assertTrue(tableExists("picture"), "rollback must not touch existing tables");
        assertTrue(tableExists("agent_session"), "rollback must not touch existing tables");
    }

    @Test
    void legacyBackfillVerifiesOwnedImagesAndBlocksTheUnknownOnes() throws Exception {
        run(FORWARD, true);

        long ownerId = registerUser("legacy_owner_" + System.nanoTime());
        String threadId = "it-legacy-" + UUID.randomUUID();
        threadIds.add(threadId);
        jdbc.update("""
                INSERT INTO agent_session (sessionId, userId, title, status, lastMessage, messageCount,
                                           createdTime, updatedTime, isDelete)
                VALUES (?, ?, 'legacy thread', 1, '', 0, NOW(), NOW(), 0)
                """, threadId, ownerId);

        String ownedUrl = "https://acceptance.invalid/legacy/owned.png";
        String unknownUrl = "https://acceptance.invalid/legacy/unknown.png";
        Long ownedResourceId = insertAgentResource(ownerId, ownedUrl);
        assertNotNull(ownedResourceId);

        jdbc.update("INSERT INTO agent_message (sessionId, role, subType, content, createdTime) " +
                        "VALUES (?, 'USER', NULL, ?, NOW())",
                threadId, "<image-analysis>" + ownedUrl + "</image-analysis>\nhello");
        Long messageId = jdbc.queryForObject(
                "SELECT MAX(id) FROM agent_message WHERE sessionId = ?", Long.class, threadId);

        // 归属不明的地址：登记为待核对，不建立绑定
        jdbc.update("INSERT INTO agent_message (sessionId, role, subType, content, createdTime) " +
                        "VALUES (?, 'USER', NULL, ?, NOW())",
                threadId, "<image-analysis>" + unknownUrl + "</image-analysis>\nhello");

        run(LEGACY, false);

        List<Integer> verified = jdbc.queryForList(
                "SELECT status FROM `" + LEGACY_TABLE + "` WHERE messageId = ? AND resourceId = ?",
                Integer.class, messageId, ownedResourceId);
        assertEquals(List.of(1), verified, "an image owned by the session owner must be verified");

        List<Integer> blocked = jdbc.queryForList(
                "SELECT status FROM `" + LEGACY_TABLE + "` WHERE sessionId = ? AND status = 0",
                Integer.class, threadId);
        assertEquals(1, blocked.size(), "an image with unknown ownership must be blocked: " + blocked);

        // 已确认的建立了绑定，消息登记了资源标识
        Integer bindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `" + ATTACHMENT + "` WHERE resourceId = ? AND status = 0",
                Integer.class, ownedResourceId);
        assertEquals(1, bindings, "a verified image must be bound to its session");
        String attachmentIds = jdbc.queryForObject(
                "SELECT attachmentIds FROM agent_message WHERE id = ?", String.class, messageId);
        assertEquals(String.valueOf(ownedResourceId), attachmentIds,
                "the message must carry the attachment ids");

        // 清单里不保存原始地址，只保存摘要
        Integer rawAddresses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `" + LEGACY_TABLE + "` WHERE urlHash LIKE '%acceptance.invalid%'",
                Integer.class);
        assertEquals(0, rawAddresses, "the inventory must not keep the origin address");

        run(LEGACY_ROLLBACK, false);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM `" + ATTACHMENT + "`", Integer.class), "rollback must drop the backfilled bindings");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM `" + LEGACY_TABLE + "`", Integer.class), "rollback must clear the inventory");
        assertNull(jdbc.queryForObject(
                "SELECT attachmentIds FROM agent_message WHERE id = ?", String.class, messageId),
                "rollback must clear the backfilled attachment ids");
    }

    // ---- helpers ----

    private Long insertAgentResource(long ownerId, String url) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform, objectKey,
                                          url, fileSize, status, deleteState, deleteRetryCount, createTime, updateTime)
                VALUES (?, NULL, NULL, 'agent-chat', 'original', 'acceptance', ?, ?, 128, 0, 0, 0, NOW(), NOW())
                """, ownerId, url, url);
        return jdbc.queryForObject("SELECT MAX(id) FROM picture_file WHERE url = ?", Long.class, url);
    }

    private long registerUser(String account) {
        jdbc.update("INSERT INTO user (userAccount, userPassword, userRole, createTime, isDelete) " +
                "VALUES (?, 'x', 'user', NOW(), 0)", account);
        Long id = jdbc.queryForObject("SELECT id FROM user WHERE userAccount = ?", Long.class, account);
        assertNotNull(id);
        return id;
    }

    /**
     * 执行脚本。
     *
     * @param replaceSourceTables 目标表是否也替换到临时表上。结构迁移要替换
     *                             （不改既有表），存量回填只替换它的两张目标表，
     *                             必须读真实的会话与消息。
     */
    private void run(Path script, boolean replaceSourceTables) throws IOException {
        String sql = Files.readString(script, StandardCharsets.UTF_8)
                .replace("`agent_session_attachment`", "`" + ATTACHMENT + "`")
                .replace("`agent_legacy_attachment`", "`" + LEGACY_TABLE + "`");
        if (replaceSourceTables) {
            sql = sql.replace("`picture_file`", "`" + FILES + "`")
                    .replace("`agent_message`", "`" + MESSAGES + "`")
                    // 存储过程按表名字符串定位待改的表，这里也要指向临时表
                    .replace("'picture_file'", "'" + FILES + "'")
                    .replace("'agent_message'", "'" + MESSAGES + "'");
        }
        for (String statement : statements(sql)) {
            jdbc.execute(statement);
        }
    }

    /**
     * 按分隔符切分语句：脚本里既有 {@code ;} 也有 {@code DELIMITER $$} 定义的
     * 存储过程定义，不能只按分号切。
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
                int end = current.length() - delimiter.length() - 1;
                result.add(current.substring(0, end));
                current.setLength(0);
            }
        }
        assertEquals(0, current.toString().trim().length(), "script must end every statement with a delimiter");
        return result;
    }

    private void dropScratch() {
        jdbc.execute("DROP TABLE IF EXISTS `" + ATTACHMENT + "`");
        jdbc.execute("DROP TABLE IF EXISTS `" + LEGACY_TABLE + "`");
        jdbc.execute("DROP TABLE IF EXISTS `" + FILES + "`");
        jdbc.execute("DROP TABLE IF EXISTS `" + MESSAGES + "`");
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class, table);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                String.class, table);
    }
}
