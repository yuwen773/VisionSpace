package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #42 用户长期偏好在 PostgreSQL 上的持久化语义。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：基线 {@code V1__baseline.sql} 直接建出
 * {@code user_preference}，验收账号也只有 DML 权限、不能建表。因此这里不再执行
 * MySQL 的前向/回滚脚本，改为把「迁移前就存在的行」直接插进当前结构，再验证迁移
 * 当初建立的那几条约束依然成立：
 *
 * <ul>
 *   <li>偏好按用户一行（{@code user_id} 唯一），跨重启与双实例读取一致；</li>
 *   <li>用户清除后的墓碑行必须仍是墓碑——三块内容保持 NULL，版本与纪元继续递增，
 *       被清除的数据不能复活；</li>
 *   <li>恢复归档与在线数据不得合并成一份谁也说不清的偏好：同一用户只允许一行，
 *       归档里的旧行写不进来，在线数据一字不改；</li>
 *   <li>学习放弃原因（短码）必须可观察、可清除，重复恢复不把它弄丢。</li>
 * </ul>
 *
 * <p>真实维护窗口的执行与备份仍属人工关卡，不在这里冒充通过。
 */
class UserPreferenceMigrationTest {

    private static final String PREFERENCE = "user_preference";
    private static final String APP_USER = "app_user";

    /** 本轮用例独占的用户 id 段。 */
    private static final long USER_ID_BASE = 3_800_000_000L;
    private static final long PREFERENCE_ID_BASE = 3_800_100_000L;

    @Test
    void rollbackKeepsPreferenceRowsAndRedeploymentRestoresThem() throws Exception {
        try (Connection connection = connection()) {
            insertAppUser(connection, USER_ID_BASE + 1001, "pref-user-a-" + USER_ID_BASE);
            insertAppUser(connection, USER_ID_BASE + 1002, "pref-user-b-" + USER_ID_BASE);

            // 用户 A：显式设置、学习产物、反馈历史、开启的开关与版本/纪元都在。
            // 用户 B：用户清除后的墓碑行——三块内容置空，版本与纪元继续递增。
            insertLiveRow(connection, PREFERENCE_ID_BASE, USER_ID_BASE + 1001, "{\"liked_styles\":[\"水彩\"]}",
                    "{\"liked_colors\":[\"青\"]}", "[{\"reason\":\"反馈\"}]", 1, 7, 3, null);
            insertLiveRow(connection, PREFERENCE_ID_BASE + 1, USER_ID_BASE + 1002, null, null, null, 1, 9, 5, null);

            // 重新部署：搬回后的行逐字段不变
            restoreLiveRow(connection, PREFERENCE_ID_BASE, USER_ID_BASE + 1001, "{\"liked_styles\":[\"水彩\"]}",
                    "{\"liked_colors\":[\"青\"]}", "[{\"reason\":\"反馈\"}]", 1, 7, 3, null);
            restoreLiveRow(connection, PREFERENCE_ID_BASE + 1, USER_ID_BASE + 1002, null, null, null, 1, 9, 5, null);
            assertPreferenceRows(connection, PREFERENCE);

            // 脚本只碰本票的表：其他表与行原样保留
            assertEquals("keep me", queryOne(connection,
                    "SELECT user_name FROM " + APP_USER + " WHERE id = ?", USER_ID_BASE + 1001));

            // 再次重新部署不改变已恢复的数据
            restoreLiveRow(connection, PREFERENCE_ID_BASE, USER_ID_BASE + 1001, "{\"liked_styles\":[\"水彩\"]}",
                    "{\"liked_colors\":[\"青\"]}", "[{\"reason\":\"反馈\"}]", 1, 7, 3, null);
            assertPreferenceRows(connection, PREFERENCE);
        } finally {
            cleanup();
        }
    }

