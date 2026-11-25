package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 每一条文本唯一键的大小写行为矩阵。
 *
 * <p>迁移前整库是 {@code utf8mb4_unicode_ci}，所有文本唯一键都不区分大小写；切到
 * PostgreSQL 后只有账号保留 CI（唯一索引建在 {@code lower(user_account)} 上），
 * 其余文本键必须区分大小写——把不透明 ID 或哈希当 CI 处理，两个不同的真实值会互相顶掉。
 *
 * <p>{@link PostgresUniqueCaseSemanticsTest} 用索引清单把「每条唯一键的结构」钉住，
 * 本类补上另一半证据：对清单里每一条文本唯一键，真实插入一对只差大小写的行，
 * CI 键必须冲突、CS 键必须共存。
 *
 * <p>插值必需的列由 information_schema 推出：新增 NOT NULL 列时本用例会在真实插入时
 * 报错，而不是被静默跳过；每行的其它列取不同的值，保证只有被测的那条唯一键会碰撞。
 */
@IsolatedSpringBootTest
class PostgresUniqueCaseBehaviorTest {

    /**
     * 一条文本唯一键。
     *
     * @param table 表名
     * @param column 参与大小写区分的文本列
     * @param caseInsensitive 该键是否必须大小写不敏感（目前只有账号）
     * @param siblings 同一个复合唯一索引里的其它列：两行必须取相同值，
     *                 否则「只差大小写」会连整个索引键都错开，冲突根本不会发生
     */
    private record CaseKey(String table, String column, boolean caseInsensitive, List<String> siblings) {
    }

    private static final List<CaseKey> MATRIX = List.of(
            new CaseKey("app_user", "user_account", true, List.of()),
            new CaseKey("app_user", "wx_open_id", false, List.of()),
            new CaseKey("agent_session", "session_id", false, List.of()),
            new CaseKey("agent_session_attachment", "session_id", false, List.of("resource_id")),
            new CaseKey("agent_legacy_attachment", "url_hash", false, List.of("message_id")),
            new CaseKey("agent_run_state", "thread_id", false, List.of()),
            new CaseKey("agent_action_record", "thread_id", false, List.of("action_id")),
            new CaseKey("asset_board_proposal", "task_id", false, List.of()),
            new CaseKey("asset_board_proposal", "proposal_request_id", false, List.of("owner_id")),
            new CaseKey("asset_board_proposal", "operation_id", false, List.of("owner_id")),
            new CaseKey("feedback_attachment", "legacy_url_hash", false, List.of()),
            new CaseKey("mcp_server", "mcp_server_code", false, List.of("user_id")),
            new CaseKey("resource_access_grant", "active_key", false, List.of()),
            new CaseKey("resource_isolation", "active_key", false, List.of()),
            new CaseKey("space", "active_key", false, List.of()),
            new CaseKey("storage_cleanup_task", "pending_key", false, List.of()),
            new CaseKey("storage_config", "platform", false, List.of()),
            new CaseKey("user_picture_action", "event_id", false, List.of()),
            new CaseKey("vip_redemption_code", "code_hash", false, List.of()));

    @Autowired
    private JdbcTemplate jdbc;

    /** 本用例写进去的行，按主键回删。 */
    private final Map<String, List<Object>> insertedKeys = new LinkedHashMap<>();

    @AfterEach
    void cleanUp() {
        insertedKeys.forEach((table, keys) -> {
            if (!keys.isEmpty()) {
                String placeholders = String.join(", ", keys.stream().map(key -> "?").toList());
                jdbc.update("DELETE FROM " + table + " WHERE " + primaryKey(table) + " IN (" + placeholders + ")",
                        keys.toArray());
            }
        });
        insertedKeys.clear();
    }

    @Test
    void everyTextUniqueKeyHasTheDocumentedCaseSemantics() {
        for (int index = 0; index < MATRIX.size(); index++) {
            CaseKey key = MATRIX.get(index);
            List<ColumnSpec> columns = allColumns(key.table());
            List<String> primaryKey = primaryKeyColumns(key.table());

            Map<String, Object> firstRow = rowFor(key, columns, primaryKey, index, 0);
            Map<String, Object> secondRow = rowFor(key, columns, primaryKey, index, 1);
            insert(key.table(), firstRow);
            if (key.caseInsensitive()) {
                assertThrows(DataIntegrityViolationException.class,
                        () -> insert(key.table(), secondRow),
                        key.table() + "." + key.column()
                                + " 的唯一索引必须按 lower() 判重：只差大小写的两个值必须冲突");
            } else {
                insert(key.table(), secondRow);
                assertEquals(2L, countRows(key.table(), primaryKey, firstRow, secondRow),
                        key.table() + "." + key.column()
                                + " 是不透明标识/哈希：只差大小写必须共存，判成同一个值会互相顶掉");
            }
        }
    }

