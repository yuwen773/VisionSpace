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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开图库的匿名与私有边界（原 05.1）。
 *
 * <p>已审核的公开图片允许匿名浏览；未审核内容、空间图片与修改操作仍按角色矩阵拒绝。
 * 分页同时验证公开查询只返回已审核的公共图片。
 */
@IsolatedSpringBootTest
class HttpPublicGalleryTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long PASSED_PICTURE = 87610;
    private static final long PENDING_PICTURE = 87611;
    private static final long PRIVATE_SPACE = 87601;
    private static final long PRIVATE_PICTURE = 87612;

    @LocalServerPort
    private int port;

    /** 公共图片作者的 id，供"作者可读自己未审核图片"用例登录使用 */
    private long authorId;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)",
                PASSED_PICTURE, PENDING_PICTURE, PRIVATE_PICTURE);
        jdbc.update("DELETE FROM space WHERE id = ?", PRIVATE_SPACE);
        long ownerId = register("gallery_owner_" + System.nanoTime());
        authorId = ownerId;
        seedPublicPicture(PASSED_PICTURE, "passed", 1, ownerId);
        seedPublicPicture(PENDING_PICTURE, "pending", 0, ownerId);
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'private', ?, 0, 0, 100, 100, 0, 1, 0)
                """, PRIVATE_SPACE, ownerId);
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'private-picture', 'private', ?, 10, ?, 1, 0)
                """, PRIVATE_PICTURE, ownerId, PRIVATE_SPACE);
    }

    @Test
    void anonymousCanReadApprovedPublicPictureAndListing() throws Exception {
        try (var anonymous = client()) {
            var detail = get(anonymous, "/picture/get/vo?id=" + PASSED_PICTURE);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            assertEquals("passed", detail.path("data").path("name").asText());
            assertTrue(detail.path("data").path("permissionList").isArray());

            var listing = post(anonymous, "/picture/list/page/vo",
                    body(Map.of("current", 1, "pageSize", 10)));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            List<Long> ids = ids(listing);
            assertTrue(ids.contains(PASSED_PICTURE), "approved public picture must be listed: " + ids);
            assertFalse(ids.contains(PENDING_PICTURE), "unreviewed public picture must not be listed");
            assertFalse(ids.contains(PRIVATE_PICTURE), "space picture must not be listed");
        }
    }

    @Test
    void anonymousCannotReadUnreviewedOrPrivatePicture() throws Exception {
        try (var anonymous = client()) {
            assertRejected(get(anonymous, "/picture/get/vo?id=" + PENDING_PICTURE), "unreviewed detail");
            assertRejected(get(anonymous, "/picture/get/vo?id=" + PRIVATE_PICTURE), "private detail");
        }
    }

    @Test
    void authorCanReadOwnUnreviewedPicture() throws Exception {
        try (var author = client()) {
            login(author, authorId);
            var detail = get(author, "/picture/get/vo?id=" + PENDING_PICTURE);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
        }
    }

    @Test
    void publicListingRespectsPagingBounds() throws Exception {
        try (var anonymous = client()) {
            var oversized = post(anonymous, "/picture/list/page/vo",
                    body(Map.of("current", 1, "pageSize", 31)));
            assertEquals(40000, oversized.path("code").asInt(), "page size must stay bounded");

            var paged = post(anonymous, "/picture/list/page/vo",
                    body(Map.of("current", 1, "pageSize", 1, "sortField", "createTime")));
            assertEquals(0, paged.path("code").asInt(), paged.toString());
            assertEquals(1, paged.path("data").path("records").size());
        }
    }

    // ---- fixtures ----

    private void seedPublicPicture(long id, String name, int reviewStatus, long ownerId) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, NULL, ?, 0)
                """, id, "public-" + id, name, ownerId, reviewStatus);
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

    private List<Long> ids(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode record : response.path("data").path("records")) {
            ids.add(record.path("id").asLong());
        }
        return ids;
    }

    private void assertRejected(JsonNode response, String message) {
        int code = response.path("code").asInt();
        assertTrue(code == 40100 || code == 40101, message + " -> " + response);
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        String payload = json.writeValueAsString(body);
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build());
    }

    private Map<String, Object> body(Map<String, Object> values) {
        return new HashMap<>(values);
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