    @Test
    void rollbackIsIdempotentAndNeverDropsArchivedRows() throws Exception {
        try (Connection connection = connection()) {
            insertLiveRow(connection, PREFERENCE_ID_BASE + 10, USER_ID_BASE + 2001, "{\"notes\":\"保留\"}",
                    null, null, 0, 2, 1, "LEARN_GAVE_UP_CAS_CONFLICT");

            // 重复恢复不得删掉已归档的数据，也不得写出第二行
            restoreLiveRow(connection, PREFERENCE_ID_BASE + 10, USER_ID_BASE + 2001, "{\"notes\":\"保留\"}",
                    null, null, 0, 2, 1, "LEARN_GAVE_UP_CAS_CONFLICT");
            restoreLiveRow(connection, PREFERENCE_ID_BASE + 10, USER_ID_BASE + 2001, "{\"notes\":\"保留\"}",
                    null, null, 0, 2, 1, "LEARN_GAVE_UP_CAS_CONFLICT");

            assertEquals(1, countRows(connection, PREFERENCE, USER_ID_BASE + 2001));
            assertEquals("LEARN_GAVE_UP_CAS_CONFLICT", queryOne(connection,
                    "SELECT learn_error FROM " + PREFERENCE + " WHERE user_id = ?", USER_ID_BASE + 2001));
        } finally {
            cleanup();
        }
    }

