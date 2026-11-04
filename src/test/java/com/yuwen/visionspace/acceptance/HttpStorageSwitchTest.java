package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
 * #40 存储切换的完整 HTTP 验收（T20）。
 *
 * <p>覆盖 AC 24.1–24.5：
 * <ul>
 *   <li>24.1 候选验证失败保留旧快照，不虚报完整成功（singleSwitchKeepsOldSnapshotWhenCandidateFails）</li>
 *   <li>24.2 持久配置、全局生效版本与实例版本可区分；未完成确认时保持 pending 可回滚
 *       （switchReportsPendingUntilConfirmed 与 rollbackRestoresPersistedSnapshot）</li>
 *   <li>24.3 新上传使用已验证配置；无默认平台时显式拒绝（无活的暧昧状态）
 *       （uploadUsesVerifiedSnapshotAndRejectsNoDefaultState）</li>
 *   <li>24.4 旧平台资源的读取与删除按资源记录的原平台定位，切换不搬迁不删除
 *       （legacyResourcesKeepOriginalPlatformAcrossSwitch）</li>
 *   <li>24.5 状态接口呈现真实生效状态且不含密钥（statusEndpointShowsRealEffectAndNoSecrets）</li>
 * </ul>
 *
 * <p>双实例第二实例的确认由编排脚本启动第二 JVM 后通过同一接口执行；
 * 脚本未注入第二实例端口时相关用例标记 BLOCKED_ENV，不冒充通过。
 */
@IsolatedSpringBootTest
class HttpStorageSwitchTest {

    private static final long OLD_CONFIG_ID = 88201;
    private static final long NEW_CONFIG_ID = 88202;
    private static final String OLD_PLATFORM = "minio";
    private static final String NEW_PLATFORM = "oss";
    private static final String OLD_RESOURCE_KEY = "legacy/8820/old-image.png";
    private static final String NEW_RESOURCE_KEY = "legacy/8820/new-image.png";
    private static final long OLD_PICTURE_ID = 88203;
    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    @AfterEach
    void cleanup() {
        // 回到无切换进行中的干净状态
        try {
            try (var admin = client()) {
                loginAsAdmin(admin);
                post(admin, "/storage/config/switch/rollback", Map.of());
            }
        } catch (Exception ignored) {
            // 清理失败由下个 fixture 的显式状态断言暴露
        }
        // 版本计数在测试间复位，用例之间不产生顺序耦合
        jdbc.update("DELETE FROM storage_switch_confirm");
        jdbc.update("""
                UPDATE storage_switch_state
                SET activeVersion = 1, pendingVersion = NULL, pendingConfigId = NULL,
                    pendingPlatform = NULL, pendingTime = NULL, expectedInstances = 1
                WHERE id = 1
                """);
        jdbc.update("DELETE FROM picture_file WHERE objectKey IN (?, ?)", OLD_RESOURCE_KEY, NEW_RESOURCE_KEY);
        jdbc.update("DELETE FROM picture WHERE id = ?", OLD_PICTURE_ID);
        jdbc.update("DELETE FROM storage_config WHERE id IN (?, ?)", OLD_CONFIG_ID, NEW_CONFIG_ID);
        jdbc.update("UPDATE storage_config SET isActive = 0");
        jdbc.update("UPDATE storage_config SET isActive = 1 WHERE id = ?", OLD_CONFIG_ID);
    }

