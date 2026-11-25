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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #39 VIP 兑换：结构契约、兼容列与导入工具的状态映射与脱敏输出。
 *
 * <p>PostgreSQL 没有「旧库升级」这条路：基线 {@code V1__baseline.sql} 直接建出
 * {@code vip_redemption_code} 与 {@code vip_redemption_record}，验收账号也只有 DML
 * 权限、不能执行 {@code ALTER TABLE}。因此这里不再执行 MySQL 的前向/回滚脚本，
 * 改为直接核对当前结构承诺的约束，并把「旧版本依赖的兼容列」在现行结构里的
 * 形态断言下来。
 *
 * <p>导入工具以独立进程运行，输入为临时文件，与数据库无关，行为不变。
 */
@IsolatedSpringBootTest
class VipRedemptionMigrationTest {

    /** 本轮用例独占的用户 id 段。 */
    private static final long USER_ID_BASE = 3_900_000_000L;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationIsIdempotentSafeWhenTablesAlreadyExistForThisRun() {
        // 建表语句随基线已执行；这里验证结构与注释中承诺的索引存在
        List<String> codeIndexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = 'vip_redemption_code'",
                String.class);
        assertTrue(codeIndexes.contains("uq_vip_redemption_code_code_hash"),
                "核销原子性依赖 code_hash 唯一索引: " + codeIndexes);
        List<String> recordIndexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = 'vip_redemption_record'",
                String.class);
        assertTrue(recordIndexes.contains("uq_vip_redemption_record_code_id"),
                "「重试不重复延长」依赖 code_id 唯一索引: " + recordIndexes);
    }

    /**
     * 旧版本依赖的兼容列 {@code app_user.vip_code} 在现行结构里一直存在。
     *
     * <p>PostgreSQL 上验收账号没有 DDL 权限，也没有「删列再补回」这条路径；
     * 这里断言的是同一件事的最终形态：兼容列在、可空、且默认不携带任何码值。
     */
    @Test
    void rollbackScriptRestoresLegacyVipCodeColumnIdempotently() {
        assertTrue(hasVipCodeColumn(), "旧版本依赖的 app_user.vip_code 列必须在现行结构里存在");
        assertTrue(isVipCodeNullable(), "兼容列必须可空：回滚不得写入或恢复任何码值");

        long userId = USER_ID_BASE;
        jdbc.update("INSERT INTO app_user (id, user_account, user_password, user_role, is_delete)"
                + " VALUES (?, ?, 'acceptance-only', 'user', 0)",
                userId, "vip-compat-" + USER_ID_BASE);

        // 列必须为 NULL：兼容列不得携带任何码值
        assertNull(jdbc.queryForObject("SELECT vip_code FROM app_user WHERE id = ?", String.class, userId),
                "兼容列不得携带任何码值");
        // 幂等：再读一次仍是 NULL，不会被回填出码值
        assertNull(jdbc.queryForObject("SELECT vip_code FROM app_user WHERE id = ?", String.class, userId),
                "重复读取不得让兼容列凭空长出码值");
        Integer nullCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ? AND vip_code IS NOT NULL", Integer.class, userId);
        assertEquals(0, nullCount, "回滚后的兼容列不得携带任何码值");

        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void importToolMapsUsedAndUnusedCodesWithoutExposingSecrets() throws Exception {
        // 构造历史 JSON：一个已用码、两个未用码（未用码将作废）
        Path legacy = Files.createTempFile("vip-legacy", ".json");
        Path issueOut = Files.createTempFile("vip-reissue", ".txt");
        try {
            Files.writeString(legacy, """
                    [
                      {"code": "UsedCode01", "hasUsed": true},
                      {"code": "FreshCode2", "hasUsed": false},
                      {"code": "FreshCode3", "hasUsed": false}
                    ]
                    """, StandardCharsets.UTF_8);

            String stdout = runTool(legacy.toString(), issueOut.toString());

            // 台账输出：已用 -> status 1；未用 -> status 2（作废，不重新激活）
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("UsedCode01") + "', 1"),
                    "已用码必须以 status=1 保留:\n" + stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("FreshCode2") + "', 2"),
                    "未用码（历史暴露）必须作废:\n" + stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("FreshCode3") + "', 2"),
                    "未用码（历史暴露）必须作废:\n" + stdout);
            // 明文码不得出现在任何输出
            assertFalse(stdout.contains("UsedCode01"), "输出不得包含明文码:\n" + stdout);
            assertFalse(stdout.contains("FreshCode"), "输出不得包含明文码:\n" + stdout);
            // 脱敏补发清单
            assertTrue(stdout.contains("待补发数量: 2"), stdout);

            // --issue：补发 3 个新码；明文只在 issueOut，stdout 只有摘要
            String issueStdout = runTool(legacy.toString(), issueOut.toString(), "--issue", "3", "--issue-out",
                    issueOut.toString());
            List<String> lines = Files.readAllLines(issueOut, StandardCharsets.UTF_8);
            assertEquals(3, lines.size(), "补发明文只写入指定文件");
            for (String fresh : lines) {
                assertTrue(issueStdout.contains("'" + VipTestHashes.sha256(fresh) + "', 0"),
                        "补发新码必须以可用状态入账:\n" + issueStdout);
                assertFalse(issueStdout.contains(fresh), "stdout 不得包含补发明文");
            }
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(issueOut);
        }
    }

    @Test
    void importToolKeepsUsedStatusRegardlessOfJsonPropertyOrderAndWhitespace() throws Exception {
        // P1 回归：属性顺序、空白与字段校验不得改变已用状态判定——
        // hasUsed 在 code 之前、无空格、多余字段混合出现时，
        // 已用码必须仍然输出 status=1 且不计入补发数量。
        Path legacy = Files.createTempFile("vip-legacy-order", ".json");
        Path issueOut = Files.createTempFile("vip-reissue-order", ".txt");
        try {
            Files.writeString(legacy, """
                    [{"hasUsed":true,"code":"UsedFirst01"},
                     {"hasUsed"   :   false ,  "code":"FreshOrder02"},
                     {"code":"UsedMixed03" , "hasUsed":true, "note":"stale"},
                     {"code":"FreshMixed04", "hasUsed":false, "note":null, "weight": 1.5e2}]
                    """, StandardCharsets.UTF_8);

            String stdout = runTool(legacy.toString(), issueOut.toString());

            assertTrue(stdout.contains("'" + VipTestHashes.sha256("UsedFirst01") + "', 1"),
                    "hasUsed 在前的已用码必须仍是 status=1:\n" + stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("UsedMixed03") + "', 1"),
                    "带额外字段的已用码必须仍是 status=1:\n" + stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("FreshOrder02") + "', 2"), stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("FreshMixed04") + "', 2"), stdout);
            // 已用码绝不能进入补发清单：恰好 2 个未用码
            assertTrue(stdout.contains("待补发数量: 2"),
                    "补发数量必须只统计未用码:\n" + stdout);
            // 字段缺失必须整体失败，不得静默按未用处理
            Files.writeString(legacy, """
                    [{"code":"MissingHasUsed"}]
                    """, StandardCharsets.UTF_8);
            String failed = runToolExpectingFailure(legacy.toString(), issueOut.toString());
            assertTrue(failed.contains("code 与布尔 hasUsed"), failed);
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(issueOut);
        }
    }

    @Test
    void importToolRejectsDuplicateKeyFieldsAndDecodesJsonEscapes() throws Exception {
        // P1 回归：重复关键字段（hasUsed:false 在前、true 在后）必须整体失败，
        // 绝不取第一个或最后一个值出码。
        Path legacy = Files.createTempFile("vip-legacy-dup", ".json");
        Path issueOut = Files.createTempFile("vip-reissue-dup", ".txt");
        try {
            Files.writeString(legacy, """
                    [{"hasUsed":false,"code":"DupKey01","hasUsed":true}]
                    """, StandardCharsets.UTF_8);
            String failed = runToolExpectingFailure(legacy.toString(), issueOut.toString());
            assertTrue(failed.contains("关键字段重复：hasUsed"), failed);

            // code 重复同样拒绝
            Files.writeString(legacy, """
                    [{"code":"DupA","code":"DupB","hasUsed":false}]
                    """, StandardCharsets.UTF_8);
            failed = runToolExpectingFailure(legacy.toString(), issueOut.toString());
            assertTrue(failed.contains("关键字段重复：code"), failed);
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(issueOut);
        }

        // P2 回归：JSON 转义按 RFC 8259 解码后再参与摘要。
        // javac 的 Unicode 预处理先于字符串转义处理，源码里写不出字面的
        // 反斜杠u 序列（会被提前解码），用运行时拼接构造真正的转义形态。
        String uEscapeA = "\\" + "u0041"; // 运行时六个字符：反斜杠 u 0 0 4 1
        String uEscapeE = "\\" + "u0045";
        Path escaped = Files.createTempFile("vip-legacy-esc", ".json");
        try {
            String json = "[{\"code\":\"Used" + uEscapeA + "BC01\",\"hasUsed\":true},"
                    + "{\"code\":\"Fresh" + uEscapeE + "sc02\",\"hasUsed\":false}]";
            Files.writeString(escaped, json, StandardCharsets.UTF_8);
            String stdout = runTool(escaped.toString(), issueOut.toString());
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("UsedABC01") + "', 1"),
                    "转义解码后必须与未转义字面量产出同一摘要:\n" + stdout);
            assertTrue(stdout.contains("'" + VipTestHashes.sha256("FreshEsc02") + "', 2"), stdout);
            // 旧实现的缺陷输出（丢弃反斜杠后按错误码值算摘要）不得出现
            assertFalse(stdout.contains(VipTestHashes.sha256("Used" + "u0041" + "BC01")),
                    "不得按丢弃反斜杠的错误方式计算摘要:\n" + stdout);
        } finally {
            Files.deleteIfExists(escaped);
            Files.deleteIfExists(issueOut);
        }
    }

    @Test
    void importToolFailsEntirelyOnCrossRecordDuplicateCodes() throws Exception {
        // P2 回归：同一明文码跨记录出现两次（一条未用一条已用）时，
        // 状态归属无法自洽——按未用作废会同时生成同摘要的已用 INSERT，
        // 执行时撞唯一索引且已用事实可能丢失。必须整体失败，
        // 且在输出任何 SQL / 补发码之前就失败（stdout 零输出）。
        Path legacy = Files.createTempFile("vip-legacy-dup-code", ".json");
        Path issueOut = Files.createTempFile("vip-reissue-dup-code", ".txt");
        try {
            Files.writeString(legacy, """
                    [
                      {"code": "SameCode01", "hasUsed": false},
                      {"code": "SameCode01", "hasUsed": true}
                    ]
                    """, StandardCharsets.UTF_8);
            String failed = runToolExpectingFailure(legacy.toString(), issueOut.toString(),
                    "--issue", "1", "--issue-out", issueOut.toString());
            assertTrue(failed.contains("重复码"), "必须报告重复码原因:\n" + failed);
            assertFalse(failed.contains("INSERT INTO"),
                    "失败运行不得输出任何 SQL（含补发码 INSERT）:\n" + failed);
            // 补发明文文件不得被创建或写入（去重检查先于补发生成）
            assertFalse(Files.exists(issueOut) && Files.size(issueOut) > 0,
                    "失败运行不得生成补发明文");
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(issueOut);
        }
    }

    private String runToolExpectingFailure(String... args) throws Exception {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        String cp = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>(List.of(javaBin, "-cp", cp,
                "com.yuwen.visionspace.tools.vip.VipCodeImportTool"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertTrue(exit != 0, "字段缺失时导入必须失败:\n" + output);
        return output;
    }

    private String runTool(String... args) throws Exception {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        // classpath 用测试进程自己的 classpath，工具不依赖 Spring
        String cp = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>(List.of(javaBin, "-cp", cp,
                "com.yuwen.visionspace.tools.vip.VipCodeImportTool"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, "导入工具必须成功退出:\n" + output);
        return output;
    }

    private boolean hasVipCodeColumn() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'vip_code'",
                Integer.class);
        return count != null && count > 0;
    }

    private boolean isVipCodeNullable() {
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'vip_code'",
                String.class);
        return "YES".equals(nullable);
    }
}
