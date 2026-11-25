package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行角色的最小权限负例，跑在真实 PostgreSQL 上。
 *
 * <p>验收与 CI 里的应用连接串用的是 {@code vision_app}（只读/写数据），迁移用
 * {@code vision_migrator}（owner，可 DDL）。这条边界如果只写在文档里，某天有人
 * 把连接串换回 owner 角色，测试依然全绿——所以这里直接尝试违规操作，要求数据库
 * 用 {@code 42501 insufficient_privilege} 拒绝。
 *
 * <p>负例刻意选在 {@code agent_legacy_attachment} 这类历史归档表上：万一权限配置
 * 出错导致语句真的执行，损失也限于归档数据，不会破坏其它用例依赖的业务表。
 */
@IsolatedSpringBootTest
class PostgresLeastPrivilegeTest {

    private static final String FORBIDDEN_TABLE = "agent_legacy_attachment";
    private static final long PICTURE_ID = 9_152_001L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM picture WHERE id = ?", PICTURE_ID);
    }

    @Test
    void applicationConnectsAsTheLowPrivilegeRole() {
        assertEquals("vision_app", jdbc.queryForObject("SELECT current_user", String.class),
                "验收/CI 必须以运行角色连接：只有迁移器可以 DDL");
        assertEquals("vision_app", jdbc.queryForObject(
                "SELECT session_user", String.class));
    }

    @Test
    void roleIsNotSuperuserAndCannotManageRolesOrDatabases() {
        var role = jdbc.queryForMap("""
                SELECT rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = current_user
                """);
        assertFalse((Boolean) role.get("rolsuper"), "运行角色不能是超级用户");
        assertFalse((Boolean) role.get("rolcreatedb"), "运行角色不能建库");
        assertFalse((Boolean) role.get("rolcreaterole"), "运行角色不能建角");
    }

    @Test
    void schemaAndTablePrivilegesMatchTheContract() {
        assertTrue(jdbc.queryForObject(
                "SELECT has_schema_privilege(current_user, 'public', 'USAGE')", Boolean.class));
        assertFalse(jdbc.queryForObject(
                "SELECT has_schema_privilege(current_user, 'public', 'CREATE')", Boolean.class),
                "没有 CREATE 才不会绕过 Flyway 直接建表");

        for (String privilege : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
            assertTrue(jdbc.queryForObject(
                    "SELECT has_table_privilege(current_user, 'picture', ?)", Boolean.class, privilege),
                    "运行角色必须保有 DML 权限: " + privilege);
        }
        for (String privilege : new String[]{"TRUNCATE", "REFERENCES", "TRIGGER"}) {
            assertFalse(jdbc.queryForObject(
                    "SELECT has_table_privilege(current_user, 'picture', ?)", Boolean.class, privilege),
                    "TRUNCATE/REFERENCES/TRIGGER 属于 owner 能力: " + privilege);
        }

        String identitySequence = jdbc.queryForObject(
                "SELECT pg_get_serial_sequence('mcp_server', 'id')", String.class);
        assertTrue(jdbc.queryForObject(
                "SELECT has_sequence_privilege(current_user, ?, 'USAGE')", Boolean.class, identitySequence),
                "IDENTITY 表的插入要靠序列 USAGE，缺了会报 permission denied for sequence");
    }

    @Test
    void createTableIsRejectedWithInsufficientPrivilege() {
        assertDenied("CREATE TABLE acceptance_forbidden (id bigint)");
        assertDenied("CREATE INDEX acceptance_forbidden_idx ON picture (name)");
        assertDenied("CREATE SCHEMA acceptance_forbidden_schema");
    }

    @Test
    void destructiveDdlOnExistingTablesIsRejected() {
        assertDenied("ALTER TABLE " + FORBIDDEN_TABLE + " ADD COLUMN acceptance_extra integer");
        assertDenied("TRUNCATE TABLE " + FORBIDDEN_TABLE);
        assertDenied("DROP TABLE " + FORBIDDEN_TABLE);
        assertDenied("DROP INDEX IF EXISTS ix_picture_file_url");
    }

    @Test
    void dmlStillWorksSoTheRoleIsActuallyUsable() {
        jdbc.update("""
                INSERT INTO picture (id, url, name, user_id) VALUES (?, ?, ?, ?)
                """, PICTURE_ID, "https://acceptance.invalid/privilege.png", "privilege", 9_152_000L);
        assertEquals(1, jdbc.update("UPDATE picture SET name = ? WHERE id = ?", "renamed", PICTURE_ID));
        assertEquals(1, jdbc.update("DELETE FROM picture WHERE id = ?", PICTURE_ID));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM picture WHERE id = ?", Long.class, PICTURE_ID));
    }

    private void assertDenied(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            SQLException error = assertThrows(SQLException.class, () -> {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }, "以 vision_app 执行必须被拒绝: " + sql);
            assertEquals("42501", error.getSQLState(),
                    "拒绝原因是权限不足（insufficient_privilege），而不是拼写/语法错误: " + sql);
        } catch (SQLException e) {
            throw new AssertionError("无法获取连接来验证权限负例: " + sql, e);
        }
    }
}
