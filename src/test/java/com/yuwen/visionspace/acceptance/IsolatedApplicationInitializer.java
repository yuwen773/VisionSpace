package com.yuwen.visionspace.acceptance;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.sql.DriverManager;
import java.time.Duration;

public class IsolatedApplicationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var settings = IsolationSettings.from(System.getenv());
        if (!java.util.Arrays.equals(new String[]{"acceptance"}, context.getEnvironment().getActiveProfiles())) {
            throw new IllegalStateException("BLOCKED_ENV: acceptance must be the only active profile");
        }
        // Check both instance markers before Spring can initialize application beans.
        try (var connection = DriverManager.getConnection(settings.jdbcUrl(), "vs_acceptance", settings.nonce());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT nonce FROM acceptance_instance")) {
            if (!rows.next() || !settings.nonce().equals(rows.getString(1))) {
                throw new IllegalStateException("MySQL isolation marker mismatch");
            }
        } catch (Exception e) {
            throw new IllegalStateException("BLOCKED_ENV: isolated MySQL marker unavailable", e);
        }
        var client = RedisClient.create(RedisURI.builder().withHost("127.0.0.1")
                .withPort(settings.redisPort()).withPassword(settings.nonce().toCharArray())
                .withDatabase(15).withTimeout(Duration.ofSeconds(5)).build());
        try (var connection = client.connect()) {
            if (!settings.nonce().equals(connection.sync().get("acceptance:instance"))) {
                throw new IllegalStateException("Redis isolation marker mismatch");
            }
        } catch (Exception e) {
            throw new IllegalStateException("BLOCKED_ENV: isolated Redis marker unavailable", e);
        } finally {
            client.shutdown();
        }
        try {
            var recorder = new ExternalCallRecorder();
            context.getBeanFactory().registerSingleton("externalCallRecorder", recorder);
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    recorder.close();
                }
            });
            TestPropertyValues.of(
                    "spring.datasource.url=" + settings.jdbcUrl(),
                    "spring.datasource.username=vs_acceptance",
                    "spring.datasource.password=" + settings.nonce(),
                    "spring.datasource.hikari.jdbc-url=" + settings.jdbcUrl(),
                    "spring.datasource.hikari.username=vs_acceptance",
                    "spring.datasource.hikari.password=" + settings.nonce(),
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=" + settings.redisPort(),
                    "spring.data.redis.password=" + settings.nonce(),
                    "spring.data.redis.database=15",
                    "spring.data.redis.url=redis://:" + settings.nonce() + "@127.0.0.1:" + settings.redisPort() + "/15",
                    "spring.ai.openai.base-url=" + recorder.baseUrl(),
                    "spring.ai.openai.chat.base-url=" + recorder.baseUrl(),
                    "spring.ai.dashscope.base-url=" + recorder.baseUrl(),
                    "vision-space.vision-recognition.base-url=" + recorder.baseUrl(),
                    "vision-space.aliyun-ai.create-out-painting-task=" + recorder.baseUrl() + "/out-painting",
                    "vision-space.aliyun-ai.get-out-painting-task=" + recorder.baseUrl() + "/tasks/%s"
            ).applyTo(context);
        } catch (Exception e) {
            throw new IllegalStateException("BLOCKED_ENV: external recorder unavailable", e);
        }
    }
}
