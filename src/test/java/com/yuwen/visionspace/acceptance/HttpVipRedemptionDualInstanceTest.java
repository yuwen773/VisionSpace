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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #39 双实例同码原子性：实例 A 是本测试进程，实例 B 是脚本启动的真实第二 JVM
 * （见 {@link VipSecondInstanceMain}，端口经系统属性注入）。
 *
 * <p>两实例共享同一隔离 MySQL；同一码从两侧并发兑换，必须恰好成功一次。
 * 环境缺少第二实例端口时测试显式标记 BLOCKED_ENV，不静默跳过、也不冒充通过。
 */
@IsolatedSpringBootTest
class HttpVipRedemptionDualInstanceTest {
    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private static final String PASSWORD = "acceptance-password";

    @Test
    void concurrentRedemptionAcrossTwoRealInstancesSucceedsExactlyOnce() throws Exception {
        int secondPort = requiredSecondPort();
        String code = insertAvailableCode();
        // 双侧各一个用户，同一码并发
        try (Session a = newSession(port); Session b = newSession(secondPort)) {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<JsonNode> fa = pool.submit(() -> {
                start.await();
                return redeem(a, code);
            });
            Future<JsonNode> fb = pool.submit(() -> {
                start.await();
                return redeem(b, code);
            });
            start.countDown();
            JsonNode resultA = fa.get(60, TimeUnit.SECONDS);
            JsonNode resultB = fb.get(60, TimeUnit.SECONDS);
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

            List<String> statuses = List.of(resultA.path("data").path("status").asText(), resultB.path("data").path("status").asText());
            assertEquals(1, statuses.stream().filter("success"::equals).count(),
                    "双实例并发同码必须恰好成功一次: " + statuses);
            assertTrue(statuses.contains("already_used"), "落败侧必须明确收到已用状态: " + statuses);
            // 恰好一条流水；核销状态为已用
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vip_redemption_record WHERE codeId = "
                            + "(SELECT id FROM vip_redemption_code WHERE codeHash = ?)",
                    Integer.class, VipTestHashes.sha256(code)));
            long winner = "success".equals(resultA.path("data").path("status").asText()) ? a.id : b.id;
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vip_redemption_record WHERE userId = ?", Integer.class, winner));
        }
    }

    @Test
    void sameUserConcurrentTwoCodesOnTwoInstancesExtendsExactlyTwice() throws Exception {
        int secondPort = requiredSecondPort();
        String code1 = insertAvailableCode();
        String code2 = insertAvailableCode();
        try (Session primary = newSession(port)) {
            // 同一账号在第二实例的独立会话：模拟同一用户在两个实例上的并发请求
            String account = jdbc.queryForObject(
                    "SELECT userAccount FROM user WHERE id = ?", String.class, primary.id);
            try (Session mirror = loginOn(secondPort, account)) {
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Future<JsonNode> f1 = pool.submit(() -> {
                    start.await();
                    return redeem(primary, code1);
                });
                Future<JsonNode> f2 = pool.submit(() -> {
                    start.await();
                    return redeem(mirror, code2);
                });
                start.countDown();
                JsonNode r1 = f1.get(60, TimeUnit.SECONDS);
                JsonNode r2 = f2.get(60, TimeUnit.SECONDS);
                pool.shutdown();
                assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
                assertEquals("success", r1.path("data").path("status").asText(), r1.toString());
                assertEquals("success", r2.path("data").path("status").asText(), r2.toString());
                // 同一用户两个不同码并发：两次加时都生效（无既有权益 → 现在 + 24 个月）
                java.sql.Timestamp expiry = jdbc.queryForObject(
                        "SELECT vipExpireTime FROM user WHERE id = ?", java.sql.Timestamp.class, primary.id);
                LocalDate expectedLow = LocalDate.now().plusMonths(24).minusDays(2);
                LocalDate expectedHigh = LocalDate.now().plusMonths(24).plusDays(2);
                LocalDate actual = expiry.toLocalDateTime().toLocalDate();
                assertTrue(!actual.isBefore(expectedLow) && !actual.isAfter(expectedHigh),
                        "双码并发必须各自完整加时（共 24 个月），实际 " + actual);
                assertEquals(2, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM vip_redemption_record WHERE userId = ?",
                        Integer.class, primary.id));
            }
        }
    }

    // ---- helpers ----

    private JsonNode redeem(Session session, String code) throws Exception {
        return post(session.client, session.port, "/user/exchange/vip",
                json.writeValueAsString(Map.of("vipCode", code)));
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

    private Session newSession(int targetPort) throws Exception {
        HttpClient client = client();
        String account = "vipx_" + System.nanoTime() + "_" + client.hashCode();
        String body = json.writeValueAsString(
                Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, post(client, targetPort, "/user/register", body).path("code").asInt());
        assertEquals(0, post(client, targetPort, "/user/login", body).path("code").asInt());
        long id = jdbc.queryForObject("SELECT id FROM user WHERE userAccount = ?", Long.class, account);
        return new Session(client, id, targetPort);
    }

    private Session loginOn(int targetPort, String account) throws Exception {
        HttpClient client = client();
        String body = json.writeValueAsString(
                Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, post(client, targetPort, "/user/login", body).path("code").asInt());
        long id = jdbc.queryForObject("SELECT id FROM user WHERE userAccount = ?", Long.class, account);
        return new Session(client, id, targetPort);
    }

    private static final class Session implements AutoCloseable {
        final HttpClient client;
        final long id;
        final int port;

        Session(HttpClient client, long id, int port) {
            this.client = client;
            this.id = id;
            this.port = port;
        }

        @Override
        public void close() {
            client.close();
        }
    }

    private String insertAvailableCode() {
        String code = "TEST" + Long.toHexString(System.nanoTime()) + "CODE";
        jdbc.update("INSERT INTO vip_redemption_code (codeHash, status) VALUES (?, ?)",
                VipTestHashes.sha256(code), 0);
        return code;
    }

    private JsonNode post(HttpClient client, int targetPort, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + targetPort + "/api" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
}