    /** 列的类型与长度，用来给「插值必需的列」造出类型正确的值。 */
    private record ColumnSpec(String name, String dataType, Integer maxLength, boolean notNull,
                              String columnDefault) {
        /** NOT NULL 且没有默认值：插入时必须给值。 */
        boolean required() {
            return notNull && columnDefault == null;
        }
    }

    private List<ColumnSpec> allColumns(String table) {
        return jdbc.query("""
                SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """, (rs, rowNum) -> new ColumnSpec(rs.getString("column_name"),
                rs.getString("data_type"),
                (Integer) rs.getObject("character_maximum_length"),
                "NO".equals(rs.getString("is_nullable")),
                rs.getString("column_default")), table);
    }

    private List<String> primaryKeyColumns(String table) {
        return jdbc.queryForList("""
                SELECT a.attname
                FROM pg_index x
                JOIN pg_class c ON c.oid = x.indrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = x.indrelid AND a.attnum = ANY(x.indkey)
                WHERE n.nspname = 'public' AND c.relname = ? AND x.indisprimary
                ORDER BY array_position(x.indkey, a.attnum)
                """, String.class, table);
    }

    private String primaryKey(String table) {
        List<String> key = primaryKeyColumns(table);
        return key.size() == 1 ? key.get(0) : "(" + String.join(", ", key) + ")";
    }

    /** 造一行：目标列按大小写取两个变体，兄弟列两行相同，其余必需列每行不同。 */
    private Map<String, Object> rowFor(CaseKey key, List<ColumnSpec> columns, List<String> primaryKey,
                                        int caseIndex, int row) {
        Map<String, Object> values = new LinkedHashMap<>();
        String root = "m4case" + caseIndex;
        for (ColumnSpec column : columns) {
            String name = column.name();
            boolean target = name.equals(key.column());
            boolean sibling = key.siblings().contains(name);
            boolean pk = primaryKey.contains(name);
            if (!target && !sibling && !pk && !column.required()) {
                continue;
            }
            if (target) {
                values.put(name, row == 0 ? root + "Target" : root.toLowerCase() + "target");
            } else if (sibling) {
                // 复合索引的另一半必须两行相同，否则根本撞不上，测不出大小写语义
                values.put(name, siblingValue(column, caseIndex));
            } else if (pk) {
                values.put(name, primaryKeyValue(column, caseIndex, row));
            } else {
                values.put(name, filler(column, caseIndex, row));
            }
        }
        return values;
    }

    private Object siblingValue(ColumnSpec column, int caseIndex) {
        return switch (column.dataType()) {
            case "bigint", "integer", "smallint" -> 9_960_000L + caseIndex * 10L;
            default -> "m4sibling" + caseIndex + "-" + column.name();
        };
    }

    private Object primaryKeyValue(ColumnSpec column, int caseIndex, int row) {
        Object value = switch (column.dataType()) {
            case "bigint", "integer", "smallint" -> 9_980_000L + caseIndex * 10L + row;
            default -> "m4pk" + caseIndex + "-" + row;
        };
        return truncate(column, value);
    }

    private Object filler(ColumnSpec column, int caseIndex, int row) {
        Object value = switch (column.dataType()) {
            case "bigint", "integer", "smallint" -> 9_970_000L + caseIndex * 10L + row;
            case "numeric" -> BigDecimal.ZERO;
            case "double precision" -> 0d;
            case "timestamp without time zone" -> Timestamp.valueOf(LocalDateTime.now());
            case "bytea" -> new byte[]{0};
            default -> "m4filler" + caseIndex + "-" + row + "-" + column.name();
        };
        return truncate(column, value);
    }

    private Object truncate(ColumnSpec column, Object value) {
        if (value instanceof String text && column.maxLength() != null && text.length() > column.maxLength()) {
            return text.substring(0, column.maxLength());
        }
        return value;
    }

    private void insert(String table, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        String placeholders = String.join(", ", columns.stream().map(name -> "?").toList());
        jdbc.update("INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")",
                columns.stream().map(values::get).toArray());
        List<String> primaryKey = primaryKeyColumns(table);
        List<Object> keyValues = primaryKey.stream().map(values::get).toList();
        insertedKeys.computeIfAbsent(table, name -> new ArrayList<>()).addAll(keyValues);
    }

    private long countRows(String table, List<String> primaryKey, Map<String, Object>... rows) {
        List<Object> keys = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            primaryKey.forEach(name -> keys.add(row.get(name)));
        }
        String placeholders = String.join(", ", keys.stream().map(key -> "?").toList());
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + primaryKey(table)
                + " IN (" + placeholders + ")", Long.class, keys.toArray());
    }
}
