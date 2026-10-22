package com.yuwen.visionspace.acceptance;

import java.util.Map;

/** Only the acceptance runner can supply the per-run instance identity. */
public record IsolationSettings(String nonce, int mysqlPort, int redisPort) {
    public static IsolationSettings from(Map<String, String> environment) {
        String nonce = environment.getOrDefault("VS_ACCEPTANCE_NONCE", "");
        if (!nonce.matches("[a-f0-9]{32}")) {
            throw new IllegalStateException("BLOCKED_ENV: run pwsh -File scripts/acceptance.ps1; isolation identity missing");
        }
        return new IsolationSettings(nonce, port(environment, "VS_ACCEPTANCE_MYSQL_PORT"),
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

    public String jdbcUrl() {
        return "jdbc:mysql://127.0.0.1:" + mysqlPort + "/" + database()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
