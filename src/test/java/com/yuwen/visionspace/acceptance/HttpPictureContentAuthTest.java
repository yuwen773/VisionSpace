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
 * 图片归属与实际内容鉴权（原 11.x / 12.x），全部经真实 HTTP。
 *
 * <p>覆盖：资源记录形成可信归属；原图/缩略图/预览/下载每次按当前角色判定；
 * 移除成员后两个会话立即拒绝；管理入口不返回旁路地址；未知归属旧资源保留
 * 但不自动授权；删除只按资源标识与归属记录，任意 URL 不能触发删除。
 */
@IsolatedSpringBootTest
class HttpPictureContentAuthTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long TEAM_SPACE = 87701;
    private static final long PUBLIC_PICTURE = 87710;
    private static final long PENDING_PICTURE = 87711;
    private static final long TEAM_PICTURE = 87712;
    private static final long LEGACY_PICTURE = 87713;
    private static final long AGENT_FILE = 87720;
    private static final long AGENT_FILE_OTHER = 87721;

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

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM picture_file WHERE pictureId IN (?, ?, ?, ?) OR id IN (?, ?)",
                PUBLIC_PICTURE, PENDING_PICTURE, TEAM_PICTURE, LEGACY_PICTURE, AGENT_FILE, AGENT_FILE_OTHER);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?, ?)",
                PUBLIC_PICTURE, PENDING_PICTURE, TEAM_PICTURE, LEGACY_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId = ?", TEAM_SPACE);
        jdbc.update("DELETE FROM space WHERE id = ?", TEAM_SPACE);

        ownerId = register("content_owner_" + System.nanoTime());
        viewerId = register("content_viewer_" + System.nanoTime());
        strangerId = register("content_stranger_" + System.nanoTime());

        seedTeamSpace();
        joinTeam(viewerId, "viewer");

        seedPicture(PUBLIC_PICTURE, ownerId, null, 1, "public");
        seedPicture(PENDING_PICTURE, ownerId, null, 0, "pending");
        seedPicture(TEAM_PICTURE, ownerId, TEAM_SPACE, 1, "team");
        // 未知归属的存量图片：只有 url，没有资源记录
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'https://legacy.invalid/old.jpg', 'legacy', ?, 10, NULL, 1, 0)
                """, LEGACY_PICTURE, ownerId);

        seedResource(PUBLIC_PICTURE, ownerId, null, "original", "public/original.jpg");
        seedResource(PUBLIC_PICTURE, ownerId, null, "thumbnail", "public/thumb.jpg");
        seedResource(TEAM_PICTURE, ownerId, TEAM_SPACE, "original", "team/original.jpg");
    }

    @Test
    void approvedPublicContentIsReadableAnonymouslyForEveryKind() throws Exception {
        try (var anonymous = client()) {
            assertContent(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/original",
                    "/public/original.jpg");
            assertContent(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/thumbnail",
                    "/public/thumb.jpg");
            // 预览缺失时退回原图，仍按同一授权判定
            assertContent(anonymous, "/picture/content/" + PUBLIC_PICTURE + "/preview",
                    "/public/original.jpg");
        }
    }

    @Test
    void unreviewedPublicContentIsDeniedToAnonymousAndStrangers() throws Exception {
        seedResource(PENDING_PICTURE, ownerId, null, "original", "pending/original.jpg");
        try (var anonymous = client(); var stranger = client()) {
            assertDenied(anonymous, "/picture/content/" + PENDING_PICTURE + "/original");
            login(stranger, strangerId);
            assertDenied(stranger, "/picture/content/" + PENDING_PICTURE + "/original");
        }
        try (var owner = client()) {
            login(owner, ownerId);
            assertContent(owner, "/picture/content/" + PENDING_PICTURE + "/original");
        }
    }

    @Test
    void teamContentFollowsMembershipAndRevocationOnEveryRequest() throws Exception {
        try (var anonymous = client(); var viewer = client(); var stranger = client()) {
            assertDenied(anonymous, "/picture/content/" + TEAM_PICTURE + "/original");

            login(viewer, viewerId);
            assertContent(viewer, "/picture/content/" + TEAM_PICTURE + "/original");

            login(stranger, strangerId);
            assertDenied(stranger, "/picture/content/" + TEAM_PICTURE + "/original");

            // 移除成员后不重新登录，同一会话的下一次请求必须拒绝
            jdbc.update("DELETE FROM space_user WHERE spaceId = ? AND userId = ?", TEAM_SPACE, viewerId);
            assertDenied(viewer, "/picture/content/" + TEAM_PICTURE + "/original");
            assertDenied(viewer, "/picture/content/" + TEAM_PICTURE + "/thumbnail");
        }
    }

    @Test
    void downloadIsAuthorizedPerRequestAndSetsAttachmentName() throws Exception {
        try (var anonymous = client()) {
            var response = send(anonymous, HttpRequest.newBuilder(
                    uri("/picture/content/" + PUBLIC_PICTURE + "/original?download=true")).GET().build());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"));
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            assertDenied(anonymous, "/picture/content/" + TEAM_PICTURE + "/original?download=true");
        }
    }

    @Test
    void viewsExposeControlledEndpointsInsteadOfOriginAddresses() throws Exception {
        try (var anonymous = client()) {
            var detail = get(anonymous, "/picture/get/vo?id=" + PUBLIC_PICTURE);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            String url = detail.path("data").path("url").asText();
            String thumbnail = detail.path("data").path("thumbnailUrl").asText();
            assertTrue(url.startsWith("/picture/content/" + PUBLIC_PICTURE + "/original"), url);
            assertTrue(thumbnail.startsWith("/picture/content/" + PUBLIC_PICTURE + "/thumbnail"), thumbnail);

            var listing = post(anonymous, "/picture/list/page/vo", Map.of("current", 1, "pageSize", 10));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            for (JsonNode record : listing.path("data").path("records")) {
                if (record.path("url").isNull()) {
                    continue;
                }
                assertTrue(record.path("url").asText().startsWith("/picture/content/"),
                        "listing must not expose origin addresses: " + record);
            }
        }
    }

    @Test
    void adminEntryPointsDoNotReturnBypassAddresses() throws Exception {
        long adminId = registerAdmin("content_admin_" + System.nanoTime());
        try (var admin = client()) {
            login(admin, adminId);
            var single = get(admin, "/picture/get?id=" + TEAM_PICTURE);
            assertEquals(0, single.path("code").asInt(), single.toString());
            assertTrue(single.path("data").path("url").isNull());

            var listing = post(admin, "/picture/list/page", Map.of("current", 1, "pageSize", 10));
            assertEquals(0, listing.path("code").asInt(), listing.toString());
            for (JsonNode record : listing.path("data").path("records")) {
                assertTrue(record.path("url").isNull(), "admin listing must not expose url");
                assertTrue(record.path("thumbnailUrl").isNull(), "admin listing must not expose thumbnail");
                assertTrue(record.path("previewUrl").isNull(), "admin listing must not expose preview");
            }

            // 审核需要看图：管理员走受控入口读未审核的公共图库内容，而不是拿源站地址
            seedResource(PENDING_PICTURE, ownerId, null, "original", "pending/original.jpg");
            assertContent(admin, "/picture/content/" + PENDING_PICTURE + "/original",
                    "/pending/original.jpg");
            // 团队空间按成员关系判定，平台管理员不因平台角色获得成员权限
            assertDenied(admin, "/picture/content/" + TEAM_PICTURE + "/original");
        }
    }

    @Test
    void legacyPictureWithoutResourceRecordIsKeptButNotGranted() throws Exception {
        // 记录仍在，元数据可读
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE id = ?", Integer.class, LEGACY_PICTURE));
        try (var anonymous = client()) {
            var detail = get(anonymous, "/picture/get/vo?id=" + LEGACY_PICTURE);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            // 不返回任何内容地址：未知归属不自动授权
            assertTrue(detail.path("data").path("url").isNull(), detail.toString());
            assertTrue(detail.path("data").path("thumbnailUrl").isNull(), detail.toString());
        }
        // 不产生资源记录
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE pictureId = ?", Integer.class, LEGACY_PICTURE));
    }

    @Test
    void uploadRecordsTrustedOwnershipAndStorageLocation() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            var response = upload(owner, "/picture/upload", "content-upload.png");
            assertEquals(0, response.path("code").asInt(), response.toString());
            long pictureId = response.path("data").path("id").asLong();

            var records = jdbc.queryForList(
                    "SELECT ownerUserId, platform, objectKey, status, kind FROM picture_file WHERE pictureId = ?",
                    pictureId);
            assertFalse(records.isEmpty(), "upload must register resource records");
            for (var record : records) {
                assertEquals(ownerId, ((Number) record.get("ownerUserId")).longValue());
                assertEquals(RecordingStorageConfiguration.PLATFORM, record.get("platform"));
                assertTrue(String.valueOf(record.get("objectKey")).startsWith("/public/" + ownerId + "/"));
                assertEquals(1, ((Number) record.get("status")).intValue(), "bound records are active");
            }
            assertTrue(records.stream().anyMatch(r -> "thumbnail".equals(r.get("kind"))),
                    "thumbnail object must be recorded: " + records);

            jdbc.update("DELETE FROM picture_file WHERE pictureId = ?", pictureId);
            jdbc.update("DELETE FROM picture WHERE id = ?", pictureId);
        }
    }

    @Test
    void agentUploadReturnsResourceIdAndDeleteIgnoresForgedUrls() throws Exception {
        try (var owner = client(); var stranger = client()) {
            login(owner, ownerId);
            var upload = upload(owner, "/agent/image/upload", "agent.png");
            assertEquals(0, upload.path("code").asInt(), upload.toString());
            long resourceId = upload.path("data").path("resourceId").asLong();
            String url = upload.path("data").path("url").asText();
            assertTrue(resourceId > 0);
            assertTrue(!url.isBlank());

            // 跨用户按资源标识删除：拒绝且对象不被删除
            login(stranger, strangerId);
            int before = recorder.calls().size();
            var forged = post(stranger, "/agent/image/upload/delete", Map.of("resourceId", resourceId));
            assertRejected(forged);
            assertEquals(before, recorder.calls().size(), "cross-user delete must not touch storage");
            assertEquals(0, jdbc.queryForObject(
                    "SELECT status FROM picture_file WHERE id = ?", Integer.class, resourceId),
                    "status must remain TEMP");

            // 伪造 URL（仅前缀相同）不能触发删除
            login(owner, ownerId);
            var forgedUrl = post(owner, "/agent/image/upload/delete",
                    Map.of("url", url + "-forged-suffix"));
            assertRejected(forgedUrl);
            assertEquals(before, recorder.calls().size(), "forged url must not touch storage");

            // 正确所有者按资源标识删除，对象删除记录使用记录中的平台与键
            var deleted = post(owner, "/agent/image/upload/delete", Map.of("resourceId", resourceId));
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
            List<ExternalCallRecorder.Call> calls = recorder.calls();
            assertTrue(calls.subList(before, calls.size()).stream()
                            .anyMatch(call -> "DELETE".equals(call.method())),
                    "expected a recorded storage delete: " + calls.subList(before, calls.size()));

            // 重复删除：无额外副作用
            int afterDelete = recorder.calls().size();
            var repeated = post(owner, "/agent/image/upload/delete", Map.of("resourceId", resourceId));
            assertEquals(0, repeated.path("code").asInt(), repeated.toString());
            assertEquals(afterDelete, recorder.calls().size(), "repeated delete must be a no-op");
        }
    }

    @Test
    void legacyUrlDeleteOnlyWorksForExactOwnedRecord() throws Exception {
        seedAgentResource(AGENT_FILE, ownerId, "https://acceptance.invalid/agent-chat/1/a.png");
        seedAgentResource(AGENT_FILE_OTHER, strangerId, "https://acceptance.invalid/agent-chat/2/b.png");

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();

            // 他人记录：拒绝
            assertRejected(post(owner, "/agent/image/upload/delete",
                    Map.of("url", "https://acceptance.invalid/agent-chat/2/b.png")));
            // 前缀匹配：拒绝
            assertRejected(post(owner, "/agent/image/upload/delete",
                    Map.of("url", "https://acceptance.invalid/agent-chat/1/")));
            // 未知地址：拒绝
            assertRejected(post(owner, "/agent/image/upload/delete",
                    Map.of("url", "https://acceptance.invalid/agent-chat/1/unknown.png")));
            assertEquals(before, recorder.calls().size(), "rejected legacy deletes must not touch storage");

            // 精确匹配本人记录：允许
            var allowed = post(owner, "/agent/image/upload/delete",
                    Map.of("url", "https://acceptance.invalid/agent-chat/1/a.png"));
            assertEquals(0, allowed.path("code").asInt(), allowed.toString());
        }
    }

    @Test
    void chatRejectsAttachmentsThatDoNotBelongToTheUser() throws Exception {
        seedAgentResource(AGENT_FILE, ownerId, "https://acceptance.invalid/agent-chat/1/a.png");
        try (var stranger = client()) {
            login(stranger, strangerId);
            var response = post(stranger, "/agent/image/chat", Map.of(
                    "message", "看看这张图",
                    "imageUrls", List.of("https://acceptance.invalid/agent-chat/1/a.png")));
            assertRejected(response);
        }
    }

    // ---- fixtures ----

    private void seedTeamSpace() {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'team', ?, 0, 1, 1000, 1000, 0, 3, 0)
                """, TEAM_SPACE, ownerId);
    }

    private void joinTeam(long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                TEAM_SPACE, userId, role);
    }

    private void seedPicture(long id, long ownerId, Long spaceId, int reviewStatus, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, thumbnailUrl, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, ?, 10, ?, ?, 0)
                """, id, "https://origin.invalid/" + name + ".jpg",
                "https://origin.invalid/" + name + ".min.jpg", name, ownerId, spaceId, reviewStatus);
    }

    private void seedResource(long pictureId, long ownerUserId, Long spaceId, String kind, String objectKey) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status)
                VALUES (?, ?, ?, 'gallery', ?, ?, ?, ?, 10, 1)
                """, ownerUserId, spaceId, pictureId, kind,
                RecordingStorageConfiguration.PLATFORM, "/" + objectKey,
                "https://acceptance.invalid/" + objectKey);
    }

    private void seedAgentResource(long id, long ownerUserId, String url) {
        jdbc.update("""
                INSERT INTO picture_file (id, ownerUserId, purpose, kind, platform, objectKey, url,
                                          fileSize, status)
                VALUES (?, ?, 'agent-chat', 'original', ?, ?, ?, 10, 0)
                """, id, ownerUserId, RecordingStorageConfiguration.PLATFORM,
                url.replace("https://acceptance.invalid", ""), url);
    }

    private void assertContent(HttpClient client, String path) throws Exception {
        assertContent(client, path, null);
    }

    /**
     * 校验内容可读，并（可选）确认读取的是记录里的那个对象键，
     * 避免"只验了授权、没验路由"。
     */
    private void assertContent(HttpClient client, String path, String expectedKey) throws Exception {
        int before = recorder.calls().size();
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
        assertEquals("acceptance-image-bytes", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("image/"),
                path + " content type: " + response.headers().firstValue("Content-Type"));
        if (expectedKey != null) {
            List<ExternalCallRecorder.Call> reads = recorder.calls().subList(before, recorder.calls().size())
                    .stream().filter(call -> "READ".equals(call.method())).toList();
            assertEquals(1, reads.size(), path + " must read exactly one object: " + reads);
            // 记录格式与方法一致：Call(method, platform, objectKey)
            assertEquals(RecordingStorageConfiguration.PLATFORM, reads.get(0).path(),
                    path + " must read through the recorded platform");
            assertEquals(expectedKey, reads.get(0).body(),
                    path + " must read the recorded object key");
        }
    }

    private void assertDenied(HttpClient client, String path) throws Exception {
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        int status = response.statusCode();
        if (status == 200) {
            JsonNode body = json.readTree(response.body());
            assertRejected(body);
        } else {
            assertTrue(status == 401 || status == 403, path + " -> " + status);
        }
    }

    private void assertRejected(JsonNode response) {
        int code = response.path("code").asInt();
        assertTrue(code == 40100 || code == 40101, "expected authorization rejection: " + response);
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

    private JsonNode upload(HttpClient client, String path, String filename) throws Exception {
        String boundary = "----acceptance" + System.nanoTime();
        byte[] content = "acceptance-image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = client.send(HttpRequest.newBuilder(uri(path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
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
