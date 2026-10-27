package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * 隔离与替换、删除之间的竞态。
 *
 * <p>用闸门（latch）把"预检通过 → 隔离生效 → 继续破坏性操作"这一段固定下来，
 * 不依赖 sleep 的偶然时序：破坏性请求停在闸门上，处置请求先把隔离提交，
 * 再放行破坏性请求，断言它必须被拒绝、原记录与原对象都保留。
 *
 * <p>覆盖两条路径：删除（隔离在预检之后生效）与重新上传替换
 * （隔离在对象写入之后、数据库提交之前生效）。
 */
@IsolatedSpringBootTest
class HttpQuarantineRaceTest {

    private static final String PASSWORD = "acceptance-password";
    private static final long TEAM_SPACE = 88101;
    private static final long TEAM_PICTURE = 88102;
    private static final String ORIGINAL_OBJECT_KEY = "/race/original.jpg";
    private static final String ORIGINAL_URL = "https://origin.invalid/race-original.jpg";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    /**
     * 隔离判定的探针：删除请求的预检在这里停住，等隔离提交后再放行。
     */
    @SpyBean
    private ResourceIsolationService isolationService;

    /**
     * 对象存储的探针：重新上传写完对象后停住，等隔离提交后再放行。
     */
    @SpyBean
    private PictureStorageService storageService;

    private final AtomicBoolean preCheckArmed = new AtomicBoolean();
    private final AtomicBoolean uploadArmed = new AtomicBoolean();
    private CountDownLatch preCheckReached;
    private CountDownLatch uploadStored;
    private CountDownLatch quarantineCommitted;
    private CountDownLatch objectDeleted;

    private long ownerId;
    private long moderatorId;

