package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.service.AgentAttachmentService;
import com.yuwen.visionspace.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 图片完整生命周期（工单 13.x），绑定、读取、回收与删除都经真实 HTTP 与
 * 真实数据库。
 *
 * <p>覆盖：临时资源只绑定到本人有权会话；重复绑定幂等；附件读取按会话与资源
 * 双重授权；已绑定的图片不被上传清理入口删除；未绑定的临时图片二十四小时后
 * 回收且与绑定并发不误删；会话或图片被明确删除后访问停止；存储删除失败保留
 * 可重试状态；共享引用不会被误清理；跨用户删除零副作用。
 */
@IsolatedSpringBootTest
class HttpAgentAttachmentLifecycleTest {

    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    @Autowired
    private AgentAttachmentService agentAttachmentService;

    private long ownerId;
    private long strangerId;

    private final List<String> threadIds = new ArrayList<>();

    @BeforeEach
    void fixture() throws Exception {
        ownerId = register("attach_owner_" + System.nanoTime());
        strangerId = register("attach_stranger_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        for (String threadId : threadIds) {
            jdbc.update("DELETE FROM agent_session_attachment WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_message WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_session WHERE sessionId = ?", threadId);
        }
        // 附件删除未确认时也会登记统一的清理任务：按本人资源记录的对象键清理，
        // 否则任务会留到别的用例里被当成自己的残留
        jdbc.update("DELETE FROM storage_cleanup_task WHERE reason = '附件对象删除未确认' "
                + "AND objectKey LIKE 'https://acceptance.invalid/agent-chat/%'");
        jdbc.update("DELETE FROM picture_file WHERE purpose = 'agent-chat' AND ownerUserId IN (?, ?)",
                ownerId, strangerId);
    }

    // ---- 13.1 绑定与授权 ----

    @Test
    void temporaryResourceBindsOnlyToASessionTheUserOwns() throws Exception {
        String ownThread = seedSession(ownerId, "own thread");
        String strangerThread = seedSession(strangerId, "stranger thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/a.png");

        // 绑定到他人会话：拒绝，且不产生绑定
        assertThrows(BusinessException.class,
                () -> agentAttachmentService.bind(strangerThread, ownerId, List.of(resourceId)));
        assertEquals(0, bindingCount(resourceId), "binding to another user's session must not happen");

        // 绑定到本人会话：成功
        agentAttachmentService.bind(ownThread, ownerId, List.of(resourceId));
        assertEquals(1, bindingCount(resourceId));
        assertEquals(1, resourceStatus(resourceId), "a bound resource is active, not reclaimable");
    }

