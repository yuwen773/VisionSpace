package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IsolationSettingsTest {
    @Test
    void missingIsolationMustFailBeforeConnecting() {
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(Map.of()));
    }

    @Test
    void developmentServicePortsMustBeRejected() {
        var mysqlEnvironment = environment();
        mysqlEnvironment.put("VS_ACCEPTANCE_MYSQL_PORT", "3306");
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(mysqlEnvironment));
        var redisEnvironment = environment();
        redisEnvironment.put("VS_ACCEPTANCE_REDIS_PORT", "6379");
        assertThrows(IllegalStateException.class, () -> IsolationSettings.from(redisEnvironment));
    }

    private Map<String, String> environment() {
        return new HashMap<>(Map.of(
                "VS_ACCEPTANCE_NONCE", "0123456789abcdef0123456789abcdef",
                "VS_ACCEPTANCE_MYSQL_PORT", "13306",
                "VS_ACCEPTANCE_REDIS_PORT", "16379"));
    }
}
