package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本唯一键的大小写语义，跑在真实 PostgreSQL 上。
 *
 * <p>MySQL 侧整库是 {@code utf8mb4_unicode_ci}，所有文本唯一键都不区分大小写。
 * PostgreSQL 默认区分大小写，因此几条唯一键必须有意识地重建：
 * <ul>
 *   <li>账号：迁移前用户能用任意大小写登录，所以唯一索引建在 {@code lower(user_account)} 上，保持 CI；</li>
 *   <li>微信 openid、兑换码哈希、事件 ID、租约令牌：不透明标识或哈希，必须 CS——
 *       把它们当 CI 处理会让两个不同的真实值互相顶掉。</li>
 * </ul>
 * 这里对每一类各插一对只差大小写的值，把「CI 必须冲突、CS 必须共存」钉住。
 */
@IsolatedSpringBootTest
class PostgresUniqueCaseSemanticsTest {

    private static final long USER_A = 9_140_001L;
    private static final long USER_B = 9_140_002L;

    /**
     * 全部非主键唯一索引的登记表：名称 → 索引覆盖的表达式。
     *
     * <p>逐项写死是有意的：迁移前整库是 CI 排序规则，切到 PG 后每条唯一键都必须
     * 重新选择大小写语义。只有账号归一化成 {@code lower(user_account)}（保留 CI），
     * 其余文本键保持原列、区分大小写。任何一条被悄悄改成函数索引或者消失，
     * 都会在这里显式失败，而不是等到线上两个不同值互相顶掉才发现。
     */
    private static final Map<String, String> UNIQUE_INDEXES = uniqueIndexes();

