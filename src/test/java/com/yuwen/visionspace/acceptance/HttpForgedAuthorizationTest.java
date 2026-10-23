package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.picture.PictureEditRequest;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.UserService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 伪造授权事实与业务边界复核（原 05.2）。
 *
 * <p>请求体里的 spaceUser / space / picture 对象、带 charset 的 JSON 媒体类型，
 * 以及绕过 Controller 直接调用服务，都不能换来宽权限；拒绝路径必须零副作用。
 */
@IsolatedSpringBootTest
class HttpForgedAuthorizationTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long TEAM_SPACE = 87401;
    private static final long PRIVATE_SPACE = 87402;
    private static final long TEAM_PICTURE = 87410;
    private static final long PRIVATE_PICTURE = 87411;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PictureService pictureService;

    @Autowired
    private UserService userService;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?)", TEAM_PICTURE, PRIVATE_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId IN (?, ?)", TEAM_SPACE, PRIVATE_SPACE);
        jdbc.update("DELETE FROM space WHERE id IN (?, ?)", TEAM_SPACE, PRIVATE_SPACE);
    }

    @Test
    void forgedSpaceUserObjectInBodyDoesNotGrantEdit() throws Exception {
        long ownerId = register("owner_" + System.nanoTime());
        long viewerId = register("viewer_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(viewerId, "viewer");
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE, "original");

        Map<String, Object> body = editBody(TEAM_PICTURE);
        // 伪造的成员对象：即使声明 admin 角色也不能作为授权事实
        body.put("spaceUser", Map.of("userId", viewerId, "spaceId", TEAM_SPACE, "spaceRole", "admin"));
        body.put("space", Map.of("id", TEAM_SPACE, "spaceType", 1));
        body.put("spaceRole", "admin");

        try (var viewer = client()) {
            login(viewer, viewerId);
            var response = post(viewer, "/picture/edit", body);
            assertRejected(response, "forged spaceUser must not grant edit");
        }
        assertEquals("original", storedName(TEAM_PICTURE));
    }

    @Test
    void jsonCharsetMediaTypeDoesNotGrantWidePermissions() throws Exception {
        long ownerId = register("charset_owner_" + System.nanoTime());
        long viewerId = register("charset_viewer_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(viewerId, "viewer");
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE, "original");

        try (var viewer = client()) {
            login(viewer, viewerId);
            // 旧实现遇到 charset 参数会回落成空上下文并授予管理员权限；
            // viewer 带 charset 提交仍然必须被拒绝
            var response = post(viewer, "/picture/edit", editBody(TEAM_PICTURE),
                    "application/json;charset=UTF-8");
            assertRejected(response, "charset must not upgrade a viewer to editor");
        }
        assertEquals("original", storedName(TEAM_PICTURE));

        try (var editor = client()) {
            long editorId = register("charset_editor_" + System.nanoTime());
            joinTeam(editorId, "editor");
            login(editor, editorId);
            // 反过来确认合法 JSON 参数没有被误判成表单请求
            var allowed = post(editor, "/picture/edit", editBody(TEAM_PICTURE),
                    "application/json;charset=UTF-8");
            assertEquals(0, allowed.path("code").asInt(), allowed.toString());
        }
    }

    @Test
    void directServiceCallRevalidatesPictureOwnership() throws Exception {
        long ownerId = register("direct_owner_" + System.nanoTime());
        long strangerId = register("direct_stranger_" + System.nanoTime());
        seedTeamSpace(ownerId);
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE, "original");

        User stranger = userService.getById(strangerId);
        PictureEditRequest request = new PictureEditRequest();
        request.setId(TEAM_PICTURE);
        request.setName("bypassed");

        // 没有 Controller 层的注解鉴权，服务边界必须自行拒绝
        BusinessException error = assertThrows(BusinessException.class,
                () -> pictureService.editPicture(request, stranger));
        assertEquals(40101, error.getCode());
        assertEquals("original", storedName(TEAM_PICTURE));
    }

    @Test
    void privateSpaceContentStaysUnreadableThroughTeamEndpoints() throws Exception {
        long ownerId = register("private_owner_" + System.nanoTime());
        long strangerId = register("private_stranger_" + System.nanoTime());
        seedSpace(PRIVATE_SPACE, ownerId, 0);
        seedPicture(PRIVATE_PICTURE, ownerId, PRIVATE_SPACE, "private");

        try (var stranger = client()) {
            login(stranger, strangerId);
            assertRejected(get(stranger, "/picture/get/vo?id=" + PRIVATE_PICTURE), "private detail");
            assertRejected(post(stranger, "/picture/list/page/vo",
                    body(Map.of("spaceId", PRIVATE_SPACE))), "private listing");
        }
    }

    @Test
    void adminListingDoesNotExposeContentUrls() throws Exception {
        long adminId = registerAdmin("admin_" + System.nanoTime());
        seedSpace(PRIVATE_SPACE, adminId, 0);
        seedPicture(PRIVATE_PICTURE, adminId, PRIVATE_SPACE, "private");

        try (var admin = client()) {
            login(admin, adminId);
            var response = post(admin, "/picture/list/page", body(Map.of("current", 1, "pageSize", 10)));
            assertEquals(0, response.path("code").asInt(), response.toString());
            // 必须命中夹具记录，否则空列表会让下面的断言形同虚设
            assertTrue(response.path("data").path("records").size() > 0,
                    "admin listing must contain the seeded picture: " + response);
            for (JsonNode record : response.path("data").path("records")) {
                assertTrue(record.path("url").isNull(), "admin listing must not expose content url");
                assertTrue(record.path("thumbnailUrl").isNull(), "admin listing must not expose thumbnail url");
                assertTrue(record.path("previewUrl").isNull(), "admin listing must not expose preview url");
            }
        }
    }

    // ---- fixtures ----

    private long register(String account) throws Exception {
        try (var client = client()) {
            var response = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, response.path("code").asInt(), response.toString());
            return response.path("data").asLong();
        }
    }

    private long registerAdmin(String account) throws Exception {
        long id = register(account);
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", id);
        return id;
    }

    private void seedTeamSpace(long ownerId) {
        seedSpace(TEAM_SPACE, ownerId, 1);
    }

    private void seedSpace(long id, long ownerId, int type) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'space', ?, 0, ?, 1000, 1000, 0, 0, 0)
                """, id, ownerId, type);
    }

    private void joinTeam(long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                TEAM_SPACE, userId, role);
    }

    private void seedPicture(long id, long ownerId, long spaceId, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, thumbnailUrl, previewUrl, name, userId, picSize, spaceId,
                                     reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, ?, ?, 10, ?, 1, 0)
                """, id, "https://cdn.example/original-" + id, "https://cdn.example/thumb-" + id,
                "https://cdn.example/preview-" + id, name, ownerId, spaceId);
    }

    private String storedName(long pictureId) {
        return jdbc.queryForObject("SELECT name FROM picture WHERE id = ?", String.class, pictureId);
    }

    private Map<String, Object> editBody(long pictureId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", pictureId);
        body.put("name", "edited");
        return body;
    }

    private void login(HttpClient client, long userId) throws Exception {
        String account = jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        var response = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private void assertRejected(JsonNode response, String message) {
        int code = response.path("code").asInt();
        assertTrue(code == 40100 || code == 40101, message + " -> " + response);
        assertFalse(response.path("data").isArray() && response.path("data").size() > 0,
                message + " must not return data");
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        return post(client, path, body, "application/json");
    }

    private JsonNode post(HttpClient client, String path, Object body, String contentType) throws Exception {
        String payload = body instanceof String text ? text : json.writeValueAsString(body);
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .header(CsrfToken.HEADER, CsrfToken.of(client))
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
