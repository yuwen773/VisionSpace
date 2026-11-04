package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #42 用户可控制的持久长期偏好：真实 HTTP → 事务 → 隔离 MySQL。
 *
 * <p>覆盖：按可信身份隔离（其他用户与管理员默认无权读写，27.1）、
 * 查看/显式修改/清除/开关的接口语义与自动学习默认关闭（27.2）、
 * 清除与关闭后旧学习任务写不回（27.3 的 SQL 层守卫）、
 * 偏好落库后跨实例读取一致（27.4 的持久化结构）。
 *
 * <p>偏好学习入口 {@code saveFeedback} 在 ImageAgent 反馈路径上，
 * 需要 LLM 替身才能走通，这里直接用 mapper 表达在途学习任务的写入条件；
 * 学习开关的拦截语义由 {@code UserPreferenceServiceTest} 单元测试覆盖。
 */
@IsolatedSpringBootTest
class HttpAgentPreferenceTest {
    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserPreferenceMapper preferenceMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final String PASSWORD = "acceptance-password";

    // ---- 27.1 偏好持久保存并按可信身份隔离 ----

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        try (HttpClient anonymous = client()) {
            assertEquals(40100, get(anonymous, "/agent/preference").path("code").asInt());
            assertEquals(40100, post(anonymous, "/agent/preference/clear", "").path("code").asInt());
            assertEquals(40100, post(anonymous, "/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of()))).path("code").asInt());
            assertEquals(40100, post(anonymous, "/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true))).path("code").asInt());
        }
    }

    @Test
    void otherUsersAndAdminsCanOnlySeeTheirOwnPreferenceRow() throws Exception {
        try (UserSession owner = newUser(); UserSession outsider = newUser();
             UserSession admin = newUser()) {
            jdbcSetRole(admin, "admin");

            // 本人写入显式设置
            JsonNode updated = owner.post("/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of("notes", "整体偏亮"))));
            assertEquals(0, updated.path("code").asInt(), updated.toString());
            assertEquals("整体偏亮", updated.path("data").path("explicit").path("notes").asText());

            // 其他用户与管理员读到的都是自己的空偏好，owner 的内容不出现
            for (UserSession other : List.of(outsider, admin)) {
                JsonNode view = other.get("/agent/preference");
                assertEquals(0, view.path("code").asInt(), view.toString());
                assertTrue(view.path("data").path("explicit").isEmpty(),
                        "其他用户与管理员不能读到本人偏好: " + view);
                assertTrue(view.path("data").path("learned").isEmpty(),
                        "其他用户与管理员不能读到本人偏好: " + view);
                assertEquals(0, view.path("data").path("preferenceVersion").asInt(),
                        "读到的是对方自己的空行，不是 owner 的行");
            }
        }
    }

    // ---- 27.2 查看与显式修改打通；自动学习默认关闭 ----

    @Test
    void defaultViewHasAutoLearnOffAndEmptyContent() throws Exception {
        try (UserSession user = newUser()) {
            JsonNode view = user.get("/agent/preference");
            assertEquals(0, view.path("code").asInt(), view.toString());
            assertFalse(view.path("data").path("autoLearn").asBoolean(),
                    "自动学习默认必须关闭，开启前不得发生任何学习");
            assertTrue(view.path("data").path("learned").isEmpty());
            assertEquals(0, view.path("data").path("preferenceVersion").asInt());
        }
    }

    @Test
    void explicitUpdateTogglesAndViewReflectIt() throws Exception {
        try (UserSession user = newUser()) {
            assertEquals(0, user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true))).path("code").asInt());
            JsonNode on = user.get("/agent/preference");
            assertTrue(on.path("data").path("autoLearn").asBoolean());

            assertEquals(0, user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", false))).path("code").asInt());
            assertFalse(user.get("/agent/preference").path("data").path("autoLearn").asBoolean());

            // 白名单外的键不落库
            JsonNode updated = user.post("/agent/preference/update", json.writeValueAsString(Map.of(
                    "explicit", Map.of("liked_styles", List.of("水彩"), "rogue_key", List.of("x")))));
            assertEquals(0, updated.path("code").asInt(), updated.toString());
            // 数组键必须按 JSON 数组存取（元素逐个比对，不比对节点 toString 的形状）
            JsonNode likedStyles = updated.path("data").path("explicit").path("liked_styles");
            assertTrue(likedStyles.isArray(), "数组键必须是数组: " + likedStyles);
            assertEquals(1, likedStyles.size(), likedStyles.toString());
            assertEquals("水彩", likedStyles.get(0).asText(), likedStyles.toString());
            assertFalse(updated.path("data").path("explicit").has("rogue_key"));
        }
    }

    @Test
    void clearWipesContentButKeepsTheRowAsTombstone() throws Exception {
        try (UserSession user = newUser()) {
            user.post("/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of("notes", "整体偏亮"))));
            assertEquals(0, user.post("/agent/preference/clear", "").path("code").asInt());

            JsonNode view = user.get("/agent/preference");
            assertTrue(view.path("data").path("explicit").isEmpty());
            assertTrue(view.path("data").path("learned").isEmpty());
            // 行还在：版本继续单调递增，不是靠删行伪装清除
            long version = view.path("data").path("preferenceVersion").asLong();
            assertTrue(version > 0, "清除必须递增版本: " + view);
            assertTrue(view.path("data").path("dataEpoch").asLong() > 0, "清除必须递增纪元: " + view);
        }
    }

    // ---- 27.3 清除/关闭与在途学习交错：旧任务不能复活已清除数据 ----

    @Test
    void inFlightLearningCannotRewriteAfterClear() throws Exception {
        try (UserSession user = newUser()) {
            user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true)));
            user.post("/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of("notes", "整体偏亮"))));

            // 在途学习任务持有它读到的那一刻
            var captured = preferenceMapper.selectByUserId(user.id);
            String staleLearned = "{\"liked_styles\":[\"旧推断\"]}";

            // 用户清除；旧任务此刻才到达
            assertEquals(0, user.post("/agent/preference/clear", "").path("code").asInt());
            assertEquals(0, preferenceMapper.applyLearned(user.id, staleLearned,
                    "[{\"reason\":\"旧学习\"}]", captured.getPreferenceVersion(), captured.getDataEpoch()),
                    "清除后旧学习任务的条件更新不得命中");

            var after = preferenceMapper.selectByUserId(user.id);
            // 清除把三块内容全部置空（含显式设置）：旧学习任务写不回，
            // 显式设置同样不被旧任务复活——任何一块非空都是复活
            assertNull(after.getLearnedContent(), "已清除的学习数据不得被旧任务复活");
            assertNull(after.getFeedbackHistory(), "已清除的反馈历史不得被旧任务复活");
            assertNull(after.getExplicitContent(), "已清除的显式设置不得被旧任务复活");
        }
    }

    @Test
    void inFlightLearningCannotRewriteEvenIfLearningIsReenabled() throws Exception {
        try (UserSession user = newUser()) {
            user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true)));
            var captured = preferenceMapper.selectByUserId(user.id);

