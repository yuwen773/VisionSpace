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
            "spring.ai.openai.base-url",
            "spring.ai.openai.chat.options.model",
            "aliYunAi.apiKey",
            "vision-space.pexels.api-key",
            "vision-space.vision-recognition.api-key",
            "vision-space.security.default-password");

    @Test
    void completeProductionSecretsPass() {
        var values = productionValues();
        // 守卫新增必填项时，这份夹具必须跟着补：否则新项既没被测到，
        // completeProductionSecretsPass 还会先失败，掩盖真正的回归。
        assertEquals(ProductionSecretsGuard.REQUIRED_KEYS.size(), values.size(),
                "夹具没有覆盖守卫要求的全部键：" + ProductionSecretsGuard.REQUIRED_KEYS);
        ProductionSecretsGuard.validate(true, values);
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
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/vision_space",
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
    void unfilledTemplatePlaceholderIsRejected() {
        // 部署模板里的占位是中文说明，不在英文占位词表里；没替换就带着它启动，
        // 服务能起来但用的是假口令，比直接启动失败更难查。
        for (String template : List.of("<运行角色口令>", "<填 Redis 口令>", "<APP_PASSWORD>")) {
            var candidate = new HashMap<>(productionValues());
            candidate.put("spring.datasource.password", template);
            var error = assertThrows(IllegalStateException.class,
                    () -> ProductionSecretsGuard.validate(true, candidate), template);
            assertTrue(error.getMessage().contains("spring.datasource.password"), error.getMessage());
        }
    }

    @Test
    void explicitlyBlankValueIsRejectedAndReportedAsBlank() {
        // prod 配置给模型端点留了默认值，所以「完全没配」走不到；
        // 要挡的是环境变量存在却为空串。报错要说清是空，不是缺。
        var candidate = new HashMap<>(productionValues());
        candidate.put("spring.ai.openai.base-url", "");
        var error = assertThrows(IllegalStateException.class,
                () -> ProductionSecretsGuard.validate(true, candidate));
        assertTrue(error.getMessage().contains("spring.ai.openai.base-url"), error.getMessage());
        assertTrue(error.getMessage().contains("(blank)"), error.getMessage());
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
