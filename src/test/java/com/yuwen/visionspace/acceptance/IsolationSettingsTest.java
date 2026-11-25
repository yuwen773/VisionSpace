package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolationSettingsTest {
    @Test
    void missingIsolationMustFailBeforeConnecting() {
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(Map.of()));
    }

    @Test
    void developmentServicePortsMustBeRejected() {
        var postgresEnvironment = environment();
        postgresEnvironment.put("VS_ACCEPTANCE_PG_PORT", "5432");
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(postgresEnvironment));
        var redisEnvironment = environment();
        redisEnvironment.put("VS_ACCEPTANCE_REDIS_PORT", "6379");
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(redisEnvironment));
    }

    /**
     * 隔离库的身份必须落在 PostgreSQL 上：库名带本轮 nonce，连接串指向本轮的
     * 独占端口，运行角色是低权账号 {@code vision_app}（DDL 由 Flyway 代劳）。
     */
    @Test
    void isolationIdentityPointsAtTheRunSpecificPostgresDatabase() {
        var settings = IsolationSettings.from(environment());

        assertEquals("vs_acceptance_0123456789abcdef0123456789abcdef", settings.database());
        assertEquals("jdbc:postgresql://127.0.0.1:15432/vs_acceptance_0123456789abcdef0123456789abcdef",
                settings.jdbcUrl());
        assertEquals("vision_app", IsolationSettings.APP_USER);
        assertTrue(settings.jdbcUrl().startsWith("jdbc:postgresql://"),
                "隔离连接串必须是 PostgreSQL 协议: " + settings.jdbcUrl());
    }

    private Map<String, String> environment() {
        return new HashMap<>(Map.of(
                "VS_ACCEPTANCE_NONCE", "0123456789abcdef0123456789abcdef",
                "VS_ACCEPTANCE_PG_PORT", "15432",
                "VS_ACCEPTANCE_REDIS_PORT", "16379"));
    }
}
