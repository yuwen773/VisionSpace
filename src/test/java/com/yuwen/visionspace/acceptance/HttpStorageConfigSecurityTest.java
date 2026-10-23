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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储配置的完整 HTTP 验收：真实过滤器、请求绑定、Sa-Token、Controller 与业务服务。
 *
 * <p>覆盖匿名/非管理员拒绝、管理员只得到遮蔽信息、修改时保留未变更密钥、
 * 以及显式替换生效；拒绝路径同时确认数据库未被改写。
 */
@IsolatedSpringBootTest
class HttpStorageConfigSecurityTest {

    private static final long CONFIG_ID = 87101;
    private static final String ACCESS_KEY = "test-fixture-access-key";
    private static final String SECRET_KEY = "test-fixture-secret-key";
    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM storage_config WHERE id = ?", CONFIG_ID);
        jdbc.update("""
                INSERT INTO storage_config
                    (id, platform, platformName, endpoint, region, bucket, accessKey, secretKey,
                     domain, basePath, isActive, isDefault, orderNum, status, isDelete)
                VALUES (?, 'minio', 'acceptance minio', 'http://minio.internal:9000', 'cn-east-1',
                        'acceptance-bucket', ?, ?, 'http://cdn.internal', 'acceptance/', 0, 0, 1, 1, 0)
                """, CONFIG_ID, ACCESS_KEY, SECRET_KEY);
    }

    @Test
    void anonymousReadIsRejected() throws Exception {
        try (var anonymous = client()) {
            assertRejected(get(anonymous, "/storage/config/list"),
                    "list must require admin");
            assertRejected(post(anonymous, "/storage/config/update", updateBody(ACCESS_KEY, SECRET_KEY)),
                    "update must require admin");
        }
        assertEquals(ACCESS_KEY, storedAccessKey());
        assertEquals(SECRET_KEY, storedSecretKey());
    }

    @Test
    void nonAdminReadAndWriteAreRejectedWithoutChangingSecrets() throws Exception {
        try (var user = client()) {
            registerAndLogin(user, "storage_user_" + System.nanoTime());
            assertRejected(get(user, "/storage/config/list"), "non-admin must not list");
            assertRejected(post(user, "/storage/config/update", updateBody("test-fixture-attacker-access", "test-fixture-attacker-secret")),
                    "non-admin must not update");
        }
        assertEquals(ACCESS_KEY, storedAccessKey());
        assertEquals(SECRET_KEY, storedSecretKey());
    }

    @Test
    void everyWriteEndpointRejectsNonAdmin() throws Exception {
        try (var user = client()) {
            registerAndLogin(user, "storage_writer_" + System.nanoTime());
            assertRejected(post(user, "/storage/config/add", updateBody("AK-a", "SK-b")),
                    "add must require admin");
            assertRejected(post(user, "/storage/config/delete", Map.of("id", CONFIG_ID)),
                    "delete must require admin");
            assertRejected(post(user, "/storage/config/set_active", Map.of("id", CONFIG_ID)),
                    "set_active must require admin");
            assertRejected(post(user, "/storage/config/set_default", Map.of("id", CONFIG_ID)),
                    "set_default must require admin");
            assertRejected(post(user, "/storage/config/refresh", Map.of()),
                    "refresh must require admin");
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_config WHERE id = ?", Integer.class, CONFIG_ID));
        assertEquals(ACCESS_KEY, storedAccessKey());
    }

    @Test
    void addingConfigWithMaskedCredentialIsRejected() throws Exception {
        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = post(admin, "/storage/config/add", updateBody("******", "******"));
            assertEquals(40000, response.path("code").asInt(), response.toString());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM storage_config WHERE accessKey = '******'", Integer.class));
        }
    }

    @Test
    void adminListReturnsMaskedIdentifiersOnly() throws Exception {
        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = get(admin, "/storage/config/list");
            assertEquals(0, response.path("code").asInt(), response.toString());
            JsonNode config = response.path("data").get(0);
            assertEquals(CONFIG_ID, config.path("id").asLong());
            assertEquals("acceptance-bucket", config.path("bucket").asText());
            assertTrue(config.path("hasAccessKey").asBoolean());
            assertTrue(config.path("hasSecretKey").asBoolean());
            assertFalse(config.has("accessKey"), "raw accessKey must not be serialized");
            assertFalse(config.has("secretKey"), "raw secretKey must not be serialized");
            String body = response.toString();
            assertFalse(body.contains(ACCESS_KEY), "response leaked access key");
            assertFalse(body.contains(SECRET_KEY), "response leaked secret key");
        }
    }

    @Test
    void maskedOrEmptyCredentialsPreserveStoredSecrets() throws Exception {
        try (var admin = client()) {
            loginAsAdmin(admin);
            // 页面上未改动密钥时回传的正是遮蔽值
            var masked = post(admin, "/storage/config/update",
                    updateBody("******", "******"));
            assertEquals(0, masked.path("code").asInt(), masked.toString());
            assertEquals(ACCESS_KEY, storedAccessKey());
            assertEquals(SECRET_KEY, storedSecretKey());

            var empty = post(admin, "/storage/config/update", updateBody("", null));
            assertEquals(0, empty.path("code").asInt(), empty.toString());
            assertEquals(ACCESS_KEY, storedAccessKey());
            assertEquals(SECRET_KEY, storedSecretKey());
        }
    }

    @Test
    void explicitReplacementUpdatesOnlySubmittedCredential() throws Exception {
        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = post(admin, "/storage/config/update",
                    updateBody("******", "test-fixture-rotated-secret"));
            assertEquals(0, response.path("code").asInt(), response.toString());
            assertEquals(ACCESS_KEY, storedAccessKey(), "unchanged credential must be preserved");
            assertEquals("test-fixture-rotated-secret", storedSecretKey());
            assertFalse(response.toString().contains("test-fixture-rotated-secret"), "response must stay masked");
        }
    }

    private Map<String, Object> updateBody(String accessKey, String secretKey) {
        var body = new java.util.HashMap<String, Object>();
        body.put("id", CONFIG_ID);
        body.put("platform", "minio");
        body.put("platformName", "acceptance minio");
        body.put("endpoint", "http://minio.internal:9000");
        body.put("bucket", "acceptance-bucket");
        body.put("accessKey", accessKey);
        body.put("secretKey", secretKey);
        body.put("status", 1);
        return body;
    }

    private void loginAsAdmin(HttpClient client) throws Exception {
        long userId = registerAndLogin(client, "storage_admin_" + System.nanoTime());
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", userId);
        // 重新登录，使会话中的角色与数据库一致
        var login = json.writeValueAsString(Map.of(
                "userAccount", accountOf(userId), "userPassword", PASSWORD));
        assertEquals(0, post(client, "/user/login", login).path("code").asInt());
    }

    private String accountOf(long userId) {
        return jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
    }

    private long registerAndLogin(HttpClient client, String account) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        var registration = post(client, "/user/register", body);
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        var login = post(client, "/user/login", body);
        assertEquals(0, login.path("code").asInt(), login.toString());
        return registration.path("data").asLong();
    }

    private void assertRejected(JsonNode response, String message) {
        int code = response.path("code").asInt();
        assertTrue(code == 40100 || code == 40101, message + " -> " + response);
    }

    private String storedAccessKey() {
        return jdbc.queryForObject("SELECT accessKey FROM storage_config WHERE id = ?", String.class, CONFIG_ID);
    }

    private String storedSecretKey() {
        return jdbc.queryForObject("SELECT secretKey FROM storage_config WHERE id = ?", String.class, CONFIG_ID);
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        String payload = body instanceof String text ? text : json.writeValueAsString(body);
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build());
    }

    private JsonNode send(HttpClient client, HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