    @BeforeEach
    void fixture() throws Exception {
        preCheckReached = new CountDownLatch(1);
        uploadStored = new CountDownLatch(1);
        quarantineCommitted = new CountDownLatch(1);
        objectDeleted = new CountDownLatch(1);
        preCheckArmed.set(false);
        uploadArmed.set(false);

        jdbc.update("DELETE FROM picture_file WHERE pictureId = ?", TEAM_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id = ?", TEAM_PICTURE);
        jdbc.update("DELETE FROM resource_isolation WHERE resourceId = ?", TEAM_PICTURE);
        jdbc.update("DELETE FROM space_user WHERE spaceId = ?", TEAM_SPACE);
        jdbc.update("DELETE FROM space WHERE id = ?", TEAM_SPACE);

        ownerId = register("race_owner_" + System.nanoTime());
        moderatorId = registerModerator("race_moderator_" + System.nanoTime());

        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'race-team', ?, 0, 1, 1000, 1000, 10, 1, 0)
                """, TEAM_SPACE, ownerId);
        // 空间成员关系决定空间权限：所有者必须是团队管理员，
        // 否则请求会在权限校验阶段被拒，根本到不了隔离判定
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, 'admin')",
                TEAM_SPACE, ownerId);
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, 'race-picture', ?, 10, ?, 1, 0)
                """, TEAM_PICTURE, ORIGINAL_URL, ownerId, TEAM_SPACE);
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status)
                VALUES (?, ?, ?, 'gallery', 'original', ?, ?, ?, 10, 1)
                """, ownerId, TEAM_SPACE, TEAM_PICTURE, RecordingStorageConfiguration.PLATFORM,
                ORIGINAL_OBJECT_KEY, "https://acceptance.invalid" + ORIGINAL_OBJECT_KEY);

        // 删除路径的闸门：预检返回隔离生效前的判定，然后停住等隔离提交
        doAnswer(invocation -> {
            boolean quarantined = (Boolean) invocation.callRealMethod();
            if (preCheckArmed.compareAndSet(true, false)) {
                preCheckReached.countDown();
                quarantineCommitted.await(30, TimeUnit.SECONDS);
            }
            return quarantined;
        }).when(isolationService).isQuarantined(eq("picture"), eq((Long) TEAM_PICTURE));

        // 替换路径的闸门：对象写入完成后停住，等隔离提交
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (uploadArmed.compareAndSet(true, false)) {
                uploadStored.countDown();
                quarantineCommitted.await(30, TimeUnit.SECONDS);
            }
            return result;
        }).when(storageService).putPictureObject(any(File.class), anyString(), anyString());

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            objectDeleted.countDown();
            return result;
        }).when(storageService).deleteObject(anyString(), anyString());
    }

    @Test
    void deleteIsRejectedWhenQuarantineCommitsAfterThePreCheck() throws Exception {
        preCheckArmed.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> deleted = executor.submit(() -> {
                try (var owner = client()) {
                    login(owner, ownerId);
                    return post(owner, "/picture/delete", Map.of("id", TEAM_PICTURE));
                }
            });
            assertTrue(preCheckReached.await(30, TimeUnit.SECONDS),
                    "delete must reach the isolation pre-check; request state="
                            + describe(deleted));
            // 处置人员在删除提交之前完成隔离
            quarantine(TEAM_PICTURE);
            quarantineCommitted.countDown();

            JsonNode response = deleted.get(30, TimeUnit.SECONDS);
            // 拒绝必须来自事务内的隔离判定，而不是更早的权限校验
            assertEquals(50001, response.path("code").asInt(), response.toString());
            // 隔离记录成立：处置已经生效
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM resource_isolation WHERE resourceId = ? AND status = 1",
                    Integer.class, TEAM_PICTURE), "the quarantine must be recorded");
            // 数据库最终状态：图片记录与资源记录都保留
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM picture WHERE id = ? AND isDelete = 0",
                    Integer.class, TEAM_PICTURE), "the quarantined picture must be kept");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND status = 1",
                    Integer.class, TEAM_PICTURE), "the quarantined picture file must be kept");
            assertEquals(ORIGINAL_URL, jdbc.queryForObject(
                    "SELECT url FROM picture WHERE id = ?", String.class, TEAM_PICTURE),
                    "the quarantined picture must keep its original content");
            // 原对象没有被删除
            assertFalse(objectDeleted.await(2, TimeUnit.SECONDS),
                    "the original object must not be deleted after the quarantine took effect");
            assertEquals(0, deleteCallsFor(ORIGINAL_OBJECT_KEY),
                    "the original object must not be deleted after the quarantine took effect");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void replaceIsRejectedWhenQuarantineCommitsDuringTheUpload() throws Exception {
        uploadArmed.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> uploaded = executor.submit(() -> {
                try (var owner = client()) {
                    login(owner, ownerId);
                    return upload(owner, "/picture/upload", "replacement.png", "id",
                            String.valueOf(TEAM_PICTURE));
                }
            });
            assertTrue(uploadStored.await(30, TimeUnit.SECONDS),
                    "the replacement upload must reach the object storage; request state="
                            + describe(uploaded));
            // 处置人员在替换提交之前完成隔离
            quarantine(TEAM_PICTURE);
            quarantineCommitted.countDown();

            JsonNode response = uploaded.get(30, TimeUnit.SECONDS);
            // 拒绝必须来自事务内的隔离判定，而不是更早的权限校验
            assertEquals(50001, response.path("code").asInt(), response.toString());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM resource_isolation WHERE resourceId = ? AND status = 1",
                    Integer.class, TEAM_PICTURE), "the quarantine must be recorded");
            // 数据库最终状态：原记录与原资源都保留，替换没有生效
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM picture WHERE id = ? AND isDelete = 0",
                    Integer.class, TEAM_PICTURE), "the quarantined picture must be kept");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND status = 1",
                    Integer.class, TEAM_PICTURE), "the original picture file must be kept");
            assertEquals(ORIGINAL_URL, jdbc.queryForObject(
                    "SELECT url FROM picture WHERE id = ?", String.class, TEAM_PICTURE),
                    "the quarantined picture must keep its original content");
            // 原对象没有被删除：新上传的对象被回收，原对象不动
            assertEquals(0, deleteCallsFor(ORIGINAL_OBJECT_KEY),
                    "the original object must not be deleted after the quarantine took effect");
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- helpers ----

    /**
     * 闸门没被触发时的诊断信息：请求已经返回就直接把响应打出来。
     */
    private String describe(Future<JsonNode> request) {
        if (!request.isDone()) {
            return "still running";
        }
        try {
            return String.valueOf(request.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            return "failed: " + e;
        }
    }

    private int deleteCallsFor(String objectKey) {
        return (int) recorder.calls().stream()
                .filter(call -> "DELETE".equals(call.method()))
                .filter(call -> objectKey.equals(call.body()))
                .count();
    }

    private void quarantine(long pictureId) throws Exception {
        JsonNode response = postAs(moderatorId, "/moderation/quarantine",
                Map.of("resourceType", "picture", "resourceId", pictureId, "reason", "举报"));
        assertEquals(0, response.path("code").asInt(), response.toString());
        assertEquals("QUARANTINED", response.path("data").path("status").asText());
    }

    private JsonNode postAs(long userId, String path, Object body) throws Exception {
        try (var client = client()) {
            login(client, userId);
            return post(client, path, body);
        }
    }

    /**
     * 重新上传：带一个额外的表单字段（替换目标图片 id）。
     */
    private JsonNode upload(HttpClient client, String path, String filename,
                            String fieldName, String fieldValue) throws Exception {
        String boundary = "----acceptance" + System.nanoTime();
        byte[] content = "acceptance-image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n"
                + fieldValue + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = client.send(HttpRequest.newBuilder(uri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private long register(String account) throws Exception {
        try (var client = client()) {
            var response = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, response.path("code").asInt(), response.toString());
            return response.path("data").asLong();
        }
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

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        return json.readTree(client.send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