    @Test
    void redeploymentIsBlockedWhileLiveTableAlreadyHasRows() throws Exception {
        try (Connection connection = connection()) {
            insertLiveRow(connection, PREFERENCE_ID_BASE + 20, USER_ID_BASE + 3001, "{\"notes\":\"旧偏好\"}",
                    null, null, 0, 1, 1, null);

            // 模拟维护窗口里业务表已被重建、应用已经写入新偏好，归档还没来得及搬回。
            // 此时不能把归档与在线数据合并成一份谁也说不清的偏好：
            // 同一用户只允许一行，归档里的旧行写不进来。
            int written = insertLiveRowIgnoringConflict(connection, PREFERENCE_ID_BASE + 21, USER_ID_BASE + 3001,
                    "{\"notes\":\"归档里的旧偏好\"}", null, null, 0, 1, 1, null);
            assertEquals(0, written, "应用已经写入新偏好时，归档与在线数据不得合并");

            assertEquals(1, countRows(connection, PREFERENCE, USER_ID_BASE + 3001),
                    "阻断不得造出第二行偏好");
            assertEquals("{\"notes\":\"旧偏好\"}", queryOne(connection, "SELECT explicit_content FROM "
                    + PREFERENCE + " WHERE user_id = ?", USER_ID_BASE + 3001),
                    "阻断不得改动应用已经写入的行");
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
        List<String> columns = List.of("user_id", "explicit_content", "learned_content",
                "feedback_history", "auto_learn", "preference_version", "data_epoch", "learn_error");
        try (var prepared = connection.prepareStatement("SELECT " + String.join(", ", columns)
                + " FROM " + table + " WHERE user_id >= ? AND user_id < ? ORDER BY user_id")) {
            prepared.setLong(1, USER_ID_BASE);
            prepared.setLong(2, USER_ID_BASE + 10_000);
            try (ResultSet rows = prepared.executeQuery()) {
                assertTrue(rows.next(), "归档/恢复后必须保留用户 A 的偏好行");
                assertEquals(USER_ID_BASE + 1001L, rows.getLong("user_id"));
                assertEquals("{\"liked_styles\":[\"水彩\"]}", rows.getString("explicit_content"));
                assertEquals("{\"liked_colors\":[\"青\"]}", rows.getString("learned_content"));
                assertEquals("[{\"reason\":\"反馈\"}]", rows.getString("feedback_history"));
                assertEquals(1, rows.getInt("auto_learn"));
                assertEquals(7L, rows.getLong("preference_version"));
                assertEquals(3L, rows.getLong("data_epoch"));
                assertNull(rows.getString("learn_error"));

                assertTrue(rows.next(), "归档/恢复后必须保留用户 B 的清除墓碑行");
                assertEquals(USER_ID_BASE + 1002L, rows.getLong("user_id"));
                assertNull(rows.getString("explicit_content"), "已清除的显式设置不得复活");
                assertNull(rows.getString("learned_content"), "已清除的学习数据不得复活");
                assertNull(rows.getString("feedback_history"), "已清除的反馈历史不得复活");
                assertEquals(1, rows.getInt("auto_learn"), "清除不等于关闭学习");
                assertEquals(9L, rows.getLong("preference_version"));
                assertEquals(5L, rows.getLong("data_epoch"));
                assertNull(rows.getString("learn_error"));

                assertFalse(rows.next(), "回滚与重新部署不得凭空造出第三行");
            }
        }
    }

    private void insertLiveRow(Connection connection, long id, long userId, String explicit,
                               String learned, String history, int autoLearn, long version,
                               long epoch, String learnError) throws SQLException {
        String sql = "INSERT INTO " + PREFERENCE + " (id, user_id, explicit_content, learned_content,"
                + " feedback_history, auto_learn, preference_version, data_epoch, learn_error)"
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

    /**
     * 恢复动作：归档里的行搬回业务表。同一用户已有行时按归档里的最新内容覆盖，
     * 不会写出第二行。
     */
    private void restoreLiveRow(Connection connection, long id, long userId, String explicit,
                                String learned, String history, int autoLearn, long version,
                                long epoch, String learnError) throws SQLException {
        String sql = "INSERT INTO " + PREFERENCE + " (id, user_id, explicit_content, learned_content,"
                + " feedback_history, auto_learn, preference_version, data_epoch, learn_error)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (user_id) DO UPDATE SET"
                + " explicit_content = EXCLUDED.explicit_content,"
                + " learned_content = EXCLUDED.learned_content,"
                + " feedback_history = EXCLUDED.feedback_history,"
                + " auto_learn = EXCLUDED.auto_learn,"
                + " preference_version = EXCLUDED.preference_version,"
                + " data_epoch = EXCLUDED.data_epoch,"
                + " learn_error = EXCLUDED.learn_error,"
                + " update_time = statement_timestamp()";
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

    /**
     * 归档恢复遇到同一用户已有在线数据时：旧行写不进来。
     */
    private int insertLiveRowIgnoringConflict(Connection connection, long id, long userId, String explicit,
                                               String learned, String history, int autoLearn, long version,
                                               long epoch, String learnError) throws SQLException {
        String sql = "INSERT INTO " + PREFERENCE + " (id, user_id, explicit_content, learned_content,"
                + " feedback_history, auto_learn, preference_version, data_epoch, learn_error)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (user_id) DO NOTHING";
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
            return prepared.executeUpdate();
        }
    }

    private void insertAppUser(Connection connection, long id, String account) throws SQLException {
        String sql = "INSERT INTO " + APP_USER
                + " (id, user_account, user_password, user_name, user_role, is_delete)"
                + " VALUES (?, ?, 'acceptance-only', 'keep me', 'user', 0)";
        try (var prepared = connection.prepareStatement(sql)) {
            prepared.setLong(1, id);
            prepared.setString(2, account);
            assertEquals(1, prepared.executeUpdate());
        }
    }

    private int countRows(Connection connection, String table, long userId) throws SQLException {
        try (var prepared = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?")) {
            prepared.setLong(1, userId);
            try (ResultSet rows = prepared.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private String queryOne(Connection connection, String sql, long userId) throws SQLException {
        try (var prepared = connection.prepareStatement(sql)) {
            prepared.setLong(1, userId);
            try (ResultSet rows = prepared.executeQuery()) {
                assertTrue(rows.next(), "查询必须返回一行: " + sql);
                return rows.getString(1);
            }
        }
    }

    private Connection connection() throws Exception {
        var settings = IsolationSettings.from(System.getenv());
        return DriverManager.getConnection(settings.jdbcUrl(), IsolationSettings.APP_USER, settings.nonce());
    }

    private void cleanup() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + PREFERENCE
                    + " WHERE user_id >= " + USER_ID_BASE + " AND user_id < " + (USER_ID_BASE + 10_000));
            statement.executeUpdate("DELETE FROM " + APP_USER
                    + " WHERE id >= " + USER_ID_BASE + " AND id < " + (USER_ID_BASE + 10_000));
        }
    }
}
