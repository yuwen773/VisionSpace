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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 违规资源隔离、恢复与审计（原 16.x），全部经真实 HTTP。
 *
 * <p>覆盖：独立处置权限（平台管理员与所有者都不自动获得）；隔离目标、操作者与理由校验；
 * 隔离后公开与私人访问均拒绝且内容保留；恢复后按原有归属规则生效、不扩大访问范围；
 * 后台只返回元数据，不返回内容直链。
 */
@IsolatedSpringBootTest
class HttpResourceQuarantineTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long TEAM_SPACE = 88001;
    private static final long PUBLIC_PICTURE = 88010;
    private static final long TEAM_PICTURE = 88011;
    private static final long GRANTED_PICTURE = 88012;

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
    private long memberId;
    private long moderatorId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM picture_file WHERE pictureId IN (?, ?, ?)",
                PUBLIC_PICTURE, TEAM_PICTURE, GRANTED_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)",
                PUBLIC_PICTURE, TEAM_PICTURE, GRANTED_PICTURE);
        jdbc.update("DELETE FROM resource_access_grant WHERE resourceId IN (?, ?, ?)",
                PUBLIC_PICTURE, TEAM_PICTURE, GRANTED_PICTURE);
        jdbc.update("DELETE FROM resource_isolation WHERE resourceId IN (?, ?, ?)",
                PUBLIC_PICTURE, TEAM_PICTURE, GRANTED_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId = ?", TEAM_SPACE);
        jdbc.update("DELETE FROM space WHERE id = ?", TEAM_SPACE);

        ownerId = register("quarantine_owner_" + System.nanoTime());
        viewerId = register("quarantine_viewer_" + System.nanoTime());
        memberId = register("quarantine_member_" + System.nanoTime());
        moderatorId = registerModerator("quarantine_moderator_" + System.nanoTime());

        seedTeamSpace();
        joinTeam(memberId, "viewer");

        seedPicture(PUBLIC_PICTURE, ownerId, null);
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE);
        seedPicture(GRANTED_PICTURE, ownerId, TEAM_SPACE);
        seedResource(PUBLIC_PICTURE, ownerId, null, "public/original.jpg");
        seedResource(TEAM_PICTURE, ownerId, TEAM_SPACE, "team/original.jpg");
        seedResource(GRANTED_PICTURE, ownerId, TEAM_SPACE, "granted/original.jpg");
        seedGrant(GRANTED_PICTURE, viewerId);
    }

    @Test
    void quarantineRequiresIndependentModerationPermission() throws Exception {
        // 平台管理员、资源所有者、团队成员、被授权人都不具备处置权限
        long adminId = registerAdmin("quarantine_admin_" + System.nanoTime());
        for (long userId : new long[]{adminId, ownerId, memberId, viewerId}) {
            JsonNode response = postAs(userId, "/moderation/quarantine", moderationBody(PUBLIC_PICTURE, "举报"));
            assertRejected(response);
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_isolation WHERE resourceId = ?", Integer.class, PUBLIC_PICTURE),
                "rejected quarantine must not write records");
        // 原因必填、目标必须存在
        assertRejected(postAs(moderatorId, "/moderation/quarantine", moderationBody(PUBLIC_PICTURE, "")));
        assertRejected(postAs(moderatorId, "/moderation/quarantine", moderationBody(999_999_999L, "举报")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_isolation WHERE resourceId IN (?, ?)",
                Integer.class, PUBLIC_PICTURE, 999_999_999L));
        // 管理员也不能查看隔离列表
        try (var admin = client()) {
            login(admin, adminId);
            assertRejected(get(admin, "/moderation/list"));
        }
    }

    @Test
    void quarantinedPublicContentIsDeniedEverywhereButKept() throws Exception {
        // 隔离前公开内容可匿名读取
        try (var anonymous = client()) {
            assertContent(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/original");
        }
        quarantine(PUBLIC_PICTURE);

        try (var anonymous = client()) {
            int before = recorder.calls().size();
            assertDenied(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/original");
            assertDenied(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/thumbnail");
            assertEquals(before, recorder.calls().size(), "quarantined content must not be read");
            // 公开列表不再包含隔离内容
            var listing = post(anonymous, "/picture/list/page/vo", Map.of("current", 1, "pageSize", 30));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            for (JsonNode record : listing.path("data").path("records")) {
                assertFalse(record.path("id").asLong() == PUBLIC_PICTURE,
                        "public listing must exclude quarantined pictures: " + record);
            }
            // 推荐流同样不包含
            var recommend = get(anonymous, "/picture/recommend/list?type=latest&page=1&size=30");
            for (JsonNode record : recommend.path("data").path("records")) {
                assertFalse(record.path("id").asLong() == PUBLIC_PICTURE,
                        "recommendation must exclude quarantined pictures: " + record);
            }
        }
        // 作者本人也不能读取内容，详情按不存在处理
        try (var owner = client()) {
            login(owner, ownerId);
            assertRejected(get(owner, "/picture/get/vo?id=" + PUBLIC_PICTURE));
            assertDenied(owner, "/picture/content/" + PUBLIC_PICTURE + "/original");
        }
        // 内容与记录保留，隔离记录可审计
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE id = ?", Integer.class, PUBLIC_PICTURE));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND status = 1",
                Integer.class, PUBLIC_PICTURE));
        Map<String, Object> isolation = jdbc.queryForMap(
                "SELECT operatorId, reason, status FROM resource_isolation WHERE resourceId = ?", PUBLIC_PICTURE);
        assertEquals(moderatorId, ((Number) isolation.get("operatorId")).longValue());
        assertEquals("举报", isolation.get("reason"));
        assertEquals(1, ((Number) isolation.get("status")).intValue());
    }

    @Test
    void quarantinedPrivateContentIsDeniedIncludingExplicitGrants() throws Exception {
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + GRANTED_PICTURE + "/original");
        }
        quarantine(GRANTED_PICTURE);

        // 显式授权也不能绕过隔离
        try (var viewer = client()) {
            login(viewer, viewerId);
            int before = recorder.calls().size();
            assertDenied(viewer, "/picture/content/" + GRANTED_PICTURE + "/original");
            assertEquals(before, recorder.calls().size());
            assertRejected(get(viewer, "/picture/grant/list?pictureId=" + GRANTED_PICTURE));
        }
        // 空间成员仍能看到记录，但视图清空内容地址并标记隔离状态
        try (var member = client()) {
            login(member, memberId);
            var listing = post(member, "/picture/list/page/vo",
                    Map.of("current", 1, "pageSize", 30, "spaceId", TEAM_SPACE));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            JsonNode record = findByPictureId(listing.path("data").path("records"), GRANTED_PICTURE);
            assertTrue(record != null, "members keep the metadata record: " + listing);
            assertTrue(record.path("url").isNull(), "quarantined picture must not expose url: " + record);
            assertTrue(record.path("quarantined").asBoolean(), "quarantined flag must be set: " + record);
        }
        // 团队成员读取原图同样拒绝
        try (var member = client()) {
            login(member, memberId);
            assertDenied(member, "/picture/content/" + GRANTED_PICTURE + "/original");
        }
        // 隔离中的资源不能被删除，避免绕过恢复与审计
        try (var owner = client()) {
            login(owner, ownerId);
            var deleted = post(owner, "/picture/delete", Map.of("id", GRANTED_PICTURE));
            assertRejected(deleted);
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE id = ?", Integer.class, GRANTED_PICTURE),
                "quarantined picture must be kept");
    }

    @Test
    void releaseRestoresOriginalAccessRulesWithoutWidening() throws Exception {
        quarantine(GRANTED_PICTURE);
        // 恢复后：显式授权仍在有效期内，被授权人恢复读取
        try (var moderator = client()) {
            login(moderator, moderatorId);
            var released = post(moderator, "/moderation/release",
                    moderationBody(GRANTED_PICTURE, "复核通过"));
            assertEquals(0, released.path("code").asInt(), released.toString());
            assertEquals("RELEASED", released.path("data").path("status").asText());
        }
        try (var viewer = client()) {
            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + GRANTED_PICTURE + "/original");
        }
        // 不在团队、也没有授权的人仍然拒绝：恢复不扩大访问范围
        try (var stranger = client()) {
            login(stranger, register("quarantine_stranger_" + System.nanoTime()));
            assertDenied(stranger, "/picture/content/" + GRANTED_PICTURE + "/original");
        }
        // 公开图片恢复后重新允许匿名读取
        quarantine(PUBLIC_PICTURE);
        try (var moderator = client()) {
            login(moderator, moderatorId);
            assertEquals(0, post(moderator, "/moderation/release",
                    moderationBody(PUBLIC_PICTURE, "误报")).path("code").asInt());
        }
        try (var anonymous = client()) {
            assertContent(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/original");
        }
        // 恢复记录保留操作者、理由与时间
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT status, operatorId, releaseOperatorId, reason, releaseReason, releaseTime"
                        + " FROM resource_isolation WHERE resourceId = ? ORDER BY id", PUBLIC_PICTURE);
        assertEquals(1, history.size());
        assertEquals(2, ((Number) history.get(0).get("status")).intValue());
        assertEquals(moderatorId, ((Number) history.get(0).get("operatorId")).longValue());
        assertEquals(moderatorId, ((Number) history.get(0).get("releaseOperatorId")).longValue());
        assertEquals("误报", history.get(0).get("releaseReason"));
        assertTrue(history.get(0).get("releaseTime") != null);
    }

    @Test
    void releaseRequiresActiveQuarantineAndReason() throws Exception {
        assertRejected(postAs(moderatorId, "/moderation/release", moderationBody(TEAM_PICTURE, "复核")));
        quarantine(TEAM_PICTURE);
        assertRejected(postAs(moderatorId, "/moderation/release", moderationBody(TEAM_PICTURE, " ")));
        // 重复隔离同样拒绝，不产生第二条有效记录
        assertRejected(postAs(moderatorId, "/moderation/quarantine", moderationBody(TEAM_PICTURE, "再次")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resource_isolation WHERE resourceId = ? AND status = 1",
                Integer.class, TEAM_PICTURE));
    }

    @Test
    void moderationViewsExposeMetadataOnly() throws Exception {
        quarantine(TEAM_PICTURE);
        try (var moderator = client()) {
            login(moderator, moderatorId);
            var listing = get(moderator, "/moderation/list");
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            String payload = listing.toString();
            assertFalse(payload.contains("/picture/content/"), "moderation list must not expose content: " + payload);
            assertFalse(payload.contains("acceptance.invalid"), "moderation list must not expose storage: " + payload);
            assertTrue(payload.contains("举报"), "reason must be recorded: " + payload);
            JsonNode record = findByResourceId(listing.path("data"), TEAM_PICTURE);
            assertTrue(record != null, "quarantined resource must be listed: " + listing);
            assertEquals(moderatorId, record.path("operatorId").asLong());

            var history = get(moderator, "/moderation/history?resourceType=picture&resourceId=" + TEAM_PICTURE);
            assertEquals(0, history.path("code").asInt(), history.toString());
            assertEquals(1, history.path("data").size());
        }
        // 处置人员能复核内容，但仍不能读取私人空间的非审核场景？
        // 隔离中的内容复核属于处置职责，允许读取原文但不改变处置结论
        try (var moderator = client()) {
            login(moderator, moderatorId);
            assertContent(moderator, "/picture/content/" + TEAM_PICTURE + "/original");
        }
        // 管理员仍然读不到
        long adminId = registerAdmin("quarantine_admin_" + System.nanoTime());
        try (var admin = client()) {
            login(admin, adminId);
            assertDenied(admin, "/picture/content/" + TEAM_PICTURE + "/original");
        }
    }

    @Test
    void adminContentReadOfPrivateSpaceIsDeniedButReviewDutyRemains() throws Exception {
        long adminId = registerAdmin("quarantine_admin_" + System.nanoTime());
        // 私有空间：平台管理员不再能读取内容
        seedPrivateSpace();
        long privatePicture = 88020;
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'https://origin.invalid/p.jpg', 'private', ?, 10, 88002, 1, 0)
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, privatePicture, ownerId);
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status)
                VALUES (?, 88002, ?, 'gallery', 'original', ?, '/private-space/original.jpg',
                        'https://acceptance.invalid/private-space/original.jpg', 10, 1)
                """, ownerId, privatePicture, RecordingStorageConfiguration.PLATFORM);
        try (var admin = client()) {
            login(admin, adminId);
            int before = recorder.calls().size();
            assertDenied(admin, "/picture/content/" + privatePicture + "/original");
            assertEquals(before, recorder.calls().size(), "admin must not read private space content");
        }
        try {
            // 审核职责：公共图库待审内容仍可由管理员读取（不涉及空间）
            long pendingPublic = 88021;
            jdbc.update("""
                    INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                    VALUES (?, 'https://origin.invalid/pending.jpg', 'pending', ?, 10, NULL, 0, 0)
                    ON DUPLICATE KEY UPDATE name = VALUES(name)
                    """, pendingPublic, ownerId);
            jdbc.update("""
                    INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                              objectKey, url, fileSize, status)
                    VALUES (?, NULL, ?, 'gallery', 'original', ?, '/pending/original.jpg',
                            'https://acceptance.invalid/pending/original.jpg', 10, 1)
                    """, ownerId, pendingPublic, RecordingStorageConfiguration.PLATFORM);
            try (var admin = client()) {
                login(admin, adminId);
                assertContent(admin, "/picture/content/" + pendingPublic + "/original");
            }
        } finally {
            jdbc.update("DELETE FROM picture_file WHERE pictureId IN (?, ?)", privatePicture, 88021L);
            jdbc.update("DELETE FROM picture WHERE id IN (?, ?)", privatePicture, 88021L);
            jdbc.update("DELETE FROM space WHERE id = 88002");
        }
    }

    // ---- helpers ----

    private void quarantine(long pictureId) throws Exception {
        JsonNode response = postAs(moderatorId, "/moderation/quarantine", moderationBody(pictureId, "举报"));
        assertEquals(0, response.path("code").asInt(), response.toString());
        assertEquals("QUARANTINED", response.path("data").path("status").asText());
    }

    private Map<String, Object> moderationBody(long resourceId, String reason) {
        return Map.of("resourceType", "picture", "resourceId", resourceId, "reason", reason);
    }

    private JsonNode postAs(long userId, String path, Object body) throws Exception {
        try (var client = client()) {
            login(client, userId);
            return post(client, path, body);
        }
    }

    private JsonNode findByPictureId(JsonNode records, long pictureId) {
        for (JsonNode record : records) {
            if (record.path("id").asLong() == pictureId) {
                return record;
            }
        }
        return null;
    }

    private JsonNode findByResourceId(JsonNode records, long resourceId) {
        for (JsonNode record : records) {
            if (record.path("resourceId").asLong() == resourceId) {
                return record;
            }
        }
        return null;
    }

    private void seedTeamSpace() {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'team', ?, 0, 1, 1000, 1000, 0, 3, 0)
                """, TEAM_SPACE, ownerId);
    }

    private void seedPrivateSpace() {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (88002, 'private', ?, 0, 0, 1000, 1000, 0, 1, 0)
                """, ownerId);
    }

    private void joinTeam(long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                TEAM_SPACE, userId, role);
    }

    private void seedPicture(long id, long owner, Long spaceId) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, ?, 1, 0)
                """, id, "https://origin.invalid/" + id + ".jpg", "picture-" + id, owner, spaceId);
    }

    private void seedResource(long pictureId, long owner, Long spaceId, String objectKey) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status)
                VALUES (?, ?, ?, 'gallery', 'original', ?, ?, ?, 10, 1)
                """, owner, spaceId, pictureId, RecordingStorageConfiguration.PLATFORM,
                "/" + objectKey, "https://acceptance.invalid/" + objectKey);
    }

    private void seedGrant(long pictureId, long granteeId) {
        jdbc.update("""
                INSERT INTO resource_access_grant (resourceType, resourceId, granteeUserId, grantedByUserId,
                                                  reason, expiresTime, status, activeKey)
                VALUES ('picture', ?, ?, ?, '测试授权', DATE_ADD(NOW(), INTERVAL 1 DAY), 1, ?)
                """, pictureId, granteeId, ownerId, "picture:" + pictureId + ":" + granteeId);
    }

    private void assertContent(HttpClient client, String path) throws Exception {
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
        assertEquals("acceptance-image-bytes", response.body());
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

    private long registerModerator(String account) throws Exception {
        long id = register(account);
        jdbc.update("UPDATE user SET userRole = 'moderator' WHERE id = ?", id);
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
