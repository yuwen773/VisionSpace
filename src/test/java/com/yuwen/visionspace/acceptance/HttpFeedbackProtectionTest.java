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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反馈正文与附件保护（原 14.x），全部经真实 HTTP。
 *
 * <p>覆盖：匿名、其他用户、创建者、平台管理员的允许/拒绝矩阵；响应最小化；
 * 附件内容按反馈权限判定；拒绝时零存储读取、零数据写入；旧附件不被自动认领或公开。
 */
@IsolatedSpringBootTest
class HttpFeedbackProtectionTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long LEGACY_FEEDBACK = 87601;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    private long creatorId;
    private long strangerId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM feedback_attachment WHERE feedbackId = ? OR legacyUrlHash IS NOT NULL",
                LEGACY_FEEDBACK);
        jdbc.update("DELETE FROM user_feedback WHERE id = ?", LEGACY_FEEDBACK);
        jdbc.update("DELETE FROM resource_isolation WHERE resourceType = 'feedback-attachment'");
        creatorId = register("feedback_creator_" + System.nanoTime());
        strangerId = register("feedback_stranger_" + System.nanoTime());
    }

    @Test
    void detailIsReadableOnlyByCreatorAndAdminWithFeedbackDuty() throws Exception {
        long feedbackId = submitFeedbackWithAttachment();
        long adminId = registerAdmin("feedback_admin_" + System.nanoTime());

        try (var anonymous = client()) {
            assertRejected(get(anonymous, "/feedback/get/" + feedbackId));
        }
        try (var stranger = client()) {
            login(stranger, strangerId);
            assertRejected(get(stranger, "/feedback/get/" + feedbackId));
        }
        try (var creator = client()) {
            login(creator, creatorId);
            var detail = get(creator, "/feedback/get/" + feedbackId);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
        }
        try (var admin = client()) {
            login(admin, adminId);
            var detail = get(admin, "/feedback/get/" + feedbackId);
            assertEquals(0, detail.path("code").asInt(), "feedback duty admin can read: " + detail);
        }
    }

    @Test
    void adminRoleDoesNotExtendToOtherPrivateResources() throws Exception {
        // 处置角色没有反馈管理职责，管理员才有；两种身份都不能绕过创建者判定之外的资源
        long moderatorId = registerModerator("feedback_moderator_" + System.nanoTime());
        long feedbackId = submitFeedbackWithAttachment();
        try (var moderator = client()) {
            login(moderator, moderatorId);
            assertRejected(get(moderator, "/feedback/get/" + feedbackId));
        }
    }

    @Test
    void responseIsMinimizedAndAttachmentsHaveNoOriginAddress() throws Exception {
        long feedbackId = submitFeedbackWithAttachment();
        try (var creator = client()) {
            login(creator, creatorId);
            var detail = get(creator, "/feedback/get/" + feedbackId);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            JsonNode data = detail.path("data");
            // 不返回完整用户资料，只返回名称
            assertTrue(data.path("userVO").isMissingNode() || data.path("userVO").isNull(),
                    "detail must not embed full user profile: " + data);
            assertFalse(data.path("userName").asText("").isBlank(), "creator name should be present");
            // 附件只给受控入口，不给源站地址
            JsonNode attachments = data.path("attachments");
            assertEquals(1, attachments.size(), data.toString());
            JsonNode attachment = attachments.get(0);
            String url = attachment.path("url").asText("");
            assertTrue(url.startsWith("/feedback/attachment/"), "attachment must use controlled endpoint: " + url);
            assertFalse(data.toString().contains("acceptance.invalid"), "no storage origin may leak: " + data);
            assertFalse(data.toString().contains("temp/feedback"), "no object key may leak: " + data);
        }
    }

    @Test
    void attachmentContentFollowsCurrentFeedbackPermission() throws Exception {
        long feedbackId = submitFeedbackWithAttachment();
        long attachmentId = attachmentIdOf(feedbackId);

        try (var anonymous = client()) {
            assertDenied(anonymous, "/feedback/attachment/" + attachmentId);
        }
        try (var stranger = client()) {
            login(stranger, strangerId);
            int before = recorder.calls().size();
            assertDenied(stranger, "/feedback/attachment/" + attachmentId);
            assertEquals(before, recorder.calls().size(), "rejected attachment read must not touch storage");
        }
        try (var creator = client()) {
            login(creator, creatorId);
            assertContent(creator, "/feedback/attachment/" + attachmentId);
        }
        long adminId = registerAdmin("feedback_admin_" + System.nanoTime());
        try (var admin = client()) {
            login(admin, adminId);
            assertContent(admin, "/feedback/attachment/" + attachmentId);
        }
    }

    @Test
    void unboundAttachmentIsOnlyReadableByItsUploader() throws Exception {
        long[] attachment = new long[1];
        try (var creator = client()) {
            login(creator, creatorId);
            attachment[0] = uploadAttachment(creator);
            assertContent(creator, "/feedback/attachment/" + attachment[0]);
        }
        try (var stranger = client()) {
            login(stranger, strangerId);
            assertDenied(stranger, "/feedback/attachment/" + attachment[0]);
        }
        // 其他用户不能把别人的附件绑定到自己的反馈
        try (var stranger = client()) {
            login(stranger, strangerId);
            var response = post(stranger, "/feedback/add", Map.of(
                    "type", 1, "title", "t", "content", "c", "attachmentIds", List.of(attachment[0])));
            assertRejected(response);
        }
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM feedback_attachment WHERE id = ? AND feedbackId IS NOT NULL",
                Integer.class, attachment[0]), "rejected bind must not attach the record");
    }

    @Test
    void rawUrlsCannotBeUsedAsAttachments() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM user_feedback", Integer.class);
        try (var creator = client()) {
            login(creator, creatorId);
            var response = post(creator, "/feedback/add", Map.of(
                    "type", 1, "title", "t", "content", "c",
                    "pictureUrls", List.of("https://acceptance.invalid/temp/feedback/whatever.png")));
            // 原始地址一律拒绝：附件只能来自服务端登记的上传记录
            assertEquals(40000, response.path("code").asInt(), response.toString());
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM user_feedback", Integer.class),
                "rejected submission must not write a feedback row");
    }

    @Test
    void legacyAttachmentWithUnconfirmedOwnershipIsBlockedAndNotPublic() throws Exception {
        // 旧附件：只存在于 pictureUrls，无法从可信记录确认归属
        jdbc.update("""
                INSERT INTO user_feedback (id, userId, type, title, content, pictureUrls, status, isDelete)
                VALUES (?, ?, 2, '旧反馈', '旧正文', ?, 0, 0)
                """, LEGACY_FEEDBACK, creatorId, "[\"https://acceptance.invalid/legacy/attachment.png\"]");
        jdbc.update("""
                INSERT INTO feedback_attachment (ownerUserId, feedbackId, platform, objectKey, url, status, legacyUrlHash)
                VALUES (?, ?, 'legacy', 'https://acceptance.invalid/legacy/attachment.png',
                        'https://acceptance.invalid/legacy/attachment.png', 3, SHA2('https://acceptance.invalid/legacy/attachment.png', 256))
                """, creatorId, LEGACY_FEEDBACK);

        Long attachmentId = jdbc.queryForObject(
                "SELECT id FROM feedback_attachment WHERE legacyUrlHash = SHA2(?, 256)",
                Long.class, "https://acceptance.invalid/legacy/attachment.png");
        try (var creator = client()) {
            login(creator, creatorId);
            var detail = get(creator, "/feedback/get/" + LEGACY_FEEDBACK);
            assertEquals(0, detail.path("code").asInt(), detail.toString());
            JsonNode attachments = detail.path("data").path("attachments");
            assertEquals("BLOCKED", attachments.get(0).path("status").asText(),
                    "unconfirmed legacy attachment must stay blocked: " + detail);
            assertTrue(attachments.get(0).path("url").isNull()
                            || attachments.get(0).path("url").isMissingNode(),
                    "blocked attachment must not expose an endpoint: " + detail);
            // 创建者本人也不能读取归属未确认的旧附件
            assertDenied(creator, "/feedback/attachment/" + attachmentId);
        }
        // 旧附件未被删除，仍保留原记录
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_feedback WHERE id = ?", Integer.class, LEGACY_FEEDBACK));
        assertEquals("https://acceptance.invalid/legacy/attachment.png", jdbc.queryForObject(
                "SELECT objectKey FROM feedback_attachment WHERE id = ?", String.class, attachmentId));
    }

    @Test
    void anonymousDetailRequestIsRejectedWithoutServerError() throws Exception {
        long feedbackId = submitFeedbackWithAttachment();
        try (var anonymous = client()) {
            var response = send(anonymous, HttpRequest.newBuilder(
                    uri("/feedback/get/" + feedbackId)).GET().build());
            JsonNode body = json.readTree(response.body());
            assertEquals(40100, body.path("code").asInt(), "anonymous must get an explicit login error: " + body);
        }
    }

    // ---- helpers ----

    private long submitFeedbackWithAttachment() throws Exception {
        try (var creator = client()) {
            login(creator, creatorId);
            long attachmentId = uploadAttachment(creator);
            // 附件标识按十进制字符串提交：雪花标识超出 JavaScript 的安全整数范围
            var response = post(creator, "/feedback/add", Map.of(
                    "type", 2, "title", "附件反馈", "content", "正文内容",
                    "attachmentIds", List.of(String.valueOf(attachmentId))));
            assertEquals(0, response.path("code").asInt(), response.toString());
            long feedbackId = response.path("data").asLong();
            assertEquals(attachmentId, attachmentIdOf(feedbackId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT status FROM feedback_attachment WHERE id = ?", Integer.class, attachmentId),
                    "bound attachment must be BOUND");
            return feedbackId;
        }
    }

    @Test
    void attachmentIdsBindWhetherTheyArriveAsStringOrNumber() throws Exception {
        // 服务端只接受服务端登记过的附件标识；字符串形式是客户端的正式提交方式
        try (var creator = client()) {
            login(creator, creatorId);
            long attachmentId = uploadAttachment(creator);
            var response = post(creator, "/feedback/add", Map.of(
                    "type", 2, "title", "标识格式", "content", "正文内容",
                    "attachmentIds", List.of(String.valueOf(attachmentId))));
            assertEquals(0, response.path("code").asInt(), response.toString());
            long feedbackId = response.path("data").asLong();
            assertEquals(attachmentId, attachmentIdOf(feedbackId),
                    "string attachment ids must bind to the uploaded attachment");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM feedback_attachment WHERE id = ? AND status = 1",
                    Integer.class, attachmentId));
        }
        // 非法标识整体拒绝，不做部分绑定
        try (var creator = client()) {
            login(creator, creatorId);
            long attachmentId = uploadAttachment(creator);
            var response = post(creator, "/feedback/add", Map.of(
                    "type", 2, "title", "非法标识", "content", "正文内容",
                    "attachmentIds", List.of(String.valueOf(attachmentId), "not-a-number")));
            assertEquals(40000, response.path("code").asInt(), response.toString());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM feedback_attachment WHERE id = ? AND feedbackId IS NOT NULL",
                    Integer.class, attachmentId), "rejected submission must not bind any attachment");
        }
    }

    private long uploadAttachment(HttpClient client) throws Exception {
        String boundary = "----acceptance" + System.nanoTime();
        byte[] content = "acceptance-image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"attachment.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = client.send(HttpRequest.newBuilder(uri("/feedback/upload"))
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode node = json.readTree(response.body());
        assertEquals(0, node.path("code").asInt(), node.toString());
        // 上传响应只能给受控入口，不能给源站地址
        assertFalse(node.toString().contains("acceptance.invalid"), "upload must not leak origin: " + node);
        assertTrue(node.path("data").path("url").asText("").startsWith("/feedback/attachment/"),
                "upload must return a controlled endpoint: " + node);
        assertNotEquals(0, node.path("data").path("id").asLong());
        return node.path("data").path("id").asLong();
    }

    private long attachmentIdOf(long feedbackId) {
        return jdbc.queryForObject(
                "SELECT id FROM feedback_attachment WHERE feedbackId = ?", Long.class, feedbackId);
    }

    private void assertContent(HttpClient client, String path) throws Exception {
        int before = recorder.calls().size();
        var response = send(client, HttpRequest.newBuilder(uri(path)).GET().build());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
        assertEquals("acceptance-image-bytes", response.body());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertTrue(response.headers().firstValue("X-Content-Type-Options").orElse("").contains("nosniff"));
        assertEquals(1, recorder.calls().subList(before, recorder.calls().size()).stream()
                .filter(call -> "READ".equals(call.method())).count(), "must read exactly one object");
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
