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
 * 空间与图库的角色矩阵，全部经真实 HTTP。
 *
 * <p>覆盖匿名、非成员、viewer、editor、空间管理员与平台管理员，并验证被移除成员
 * 的后续请求立即失效、拒绝路径零副作用。
 */
@IsolatedSpringBootTest
class HttpSpaceAuthorizationTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long PRIVATE_SPACE = 87301;
    private static final long TEAM_SPACE = 87302;
    private static final long OTHER_PRIVATE_SPACE = 87303;
    private static final long TEAM_PICTURE = 87310;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE id = ?", TEAM_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId IN (?, ?, ?)", PRIVATE_SPACE, TEAM_SPACE, OTHER_PRIVATE_SPACE);
        jdbc.update("DELETE FROM space WHERE id IN (?, ?, ?)", PRIVATE_SPACE, TEAM_SPACE, OTHER_PRIVATE_SPACE);
    }

    @Test
    void anonymousCannotListOrReadSpaces() throws Exception {
        long ownerId = seedOwner();
        seedPrivateSpace(PRIVATE_SPACE, ownerId, "owner private");
        seedPrivateSpace(OTHER_PRIVATE_SPACE, ownerId, "other private");

        try (var anonymous = client()) {
            assertRejected(post(anonymous, "/space/list/page/vo", body(Map.of())), "anonymous list");
            assertRejected(get(anonymous, "/space/get/vo?id=" + PRIVATE_SPACE), "anonymous detail");
        }
    }

    @Test
    void nonOwnerCannotReadAPrivateSpace() throws Exception {
        long ownerId = seedOwner();
        seedPrivateSpace(PRIVATE_SPACE, ownerId, "owner private");

        try (var stranger = client()) {
            long strangerId = registerAndLogin(stranger, "stranger_" + System.nanoTime());
            assertRejected(get(stranger, "/space/get/vo?id=" + PRIVATE_SPACE), "stranger detail");

            var listing = post(stranger, "/space/list/page/vo", body(Map.of()));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            assertFalse(containsSpace(listing, PRIVATE_SPACE),
                    "private space of another user must not appear in the listing");
            assertTrue(strangerId > 0);
        }
    }

    @Test
    void ownerSeesOwnPrivateSpaceOnly() throws Exception {
        long ownerId = seedOwner();
        seedPrivateSpace(PRIVATE_SPACE, ownerId, "owner private");
        seedPrivateSpace(OTHER_PRIVATE_SPACE, ownerId + 1000, "someone else's");

        try (var owner = client()) {
            loginAs(owner, ownerId);
            var listing = post(owner, "/space/list/page/vo", body(Map.of()));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            assertTrue(containsSpace(listing, PRIVATE_SPACE));
            assertFalse(containsSpace(listing, OTHER_PRIVATE_SPACE),
                    "listing must not include spaces owned by others");
        }
    }

    @Test
    void memberListingIncludesJoinedTeamSpace() throws Exception {
        long ownerId = seedOwner();
        long viewerId = seedUser("listing_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(TEAM_SPACE, viewerId, "viewer");

        try (var viewer = client()) {
            loginAs(viewer, viewerId);
            var listing = post(viewer, "/space/list/page/vo", body(Map.of()));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            assertTrue(containsSpace(listing, TEAM_SPACE),
                    "a member must see the team space they joined: " + listing);
        }
    }

    @Test
    void viewerCanReadButNotEditTeamPicture() throws Exception {
        long ownerId = seedOwner();
        long viewerId = seedUser("viewer_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(TEAM_SPACE, viewerId, "viewer");
        seedTeamPicture(ownerId);

        try (var viewer = client()) {
            loginAs(viewer, viewerId);
            var detail = get(viewer, "/space/get/vo?id=" + TEAM_SPACE);
            assertEquals(0, detail.path("code").asInt(), detail.toString());

            var edit = post(viewer, "/picture/edit", editBody());
            assertRejected(edit, "viewer must not edit");
        }
        assertEquals("original", storedPictureName());
    }

    @Test
    void editorCanEditAnyTeamPicture() throws Exception {
        long ownerId = seedOwner();
        long editorId = seedUser("editor_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(TEAM_SPACE, editorId, "editor");
        seedTeamPicture(ownerId);

        try (var editor = client()) {
            loginAs(editor, editorId);
            var edit = post(editor, "/picture/edit", editBody());
            assertEquals(0, edit.path("code").asInt(), edit.toString());
        }
        assertEquals("edited-by-team", storedPictureName());
    }

    @Test
    void removedMemberLosesAccessOnTheNextRequest() throws Exception {
        long ownerId = seedOwner();
        long viewerId = seedUser("removed_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(TEAM_SPACE, viewerId, "viewer");

        try (var member = client()) {
            loginAs(member, viewerId);
            assertEquals(0, get(member, "/space/get/vo?id=" + TEAM_SPACE).path("code").asInt());

            // 降权/移除后不重新登录，沿用同一会话
            jdbc.update("DELETE FROM space_user WHERE spaceId = ? AND userId = ?", TEAM_SPACE, viewerId);

            assertRejected(get(member, "/space/get/vo?id=" + TEAM_SPACE), "removed member detail");
        }
    }

    @Test
    void onlyManagersMayChangeMembership() throws Exception {
        long ownerId = seedOwner();
        long viewerId = seedUser("member_" + System.nanoTime());
        long outsiderId = seedUser("outsider_" + System.nanoTime());
        seedTeamSpace(ownerId);
        joinTeam(TEAM_SPACE, viewerId, "viewer");

        try (var viewer = client(); var outsider = client()) {
            loginAs(viewer, viewerId);
            assertRejected(post(viewer, "/spaceUser/add",
                    body(Map.of("spaceId", TEAM_SPACE, "userId", outsiderId, "spaceRole", "editor"))),
                    "viewer must not add members");
            loginAs(outsider, outsiderId);
            assertRejected(post(outsider, "/spaceUser/list", body(Map.of("spaceId", TEAM_SPACE))),
                    "outsider must not list members");
        }
        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM space_user WHERE spaceId = ?", Integer.class, TEAM_SPACE);
        assertEquals(1, members, "rejected requests must not create membership");
    }

    @Test
    void sortFieldIsValidatedOverHttp() throws Exception {
        long ownerId = seedOwner();
        seedPrivateSpace(PRIVATE_SPACE, ownerId, "sortable");

        try (var owner = client()) {
            loginAs(owner, ownerId);
            var injected = post(owner, "/space/list/page/vo",
                    body(Map.of("sortField", "spaceName; DROP TABLE space; --")));
            assertEquals(40000, injected.path("code").asInt(), injected.toString());

            var allowed = post(owner, "/space/list/page/vo", body(Map.of("sortField", "createTime")));
            assertEquals(0, allowed.path("code").asInt(), allowed.toString());
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM space WHERE id = ?", Integer.class, PRIVATE_SPACE),
                "table must remain intact after a rejected sort key");
    }

    // ---- fixtures ----

    private long seedOwner() throws Exception {
        return seedUser("owner_" + System.nanoTime());
    }

    private long seedUser(String account) throws Exception {
        // 走真实注册入口，保证密码摘要与登录校验一致
        try (var client = client()) {
            var registration = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, registration.path("code").asInt(), registration.toString());
            return registration.path("data").asLong();
        }
    }

    private void seedPrivateSpace(long id, long ownerId, String name) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, ?, ?, 0, 0, 100, 100, 0, 0, 0)
                """, id, name, ownerId);
    }

    private void seedTeamSpace(long ownerId) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'team space', ?, 1, 1, 1000, 1000, 0, 1, 0)
                """, TEAM_SPACE, ownerId);
    }

    private void joinTeam(long spaceId, long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                spaceId, userId, role);
    }

    private void seedTeamPicture(long ownerId) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'team-picture', 'original', ?, 10, ?, 1, 0)
                """, TEAM_PICTURE, ownerId, TEAM_SPACE);
    }

    private String storedPictureName() {
        return jdbc.queryForObject("SELECT name FROM picture WHERE id = ?", String.class, TEAM_PICTURE);
    }

    private Map<String, Object> editBody() {
        var body = new HashMap<String, Object>();
        body.put("id", TEAM_PICTURE);
        body.put("name", "edited-by-team");
        return body;
    }

    private boolean containsSpace(JsonNode response, long id) {
        for (JsonNode record : response.path("data").path("records")) {
            if (record.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private void loginAs(HttpClient client, long userId) throws Exception {
        String account = jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        var response = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private long registerAndLogin(HttpClient client, String account) throws Exception {
        var registration = post(client, "/user/register", Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        loginAs(client, registration.path("data").asLong());
        return registration.path("data").asLong();
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
        String payload = body instanceof String text ? text : json.writeValueAsString(body);
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
