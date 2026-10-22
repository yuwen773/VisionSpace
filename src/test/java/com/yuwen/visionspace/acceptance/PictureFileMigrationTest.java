package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源记录表的前向迁移与回滚脚本可执行且互为逆操作。
 *
 * <p>脚本在临时表名上执行，不触碰本轮的 {@code picture_file} 与 {@code picture}；
 * 真实环境的执行窗口、备份与旧直链失效仍属人工关卡。
 */
@IsolatedSpringBootTest
class PictureFileMigrationTest {

    private static final String TABLE = "picture_file";
    private static final String SCRATCH = "picture_file_migration_check";

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-22-picture-file.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-22-picture-file-rollback.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationCreatesTheRecordTableAndRollbackRemovesOnlyIt() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        try {
            run(FORWARD);
            assertTrue(tableExists(SCRATCH), "forward migration must create the record table");
            List<String> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                    String.class, SCRATCH);
            assertTrue(columns.containsAll(List.of("id", "ownerUserId", "spaceId", "pictureId", "purpose",
                    "kind", "platform", "objectKey", "url", "fileSize", "status", "createTime", "updateTime")),
                    "record table must keep the documented columns: " + columns);
            List<String> indexes = jdbc.queryForList(
                    "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, SCRATCH);
            assertTrue(indexes.containsAll(List.of("idx_picture_kind", "idx_owner_purpose_status",
                    "idx_platform_key", "idx_url")), "record table must keep the documented indexes: " + indexes);

            run(ROLLBACK);
            assertFalse(tableExists(SCRATCH), "rollback must drop the record table");
            // 回滚只删除本工单新增的表，既有表不受影响
            assertTrue(tableExists("picture"), "rollback must not touch existing tables");
            assertTrue(tableExists(TABLE), "rollback must not touch the live record table");
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS `" + SCRATCH + "`");
        }
    }

    private void run(Path script) throws IOException {
        for (String statement : statements(Files.readString(script, StandardCharsets.UTF_8))) {
            jdbc.execute(statement.replace("`" + TABLE + "`", "`" + SCRATCH + "`"));
        }
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
                result.add(current.toString());
                current.setLength(0);
            }
        }
        assertEquals(0, current.toString().trim().length(), "script must end every statement with a semicolon");
        return result;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