    @Test
    void repeatedBindingIsIdempotent() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/b.png");

        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        assertEquals(1, bindingCount(resourceId), "repeated binding must not create extra rows");
    }

    @Test
    void attachmentIdsAreAcceptedAsDecimalStrings() throws Exception {
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/h.png");

        // 标识按十进制字符串提交：雪花值超出 JavaScript 的安全整数范围，
        // 客户端转成 number 会丢精度，落到别的资源上
        List<Long> resolved = agentAttachmentService.resolveOwnedIds(
                List.of(String.valueOf(resourceId)), List.of(), ownerId);
        assertEquals(List.of(resourceId), resolved, "a decimal string id must resolve to the same resource");

        // 不是十进制数字的标识：按参数不合法拒绝，不做部分解析
        assertThrows(BusinessException.class,
                () -> agentAttachmentService.resolveOwnedIds(List.of("not-a-number"), List.of(), ownerId));
        assertThrows(BusinessException.class,
                () -> agentAttachmentService.resolveOwnedIds(List.of("0"), List.of(), ownerId));
    }

    @Test
    void attachmentContentRequiresBothSessionAndResourceAuthorization() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        String strangerThread = seedSession(strangerId, "stranger thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/c.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        try (var owner = client(); var stranger = client()) {
            login(owner, ownerId);
            login(stranger, strangerId);

            // 他人会话 + 本人资源：拒绝
            assertRejected(get(stranger, "/agent/attachment/" + resourceId + "/content?sessionId=" + threadId));
            // 他人资源：拒绝
            long strangerResource = seedAgentResource(strangerId, "https://acceptance.invalid/agent-chat/s/d.png");
            assertRejected(get(owner, "/agent/attachment/" + strangerResource + "/content?sessionId=" + threadId));
            // 未绑定的资源：拒绝
            long unbound = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/e.png");
            assertRejected(get(owner, "/agent/attachment/" + unbound + "/content?sessionId=" + threadId));
            // 本人会话 + 本人资源 + 已绑定：允许
            var allowed = getBytes(owner, "/agent/attachment/" + resourceId + "/content?sessionId=" + threadId);
            assertEquals(200, allowed.statusCode());
            assertTrue(allowed.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
                    "attachment content must not be cached: " + allowed.headers().map());
        }
    }

    // ---- 13.2 已绑定的图片不被流结束删除 ----

    @Test
    void theUploadCleanupEntryCannotDeleteABoundImage() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/f.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            // 前端的失败清理走这个入口：已绑定的图片不在这里删除
            var response = post(owner, "/agent/image/upload/delete", Map.of("resourceId", resourceId));
            assertEquals(0, response.path("code").asInt(), response.toString());
            assertEquals(before, recorder.calls().size(),
                    "a bound image must not be deleted by the upload cleanup entry: "
                            + recorder.calls().subList(before, recorder.calls().size()));
            assertEquals(1, resourceStatus(resourceId), "a bound image stays active");
            assertEquals(1, bindingCount(resourceId), "a bound image keeps its binding");
        }
    }

    @Test
    void historyCarriesTheAttachmentIdsAndServesThemThroughTheControlledEntry() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/g.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));
        jdbc.update("INSERT INTO agent_message (sessionId, role, subType, content, attachmentIds, createdTime) " +
                "VALUES (?, 'USER', NULL, '看看这张图', ?, NOW())", threadId, String.valueOf(resourceId));

        try (var owner = client()) {
            login(owner, ownerId);
            var history = get(owner, "/agent/history/" + threadId);
            assertEquals(0, history.path("code").asInt(), history.toString());
            JsonNode message = history.path("data").path("messages").get(0);
            assertEquals("看看这张图", message.path("content").asText());
            // 标识按十进制字符串给出：雪花值超出 JavaScript 的安全整数范围，
            // 客户端转成 number 会丢精度
            JsonNode ids = message.path("attachmentIds");
            assertTrue(ids.isArray() && ids.size() == 1, "history must carry the attachment ids: " + ids);
            assertTrue(ids.get(0).isTextual(), "the attachment id must be a decimal string: " + ids.get(0));
            assertEquals(String.valueOf(resourceId), ids.get(0).asText());
            // 正文里不再保留源站地址
            assertFalse(message.path("content").asText().contains("acceptance.invalid"),
                    "the message body must not keep the origin address");
        }
    }

    // ---- 13.3 回收与明确删除 ----

    @Test
    void unboundTemporaryImagesAreReclaimedAfterTheRetentionWindow() throws Exception {
        long expired = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/expired.png");
        jdbc.update("UPDATE picture_file SET createTime = DATE_SUB(NOW(), INTERVAL 25 HOUR) WHERE id = ?", expired);
        long fresh = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/fresh.png");

        int before = recorder.calls().size();
        List<com.yuwen.visionspace.model.entity.PictureFile> candidates =
                agentAttachmentService.listReclaimCandidates(100);
        assertTrue(candidates.stream().anyMatch(c -> c.getId().equals(expired)),
                "the expired temporary image must be a reclaim candidate: " + candidates);
        assertFalse(candidates.stream().anyMatch(c -> c.getId().equals(fresh)),
                "a fresh temporary image must not be a reclaim candidate");

        assertTrue(agentAttachmentService.reclaimOne(expired));
        assertEquals(2, resourceStatus(expired), "a reclaimed resource is deleted");
        assertTrue(recorder.calls().subList(before, recorder.calls().size()).stream()
                        .anyMatch(call -> "DELETE".equals(call.method())),
                "reclaiming must delete the stored object");
        assertEquals(0, resourceStatus(fresh), "a fresh temporary image is untouched");
    }

    @Test
    void boundImagesAreNeverReclaimed() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/bound.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));
        jdbc.update("UPDATE picture_file SET createTime = DATE_SUB(NOW(), INTERVAL 48 HOUR) WHERE id = ?", resourceId);

        int before = recorder.calls().size();
        assertFalse(agentAttachmentService.reclaimOne(resourceId), "a bound image must not be reclaimed");
        assertEquals(before, recorder.calls().size(), "a bound image must not touch storage");
        assertEquals(1, resourceStatus(resourceId), "a bound image stays active");
    }

    @Test
    void deletingTheSessionStopsAccessToItsImages() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/h.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            var deleted = delete(owner, "/agent/session/" + threadId);
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
            assertTrue(recorder.calls().subList(before, recorder.calls().size()).stream()
                            .anyMatch(call -> "DELETE".equals(call.method())),
                    "deleting a session must clean the objects it exclusively referenced");
            assertEquals(2, resourceStatus(resourceId));
            // 会话被删除后，历史与附件都不再可访问
            assertRejected(get(owner, "/agent/history/" + threadId));
            assertRejected(get(owner, "/agent/attachment/" + resourceId + "/content?sessionId=" + threadId));
        }
    }

    @Test
    void deletingTheImageStopsAccessToIt() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/i.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        try (var owner = client()) {
            login(owner, ownerId);
            var deleted = post(owner, "/agent/attachment/" + resourceId + "/delete", Map.of());
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
            assertEquals(0, bindingCount(resourceId), "deleting an image releases its bindings");
            assertRejected(get(owner, "/agent/attachment/" + resourceId + "/content?sessionId=" + threadId));
        }
    }

    // ---- 13.4 共享引用与可重试删除 ----

    @Test
    void deletingOneSessionKeepsImagesSharedWithAnotherSession() throws Exception {
        String firstThread = seedSession(ownerId, "first thread");
        String secondThread = seedSession(ownerId, "second thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/shared.png");
        agentAttachmentService.bind(firstThread, ownerId, List.of(resourceId));
        agentAttachmentService.bind(secondThread, ownerId, List.of(resourceId));

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            var deleted = delete(owner, "/agent/session/" + firstThread);
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
            assertEquals(before, recorder.calls().size(),
                    "an image shared with another session must not be cleaned up");
            assertEquals(1, resourceStatus(resourceId), "a shared image stays active");
            // 另一个会话仍然可以访问
            var allowed = getBytes(owner, "/agent/attachment/" + resourceId + "/content?sessionId=" + secondThread);
            assertEquals(200, allowed.statusCode());
        }
    }

    @Test
    void storageDeleteFailureKeepsARetryableState() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/j.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        RecordingStorageConfiguration.failDeletes(true);
        try (var owner = client()) {
            login(owner, ownerId);
            var deleted = post(owner, "/agent/attachment/" + resourceId + "/delete", Map.of());
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
        } finally {
            RecordingStorageConfiguration.failDeletes(false);
        }

        // 删除未确认：保留可重试状态，记录没有被静默丢弃
        assertEquals(1, deleteState(resourceId), "the deletion must be kept as retryable");
        assertEquals(1, deleteRetryCount(resourceId));
        assertNotNull(jdbc.queryForObject(
                "SELECT deleteNextRetryTime FROM picture_file WHERE id = ?", Date.class, resourceId),
                "a retryable deletion must have a next retry time");

        // 重试成功后状态收敛
        jdbc.update("UPDATE picture_file SET deleteNextRetryTime = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?",
                resourceId);
        assertTrue(agentAttachmentService.retryDeleteOne(resourceId));
        assertEquals(2, resourceStatus(resourceId), "a confirmed deletion marks the resource deleted");
        assertEquals(2, deleteState(resourceId));
    }

    @Test
    void crossUserDeleteHasNoSideEffects() throws Exception {
        String threadId = seedSession(ownerId, "own thread");
        long resourceId = seedAgentResource(ownerId, "https://acceptance.invalid/agent-chat/o/k.png");
        agentAttachmentService.bind(threadId, ownerId, List.of(resourceId));

        try (var stranger = client()) {
            login(stranger, strangerId);
            int before = recorder.calls().size();
            assertRejected(post(stranger, "/agent/attachment/" + resourceId + "/delete", Map.of()));
            assertRejected(delete(stranger, "/agent/session/" + threadId));
            assertEquals(before, recorder.calls().size(), "cross-user deletion must not touch storage");
            assertEquals(1, resourceStatus(resourceId));
            assertEquals(1, bindingCount(resourceId));
        }
    }

    // ---- assertions ----

    private int bindingCount(long resourceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_session_attachment WHERE resourceId = ? AND status = 0",
                Integer.class, resourceId);
        return count == null ? 0 : count;
    }

    private int resourceStatus(long resourceId) {
        return jdbc.queryForObject("SELECT status FROM picture_file WHERE id = ?", Integer.class, resourceId);
    }

    private int deleteState(long resourceId) {
        return jdbc.queryForObject("SELECT deleteState FROM picture_file WHERE id = ?", Integer.class, resourceId);
    }

    private int deleteRetryCount(long resourceId) {
        return jdbc.queryForObject("SELECT deleteRetryCount FROM picture_file WHERE id = ?", Integer.class, resourceId);
    }

    private boolean isRejected(JsonNode response) {
        int code = response.path("code").asInt();
        return code == 40100 || code == 40101;
    }

    private void assertRejected(JsonNode response) {
        assertTrue(isRejected(response), "expected authorization rejection: " + response);
    }

    // ---- fixtures ----

    private String seedSession(long userId, String title) {
        String threadId = "it-attach-" + UUID.randomUUID();
        threadIds.add(threadId);
        jdbc.update("""
                INSERT INTO agent_session (sessionId, userId, title, status, lastMessage, messageCount,
                                           createdTime, updatedTime, isDelete)
                VALUES (?, ?, ?, 1, '', 0, NOW(), NOW(), 0)
                """, threadId, userId, title);
        return threadId;
    }

    private long seedAgentResource(long userId, String url) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform, objectKey,
                                          url, fileSize, status, deleteState, deleteRetryCount, createTime, updateTime)
                VALUES (?, NULL, NULL, 'agent-chat', 'original', ?, ?, ?, 128, 0, 0, 0, NOW(), NOW())
                """, userId, RecordingStorageConfiguration.PLATFORM, url, url);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM picture_file WHERE url = ?", Long.class, url);
        assertNotNull(id);
        return id;
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

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path)).GET().build()).body());
    }

    private HttpResponse<byte[]> getBytes(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> body) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build()).body());
    }

    private JsonNode delete(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .DELETE().build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