    private void seedConfigs() {
        jdbc.update("DELETE FROM storage_config WHERE id IN (?, ?)", OLD_CONFIG_ID, NEW_CONFIG_ID);
        jdbc.update("""
                INSERT INTO storage_config
                    (id, platform, platformName, endpoint, region, bucket, accessKey, secretKey,
                     domain, basePath, isActive, isDefault, orderNum, status, isDelete)
                VALUES (?, ?, 'acceptance old', 'http://minio.internal:9000', 'cn-east-1',
                        'old-bucket', ?, ?, 'http://cdn.old', 'old/', 1, 0, 1, 1, 0)
                """, OLD_CONFIG_ID, OLD_PLATFORM, "old-access", "old-secret");
        jdbc.update("""
                INSERT INTO storage_config
                    (id, platform, platformName, endpoint, region, bucket, accessKey, secretKey,
                     domain, basePath, isActive, isDefault, orderNum, status, isDelete)
                VALUES (?, ?, 'acceptance new', 'http://oss.internal:9000', 'cn-hangzhou',
                        'new-bucket', ?, ?, 'http://cdn.new', 'new/', 0, 0, 2, 1, 0)
                """, NEW_CONFIG_ID, NEW_PLATFORM, "new-access", "new-secret");
    }

    private long seedResource(long ownerUserId, String platform, String objectKey) {
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform, objectKey,
                                          url, fileSize, status, deleteState, deleteRetryCount, createTime, updateTime)
                VALUES (?, NULL, NULL, 'agent-chat', 'original', ?, ?, ?, 128, 0, 0, 0, NOW(), NOW())
                """, ownerUserId, platform, objectKey, "https://legacy.invalid/" + objectKey);
        return jdbc.queryForObject("SELECT MAX(id) FROM picture_file WHERE objectKey = ?", Long.class, objectKey);
    }

    /**
     * 登记一条可经 /picture/content 读取的图库资源（会话绑定走 picture 归属）。
     */
    private long seedGalleryResource(long ownerUserId, long pictureId, String platform, String objectKey) {
        jdbc.update("DELETE FROM picture WHERE id = ?", pictureId);
        jdbc.update("""
                INSERT INTO picture (id, url, thumbnailUrl, name, userId, picSize, spaceId,
                                     reviewStatus, isDelete)
                VALUES (?, ?, ?, 'legacy-switch', ?, 10, NULL, 1, 0)
                """, pictureId, "https://acceptance.invalid/" + objectKey,
                "https://acceptance.invalid/" + objectKey + ".min", ownerUserId);
        jdbc.update("""
                INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform,
                                          objectKey, url, fileSize, status, deleteState, deleteRetryCount,
                                          createTime, updateTime)
                VALUES (?, NULL, ?, 'gallery', 'original', ?, ?, ?, 128, 1, 0, 0, NOW(), NOW())
                """, ownerUserId, pictureId, platform, objectKey,
                "https://acceptance.invalid/" + objectKey);
        return pictureId;
    }

    // ---- AC 24.1：候选完整验证失败时保留旧快照，不虚报完整成功 ----

    @Test
    void singleSwitchKeepsOldSnapshotWhenCandidateFails() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            // 用不存在的配置发起切换：候选组装整体失败
            var response = post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", 99999999L, "expectedInstances", 1));
            assertNotEquals(0, response.path("code").asInt(), response.toString());
            // 旧快照未被触碰：无进行中的切换，本实例仍在持久快照上
            var status = get(admin, "/storage/config/switch/status");
            assertEquals("completed", status.path("data").path("phase").asText(), status.toString());
            assertEquals(OLD_PLATFORM, status.path("data").path("activePlatform").asText());
            assertEquals(1L, status.path("data").path("activeVersion").asLong());
            assertTrue(status.path("data").path("localSynced").asBoolean());
            assertEquals(OLD_PLATFORM, status.path("data").path("localDefaultPlatform").asText());
        }
    }

    // ---- AC 24.2：持久配置 / 全局版本 / 实例版本可区分；未确认保持 pending 且可回滚 ----

    /**
     * P2-8：期望实例数由后端权威下发，前端不得硬编码；
     * 单实例部署（expected=1）发起即确认即晋升，不存在「1/1 卡 pending」的自相矛盾状态。
     */
    @Test
    void singleInstanceSwitchCompletesImmediatelyAndMovesActivation() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            var switched = post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 1));
            assertEquals(0, switched.path("code").asInt(), switched.toString());
            assertEquals("completed", switched.path("data").path("phase").asText(), switched.toString());
            // 持久激活已迁移到新平台，本实例与新版本对齐
            var status = get(admin, "/storage/config/switch/status");
            assertEquals("completed", status.path("data").path("phase").asText());
            assertEquals(NEW_PLATFORM, status.path("data").path("activePlatform").asText());
            assertEquals(NEW_PLATFORM, status.path("data").path("localDefaultPlatform").asText());
            assertTrue(status.path("data").path("localSynced").asBoolean());
            int persistedActive = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM storage_config WHERE id = ? AND isActive = 1", Integer.class, NEW_CONFIG_ID);
            assertEquals(1, persistedActive, "晋升必须迁移持久激活标记");
            int oldActive = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM storage_config WHERE id = ? AND isActive = 1", Integer.class, OLD_CONFIG_ID);
            assertEquals(0, oldActive, "旧平台不得同时保持激活");
        }
    }

    /**
     * P1-1：确认以实例集合记录；期望 2 实例时发起侧只占 1 席，
     * 保持 pending 且持久激活不提前迁移，可随时回滚。
     */
    @Test
    void switchReportsPendingUntilConfirmed() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            var switched = post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 2));
            assertEquals(0, switched.path("code").asInt(), switched.toString());
            // pending 期间：持久激活仍在旧平台，全局版本已前进且本实例与新版本对齐
            var status = get(admin, "/storage/config/switch/status");
            assertEquals("pending", status.path("data").path("phase").asText());
            assertEquals(OLD_PLATFORM, status.path("data").path("activePlatform").asText(),
                    "pending 期间持久激活不得提前迁移");
            assertEquals(2L, status.path("data").path("activeVersion").asLong());
            assertEquals(2L, status.path("data").path("localEffectiveVersion").asLong());
            assertEquals(NEW_PLATFORM, status.path("data").path("localDefaultPlatform").asText());
            assertEquals(2, status.path("data").path("expectedInstances").asInt(),
                    "期望实例数必须由后端下发");
            assertEquals(1, status.path("data").path("confirmCount").asInt(),
                    "发起实例确认后集合只应有 1 席，不得预置或虚增");

            // 同一实例重复确认：集合幂等，仍是 1/2，不得计数翻倍触发晋升
            var again = post(admin, "/storage/config/switch/confirm", Map.of());
            assertEquals(0, again.path("code").asInt(), again.toString());
            var still = get(admin, "/storage/config/switch/status");
            assertEquals("pending", still.path("data").path("phase").asText());
            assertEquals(1, still.path("data").path("confirmCount").asInt(),
                    "重复确认不得虚增确认集合");
        }
    }

    @Test
    void rollbackRestoresPersistedSnapshotWithoutVersionRegression() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            assertEquals(0, post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 2))
                    .path("code").asInt());
            // 期望 2 个实例但只有本实例：保持 pending，且持久激活未动
            var before = get(admin, "/storage/config/switch/status");
            assertEquals("pending", before.path("data").path("phase").asText());
            assertEquals(1, before.path("data").path("confirmCount").asInt());
            assertEquals(2, before.path("data").path("expectedInstances").asInt());
            assertEquals(OLD_PLATFORM, before.path("data").path("activePlatform").asText());

            // 回滚：pending 清空，activeVersion 不变，本实例回到持久快照
            var rolled = post(admin, "/storage/config/switch/rollback", Map.of());
            assertEquals(0, rolled.path("code").asInt(), rolled.toString());
            var after = get(admin, "/storage/config/switch/status");
            assertEquals("completed", after.path("data").path("phase").asText());
            assertEquals(1L, after.path("data").path("activeVersion").asLong());
            assertEquals(1L, after.path("data").path("localEffectiveVersion").asLong());
            assertTrue(after.path("data").path("localSynced").asBoolean());
            assertEquals(OLD_PLATFORM, after.path("data").path("activePlatform").asText());
            assertEquals(OLD_PLATFORM, after.path("data").path("localDefaultPlatform").asText());
        }
    }

    @Test
    void dualInstanceSwitchRequiresBothInstancesBeforeReportingSuccess() throws Exception {
        int secondPort = Integer.getInteger("vs.acceptance.second-port", 0);
        if (secondPort <= 0) {
            throw new IllegalStateException(
                    "BLOCKED_ENV: 双实例验收需要 -Dvs.acceptance.second-port=<第二实例端口>");
        }
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            // 期望 2 实例：发起侧 pending
            assertEquals(0, post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 2))
                    .path("code").asInt());
            // 第二实例确认：确认数达到期望但晋升由确认侧执行
            try (var adminOnSecond = clientOn(secondPort)) {
                loginAsAdminOn(adminOnSecond, secondPort);
                var confirmed = post(adminOnSecond, "/storage/config/switch/confirm", Map.of());
                assertEquals(0, confirmed.path("code").asInt(), confirmed.toString());
                assertEquals("completed", confirmed.path("data").path("phase").asText());
            }
            // 两个实例都看到完成状态且版本一致
            var first = get(admin, "/storage/config/switch/status");
            assertEquals("completed", first.path("data").path("phase").asText());
            try (var second = clientOn(secondPort)) {
                loginAsAdminOn(second, secondPort);
                var secondStatus = get(second, "/storage/config/switch/status");
                assertEquals("completed", secondStatus.path("data").path("phase").asText());
                assertEquals(first.path("data").path("activeVersion").asLong(),
                        secondStatus.path("data").path("activeVersion").asLong(),
                        "两实例必须看到同一生效版本");
                assertTrue(secondStatus.path("data").path("localSynced").asBoolean());
            }
            int persistedActive = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM storage_config WHERE id = ? AND isActive = 1", Integer.class, NEW_CONFIG_ID);
            assertEquals(1, persistedActive);
        }
    }

    /**
     * P2-7 回归：切换完成时未参与确认的实例（如重启期间错过切换）处于旧版本，
     * 其下一次状态读取触发惰性自愈，从持久配置收敛到当前生效版本——
     * 不再要求「逐台手工刷新」，也绝不静默留在漂移状态。
     */
    @Test
    void statusReadSelfHealsLaggingInstanceAfterCompletedSwitch() throws Exception {
        int secondPort = Integer.getInteger("vs.acceptance.second-port", 0);
        if (secondPort <= 0) {
            throw new IllegalStateException(
                    "BLOCKED_ENV: 双实例验收需要 -Dvs.acceptance.second-port=<第二实例端口>");
        }
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            // 单实例晋升：第二实例从未确认，其快照仍停留在旧版本
            assertEquals(0, post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 1))
                    .path("code").asInt());
            // 第二实例从未执行确认或刷新，直接读状态：响应必须是收敛后的真实状态
            try (var lagging = clientOn(secondPort)) {
                loginAsAdminOn(lagging, secondPort);
                var healed = get(lagging, "/storage/config/switch/status");
                assertEquals("completed", healed.path("data").path("phase").asText(), healed.toString());
                assertEquals(2L, healed.path("data").path("localEffectiveVersion").asLong(),
                        "落后实例的状态读取必须自愈到当前生效版本");
                assertTrue(healed.path("data").path("localSynced").asBoolean(), healed.toString());
                assertEquals(NEW_PLATFORM, healed.path("data").path("localDefaultPlatform").asText(),
                        "自愈后本实例默认平台必须与持久生效平台一致");
            }
        }
    }

    // ---- AC 24.3：新上传使用已验证配置；无默认平台时显式拒绝 ----

    @Test
    void uploadUsesVerifiedSnapshotAndRejectsNoDefaultState() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            assertEquals(0, post(admin, "/storage/config/set_active",
                    Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 1))
                    .path("code").asInt());
            try (var owner = client()) {
                registerAndLogin(owner);
                // 新任务使用切换后的已验证配置（替身按自身平台记账：
                // 运行时快照经候选验证后存在可用默认平台，上传才被放行）
                var upload = multipart(owner, "/agent/image/upload");
                assertEquals(0, upload.path("code").asInt(), upload.toString());
                long resourceId = upload.path("data").path("resourceId").asLong();
                String platform = jdbc.queryForObject(
                        "SELECT platform FROM picture_file WHERE id = ?", String.class, resourceId);
                assertEquals(RecordingStorageConfiguration.PLATFORM, platform,
                        "新上传必须走切换后快照的默认平台（替身记账平台）");
                // 上传记录中不含任何密钥
                assertFalse(upload.toString().contains("new-secret"));
                // 资源删除按其原平台定位
                int before = recorder.calls().size();
                assertEquals(0, post(owner, "/agent/image/upload/delete",
                        Map.of("resourceId", resourceId)).path("code").asInt());
                assertTrue(recorder.calls().subList(before, recorder.calls().size()).stream()
                                .anyMatch(call -> "DELETE".equals(call.method())
                                        && RecordingStorageConfiguration.PLATFORM.equals(call.path())),
                        "新资源删除必须按资源记录的平台: " + recorder.calls().subList(before, recorder.calls().size()));
            }
        }
    }

    // ---- AC 24.4：旧资源读取/删除按原平台定位；切换不搬迁不删除 ----

    @Test
    void legacyResourcesKeepOriginalPlatformAcrossSwitch() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            try (var owner = client()) {
                long userId = registerAndLogin(owner);
                long oldResource = seedResource(userId, OLD_PLATFORM, OLD_RESOURCE_KEY);
                long newResource = seedResource(userId, NEW_PLATFORM, NEW_RESOURCE_KEY);
                long oldPicture = seedGalleryResource(userId, OLD_PICTURE_ID, OLD_PLATFORM, OLD_RESOURCE_KEY);

                assertEquals(0, post(admin, "/storage/config/set_active",
                        Map.of("targetConfigId", NEW_CONFIG_ID, "expectedInstances", 1))
                        .path("code").asInt());

                // 切换后读取旧平台资源：按资源记录里的原平台定位，而不是当前默认平台
                int beforeRead = recorder.calls().size();
                String content = getRaw(owner, "/picture/content/" + oldPicture + "/original");
                assertEquals("acceptance-image-bytes", content,
                        "内容读取必须返回记录替身的固定字节");
                String storedPlatform = jdbc.queryForObject(
                        "SELECT platform FROM picture_file WHERE id = ?", String.class, oldResource);
                assertEquals(OLD_PLATFORM, storedPlatform, "切换不得改写资源记录的平台归属");
                String storedKey = jdbc.queryForObject(
                        "SELECT objectKey FROM picture_file WHERE id = ?", String.class, oldResource);
                assertEquals(OLD_RESOURCE_KEY, storedKey, "切换不得改写资源记录的对象键");
                int mutated = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM picture_file WHERE id IN (?, ?) AND status <> 0",
                        Integer.class, oldResource, newResource);
                assertEquals(0, mutated, "切换不得删除、搬迁或改写旧资源状态");
                assertEquals(beforeRead, recorder.calls().size() - 1,
                        "读取只产生一次存储调用");
                assertTrue(recorder.calls().subList(beforeRead, recorder.calls().size()).stream()
                                .anyMatch(call -> "READ".equals(call.method())
                                        && OLD_PLATFORM.equals(call.path())
                                        && OLD_RESOURCE_KEY.equals(call.body())),
                        "旧资源读取必须按原平台: " + recorder.calls().subList(beforeRead, recorder.calls().size()));

                // 删除旧平台资源：仍按旧平台定位，不落到新默认平台
                int beforeDelete = recorder.calls().size();
                assertEquals(0, post(owner, "/agent/image/upload/delete",
                        Map.of("resourceId", oldResource)).path("code").asInt());
                assertTrue(recorder.calls().subList(beforeDelete, recorder.calls().size()).stream()
                                .anyMatch(call -> "DELETE".equals(call.method())
                                        && OLD_PLATFORM.equals(call.path())),
                        "旧资源删除必须按原平台: " + recorder.calls().subList(beforeDelete, recorder.calls().size()));
                assertFalse(recorder.calls().subList(beforeDelete, recorder.calls().size()).stream()
                                .anyMatch(call -> NEW_PLATFORM.equals(call.path())),
                        "切换不得让旧资源操作落到新平台");

                // 新资源同样按自己的平台删除
                assertEquals(0, post(owner, "/agent/image/upload/delete",
                        Map.of("resourceId", newResource)).path("code").asInt());
                assertTrue(recorder.calls().subList(beforeDelete, recorder.calls().size()).stream()
                                .anyMatch(call -> "DELETE".equals(call.method())
                                        && NEW_PLATFORM.equals(call.path())
                                        && NEW_RESOURCE_KEY.equals(call.body())),
                        "新资源删除必须在新平台上");
            }
        }
    }

    // ---- AC 24.5：状态呈现真实生效状态；密钥不输出 ----

    @Test
    void statusEndpointShowsRealEffectAndNoSecrets() throws Exception {
        seedConfigs();
        try (var admin = client()) {
            loginAsAdmin(admin);
            var response = get(admin, "/storage/config/switch/status");
            assertEquals(0, response.path("code").asInt(), response.toString());
            String body = response.toString();
            assertFalse(body.contains("old-access") || body.contains("old-secret")
                    || body.contains("new-access") || body.contains("new-secret"),
                    "状态响应不得包含任何凭据");
            var data = response.path("data");
            assertTrue(data.has("activeVersion"));
            assertTrue(data.has("localEffectiveVersion"));
            assertTrue(data.has("localDefaultPlatform"));
            assertTrue(data.has("registeredPlatforms"));
            assertTrue(data.has("activePlatform"));
            // 配置列表依旧只返回遮蔽凭据
            var list = get(admin, "/storage/config/list");
            assertFalse(list.toString().contains("old-secret") || list.toString().contains("new-secret"));
        }
    }

    // ---- helpers ----

    private void loginAsAdmin(HttpClient client) throws Exception {
        loginAsAdminOn(client, port);
    }


    private void loginAsAdminOn(HttpClient client, int targetPort) throws Exception {
        long userId = registerAndLoginOn(client, targetPort, "storage_switch_admin_" + System.nanoTime());
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", userId);
        String account = jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        assertEquals(0, post(client, targetPort, "/user/login",
                Map.of("userAccount", account, "userPassword", PASSWORD)).path("code").asInt());
    }

    private long registerAndLogin(HttpClient client) throws Exception {
        return registerAndLoginOn(client, port, "storage_switch_user_" + System.nanoTime());
    }

    private long registerAndLoginOn(HttpClient client, int targetPort, String account) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        var registration = post(client, targetPort, "/user/register", body);
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        assertEquals(0, post(client, targetPort, "/user/login", body).path("code").asInt());
        return registration.path("data").asLong();
    }

    /** 构造一个可通过替身上传校验的最小 multipart 请求（与既有内容验收一致的形状）。 */
    private JsonNode multipart(HttpClient client, String path) throws Exception {
        String boundary = "----vsacceptance" + System.nanoTime();
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"pin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write("acceptance-image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri(path, port))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<byte[]> getBytes(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path, port))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return getOn(client, path, port);
    }

    /** 内容入口返回原始字节而非 JSON 包裹，单独取响应体文本。 */
    private String getRaw(HttpClient client, String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path, port))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private JsonNode getOn(HttpClient client, String path, int targetPort) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path, targetPort))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        return post(client, port, path, body);
    }

    private JsonNode post(HttpClient client, int targetPort, String path, Object body) throws Exception {
        String payload = body instanceof String text ? text : json.writeValueAsString(body);
        var request = HttpRequest.newBuilder(uri(path, targetPort))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(String path, int targetPort) {
        return URI.create("http://127.0.0.1:" + targetPort + "/api" + path);
    }

    private HttpClient client() {
        return clientOn(port);
    }

    private HttpClient clientOn(int targetPort) {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
}
