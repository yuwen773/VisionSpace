package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real TCP -> servlet filter -> JSON binding -> Sa-Token -> controller/service -> MySQL/Redis. */
@IsolatedSpringBootTest
class HttpBaselineTest {
    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ExternalCallRecorder recorder;
    @Autowired
    private StringRedisTemplate redis;

    private static final long PICTURE_ID = 87001;
    private static final long SPACE_ID = 87002;
    private static final String PASSWORD = "acceptance-password";

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE id = ?", PICTURE_ID);
        jdbc.update("DELETE FROM space WHERE id = ?", SPACE_ID);
        jdbc.update("INSERT INTO space (id, spaceName, userId, totalSize, totalCount) VALUES (?, 'unrelated', 87003, 999, 7)", SPACE_ID);
    }

    @Test
    void runtimeConnectionsUseTheVerifiedInstances() {
        var settings = IsolationSettings.from(System.getenv());
        assertEquals(settings.database(), jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(settings.nonce(), jdbc.queryForObject("SELECT nonce FROM acceptance_instance", String.class));
        assertEquals(settings.nonce(), redis.opsForValue().get("acceptance:instance"));
    }

    @Test
    void publicDeletionUsesCookieIdentityAndDoesNotChangeSpaceQuota() throws Exception {
        try (var owner = client()) {
            long ownerId = registerAndLogin(owner, "owner_" + System.nanoTime());
            seedPicture(ownerId);
            int beforeCalls = recorder.calls().size();

            assertEquals(0, post(owner, "/picture/delete", "{\"id\":" + PICTURE_ID + "}").path("code").asInt());
            assertEquals(1, jdbc.queryForObject("SELECT isDelete FROM picture WHERE id = ?", Integer.class, PICTURE_ID));
            assertQuotaUnchanged();
            assertEquals(List.of(new ExternalCallRecorder.Call("POST", "/storage/delete", "acceptance-picture")),
                    recorder.calls().subList(beforeCalls, recorder.calls().size()));
        }
    }

    @Test
    void anonymousDeletionHasNoBusinessOrExternalSideEffects() throws Exception {
        seedPicture(87003);
        int beforeCalls = recorder.calls().size();
        try (var anonymous = client()) {
            assertEquals(40100, post(anonymous, "/picture/delete", "{\"id\":" + PICTURE_ID + "}").path("code").asInt());
        }
        assertRejectedWithoutSideEffects(beforeCalls);
    }

    @Test
    void anotherLoggedInUserCannotDeletePublicAuthorsPicture() throws Exception {
        seedPicture(87003);
        try (var other = client()) {
            registerAndLogin(other, "other_" + System.nanoTime());
            int beforeCalls = recorder.calls().size();
            assertEquals(40101, post(other, "/picture/delete", "{\"id\":" + PICTURE_ID + "}").path("code").asInt());
            assertRejectedWithoutSideEffects(beforeCalls);
        }
    }

    @Test
    void loginAndAnonymousBusinessReadsGoThroughHttpAndRedisSession() throws Exception {
        try (var anonymous = client(); var loggedIn = client()) {
            assertEquals(40100, get(anonymous, "/user/picture/recent?count=1").path("code").asInt());
            long userId = registerAndLogin(loggedIn, "reader_" + System.nanoTime());
            seedPicture(userId);
            var response = get(loggedIn, "/user/picture/recent?count=1");
            assertEquals(0, response.path("code").asInt());
            assertEquals(1, response.path("data").size());
            assertEquals(Long.toString(PICTURE_ID), response.path("data").get(0).path("id").asText());
        }
    }

    private void seedPicture(long ownerId) {
        jdbc.update("INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus) VALUES (?, 'acceptance-picture', 'public', ?, 123, NULL, 1)", PICTURE_ID, ownerId);
    }

    private void assertRejectedWithoutSideEffects(int beforeCalls) {
        assertEquals(0, jdbc.queryForObject("SELECT isDelete FROM picture WHERE id = ?", Integer.class, PICTURE_ID));
        assertQuotaUnchanged();
        assertEquals(beforeCalls, recorder.calls().size(), "Rejected requests must not call storage or providers");
    }

    private void assertQuotaUnchanged() {
        assertEquals(999L, jdbc.queryForObject("SELECT totalSize FROM space WHERE id = ?", Long.class, SPACE_ID));
        assertEquals(7L, jdbc.queryForObject("SELECT totalCount FROM space WHERE id = ?", Long.class, SPACE_ID));
    }

    private long registerAndLogin(HttpClient client, String account) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        var registration = post(client, "/user/register", body);
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        var login = post(client, "/user/login", body);
        assertEquals(0, login.path("code").asInt(), login.toString());
        assertFalse(client.cookieHandler().isEmpty());
        assertTrue(((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore().getCookies().size() >= 2,
                "Spring Session and Sa-Token cookies must be issued");
        return registration.path("data").asLong();
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, String body) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
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
