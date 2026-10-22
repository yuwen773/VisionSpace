package com.yuwen.visionspace.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产启动守卫：缺项、开发回退值和占位值都必须在创建任何业务 Bean 之前失败。
 * 断言只看异常信息中的"名称"，不依赖真实秘密值。
 */
class ProductionSecretsGuardTest {

    private static final List<String> REQUIRED = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.data.redis.host",
            "spring.data.redis.password",
            "spring.ai.dashscope.api-key",
            "spring.ai.openai.api-key",
            "aliYunAi.apiKey",
            "vision-space.pexels.api-key",
            "vision-space.vision-recognition.api-key",
            "vision-space.security.default-password");

    @Test
    void completeProductionSecretsPass() {
        ProductionSecretsGuard.validate(true, productionValues());
    }

    @Test
    void nonProductionProfileIsNotChecked() {
        ProductionSecretsGuard.validate(false, Map.of());
    }

    @Test
    void everyMissingItemIsNamedWithoutLeakingValues() {
        var values = productionValues();
        for (String name : REQUIRED) {
            var candidate = new HashMap<>(values);
            candidate.remove(name);
            var error = assertThrows(IllegalStateException.class,
                    () -> ProductionSecretsGuard.validate(true, candidate), name);
            assertTrue(error.getMessage().contains(name), error.getMessage());
            assertTrue(error.getMessage().startsWith("BLOCKED_ENV"), error.getMessage());
        }
    }

    @Test
    void developmentFallbackValuesAreRejected() {
        var cases = Map.of(
                "spring.datasource.password", "123456",
                "vision-space.security.default-password", "12345678",
                "spring.datasource.url", "jdbc:mysql://localhost:3306/vision_space",
                "spring.data.redis.host", "127.0.0.1");
        cases.forEach((name, devValue) -> {
            var candidate = new HashMap<>(productionValues());
            candidate.put(name, devValue);
            var error = assertThrows(IllegalStateException.class,
                    () -> ProductionSecretsGuard.validate(true, candidate), name);
            assertTrue(error.getMessage().contains(name), error.getMessage());
        });
    }

    @Test
    void placeholderTextIsRejected() {
        for (String placeholder : List.of("xxx", "your_mysql_password", "your_db_password",
                "your-redis-host", "change_me_before_production", "changeme", "<secret>", "TODO")) {
            var candidate = new HashMap<>(productionValues());
            candidate.put("spring.datasource.password", placeholder);
            assertThrows(IllegalStateException.class,
                    () -> ProductionSecretsGuard.validate(true, candidate), placeholder);
        }
    }

    @Test
    void activeProfilesDetectsExplicitProductionOnly() {
        assertTrue(ProductionSecretsGuard.isProduction(List.of("prod")));
        assertTrue(ProductionSecretsGuard.isProduction(List.of("local", "prod")));
        assertTrue(ProductionSecretsGuard.isProduction(List.of("production")));
        assertEquals(false, ProductionSecretsGuard.isProduction(List.of("local")));
        assertEquals(false, ProductionSecretsGuard.isProduction(List.of()));
    }

    private Map<String, String> productionValues() {
        var values = new HashMap<String, String>();
        for (String name : REQUIRED) {
            values.put(name, "injected-" + name + "-9f2c");
        }
        values.put("spring.datasource.url", "jdbc:mysql://db.internal:3306/vision_space");
        values.put("spring.data.redis.host", "redis.internal");
        return values;
    }
}
