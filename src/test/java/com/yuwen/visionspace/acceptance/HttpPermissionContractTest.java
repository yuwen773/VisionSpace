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
 * 权限返回值与实际行为一致（原 05.5）。
 *
 * <p>详情接口返回的 permissionList 必须能预测同一角色对写接口的结果：
 * 提示可编辑就必须真的能编辑，提示不可编辑就必须被拒绝。拒绝请求不产生任何数据变化。
 */
@IsolatedSpringBootTest
class HttpPermissionContractTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long TEAM_SPACE = 87501;
    private static final long TEAM_PICTURE = 87510;

    private static final String PICTURE_EDIT = "picture:edit";
    private static final String PICTURE_DELETE = "picture:delete";
    private static final String PICTURE_UPLOAD = "picture:upload";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE id = ?", TEAM_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId = ?", TEAM_SPACE);
        jdbc.update("DELETE FROM space WHERE id = ?", TEAM_SPACE);
    }

    @Test
    void permissionListPredictsEditAndDeleteOutcome() throws Exception {
        long ownerId = register("contract_owner_" + System.nanoTime());
        long viewerId = register("contract_viewer_" + System.nanoTime());
        long editorId = register("contract_editor_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(viewerId, "viewer");
        joinTeam(editorId, "editor");
        seedPicture(ownerId, "original");

        for (RoleCase roleCase : List.of(
                new RoleCase("viewer", viewerId, false),
                new RoleCase("editor", editorId, true))) {
            seedPicture(ownerId, "original");
            try (var client = client()) {
                login(client, roleCase.userId());
                List<String> permissions = picturePermissions(client);
                assertEquals(roleCase.canEdit(), permissions.contains(PICTURE_EDIT),
                        roleCase.role() + " permission list mismatch: " + permissions);
                assertEquals(roleCase.canEdit(), permissions.contains(PICTURE_DELETE),
                        roleCase.role() + " delete permission mismatch: " + permissions);

                var edit = post(client, "/picture/edit", editBody("edited"));
                assertEquals(roleCase.canEdit() ? 0 : 40101, edit.path("code").asInt(),
                        roleCase.role() + " edit outcome must match permission list: " + edit);
            }
            assertEquals(roleCase.canEdit() ? "edited" : "original", storedName(),
                    roleCase.role() + " must leave storage unchanged when rejected");
        }
    }

    @Test
    void viewerCannotDeleteAndPictureSurvivesRejection() throws Exception {
        long ownerId = register("delete_owner_" + System.nanoTime());
        long viewerId = register("delete_viewer_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(viewerId, "viewer");
        seedPicture(ownerId, "original");

        try (var viewer = client()) {
            login(viewer, viewerId);
            var response = post(viewer, "/picture/delete", body(Map.of("id", TEAM_PICTURE)));
            int code = response.path("code").asInt();
            assertTrue(code == 40100 || code == 40101, response.toString());
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE id = ?", Integer.class, TEAM_PICTURE),
                "rejected delete must not remove the picture");
    }

    @Test
    void viewerWithoutUploadPermissionIsRejectedBeforeAnyStorageWork() throws Exception {
        long ownerId = register("upload_owner_" + System.nanoTime());
        long viewerId = register("upload_viewer_" + System.nanoTime());
        long editorId = register("upload_editor_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(viewerId, "viewer");
        joinTeam(editorId, "editor");

        Integer countBefore = jdbc.queryForObject(
                "SELECT totalCount FROM space WHERE id = ?", Integer.class, TEAM_SPACE);
        try (var viewer = client()) {
            login(viewer, viewerId);
            List<String> permissions = spacePermissions(viewer);
            assertFalse(permissions.contains(PICTURE_UPLOAD), "viewer must not be promised upload");

            // 与提示一致：实际请求在进入对象存储前就被拒绝
            var response = post(viewer, "/picture/upload/url",
                    Map.of("spaceId", TEAM_SPACE, "fileUrl", "https://cdn.example/not-fetched"));
            int code = response.path("code").asInt();
            assertTrue(code == 40100 || code == 40101, "viewer upload must be rejected: " + response);
        }
        assertEquals(countBefore, jdbc.queryForObject(
                "SELECT totalCount FROM space WHERE id = ?", Integer.class, TEAM_SPACE),
                "rejected upload must not consume space quota");
    }

    private record RoleCase(String role, long userId, boolean canEdit) {
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

    private void seedTeamSpace(long ownerId) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'team space', ?, 0, 1, 1000, 1000, 0, 1, 0)
                """, TEAM_SPACE, ownerId);
    }

    private void joinTeam(long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                TEAM_SPACE, userId, role);
    }

    private void seedPicture(long ownerId, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'team-picture', ?, ?, 10, ?, 1, 0)
                ON DUPLICATE KEY UPDATE name = VALUES(name), userId = VALUES(userId)
                """, TEAM_PICTURE, name, ownerId, TEAM_SPACE);
    }

    private String storedName() {
        return jdbc.queryForObject("SELECT name FROM picture WHERE id = ?", String.class, TEAM_PICTURE);
    }

    private List<String> picturePermissions(HttpClient client) throws Exception {
        return permissionList(get(client, "/picture/get/vo?id=" + TEAM_PICTURE));
    }

    private List<String> spacePermissions(HttpClient client) throws Exception {
        return permissionList(get(client, "/space/get/vo?id=" + TEAM_SPACE));
    }

    private List<String> permissionList(JsonNode response) {
        assertEquals(0, response.path("code").asInt(), response.toString());
        List<String> permissions = new ArrayList<>();
        response.path("data").path("permissionList").forEach(node -> permissions.add(node.asText()));
        return permissions;
    }

    private Map<String, Object> editBody(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", TEAM_PICTURE);
        body.put("name", name);
        return body;
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
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        String payload = json.writeValueAsString(body);
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
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