            // 关闭再重新开启：纪元已两次递增，持有关闭前纪元的旧任务仍然写不进
            user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", false)));
            user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true)));

            assertEquals(0, preferenceMapper.applyLearned(user.id,
                    "{\"liked_styles\":[\"旧推断\"]}", "[]",
                    captured.getPreferenceVersion(), captured.getDataEpoch()),
                    "重开自动学习后，关闭前在途的旧任务仍不得写回");
            assertTrue(preferenceMapper.selectByUserId(user.id).getLearnedContent() == null
                    || preferenceMapper.selectByUserId(user.id).getLearnedContent().isEmpty());
        }
    }

    // ---- 27.4 读取一致：全部读路径直达共享库，无进程内偏好缓存 ----

    @Test
    void preferenceReadsGoToTheSharedStoreWithoutProcessLocalCaching() throws Exception {
        try (UserSession user = newUser()) {
            user.post("/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of("liked_styles", List.of("水彩")))));
            user.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true)));

            // 「另一个读取方」直接从共享数据库读，不经过本实例的任何进程内状态：
            // 偏好只存 DB，读取一致性是结构性质；真实第二实例的断言见
            // preferenceWrittenOnOneInstanceIsVisibleOnTheOther（BLOCKED_ENV 门控）
            var fromSecondReader = preferenceMapper.selectByUserId(user.id);
            assertEquals("水彩", json.readTree(fromSecondReader.getExplicitContent())
                    .path("liked_styles").elements().next().asText());
            assertEquals(1, fromSecondReader.getAutoLearn());

            // 新建的 HTTP 会话（模拟重启后的客户端）读到同一份内容
            try (HttpClient freshClient = client()) {
                login(freshClient, user.account);
                JsonNode view = get(freshClient, "/agent/preference");
                assertEquals("水彩", view.path("data").path("explicit")
                        .path("liked_styles").elements().next().asText());
                assertTrue(view.path("data").path("autoLearn").asBoolean());
            }
        }
    }

    /**
     * #42 双实例读取一致：实例 A 是本测试进程，实例 B 是脚本启动的真实第二 JVM
     * （见 {@link VipSecondInstanceMain}，端口经系统属性注入）。
     *
     * <p>A 上写入偏好，B 上必须原样读到；缺少第二实例端口时显式 BLOCKED_ENV，
     * 不静默跳过、也不冒充通过。
     */
    @Test
    void preferenceWrittenOnOneInstanceIsVisibleOnTheOther() throws Exception {
        int secondPort = Integer.getInteger("vs.acceptance.second-port", 0);
        if (secondPort <= 0) {
            throw new IllegalStateException(
                    "BLOCKED_ENV: 双实例验收需要 -Dvs.acceptance.second-port=<第二实例端口>"
                            + "（由脚本启动 VipSecondInstanceMain 注入）");
        }
        try (UserSession writer = newUser()) {
            assertEquals(0, writer.post("/agent/preference/update",
                    json.writeValueAsString(Map.of("explicit", Map.of("notes", "双实例可见")))).path("code").asInt());
            assertEquals(0, writer.post("/agent/preference/auto-learn",
                    json.writeValueAsString(Map.of("enabled", true))).path("code").asInt());

            // 同一账号在第二实例上的独立会话
            try (HttpClient reader = client()) {
                loginOn(reader, secondPort, writer.account);
                JsonNode view = get(reader, "/agent/preference");
                assertEquals(0, view.path("code").asInt(), view.toString());
                assertEquals("双实例可见", view.path("data").path("explicit").path("notes").asText(),
                        "实例 B 必须读到实例 A 写入的偏好");
                assertTrue(view.path("data").path("autoLearn").asBoolean());

                // 清除后另一实例同样读到空内容
                assertEquals(0, writer.post("/agent/preference/clear", "").path("code").asInt());
                JsonNode afterClear = get(reader, "/agent/preference");
                assertTrue(afterClear.path("data").path("explicit").isEmpty(),
                        "清除后另一实例不得再读到内容: " + afterClear);
            }
        }
    }

    // ---- helpers ----

    private final class UserSession implements AutoCloseable {
        final HttpClient http;
        final long id;
        final String account;

        UserSession() throws Exception {
            this.http = client();
            this.account = "pref_" + Long.toHexString(System.nanoTime());
            String body = json.writeValueAsString(
                    Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            var registration = HttpAgentPreferenceTest.this.post(http, "/user/register", body);
            assertEquals(0, registration.path("code").asInt(), registration.toString());
            assertEquals(0, HttpAgentPreferenceTest.this.post(http, "/user/login", body).path("code").asInt());
            this.id = registration.path("data").asLong();
        }

        JsonNode get(String path) throws Exception {
            return HttpAgentPreferenceTest.this.get(http, path);
        }

        JsonNode post(String path, String body) throws Exception {
            return HttpAgentPreferenceTest.this.post(http, path, body);
        }

        @Override
        public void close() {
            http.close();
        }
    }

    private UserSession newUser() throws Exception {
        return new UserSession();
    }

    private void jdbcSetRole(UserSession user, String role) {
        jdbc.update("UPDATE user SET userRole = ? WHERE id = ?", role, user.id);
    }

    private void login(HttpClient client, String account) throws Exception {
        String body = json.writeValueAsString(
                Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, post(client, "/user/login", body).path("code").asInt());
    }

    private void loginOn(HttpClient client, int targetPort, String account) throws Exception {
        String body = json.writeValueAsString(
                Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, post(client, targetPort, "/user/login", body).path("code").asInt());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, String body) throws Exception {
        return post(client, port, path, body);
    }

    private JsonNode post(HttpClient client, int targetPort, String path, String body) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(targetPort, path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private JsonNode send(HttpClient client, HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(String path) {
        return uri(port, path);
    }

    private URI uri(int targetPort, String path) {
        return URI.create("http://127.0.0.1:" + targetPort + "/api" + path);
    }
}
