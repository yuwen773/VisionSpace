package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 更新时间触发器矩阵，跑在真实 PostgreSQL 上。
 *
 * <p>MySQL 侧靠列定义 {@code ON UPDATE CURRENT_TIMESTAMP} 维护 update_time；PostgreSQL
 * 没有这个列属性，改用触发器实现（§5.4）。实现里有一处刻意的取舍：
 * <pre>
 * IF NEW IS DISTINCT FROM OLD AND NEW.update_time IS NOT DISTINCT FROM OLD.update_time
 * </pre>
 * 触发器无法区分「调用方没写 update_time」与「调用方显式写了与旧值相同的时间」——
 * 两种情况在行数据上完全一样，都会刷新。这是已知差异，用测试固定下来，避免以后
 * 有人把它当 bug「修」成另一个更难解释的语义。
 */
@IsolatedSpringBootTest
class PostgresTimestampTriggerTest {

    private static final long PICTURE_A = 9_150_001L;
    private static final long PICTURE_B = 9_150_002L;
    private static final long FEEDBACK_ID = 9_150_011L;
    private static final long MCP_ID = 9_150_021L;

    private static final LocalDateTime OLD_TIME = LocalDateTime.of(2020, 1, 1, 0, 0, 0);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?)", PICTURE_A, PICTURE_B);
        jdbc.update("DELETE FROM user_feedback WHERE id = ?", FEEDBACK_ID);
        jdbc.update("DELETE FROM mcp_server WHERE id = ?", MCP_ID);
    }

    @Test
    void contentChangeRefreshesUpdateTimeAtSecondPrecision() {
        insertPicture(PICTURE_A, "before");

        LocalDateTime refreshed = jdbc.queryForObject(
                "UPDATE picture SET name = ? WHERE id = ? RETURNING update_time",
                LocalDateTime.class, "after", PICTURE_A);

        assertTrue(refreshed.isAfter(OLD_TIME), "改内容必须刷新 update_time，实际: " + refreshed);
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(LocalDateTime.now(), refreshed)) < 60,
                "刷新值应取语句时间，而不是保留旧值: " + refreshed);
        assertEquals(0, refreshed.getNano(), "update_time 是 timestamp(0)，不允许带小数秒");
        assertEquals(refreshed, jdbc.queryForObject(
                "SELECT update_time FROM picture WHERE id = ?", LocalDateTime.class, PICTURE_A));
    }

    @Test
    void updateWithoutContentChangeLeavesUpdateTimeAlone() {
        insertPicture(PICTURE_A, "same");

        jdbc.update("UPDATE picture SET name = name WHERE id = ?", PICTURE_A);

        assertEquals(OLD_TIME, jdbc.queryForObject(
                "SELECT update_time FROM picture WHERE id = ?", LocalDateTime.class, PICTURE_A),
                "内容未变的 UPDATE 不能刷新时间：否则轮询式写入会让『最近修改』失去意义");
    }

    @Test
    void explicitNewTimestampIsPreserved() {
        insertPicture(PICTURE_A, "before");
        LocalDateTime explicit = LocalDateTime.of(2021, 5, 5, 12, 0, 0);

        jdbc.update("UPDATE picture SET name = ?, update_time = ? WHERE id = ?",
                "after", explicit, PICTURE_A);

        assertEquals(explicit, jdbc.queryForObject(
                "SELECT update_time FROM picture WHERE id = ?", LocalDateTime.class, PICTURE_A),
                "调用方显式给出的新时间必须原样保留（导入、回放、数据修补都依赖这一点）");
    }

    @Test
    void explicitOldTimestampIsIndistinguishableAndGetsRefreshed() {
        insertPicture(PICTURE_A, "before");

        jdbc.update("UPDATE picture SET name = ?, update_time = ? WHERE id = ?", "after", OLD_TIME, PICTURE_A);
        LocalDateTime result = jdbc.queryForObject(
                "SELECT update_time FROM picture WHERE id = ?", LocalDateTime.class, PICTURE_A);

        assertTrue(result.isAfter(OLD_TIME),
                "显式写回旧值与『没写』在行上无法区分，触发器会刷新；这是 ADR 记录的已知差异");
    }

    @Test
    void batchUpdateRefreshesEveryRow() {
        insertPicture(PICTURE_A, "a");
        insertPicture(PICTURE_B, "b");

        jdbc.update("UPDATE picture SET name = name || '-x' WHERE id IN (?, ?)", PICTURE_A, PICTURE_B);

        for (long id : new long[]{PICTURE_A, PICTURE_B}) {
            assertTrue(jdbc.queryForObject("SELECT update_time FROM picture WHERE id = ?",
                            LocalDateTime.class, id).isAfter(OLD_TIME),
                    "批量更新逐行刷新 update_time: " + id);
        }
    }

    @Test
    void rolledBackUpdateLeavesTheOldTimestamp() throws SQLException {
        insertPicture(PICTURE_A, "before");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE picture SET name = ? WHERE id = ?")) {
                statement.setString(1, "after");
                statement.setLong(2, PICTURE_A);
                statement.executeUpdate();
            }
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }

        assertEquals(OLD_TIME, jdbc.queryForObject(
                "SELECT update_time FROM picture WHERE id = ?", LocalDateTime.class, PICTURE_A),
                "触发器写入与业务写入同属一个事务：回滚后时间也不能残留");
        assertEquals("before", jdbc.queryForObject(
                "SELECT name FROM picture WHERE id = ?", String.class, PICTURE_A));
    }

    @Test
    void feedbackMaintainsCreateTimeAndUpdateTimeWithTwoTriggers() {
        jdbc.update("""
                INSERT INTO user_feedback (id, user_id, title, content, create_time)
                VALUES (?, 9_150_000, 'title', 'content', ?)
                """, FEEDBACK_ID, OLD_TIME);

        jdbc.update("UPDATE user_feedback SET status = 1 WHERE id = ?", FEEDBACK_ID);

        LocalDateTime createTime = jdbc.queryForObject(
                "SELECT create_time FROM user_feedback WHERE id = ?", LocalDateTime.class, FEEDBACK_ID);
        LocalDateTime updateTime = jdbc.queryForObject(
                "SELECT update_time FROM user_feedback WHERE id = ?", LocalDateTime.class, FEEDBACK_ID);
        assertTrue(createTime.isAfter(OLD_TIME), "user_feedback.create_time 由 set_updated_create_time 维护");
        assertTrue(updateTime.isAfter(OLD_TIME), "同一张表上的两个触发器互不干扰");
    }

    @Test
    void mcpServerMaintainsGmtModified() {
        jdbc.update("""
                INSERT INTO mcp_server (id, mcp_server_code, user_id, name, deploy_config, gmt_modified)
                VALUES (?, 'timestamp-trigger', 9_150_000, 'before', '{}', ?)
                """, MCP_ID, OLD_TIME);

        jdbc.update("UPDATE mcp_server SET name = 'after' WHERE id = ?", MCP_ID);

        assertTrue(jdbc.queryForObject("SELECT gmt_modified FROM mcp_server WHERE id = ?",
                        LocalDateTime.class, MCP_ID).isAfter(OLD_TIME),
                "mcp_server 用的是 gmt_modified 列名，触发器函数按列名分开正是为了这类历史命名");
    }

    private void insertPicture(long id, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, user_id, create_time, edit_time, update_time)
                VALUES (?, ?, ?, 9_150_000, ?, ?, ?)
                """, id, "https://acceptance.invalid/" + id + ".png", name, OLD_TIME, OLD_TIME, OLD_TIME);
    }
}
