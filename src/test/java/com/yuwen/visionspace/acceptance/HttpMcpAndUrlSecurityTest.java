package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 归属、启用状态与 URL 下载出站策略的真实 HTTP 验收。
 *
 * <p>远程 MCP 与 URL 下载目标都是本机受控替身（只监听回环），
 * 拒绝路径断言替身收到零请求、数据库零新增，不接触真实内网、元数据或第三方服务。
 */
@IsolatedSpringBootTest
class HttpMcpAndUrlSecurityTest {

    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ExternalCallRecorder recorder;

    private ControlledMcpServer mcp;
    private long ownerId;
    private long strangerId;

    @BeforeEach
    void fixture() throws Exception {
        mcp = new ControlledMcpServer();
        ownerId = register("mcp_owner_" + System.nanoTime());
        strangerId = register("mcp_stranger_" + System.nanoTime());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM mcp_server WHERE userId IN (?, ?)", ownerId, strangerId);
        mcp.close();
    }

    @Test
    void capabilitiesReflectDeploymentAndRequireLogin() throws Exception {
        // 未登录不得读到部署能力；登录后返回值与服务端拒绝规则同源
        try (var anonymous = client()) {
            var response = get(anonymous, "/mcp-server/capabilities");
            assertNotEquals(0, response.path("code").asInt(), response.toString());
        }
        try (var owner = client()) {
            login(owner, ownerId);
            var response = get(owner, "/mcp-server/capabilities");
            assertEquals(0, response.path("code").asInt(), response.toString());
            // 验收 profile 不是生产，STDIO 仍可用
            assertFalse(response.path("data").path("stdioDisabled").asBoolean(), response.toString());
        }
    }

    @Test
    void ownerDiscoversToolsFromEnabledServer() throws Exception {
        String code = seedServer(ownerId, "SSE", mcp.url(), 1);
        try (var owner = client()) {
            login(owner, ownerId);
            var detail = get(owner, "/mcp-server/" + code + "?needTools=true");
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            JsonNode tools = detail.path("data").path("tools");
            assertEquals(1, tools.size(), detail.toString());
            assertEquals("echo", tools.get(0).path("name").asText());
            assertTrue(mcp.requestCount() > 0, "owner discovery must reach the controlled server");
        }
    }

    @Test
    void anotherUserCannotPreviewOrDiscoverToolsAndNothingIsContacted() throws Exception {
        String code = seedServer(ownerId, "SSE", mcp.url(), 1);
        int before = mcp.requestCount();
        try (var stranger = client()) {
            login(stranger, strangerId);
            var detail = get(stranger, "/mcp-server/" + code + "?needTools=true");
            assertNotEquals(0, detail.path("code").asInt(), detail.toString());
            // 跨用户预览不产生任何外部连接
            assertEquals(before, mcp.requestCount(), "foreign preview must not contact the server");
        }
    }

    @Test
    void disabledServerStopsDiscoveryAndCacheDoesNotResurrectIt() throws Exception {
        String code = seedServer(ownerId, "SSE", mcp.url(), 1);
        try (var owner = client()) {
            login(owner, ownerId);
            // 先建立一次缓存，确认命中路径也受启用状态约束
            assertEquals(0, get(owner, "/mcp-server/" + code + "?needTools=true").path("code").asInt());
            String cacheKey = "mcp:tools:" + ownerId + ":" + code;
            assertTrue(Boolean.TRUE.equals(redis.hasKey(cacheKey)), "tools must be cached");

            jdbc.update("UPDATE mcp_server SET status = 0 WHERE mcpServerCode = ?", code);
            int before = mcp.requestCount();

            var afterDisable = get(owner, "/mcp-server/" + code + "?needTools=true");
            assertEquals(0, afterDisable.path("code").asInt(), afterDisable.toString());
            assertEquals(0, afterDisable.path("data").path("tools").size(),
                    "disabled server must not return cached tools");
            assertEquals(before, mcp.requestCount(), "disabled server must not be contacted");
        }
    }

    @Test
    void toolsCacheIsIsolatedPerOwner() throws Exception {
        String ownerCode = seedServer(ownerId, "SSE", mcp.url(), 1);
        // 同一 code 属于另一用户时不可见；缓存键带归属，不会串用
        jdbc.update("""
                INSERT INTO mcp_server (mcpServerCode, userId, name, deployConfig, status, installType, host)
                VALUES (?, ?, 'shared-code', ?, 1, 'SSE', ?)
                """, ownerCode, strangerId, deployConfig(mcp.url()), mcp.url());

        try (var owner = client(); var stranger = client()) {
            login(owner, ownerId);
            login(stranger, strangerId);

            assertEquals(0, get(owner, "/mcp-server/" + ownerCode + "?needTools=true").path("code").asInt());
            assertEquals(0, get(stranger, "/mcp-server/" + ownerCode + "?needTools=true").path("code").asInt());

            assertTrue(Boolean.TRUE.equals(redis.hasKey("mcp:tools:" + ownerId + ":" + ownerCode)));
            assertTrue(Boolean.TRUE.equals(redis.hasKey("mcp:tools:" + strangerId + ":" + ownerCode)),
                    "each owner resolves tools under its own cache key");
        }
    }

    @Test
    void savingRestrictedRemoteTargetIsRejectedAndNothingPersists() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            var response = post(owner, "/mcp-server", Map.of(
                    "name", "metadata",
                    "deployConfig", deployConfig("http://169.254.169.254/sse"),
                    "installType", "SSE"));
            assertEquals(40000, response.path("code").asInt(), response.toString());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mcp_server WHERE userId = ?", Integer.class, ownerId),
                    "rejected config must not be persisted");
        }
    }

    @Test
    void urlUploadRejectsRestrictedTargetWithoutSideEffects() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            var response = post(owner, "/picture/upload/url",
                    Map.of("fileUrl", "http://169.254.169.254/latest/meta-data/"));
            assertEquals(40000, response.path("code").asInt(), response.toString());
            assertEquals(before, recorder.calls().size(), "rejected download must not touch storage");
        }
    }

    @Test
    void urlUploadFromControlledTargetStillWorks() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            var response = post(owner, "/picture/upload/url", Map.of("fileUrl", mcpImageUrl()));
            assertEquals(0, response.path("code").asInt(), response.toString());
            long pictureId = response.path("data").path("id").asLong();
            assertTrue(pictureId > 0);

            jdbc.update("DELETE FROM picture_file WHERE pictureId = ?", pictureId);
            jdbc.update("DELETE FROM picture WHERE id = ?", pictureId);
        }
    }

    // ---- fixtures ----

    private String mcpImageUrl() {
        return "http://127.0.0.1:" + mcp.port() + "/photo.png";
    }

    private String seedServer(long userId, String installType, String url, int status) {
        String code = "mcp-" + userId + "-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO mcp_server (mcpServerCode, userId, name, deployConfig, status, installType, host)
                VALUES (?, ?, 'controlled', ?, ?, ?, ?)
                """, code, userId, deployConfig(url), status, installType, url);
        return code;
    }

    private String deployConfig(String url) {
        return "{\"mcpServers\":{\"x\":{\"type\":\"sse\",\"url\":\"" + url + "\"}}}";
    }

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
        var response = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path)).GET().build()).body());
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> body) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
