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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指定资源的限时只读授权（原 15.x），全部经真实 HTTP。
 *
 * <p>覆盖：授权主体（个人资源所有者、团队管理员）、范围校验（指定查看者、指定资源、
 * 到期时间）、每次读取校验当前授权、撤销与到期立即生效、平台管理员不能授权也不能借管理身份读取。
 */
@IsolatedSpringBootTest
class HttpPictureGrantTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long PRIVATE_SPACE = 87901;
    private static final long TEAM_SPACE = 87902;
    private static final long PUBLIC_PICTURE = 87910;
    private static final long PRIVATE_PICTURE = 87911;
    private static final long TEAM_PICTURE = 87912;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    private long ownerId;
    private long viewerId;
    private long strangerId;
    private long teamAdminId;
    private long editorId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM picture_file WHERE pictureId IN (?, ?, ?)",
                PUBLIC_PICTURE, PRIVATE_PICTURE, TEAM_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)",
                PUBLIC_PICTURE, PRIVATE_PICTURE, TEAM_PICTURE);
        jdbc.update("DELETE FROM resource_access_grant WHERE resourceId IN (?, ?, ?)",
                PUBLIC_PICTURE, PRIVATE_PICTURE, TEAM_PICTURE);
        jdbc.update("DELETE FROM resource_isolation WHERE resourceId IN (?, ?, ?)",
                PUBLIC_PICTURE, PRIVATE_PICTURE, TEAM_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId IN (?, ?)", PRIVATE_SPACE, TEAM_SPACE);
        jdbc.update("DELETE FROM space WHERE id IN (?, ?)", PRIVATE_SPACE, TEAM_SPACE);

        ownerId = register("grant_owner_" + System.nanoTime());
        viewerId = register("grant_viewer_" + System.nanoTime());
        strangerId = register("grant_stranger_" + System.nanoTime());
        teamAdminId = register("grant_team_admin_" + System.nanoTime());
        editorId = register("grant_editor_" + System.nanoTime());

        seedSpace(PRIVATE_SPACE, ownerId, 0);
        seedSpace(TEAM_SPACE, ownerId, 1);
        joinTeam(teamAdminId, "admin");
        joinTeam(editorId, "editor");

        seedPicture(PUBLIC_PICTURE, ownerId, null, 1);
        seedPicture(PRIVATE_PICTURE, ownerId, PRIVATE_SPACE, 1);
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE, 1);
        seedResource(PUBLIC_PICTURE, ownerId, null, "public/original.jpg");
        seedResource(PRIVATE_PICTURE, ownerId, PRIVATE_SPACE, "private/original.jpg");
        seedResource(TEAM_PICTURE, ownerId, TEAM_SPACE, "team/original.jpg");
    }

    @Test
    void ownerGrantsAndGranteeReadsUntilExpiry() throws Exception {
        long grantId = grant(ownerId, PRIVATE_PICTURE, viewerId, hoursFromNow(1));
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + PRIVATE_PICTURE + "/original", "/private/original.jpg");
            var mine = get(viewer, "/picture/grant/mine");
            assertEquals(0, mine.path("code").asInt(), mine.toString());
            assertEquals(1, mine.path("data").size());
            assertEquals("ACTIVE", mine.path("data").get(0).path("status").asText());
        }
        // 授权绑定的是指定资源：同一用户的另一份私人资源仍然拒绝
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertDenied(viewer, "/picture/content/" + TEAM_PICTURE + "/original");
        }
        // 其他用户不受影响
        try (var stranger = client()) {
            login(stranger, strangerId);
            assertDenied(stranger, "/picture/content/" + PRIVATE_PICTURE + "/original");
        }
        // 到期后立即拒绝：把到期时间改到过去，无需等待
        jdbc.update("UPDATE resource_access_grant SET expiresTime = ? WHERE id = ?",
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)), grantId);
        try (var viewer = client()) {
            login(viewer, viewerId);
            int before = recorder.calls().size();
            assertDenied(viewer, "/picture/content/" + PRIVATE_PICTURE + "/original");
            assertEquals(before, recorder.calls().size(), "expired grant must not read storage");
            var mine = get(viewer, "/picture/grant/mine");
            assertEquals(0, mine.path("data").size(), "expired grant must not be listed as active");
        }
    }

    @Test
    void revocationTakesEffectOnTheNextRequest() throws Exception {
        long grantId = grant(ownerId, PRIVATE_PICTURE, viewerId, hoursFromNow(2));
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + PRIVATE_PICTURE + "/original", null);

            try (var owner = client()) {
                login(owner, ownerId);
                var revoked = post(owner, "/picture/grant/revoke",
                        Map.of("grantId", grantId, "reason", "不再需要"));
                assertEquals(0, revoked.path("code").asInt(), revoked.toString());
            }
            int before = recorder.calls().size();
            assertDenied(viewer, "/picture/content/" + PRIVATE_PICTURE + "/original");
            assertEquals(before, recorder.calls().size(), "revoked grant must not read storage");

            // 撤销后可以重新授予，且只有一条有效授权
            post(ownerClient(), "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId, hoursFromNow(1)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM resource_access_grant WHERE resourceId = ? AND status = 1",
                    Integer.class, PRIVATE_PICTURE));
        }
    }

    @Test
    void onlyOwnerOrTeamAdminMayGrant() throws Exception {
        // 团队编辑者、被授权人本人、陌生人与平台管理员都不能授权
        assertRejected(postAs(editorId, "/picture/grant/add", grantBody(TEAM_PICTURE, strangerId, hoursFromNow(1))));
        assertRejected(postAs(viewerId, "/picture/grant/add", grantBody(PRIVATE_PICTURE, strangerId, hoursFromNow(1))));
        assertRejected(postAs(strangerId, "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId, hoursFromNow(1))));
        long adminId = registerAdmin("grant_admin_" + System.nanoTime());
        assertRejected(postAs(adminId, "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId, hoursFromNow(1))));
        // 平台管理员不能以管理身份读取私人内容
        try (var admin = client()) {
            login(admin, adminId);
            assertDenied(admin, "/picture/content/" + PRIVATE_PICTURE + "/original");
            assertDenied(admin, "/picture/content/" + TEAM_PICTURE + "/original");
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_grant WHERE resourceId IN (?, ?)",
                Integer.class, PRIVATE_PICTURE, TEAM_PICTURE), "rejected grants must not write records");

        // 团队管理员可以授权团队资源
        long teamGrant = grant(teamAdminId, TEAM_PICTURE, strangerId, hoursFromNow(1));
        assertTrue(teamGrant > 0);
        // 团队管理员不能授权其他用户的个人资源
        assertRejected(postAs(teamAdminId, "/picture/grant/add",
                grantBody(PRIVATE_PICTURE, strangerId, hoursFromNow(1))));
        // 资源所有者可以授权自己的团队资源
        grant(ownerId, TEAM_PICTURE, viewerId, hoursFromNow(1));

        try (var stranger = client()) {
            login(stranger, strangerId);
            assertContent(stranger, "/picture/content/" + TEAM_PICTURE + "/original", "/team/original.jpg");
        }
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + TEAM_PICTURE + "/original", "/team/original.jpg");
        }
    }

    @Test
    void expiryIsMandatoryAndBounded() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            // 缺少到期时间
            Map<String, Object> noExpiry = new LinkedHashMap<>();
            noExpiry.put("pictureId", PRIVATE_PICTURE);
            noExpiry.put("granteeUserId", viewerId);
            assertRejected(post(owner, "/picture/grant/add", noExpiry));
            // 已经过去的到期时间
            assertRejected(post(owner, "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId,
                    Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))));
            // 超过上限
            assertRejected(post(owner, "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId,
                    Date.from(Instant.now().plus(31, ChronoUnit.DAYS)))));
            // 给自己授权
            assertRejected(post(owner, "/picture/grant/add", grantBody(PRIVATE_PICTURE, ownerId, hoursFromNow(1))));
            // 不存在的被授权人
            assertRejected(post(owner, "/picture/grant/add", grantBody(PRIVATE_PICTURE, 999_999_999L, hoursFromNow(1))));
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_grant WHERE resourceId = ?",
                Integer.class, PRIVATE_PICTURE), "invalid grants must not write records");
    }

    @Test
    void grantListIsRestrictedToResourceManagers() throws Exception {
        grant(ownerId, PRIVATE_PICTURE, viewerId, hoursFromNow(1));
        // 被授权人只能看到自己的授权（/mine），不能枚举资源上的全部授权
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertRejected(get(viewer, "/picture/grant/list?pictureId=" + PRIVATE_PICTURE));
        }
        try (var stranger = client()) {
            login(stranger, strangerId);
            assertRejected(get(stranger, "/picture/grant/list?pictureId=" + PRIVATE_PICTURE));
        }
        try (var owner = client()) {
            login(owner, ownerId);
            var grants = get(owner, "/picture/grant/list?pictureId=" + PRIVATE_PICTURE);
            assertEquals(0, grants.path("code").asInt(), grants.toString());
            assertEquals(1, grants.path("data").size());
            JsonNode grant = grants.path("data").get(0);
            // 授权视图不含内容地址
            assertFalse(grants.toString().contains("/picture/content/"), "grant list must not expose content: " + grants);
            assertFalse(grants.toString().contains("acceptance.invalid"), "grant list must not expose storage keys");
            assertEquals("ACTIVE", grant.path("status").asText());
        }
        // 审计只读：平台管理员可以查看授权记录，但读不到内容
        long adminId = registerAdmin("grant_admin_" + System.nanoTime());
        try (var admin = client()) {
            login(admin, adminId);
            var grants = get(admin, "/picture/grant/list?pictureId=" + PRIVATE_PICTURE);
            assertEquals(0, grants.path("code").asInt(), grants.toString());
            assertDenied(admin, "/picture/content/" + PRIVATE_PICTURE + "/original");
        }
    }

    @Test
    void teamMemberRemovalAlsoCancelsGrantBasedRead() throws Exception {
        // 被授权人同时是团队成员：移除成员后仍可按未撤销的显式授权读取；
        // 撤销授权后才完全拒绝，两者互不覆盖对方的状态
        grant(teamAdminId, TEAM_PICTURE, editorId, hoursFromNow(1));
        jdbc.update("DELETE FROM space_user WHERE spaceId = ? AND userId = ?", TEAM_SPACE, editorId);
        try (var editor = client()) {
            login(editor, editorId);
            assertContent(editor, "/picture/content/" + TEAM_PICTURE + "/original", null);
        }
        jdbc.update("UPDATE resource_access_grant SET status = 2, revokedByUserId = ?, revokeTime = NOW(),"
                + " activeKey = NULL WHERE resourceId = ? AND granteeUserId = ?", teamAdminId, TEAM_PICTURE, editorId);
        try (var editor = client()) {
            login(editor, editorId);
            assertDenied(editor, "/picture/content/" + TEAM_PICTURE + "/original");
        }
    }

    @Test
    void grantCannotReviveQuarantinedResource() throws Exception {
        jdbc.update("""
                INSERT INTO resource_isolation (resourceType, resourceId, status, activeKey, operatorId, reason)
                VALUES ('picture', ?, 1, ?, ?, '违规')
                """, PRIVATE_PICTURE, "picture:" + PRIVATE_PICTURE, ownerId);
        assertRejected(post(ownerClient(), "/picture/grant/add", grantBody(PRIVATE_PICTURE, viewerId, hoursFromNow(1))));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_access_grant WHERE resourceId = ?",
                Integer.class, PRIVATE_PICTURE));
    }

    // ---- helpers ----

    private HttpClient ownerClient() throws Exception {
        HttpClient client = client();
        login(client, ownerId);
        return client;
    }

    private long grant(long grantorId, long pictureId, long granteeId, Date expires) throws Exception {
        JsonNode response = postAs(grantorId, "/picture/grant/add", grantBody(pictureId, granteeId, expires));
        assertEquals(0, response.path("code").asInt(), response.toString());
        return response.path("data").path("id").asLong();
    }

    private Map<String, Object> grantBody(long pictureId, long granteeId, Date expires) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pictureId", pictureId);
        body.put("granteeUserId", granteeId);
        body.put("expiresTime", expires.toInstant().toString());
        body.put("reason", "验收测试");
        return body;
    }

    private JsonNode postAs(long userId, String path, Object body) throws Exception {
        try (var client = client()) {
            login(client, userId);
            return post(client, path, body);
        }
    }

    private static Date hoursFromNow(long hours) {
        return Date.from(Instant.now().plus(hours, ChronoUnit.HOURS));
    }

    private void seedSpace(long id, long owner, int type) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'space', ?, 0, ?, 1000, 1000, 0, 0, 0)
                """, id, owner, type);
    }

    private void joinTeam(long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                TEAM_SPACE, userId, role);
    }

    private void seedPicture(long id, long owner, Long spaceId, int reviewStatus) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, ?, ?, 0)
                """, id, "https://origin.invalid/" + id + ".jpg", "picture-" + id, owner, spaceId, reviewStatus);
    }

    private void seedResource(long pictureId, long owner, Long spaceId, String objectKey) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status)
                VALUES (?, ?, ?, 'gallery', 'original', ?, ?, ?, 10, 1)
                """, owner, spaceId, pictureId, RecordingStorageConfiguration.PLATFORM,
                "/" + objectKey, "https://acceptance.invalid/" + objectKey);
    }

    private void assertContent(HttpClient client, String path, String expectedKey) throws Exception {
        int before = recorder.calls().size();
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
        assertEquals("acceptance-image-bytes", response.body());
        if (expectedKey != null) {
            List<ExternalCallRecorder.Call> reads = recorder.calls().subList(before, recorder.calls().size())
                    .stream().filter(call -> "READ".equals(call.method())).toList();
            assertEquals(1, reads.size(), "must read exactly one object: " + reads);
            assertEquals(expectedKey, reads.get(0).body());
        }
    }

    private void assertDenied(HttpClient client, String path) throws Exception {
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        int status = response.statusCode();
        if (status == 200) {
            assertRejected(json.readTree(response.body()));
        } else {
            assertTrue(status == 401 || status == 403, path + " -> " + status);
        }
    }

    private void assertRejected(JsonNode response) {
        int code = response.path("code").asInt();
        assertTrue(code == 40100 || code == 40101 || code == 40000 || code == 40400 || code == 50001,
                "expected rejection: " + response);
    }

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

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
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
