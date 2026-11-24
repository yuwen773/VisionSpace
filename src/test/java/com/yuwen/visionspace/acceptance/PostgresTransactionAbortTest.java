package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PostgreSQL 的事务失败语义，跑在真实 PostgreSQL 上。
 *
 * <p>这是迁移里最容易被忽略、后果又最隐蔽的一条差异：MySQL 里某条语句报错（比如
 * 唯一键冲突）之后，同一个事务还能继续执行；PostgreSQL 会把整个事务置为 aborted，
 * 后续任何语句都返回 {@code 25P02 current transaction is aborted}，直到 ROLLBACK
 * 或回滚到 SAVEPOINT。
 *
 * <p>因此「捕获 {@code DuplicateKeyException} 后接着做别的写操作」在 PG 上必然失败。
 * 需要「冲突即忽略」的地方统一改成 {@code INSERT ... ON CONFLICT DO NOTHING} +
 * 影响行数判断（见 {@code PictureActionServiceImpl}）；确需局部回滚时才用 SAVEPOINT。
 * 这两个正例与一个反例在这里钉住，避免以后有人写回 MySQL 风格的 catch 后继续。
 */
@IsolatedSpringBootTest
class PostgresTransactionAbortTest {

    private static final long EXISTING_ID = 9_153_001L;
    private static final long CONFLICT_ID = 9_153_002L;
    private static final long OTHER_ID = 9_153_003L;
    private static final String EXISTING_ACCOUNT = "TxAbortUser";
    private static final String OTHER_ACCOUNT = "TxOtherUser";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_user WHERE user_account IN (?, ?)", EXISTING_ACCOUNT, OTHER_ACCOUNT);
    }

    @Test
    void effectiveIsolationIsReadCommitted() {
        assertEquals("read committed", jdbc.queryForObject("SHOW transaction_isolation", String.class),
                "连接池显式设为 READ COMMITTED：不能依赖数据库默认值，PG 与 MySQL 的默认值不同");
    }

    @Test
    void uniqueViolationAbortsTheWholeTransaction() throws SQLException {
        insertUser(EXISTING_ID, EXISTING_ACCOUNT);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            SQLException conflict = assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO app_user (id, user_account, user_password, user_role)
                        VALUES (?, ?, 'acceptance-only', 'user')
                        """)) {
                    statement.setLong(1, CONFLICT_ID);
                    statement.setString(2, EXISTING_ACCOUNT.toLowerCase());
                    statement.executeUpdate();
                }
            }, "lower(user_account) 唯一索引上只差大小写的账号必须冲突");
            assertEquals("23505", conflict.getSQLState());

            SQLException aborted = assertThrows(SQLException.class, () -> {
                try (var statement = connection.createStatement()) {
                    statement.executeQuery("SELECT count(*) FROM app_user");
                }
            }, "冲突之后同一事务内的任何语句都必须被拒绝——这正是『catch 后继续』在 PG 上失效的原因");
            assertEquals("25P02", aborted.getSQLState(), "25P02 = current transaction is aborted");

            connection.rollback();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE user_account IN (?, ?)", Long.class,
                EXISTING_ACCOUNT, OTHER_ACCOUNT), "回滚后只应剩下事务开始前那一行");
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE id = ?", Long.class, CONFLICT_ID));
    }

    @Test
    void onConflictDoNothingKeepsTheTransactionUsable() throws SQLException {
        insertUser(EXISTING_ID, EXISTING_ACCOUNT);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            int ignored;
            try (var statement = connection.prepareStatement("""
                    INSERT INTO app_user (id, user_account, user_password, user_role)
                    VALUES (?, ?, 'acceptance-only', 'user')
                    ON CONFLICT DO NOTHING
                    """)) {
                statement.setLong(1, CONFLICT_ID);
                statement.setString(2, EXISTING_ACCOUNT);
                ignored = statement.executeUpdate();
            }
            assertEquals(0, ignored, "ON CONFLICT DO NOTHING 不抛异常，用影响行数 0 表示重复");

            // 关键点：冲突之后事务仍然可写，这正是不再捕获 DuplicateKeyException 的实现方式。
            try (var statement = connection.prepareStatement("""
                    INSERT INTO app_user (id, user_account, user_password, user_role)
                    VALUES (?, ?, 'acceptance-only', 'user')
                    """)) {
                statement.setLong(1, OTHER_ID);
                statement.setString(2, OTHER_ACCOUNT);
                assertEquals(1, statement.executeUpdate(), "冲突被忽略后同一事务仍能写新行");
            }

            connection.commit();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE id = ?", Long.class, OTHER_ID));
        assertEquals(EXISTING_ACCOUNT, jdbc.queryForObject(
                "SELECT user_account FROM app_user WHERE id = ?", String.class, EXISTING_ID),
                "被忽略的插入不能改写已有行");
    }

    @Test
    void savepointIsTheOnlyWayToRecoverFromARealError() throws SQLException {
        insertUser(EXISTING_ID, EXISTING_ACCOUNT);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            Savepoint savepoint = connection.setSavepoint("before_conflict");

            assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO app_user (id, user_account, user_password, user_role)
                        VALUES (?, ?, 'acceptance-only', 'user')
                        """)) {
                    statement.setLong(1, CONFLICT_ID);
                    statement.setString(2, EXISTING_ACCOUNT);
                    statement.executeUpdate();
                }
            });

            connection.rollback(savepoint);

            try (var statement = connection.prepareStatement("""
                    INSERT INTO app_user (id, user_account, user_password, user_role)
                    VALUES (?, ?, 'acceptance-only', 'user')
                    """)) {
                statement.setLong(1, OTHER_ID);
                statement.setString(2, OTHER_ACCOUNT);
                assertEquals(1, statement.executeUpdate(), "回滚到 SAVEPOINT 后事务恢复可用");
            }
            connection.commit();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE id IN (?, ?)", Long.class, OTHER_ID, CONFLICT_ID),
                "SAVEPOINT 之后只应留下成功写入的那一行");
    }

    private void insertUser(long id, String account) {
        jdbc.update("""
                INSERT INTO app_user (id, user_account, user_password, user_role)
                VALUES (?, ?, 'acceptance-only', 'user')
                """, id, account);
    }
}
