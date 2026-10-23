package com.yuwen.visionspace.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生产启动守卫：显式 prod profile 下，必要秘密必须由环境注入。
 *
 * <p>缺失、空白、开发回退值与占位文本都会让启动失败，错误信息只包含属性名，
 * 绝不包含属性值。非生产 profile 不做检查，避免影响本地开发与隔离验收。
 */
public final class ProductionSecretsGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String BLOCKED = "BLOCKED_ENV: production secrets are not configured: ";

    static final List<String> REQUIRED_KEYS = List.of(
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

    /** 开发期回退值：出现在生产即视为未正确注入。 */
    private static final Map<String, List<String>> DEVELOPMENT_FALLBACKS = Map.of(
            "spring.datasource.password", List.of("123456"),
            "vision-space.security.default-password", List.of("12345678", "123456"),
            "spring.datasource.url", List.of("jdbc:mysql://localhost:3306/vision_space"),
            "spring.data.redis.host", List.of("localhost", "127.0.0.1", "::1"),
            "spring.ai.openai.api-key", List.of("acceptance-dummy"),
            "spring.ai.dashscope.api-key", List.of("acceptance-dummy"));

    /** 占位文本：示例值或未替换的模板必须在生产拒绝。 */
    private static final List<String> PLACEHOLDERS = List.of(
            "xxx", "xxxx", "your_mysql_password", "your_redis_password",
            "your_db_password", "your_db_user", "your_db_host", "your-redis-host",
            "your_dashscope_key", "your_pexels_key", "your_vision_recognition_key",
            "change_me_before_production", "changeme", "todo", "<secret>", "secret", "password");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        var values = new HashMap<String, String>();
        for (String key : REQUIRED_KEYS) {
            values.put(key, environment.getProperty(key));
        }
        validate(isProduction(List.of(environment.getActiveProfiles())), values);
    }

    static boolean isProduction(List<String> activeProfiles) {
        return ProductionProfiles.isProduction(activeProfiles);
    }

    /**
     * 校验生产秘密。失败时抛出带 {@link #BLOCKED} 前缀的异常，信息中只含属性名。
     */
    static void validate(boolean production, Map<String, String> values) {
        if (!production) {
            return;
        }
        var problems = new java.util.ArrayList<String>();
        for (String key : REQUIRED_KEYS) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                problems.add(key + " (missing)");
            } else if (isPlaceholder(value)) {
                problems.add(key + " (placeholder)");
            } else if (isDevelopmentFallback(key, value)) {
                problems.add(key + " (development fallback)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(BLOCKED + String.join(", ", problems));
        }
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        // 模板残留：以 your_/your- 开头或含 change_me 的值一律视为未替换。
        return PLACEHOLDERS.contains(normalized)
                || normalized.startsWith("your_") || normalized.startsWith("your-")
                || normalized.contains("change_me");
    }

    private static boolean isDevelopmentFallback(String key, String value) {
        List<String> fallbacks = DEVELOPMENT_FALLBACKS.get(key);
        return fallbacks != null && fallbacks.contains(value.trim());
    }
}