    private static Map<String, String> uniqueIndexes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("uq_agent_session_session_id", "session_id");
        map.put("uq_picture_stats_picture_id", "picture_id");
        map.put("uq_space_space_active_key", "active_key");
        map.put("uq_space_user_space_id_user_id", "space_id, user_id");
        map.put("uq_storage_config_platform", "platform");
        map.put("uq_app_user_user_wx_open_id", "wx_open_id");
        map.put("uq_app_user_user_account_ci", "lower(user_account)");
        map.put("uq_user_picture_action_event_id", "event_id");
        map.put("uq_mcp_server_mcp_server_code_user", "mcp_server_code, user_id");
        map.put("uq_storage_cleanup_task_pending_object", "pending_key");
        map.put("uq_agent_run_state_thread", "thread_id");
        map.put("uq_agent_action_record_thread_action", "thread_id, action_id");
        map.put("uq_vip_redemption_code_code_hash", "code_hash");
        map.put("uq_vip_redemption_record_code_id", "code_id");
        map.put("uq_agent_session_attachment_session_resource", "session_id, resource_id");
        map.put("uq_agent_legacy_attachment_message_hash", "message_id, url_hash");
        map.put("uq_feedback_attachment_legacy_url_hash", "legacy_url_hash");
        map.put("uq_resource_access_grant_active_key", "active_key");
        map.put("uq_resource_isolation_active_key", "active_key");
        map.put("uq_user_preference_user_id", "user_id");
        map.put("uq_asset_board_proposal_task", "task_id");
        map.put("uq_asset_board_proposal_request", "owner_id, proposal_request_id");
        map.put("uq_asset_board_proposal_operation", "owner_id, operation_id");
        map.put("uq_picture_index_task_picture", "picture_id");
        return map;
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService userService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_user WHERE lower(user_account) = lower(?) OR id IN (?, ?)",
                "CaseUserOne", USER_A, USER_B);
        jdbc.update("DELETE FROM vip_redemption_code WHERE code_hash IN (?, ?)", "CaseCodeHashX", "casecodehashx");
    }

    @Test
    void accountUniquenessIgnoresCaseAndLoginMatchesAnyCase() {
        String password = "acceptance-case-password";
        // 走真实注册/登录链路：索引、查重、登录查询三处必须同口径，读回自己写的行不算证据。
        long userId = userService.userRegister("CaseUserOne", password, password);
        assertThrows(BusinessException.class,
                () -> userService.userRegister("caseuserone", password, password),
                "注册查重必须与 lower(user_account) 唯一索引同口径，否则第二个账号要等数据库抛 500");
        assertEquals(userId, userService.authenticateForPassword("CASEUSERONE", password).getId(),
                "登录查询必须大小写不敏感：迁移前整库是 CI 排序规则，用户能用任意大小写登录");
    }

    @Test
    void weChatOpenIdUniquenessIsCaseSensitive() {
        insertUser(USER_A, "CaseUserOne", "OpenIdCaseX");
        insertUser(USER_B, "CaseUserTwo", "openidcasex");
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE id IN (?, ?)", Long.class, USER_A, USER_B),
                "openid 是微信下发的不透明标识：只差大小写就是两个不同的用户");
        assertThrows(DataIntegrityViolationException.class, () -> insertUser(USER_B, "CaseUserTwo", "OpenIdCaseX"),
                "同一个 openid 仍然只能绑定一个账号");
    }

    @Test
    void redemptionCodeHashesAreCaseSensitive() {
        jdbc.update("INSERT INTO vip_redemption_code (code_hash) VALUES (?)", "CaseCodeHashX");
        jdbc.update("INSERT INTO vip_redemption_code (code_hash) VALUES (?)", "casecodehashx");
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM vip_redemption_code WHERE code_hash IN (?, ?)",
                Long.class, "CaseCodeHashX", "casecodehashx"),
                "十六进制摘要是哈希值：大小写不同即不同值，不能被当成同一张兑换码");
    }

    /**
     * 逐条核对库里的唯一索引登记是否与大小写决策一致。
     *
     * <p>行为用例只能抽查几类，这条把全部非主键唯一索引一次性钉住：名称、覆盖列
     * （或表达式）必须与登记表完全相同，因此「新增一条唯一索引」和「把某个文本键
     * 改成 lower()」都会让本用例失败，而不是悄悄改变大小写语义。
     */
    @Test
    void uniqueIndexInventoryMatchesTheCaseSensitivityDecision() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.relname AS index_name,
                       pg_get_indexdef(i.oid) AS definition
                FROM pg_index x
                JOIN pg_class i ON i.oid = x.indexrelid
                JOIN pg_class t ON t.oid = x.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public' AND x.indisunique AND NOT x.indisprimary
                ORDER BY i.relname
                """);
        Map<String, String> actual = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String definition = ((String) row.get("definition")).trim();
            String marker = "USING btree (";
            int start = definition.lastIndexOf(marker);
            assertTrue(start > 0, "索引定义必须能取出覆盖的列: " + definition);
            assertEquals(definition.length() - 1, definition.lastIndexOf(')'),
                    "索引定义形状不像预期（含 WHERE 或 WITH 子句）: " + definition);
            actual.put((String) row.get("index_name"),
                    normalize(definition.substring(start + marker.length(), definition.length() - 1)));
        }
        Map<String, String> expected = new TreeMap<>();
        UNIQUE_INDEXES.forEach((name, expression) -> expected.put(name, normalize(expression)));
        assertEquals(expected, actual,
                "唯一索引与大小写决策的登记表不一致：新增/删除/改写唯一键必须同时更新期望值");
        assertEquals(1L, actual.values().stream().filter(value -> value.contains("lower(")).count(),
                "只有账号唯一键做大小写归一化：其它文本键保持区分大小写");
    }

    /**
     * 抹平 PostgreSQL 的渲染差异：表达式索引会打印成 {@code lower((user_account)::text)}，
     * 多出来的类型转换和括号不该让「语义相同」的索引看起来不同。
     */
    private static String normalize(String expression) {
        return expression.replace("::text", "").replace("((", "(").replace("))", ")").replace(" ", "");
    }

    private void insertUser(long id, String account, String openId) {
        jdbc.update("""
                INSERT INTO app_user (id, user_account, user_password, user_role, wx_open_id)
                VALUES (?, ?, 'acceptance-only', 'user', ?)
                """, id, account, openId);
    }
}
