package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 空间唯一性的双实例验收：实例 A 是本测试进程，实例 B 是脚本启动的真实第二 JVM
 * （见 {@link VipSecondInstanceMain}，端口经系统属性注入），两实例共享同一隔离 MySQL。
 *
 * <p>同一用户在两个实例上同时创建同类型空间，必须恰好成功一次，失败方拿到明确的
 * 业务结果（50001）而不是系统错误或重复数据。唯一性由数据库的
 * {@code uk_space_active_key} 决定，不依赖任何进程内锁。
 *
 * <p>环境缺少第二实例端口时显式标记 BLOCKED_ENV，不静默跳过、也不冒充通过。
 */
@IsolatedSpringBootTest
class HttpSpaceDualInstanceTest {

    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentSpaceCreationAcrossTwoRealInstancesSucceedsExactlyOnce() throws Exception {
        int secondPort = requiredSecondPort();
        String account = "space_dual_" + System.nanoTime();
        long userId;
        try (var client = client()) {
            var registered = post(client, port, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, registered.path("code").asInt(), registered.toString());
            userId = registered.path("data").asLong();
        }
        try (var primary = newSession(port, account);
             var secondary = newSession(secondPort, account)) {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<JsonNode> fa = pool.submit(() -> {
                    start.await();
                    return createTeamSpace(primary);
                });
                Future<JsonNode> fb = pool.submit(() -> {
                    start.await();
                    return createTeamSpace(secondary);
                });
                start.countDown();
                JsonNode a = fa.get(60, TimeUnit.SECONDS);
                JsonNode b = fb.get(60, TimeUnit.SECONDS);

                int successes = (a.path("code").asInt() == 0 ? 1 : 0) + (b.path("code").asInt() == 0 ? 1 : 0);
                assertEquals(1, successes, "双实例并发创建同类空间必须恰好成功一次: " + a + " / " + b);
                // 失败方必须是明确的业务结果，不是系统错误
                for (JsonNode result : new JsonNode[]{a, b}) {
                    if (result.path("code").asInt() != 0) {
                        assertEquals(50001, result.path("code").asInt(),
                                "落败侧必须收到业务错误码: " + result);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }
        // 恰好一条有效空间，且创建者成员记录与空间一致提交
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                Integer.class, userId), "双实例并发只能留下一条有效团队空间");
        Long spaceId = jdbc.queryForObject(
                "SELECT id FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                Long.class, userId);
        assertEquals("admin", jdbc.queryForObject(
                "SELECT spaceRole FROM space_user WHERE spaceId = ? AND userId = ?",
                String.class, spaceId, userId), "创建者的团队管理员成员记录必须一并提交");

        // 清理：本用例注册的用户只有这一条空间
        jdbc.update("DELETE FROM space_user WHERE userId = ?", userId);
        jdbc.update("DELETE FROM space WHERE userId = ?", userId);
    }

    @Test
    void deletionOnOneInstanceAllowsRecreationOnTheOther() throws Exception {
        int secondPort = requiredSecondPort();
        String account = "space_dual_rebuild_" + System.nanoTime();
        long userId;
        try (var client = client()) {
            var registered = post(client, port, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, registered.path("code").asInt(), registered.toString());
            userId = registered.path("data").asLong();
        }
        try (var primary = newSession(port, account);
             var secondary = newSession(secondPort, account)) {
            // 在 A 上创建，在 B 上删除，再在 B 上重建：唯一占位必须随删除一起释放
            var created = createTeamSpace(primary);
            assertEquals(0, created.path("code").asInt(), created.toString());
            long spaceId = created.path("data").asLong();

            var deleted = post(secondary.client, secondPort, "/space/delete", Map.of("id", spaceId));
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());

            var rebuilt = createTeamSpace(secondary);
            assertEquals(0, rebuilt.path("code").asInt(),
                    "跨实例删除后必须能在另一实例上重建同类空间: " + rebuilt);
            assertNotEquals(spaceId, rebuilt.path("data").asLong(),
                    "重建必须是一条新记录，不是复用了已删除的那条");
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                Integer.class, userId), "删除后重建只应留下一条有效空间");

        jdbc.update("DELETE FROM space_user WHERE userId = ?", userId);
        jdbc.update("DELETE FROM space WHERE userId = ?", userId);
    }

    // ---- helpers ----

    private JsonNode createTeamSpace(Session session) throws Exception {
        return post(session.client, session.port, "/space/add",
                Map.of("spaceName", "dual-" + System.nanoTime(), "spaceType", 1));
    }

    private int requiredSecondPort() {
        int secondPort = Integer.getInteger("vs.acceptance.second-port", 0);
        if (secondPort <= 0) {
            throw new IllegalStateException(
                    "BLOCKED_ENV: 双实例验收需要 -Dvs.acceptance.second-port=<第二实例端口>"
                            + "（由脚本启动 VipSecondInstanceMain 注入）");
        }
        return secondPort;
    }

    private Session newSession(int targetPort, String account) throws Exception {
        HttpClient client = client();
        var response = post(client, targetPort, "/user/login",
                Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
        return new Session(client, targetPort);
    }

    private static final class Session implements AutoCloseable {
        final HttpClient client;
        final int port;

        Session(HttpClient client, int port) {
            this.client = client;
            this.port = port;
        }

        @Override
        public void close() {
            client.close();
        }
    }

    private JsonNode post(HttpClient client, int targetPort, String path, Map<String, ?> body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + targetPort + "/api" + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
}
