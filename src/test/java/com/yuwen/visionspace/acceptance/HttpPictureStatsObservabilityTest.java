package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行为上报与聚合的可观测信息（原 19.4 / 20.4），经真实 HTTP。
 *
 * <p>待处理积压、上报结果与限流拒绝都必须能从 HTTP 入口看到，且只含数量：
 * 不含用户、来源地址、图片或事件内容。
 */
@IsolatedSpringBootTest
class HttpPictureStatsObservabilityTest {

    private static final String PASSWORD = "acceptance-password";

    private static final long PICTURE = 88_301L;
    private static final long OWNER = 88_300L;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM user_picture_action WHERE pictureId = ?", PICTURE);
        jdbc.update("DELETE FROM picture_stats WHERE pictureId = ?", PICTURE);
        jdbc.update("DELETE FROM picture WHERE id = ?", PICTURE);
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, 'observable', ?, 10, NULL, 1, 0)
                """, PICTURE, "content-" + PICTURE, OWNER);

        adminId = register("stats_admin_" + System.nanoTime());
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", adminId);
    }

    @Test
    void theBacklogIsVisibleToAdministratorsOverHttp() throws Exception {
        // 一个待处理事件：上报成功但还没聚合
        try (var anonymous = client()) {
            var response = post(anonymous, "/picture/action/report", Map.of(
                    "pictureId", PICTURE, "actionType", 2, "source", 1, "eventId", "observable-event"));
            assertEquals(0, response.path("code").asInt(), response.toString());
        }

        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = post(admin, "/admin/stats/picture-stats/backlog", Map.of());

            assertEquals(0, response.path("code").asInt(), response.toString());
            JsonNode backlog = response.path("data");
            assertTrue(backlog.path("pendingCount").asLong() >= 1,
                    "the pending backlog must be visible: " + backlog);
            assertTrue(backlog.has("accumulatedPendingCount"), "the backlog shape must be stable: " + backlog);
            assertTrue(backlog.has("stuckCount"), "the backlog shape must be stable: " + backlog);
            assertTrue(backlog.has("orphanCount"), "the backlog shape must be stable: " + backlog);

            // 脱敏：只含数量，不含事件、图片或用户信息
            String body = response.toString();
            assertFalse(body.contains("observable-event"), "the backlog leaked an event id: " + body);
            assertFalse(body.contains(String.valueOf(PICTURE)), "the backlog leaked a picture id: " + body);
        }
    }

    @Test
    void theReportMetricsAreVisibleToAdministratorsOverHttp() throws Exception {
        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = post(admin, "/admin/stats/picture-action/metrics", Map.of());

            assertEquals(0, response.path("code").asInt(), response.toString());
            JsonNode metrics = response.path("data");
            assertTrue(metrics.has("acceptedCount"), "the metrics shape must be stable: " + metrics);
            assertTrue(metrics.has("duplicateCount"), "the metrics shape must be stable: " + metrics);
            assertTrue(metrics.has("rejectedCount"), "the metrics shape must be stable: " + metrics);
            assertTrue(metrics.has("rateLimitedCount"), "the metrics shape must be stable: " + metrics);
        }
    }

    @Test
    void theObservabilityEntriesAreNotOpenToAnonymousOrNormalUsers() throws Exception {
        try (var anonymous = client()) {
            assertEquals(40100, post(anonymous, "/admin/stats/picture-stats/backlog", Map.of())
                    .path("code").asInt(), "anonymous must not read the backlog");
            assertEquals(40100, post(anonymous, "/admin/stats/picture-action/metrics", Map.of())
                    .path("code").asInt(), "anonymous must not read the report metrics");
        }

        long normalUser = register("stats_user_" + System.nanoTime());
        try (var normal = client()) {
            login(normal, normalUser);
            assertEquals(40101, post(normal, "/admin/stats/picture-stats/backlog", Map.of())
                    .path("code").asInt(), "a normal user must not read the backlog");
            assertEquals(40101, post(normal, "/admin/stats/picture-action/metrics", Map.of())
                    .path("code").asInt(), "a normal user must not read the report metrics");
        }
    }

    // ---- helpers ----

    private long register(String account) throws Exception {
        try (var client = client()) {
            var response = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, response.path("code").asInt(), response.toString());
            return response.path("data").asLong();
        }
    }

    private void login(HttpClient client, long userId) throws Exception {
        String account = jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        var response = post(client, "/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private void loginAsAdmin(HttpClient client) throws Exception {
        login(client, adminId);
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> payload) throws Exception {
        Map<String, Object> body = new HashMap<>(payload);
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
