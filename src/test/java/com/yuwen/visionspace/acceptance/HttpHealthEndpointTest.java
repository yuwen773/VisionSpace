package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 健康端点契约：业务健康入口不泄露依赖细节，探活端点在依赖正常时报告 UP。
 *
 * <p>容器探活使用独立管理端口的 readiness 端点（见 Dockerfile / docker-compose.yml）；
 * 这里通过同一 HTTP 栈验证响应形状，组件级故障迁移由容器验证脚本实测。
 */
@IsolatedSpringBootTest
class HttpHealthEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void businessHealthStaysMinimalWithoutDependencyDetails() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/health")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            JsonNode body = json.readTree(response.body());
            assertEquals(0, body.path("code").asInt());
            assertEquals("ok", body.path("data").asText());
            String raw = response.body();
            assertFalse(raw.contains("jdbc"), "health response must not include datasource details");
            assertFalse(raw.toLowerCase().contains("password"), "health response must not mention credentials");
        }
    }

    @Test
    void actuatorHealthGroupReportsUpWithoutComponentLeakage() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            // acceptance 未启用独立管理端口，沿用业务端口验证端点契约本身
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/actuator/health")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String body = response.body();
            JsonNode node = json.readTree(body);
            assertEquals("UP", node.path("status").asText());
            // show-details: never —— 组件拓扑与连接串不得出现在响应里
            assertFalse(body.contains("jdbc"), "actuator health must not include datasource url");
            assertFalse(node.has("components"), "actuator health must not expose component map");
        }
    }
}
