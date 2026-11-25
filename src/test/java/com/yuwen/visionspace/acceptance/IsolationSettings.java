package com.yuwen.visionspace.acceptance;

import java.util.Map;

/** Only the acceptance runner can supply the per-run instance identity. */
public record IsolationSettings(String nonce, int postgresPort, int redisPort) {

    /** 运行角色：只有 DML 权限，DDL 由 Flyway 以 vision_migrator 身份执行。 */
    public static final String APP_USER = "vision_app";

    public static IsolationSettings from(Map<String, String> environment) {
        String nonce = environment.getOrDefault("VS_ACCEPTANCE_NONCE", "");
        if (!nonce.matches("[a-f0-9]{32}")) {
            throw new IllegalStateException("BLOCKED_ENV: run pwsh -File scripts/acceptance.ps1; isolation identity missing");
        }
        return new IsolationSettings(nonce, port(environment, "VS_ACCEPTANCE_PG_PORT"),
                port(environment, "VS_ACCEPTANCE_REDIS_PORT"));
    }

    private static int port(Map<String, String> environment, String name) {
        try {
            int port = Integer.parseInt(environment.getOrDefault(name, "0"));
            if (port >= 10000 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Fail closed below, before any connection is opened.
        }
        throw new IllegalStateException("BLOCKED_ENV: missing or unsafe isolated port: " + name);
    }

    public String database() {
        return "vs_acceptance_" + nonce;
    }

    /**
     * 应用与测试共用的连接串。
     *
     * <p>时区不写在连接串里：库级 {@code timezone}、验收 JVM 的 {@code -Duser.timezone=UTC}
     * 与连接会话三者必须一致，库级设置由隔离脚本建立（{@code ALTER DATABASE ... SET timezone='UTC'}）。
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:" + postgresPort + "/" + database();
    }
}
