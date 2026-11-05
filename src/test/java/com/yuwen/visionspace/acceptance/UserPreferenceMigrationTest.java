package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #42 前向迁移 / 回滚脚本在隔离 MySQL 上的行为。
 *
 * <p>脚本在临时表名上执行（验收账号只有本轮库的权限，且不能破坏同一 JVM 里
 * 其他用例正在使用的 {@code user_preference}），观察点是脚本执行后库里的表与行：
 * 偏好数据是否随回滚保留、重新部署后是否原样搬回、已清除的内容是否复活、
 * 其他表是否不受影响。真实维护窗口的执行与备份仍属人工关卡，不在这里冒充通过。
 */
class UserPreferenceMigrationTest {

    private static final String LIVE = "scratch_user_preference_42";
    private static final String ARCHIVE = "scratch_user_preference_archive_42";
    private static final String GUARD = "scratch_user_preference_guard_42";
    private static final String UNRELATED = "scratch_unrelated_42";

    private static final Path FORWARD = Path.of("sql/migrations/2026-11-05-user-preference.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-11-05-user-preference-rollback.sql");

    @Test
    void rollbackKeepsPreferenceRowsAndRedeploymentRestoresThem() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, "CREATE TABLE `" + UNRELATED + "` (id bigint PRIMARY KEY, note varchar(64))");
            execute(connection, "INSERT INTO `" + UNRELATED + "` VALUES (1, 'keep me')");

            run(connection, FORWARD);
            run(connection, FORWARD);
            assertTrue(tableExists(connection, LIVE), "前向迁移必须建出偏好表且可重复执行");

            // 用户 A：显式设置、学习产物、反馈历史、开启的开关与版本/纪元都在。
            // 用户 B：用户清除后的墓碑行——三块内容置空，版本与纪元继续递增。
            insertLiveRow(connection, 101, 1001, "{\"liked_styles\":[\"水彩\"]}",
                    "{\"liked_colors\":[\"青\"]}", "[{\"reason\":\"反馈\"}]", 1, 7, 3, null);
            insertLiveRow(connection, 102, 1002, null, null, null, 1, 9, 5, null);

            run(connection, ROLLBACK);
            assertFalse(tableExists(connection, LIVE), "回滚后旧版本应用不再看到业务表");
            assertTrue(tableExists(connection, ARCHIVE), "回滚必须归档偏好数据，而不是随表一起删掉");
            assertPreferenceRows(connection, ARCHIVE);

            run(connection, FORWARD);
            assertTrue(tableExists(connection, LIVE), "重新部署必须重建业务表");
            assertFalse(tableExists(connection, ARCHIVE), "搬回后归档表应被清理");
            assertPreferenceRows(connection, LIVE);
            assertFalse(tableExists(connection, GUARD), "成功的前向迁移必须清理恢复守卫");

            // 脚本只碰本票的表：其他表与行原样保留
            assertEquals("keep me", queryOne(connection,
                    "SELECT note FROM `" + UNRELATED + "` WHERE id = 1"));

            // 再次执行前向脚本不改变已恢复的数据
            run(connection, FORWARD);
            assertPreferenceRows(connection, LIVE);
        } finally {
            cleanup();
        }
    }

    @Test
    void rollbackIsIdempotentAndNeverDropsArchivedRows() throws Exception {
        try (Connection connection = connection()) {
            run(connection, FORWARD);
            insertLiveRow(connection, 201, 2001, "{\"notes\":\"保留\"}", null, null, 0, 2, 1,
                    "LEARN_GAVE_UP_CAS_CONFLICT");

            run(connection, ROLLBACK);
            run(connection, ROLLBACK);
            assertTrue(tableExists(connection, ARCHIVE), "重复回滚不得删掉已归档的数据");
            assertFalse(tableExists(connection, LIVE));
            assertEquals(1, countRows(connection, ARCHIVE));
            assertEquals("LEARN_GAVE_UP_CAS_CONFLICT", queryOne(connection,
                    "SELECT learnError FROM `" + ARCHIVE + "` WHERE userId = 2001"));

            run(connection, FORWARD);
            assertEquals(1, countRows(connection, LIVE));
            assertEquals("LEARN_GAVE_UP_CAS_CONFLICT", queryOne(connection,
                    "SELECT learnError FROM `" + LIVE + "` WHERE userId = 2001"));
        } finally {
            cleanup();
        }
    }

    @Test
    void redeploymentIsBlockedWhileLiveTableAlreadyHasRows() throws Exception {
        try (Connection connection = connection()) {
            run(connection, FORWARD);
            insertLiveRow(connection, 301, 3001, "{\"notes\":\"旧偏好\"}", null, null, 0, 1, 1, null);
            run(connection, ROLLBACK);

            // 模拟维护窗口里业务表已被重建、应用已经写入新偏好，归档还没来得及搬回。
            // 此时前向脚本不能把归档与在线数据合并成一份谁也说不清的偏好。
            execute(connection, "CREATE TABLE `" + LIVE + "` LIKE `" + ARCHIVE + "`");
            insertLiveRow(connection, 302, 3002, "{\"notes\":\"新写入\"}", null, null, 0, 1, 1, null);

            try {
                run(connection, FORWARD);
                throw new AssertionError("应用已经写入新偏好时，重新部署不得把归档与在线数据合并");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("BLOCKED_user_preference_live_table_not_empty"),
                        "阻断必须来自恢复守卫: " + expected.getMessage());
            }
            assertTrue(tableExists(connection, ARCHIVE), "阻断时归档必须留在库里供人工核对");
            assertTrue(tableExists(connection, GUARD), "阻断时空守卫表按惯例留在库里供处置");
            assertEquals("{\"notes\":\"新写入\"}", queryOne(connection, "SELECT explicitContent FROM `"
                    + LIVE + "` WHERE userId = 3002"), "阻断不得改动应用已经写入的行");
            assertEquals(0, countRows(connection, GUARD), "被阻断的守卫表不写入成功标记");
        } finally {
            cleanup();
        }
    }

    // ---- helpers ----

    /**
     * 逐字段断言两行偏好：显式设置、学习产物、反馈历史、开关、版本与纪元。
     * 已清除的行三块内容必须仍为 NULL——回滚与重新部署不能让被清除的数据复活。
     */
    private void assertPreferenceRows(Connection connection, String table) throws SQLException {
        List<String> columns = List.of("userId", "explicitContent", "learnedContent",
                "feedbackHistory", "autoLearn", "preferenceVersion", "dataEpoch", "learnError");
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT " + String.join(", ", columns)
                     + " FROM `" + table + "` ORDER BY userId")) {
            assertTrue(rows.next(), "归档/恢复后必须保留用户 A 的偏好行");
            assertEquals(1001L, rows.getLong("userId"));
            assertEquals("{\"liked_styles\":[\"水彩\"]}", rows.getString("explicitContent"));
            assertEquals("{\"liked_colors\":[\"青\"]}", rows.getString("learnedContent"));
            assertEquals("[{\"reason\":\"反馈\"}]", rows.getString("feedbackHistory"));
            assertEquals(1, rows.getInt("autoLearn"));
            assertEquals(7L, rows.getLong("preferenceVersion"));
            assertEquals(3L, rows.getLong("dataEpoch"));
            assertNull(rows.getString("learnError"));

            assertTrue(rows.next(), "归档/恢复后必须保留用户 B 的清除墓碑行");
            assertEquals(1002L, rows.getLong("userId"));
            assertNull(rows.getString("explicitContent"), "已清除的显式设置不得复活");
            assertNull(rows.getString("learnedContent"), "已清除的学习数据不得复活");
            assertNull(rows.getString("feedbackHistory"), "已清除的反馈历史不得复活");
            assertEquals(1, rows.getInt("autoLearn"), "清除不等于关闭学习");
            assertEquals(9L, rows.getLong("preferenceVersion"));
            assertEquals(5L, rows.getLong("dataEpoch"));
            assertNull(rows.getString("learnError"));

            assertFalse(rows.next(), "回滚与重新部署不得凭空造出第三行");
        }
    }

    private void insertLiveRow(Connection connection, long id, long userId, String explicit,
                               String learned, String history, int autoLearn, long version,
                               long epoch, String learnError) throws SQLException {
        String sql = "INSERT INTO `" + LIVE + "` (`id`, `userId`, `explicitContent`, `learnedContent`,"
                + " `feedbackHistory`, `autoLearn`, `preferenceVersion`, `dataEpoch`, `learnError`)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var prepared = connection.prepareStatement(sql)) {
            prepared.setLong(1, id);
            prepared.setLong(2, userId);
            prepared.setString(3, explicit);
            prepared.setString(4, learned);
            prepared.setString(5, history);
            prepared.setInt(6, autoLearn);
            prepared.setLong(7, version);
            prepared.setLong(8, epoch);
            prepared.setString(9, learnError);
            assertEquals(1, prepared.executeUpdate());
        }
    }

    private int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private String queryOne(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "查询必须返回一行: " + sql);
            return rows.getString(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        Integer count = null;
        try (var prepared = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            prepared.setString(1, table);
            try (ResultSet rows = prepared.executeQuery()) {
                if (rows.next()) {
                    count = rows.getInt(1);
                }
            }
        }
        return count != null && count > 0;
    }

    /** 在**同一条连接**上执行整份脚本：会话变量与 PREPARE 只存在于一条连接上。 */
    private void run(Connection connection, Path script) throws IOException, SQLException {
        List<String> statements = statements(Files.readString(script, StandardCharsets.UTF_8));
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(rename(sql));
            }
        }
    }

    private String rename(String sql) {
        // 先替换更长的标识符，避免 user_preference 先命中把归档名改坏
        return sql.replace("`user_preference_rollback_archive`", "`" + ARCHIVE + "`")
                .replace("'user_preference_rollback_archive'", "'" + ARCHIVE + "'")
                .replace("`_user_preference_restore_guard`", "`" + GUARD + "`")
                .replace("'_user_preference_restore_guard'", "'" + GUARD + "'")
                .replace("`user_preference`", "`" + LIVE + "`")
                .replace("'user_preference'", "'" + LIVE + "'");
    }

    /** 按脚本原文切分语句，同时支持 mysql 客户端的 {@code DELIMITER} 指令。 */
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

    private Connection connection() throws Exception {
        var settings = IsolationSettings.from(System.getenv());
        return DriverManager.getConnection(settings.jdbcUrl(), "vs_acceptance", settings.nonce());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void cleanup() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String table : List.of(LIVE, ARCHIVE, GUARD, UNRELATED)) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
        }
    }
}
