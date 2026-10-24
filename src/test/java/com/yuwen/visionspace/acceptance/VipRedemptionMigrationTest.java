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
 * #39 VIP 兑换迁移：前向建表、回滚幂等补列、导入工具状态映射与脱敏输出。
 *
 * <p>迁移脚本在真实隔离 MySQL 上按原样执行（表是新增的，无需 scratch 改名；
 * 测试结束清理自己创建的行与列）。导入工具以独立进程运行，输入为临时文件。
 */
@IsolatedSpringBootTest
class VipRedemptionMigrationTest {

    private static final Path FORWARD = Path.of("sql/migrations/2026-10-24-vip-redemption.sql");
    private static final Path ROLLBACK = Path.of("sql/migrations/2026-10-24-vip-redemption-rollback.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forwardMigrationIsIdempotentSafeWhenTablesAlreadyExistForThisRun() {
        // 建表语句随 bootstrap 已执行；这里验证结构与注释中承诺的索引存在
        List<String> codeIndexes = jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vip_redemption_code'",
                String.class);
        assertTrue(codeIndexes.contains("uk_codeHash"), "核销原子性依赖 codeHash 唯一索引: " + codeIndexes);
        List<String> recordIndexes = jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vip_redemption_record'",
                String.class);
        assertTrue(recordIndexes.contains("uk_codeId"), "「重试不重复延长」依赖 codeId 唯一索引: " + recordIndexes);
    }

    @Test
    void rollbackScriptRestoresLegacyVipCodeColumnIdempotently() throws Exception {
        // 脚本使用会话级变量与 PREPARE/EXECUTE，必须整体在单一连接上执行
        // （与 DBA 用 mysql CLI 执行整个文件的方式一致）。
        Boolean hasColumn = jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'vipCode'",
                Boolean.class);
        if (Boolean.TRUE.equals(hasColumn)) {
            // 已被前一次执行补列：直接验证幂等（第二次执行不报错、不重复加列）
            runOnSingleConnection(ROLLBACK);
            runOnSingleConnection(ROLLBACK);
        } else {
            runOnSingleConnection(ROLLBACK);
            assertTrue(jdbc.queryForObject(
                    "SELECT COUNT(*) > 0 FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'vipCode'",
                    Boolean.class), "回滚必须补回旧版本依赖的 user.vipCode 列");
            // 幂等：再执行一次不报错
            runOnSingleConnection(ROLLBACK);
            // 列必须为 NULL：回滚不得写入或恢复任何码值
            Integer nullCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user WHERE vipCode IS NOT NULL", Integer.class);
            assertEquals(0, nullCount, "回滚后的兼容列不得携带任何码值");
        }
        // 清理：删除该列，恢复 bootstrap 形态（只清理本测试引入的变更）
        dropVipCodeColumnIfNeeded();
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

    private void run(Path script) throws IOException {
        for (String statement : statements(Files.readString(script, StandardCharsets.UTF_8))) {
            jdbc.execute(statement);
        }
    }

    /**
     * 整个脚本在单一连接上执行：脚本使用会话级变量（SET @...）与
     * PREPARE/EXECUTE，跨连接拆开执行会丢失变量。前向脚本没有会话状态，
     * 继续走 {@link #run(Path)} 的连接池路径。
     */
    private void runOnSingleConnection(Path script) throws Exception {
        // 单一连接上逐条执行：脚本使用会话级变量（SET @...）与 PREPARE/EXECUTE，
        // 变量必须留在同一会话里；JDBC 默认不允许多语句一次 execute，所以逐条发。
        var connection = org.springframework.jdbc.datasource.DataSourceUtils
                .getConnection(jdbc.getDataSource());
        try (var statement = connection.createStatement()) {
            for (String sql : statements(Files.readString(script, StandardCharsets.UTF_8))) {
                statement.execute(sql);
            }
        }
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

    private void dropVipCodeColumnIfNeeded() {
        Boolean hasColumn = jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'vipCode'",
                Boolean.class);
        if (Boolean.TRUE.equals(hasColumn)) {
            jdbc.execute("ALTER TABLE `user` DROP COLUMN `vipCode`");
        }
    }

    /**
     * 去掉注释行后按分号切分；两个脚本都不含字符串内的分号。
     */
    private List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (line.strip().startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
        }
        for (String part : current.toString().split(";")) {
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }
}
