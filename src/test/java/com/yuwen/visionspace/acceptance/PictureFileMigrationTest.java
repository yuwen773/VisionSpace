package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源记录表的前向迁移与回滚互为逆操作：前向建出这张表，回滚只删这张表。
 *
 * <p>PostgreSQL 之后业务表只由 Flyway 的基线 {@code db/migration/V1__baseline.sql} 建立，
 * 隔离库里的运行角色 {@code vision_app} 只有 DML 权限，测试不能再自己建表跑脚本。
 * 因此前向迁移改由「基线已落地」来证明：基线脚本必须声明这张资源记录表，隔离库里
 * 这张表必须存在、并且保留约定的列与索引；回滚脚本仍然只删除本工单新增的表，
 * 既有表不受影响。
 *
 * <p>真实环境的执行窗口、备份与旧直链失效仍属人工关卡。
 */
@IsolatedSpringBootTest
class PictureFileMigrationTest {

    private static final String TABLE = "picture_file";

    /** 前向迁移的落地形态：PostgreSQL 基线脚本（唯一的建表入口）。 */
    private static final Path FORWARD = Path.of("src/main/resources/db/migration/V1__baseline.sql");
    /** 回滚脚本：只删除本工单新增的资源记录表。 */
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-22-picture-file-rollback.sql");

    /** 基线里约定的列（snake_case）。 */
    private static final List<String> DOCUMENTED_COLUMNS = List.of("id", "owner_user_id", "space_id",
            "picture_id", "purpose", "kind", "platform", "object_key", "url", "file_size", "status",
            "create_time", "update_time");

    /** 基线里约定的索引。 */
    private static final List<String> DOCUMENTED_INDEXES = List.of("ix_picture_file_picture_kind",
            "ix_picture_file_owner_purpose_status", "ix_picture_file_platform_key", "ix_picture_file_url");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationCreatesTheRecordTableAndRollbackRemovesOnlyIt() throws Exception {
        // 前向迁移：基线脚本声明了资源记录表
        String forward = Files.readString(FORWARD, StandardCharsets.UTF_8);
        assertTrue(forward.contains("CREATE TABLE " + TABLE + " ("),
                "forward migration must declare the record table: " + FORWARD);

        // 基线已由 Flyway 落到隔离库：表存在，且保留约定的列
        assertTrue(tableExists(TABLE), "forward migration must create the record table");
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                String.class, TABLE);
        assertTrue(columns.containsAll(DOCUMENTED_COLUMNS),
                "record table must keep the documented columns: " + columns);
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, TABLE);
        assertTrue(indexes.containsAll(DOCUMENTED_INDEXES),
                "record table must keep the documented indexes: " + indexes);

        // 回滚脚本：只删除本工单新增的表，既有表不受影响
        List<String> rollbackStatements = statements(Files.readString(ROLLBACK, StandardCharsets.UTF_8));
        assertEquals(1, rollbackStatements.size(), "rollback must be a single statement: " + rollbackStatements);
        String rollback = rollbackStatements.get(0);
        assertTrue(rollback.contains("DROP TABLE IF EXISTS") && rollback.contains(TABLE),
                "rollback must drop the record table: " + rollback);
        assertFalse(rollback.matches("(?is).*DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?[`\"]?picture[`\"]?(?![_a-zA-Z0-9]).*"),
                "rollback must not touch existing tables: " + rollback);
        // 回滚只删除本工单新增的表，既有表不受影响
        assertTrue(tableExists("picture"), "rollback must not touch existing tables");
        assertTrue(tableExists(TABLE), "rollback must not touch the live record table");
    }

    /**
     * 去掉注释行后按分号切分；脚本本身不含字符串内的分号。
     */
    private List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
        }
        assertEquals(0, current.toString().trim().length(), "script must end every statement with a semicolon");
        return result;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
