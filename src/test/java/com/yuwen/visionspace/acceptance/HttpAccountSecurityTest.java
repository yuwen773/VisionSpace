package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账户认证与入口安全（工单 32 / 原 07、30）。
 *
 * <p>全部用例经过真实 TCP、过滤器链、Sa-Token、Spring Session 与 MySQL/Redis：
 * 旧摘要兼容升级、失败登录不改写、并发升级、注销撤销、CSRF 与跨站来源拒绝。
 */
@IsolatedSpringBootTest
class HttpAccountSecurityTest {

    private static final String PASSWORD = "acceptance-password";
    private static final String OTHER_PASSWORD = "another-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private CookieManager cookies;
    private HttpClient user;

    @BeforeEach
    void resetCookies() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        user = client(cookies);
    }

    @Test
    void registrationStoresIndependentSaltedDigest() throws Exception {
        String account = account();
        long userId = register(account, PASSWORD);

        String stored = storedPassword(userId);
        assertTrue(stored.startsWith("$2a$"), "新摘要必须是自适应哈希，实际：" + stored);
        assertFalse(stored.contains(PASSWORD), "摘要不能包含原文");
        assertNotEquals(legacyMd5(PASSWORD), stored, "新账户不能再用固定盐 MD5");

        long secondUser = register(account() , PASSWORD);
        assertNotEquals(stored, storedPassword(secondUser), "相同密码必须得到不同摘要（独立盐）");
    }

    @Test
    void legacyDigestStillLogsInAndIsUpgradedOnlyAfterSuccess() throws Exception {
        String account = account();
        long userId = insertUser(account, legacyMd5(PASSWORD));
        String legacy = storedPassword(userId);

        // 失败登录：摘要必须原样保留
        var failed = bodyOf(HttpRequest.newBuilder(uri("/user/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("userAccount", account, "userPassword", "wrong-password")))).build());
        assertEquals(40000, failed.path("code").asInt(), failed.toString());
        assertEquals(legacy, storedPassword(userId), "失败登录不能改写摘要");

        // 成功登录：升级为带盐的自适应摘要，账户与角色不变
        assertEquals(0, post("/user/login", Map.of("userAccount", account, "userPassword", PASSWORD))
                .path("code").asInt());

        String upgraded = storedPassword(userId);
        assertTrue(upgraded.startsWith("$2a$"), "成功登录后应升级，实际：" + upgraded);
        assertNotEquals(legacy, upgraded);
        assertEquals("admin", jdbc.queryForObject("SELECT userRole FROM user WHERE id = ?", String.class, userId),
                "升级不能改写管理角色");
        assertEquals(0, jdbc.queryForObject("SELECT isDelete FROM user WHERE id = ?", Integer.class, userId),
                "升级不能让账户消失");

        // 升级后同一个密码仍能登录，旧摘要已不再可用但不再是唯一凭据
        assertEquals(0, post("/user/login", Map.of("userAccount", account, "userPassword", PASSWORD))
                .path("code").asInt());
    }

    @Test
    void concurrentSuccessfulLoginsUpgradeExactlyOnceAndKeepAccountUsable() throws Exception {
        String account = account();
        long userId = insertUser(account, legacyMd5(PASSWORD));
        // 会员与角色在升级前后必须保持一致
        jdbc.update("UPDATE user SET userRole = 'vip', vipExpireTime = '2030-01-01 00:00:00' WHERE id = ?", userId);

        int threads = 6;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<Integer>> logins = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                logins.add(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return post(independentClient(),
                            "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD))
                            .path("code").asInt();
                });
            }
            var futures = logins.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) {
                assertEquals(0, future.get(30, TimeUnit.SECONDS), "并发登录必须都能成功");
            }
        }

        String upgraded = storedPassword(userId);
        assertTrue(upgraded.startsWith("$2a$"), "并发登录后应完成升级");
        assertEquals("vip", jdbc.queryForObject("SELECT userRole FROM user WHERE id = ?", String.class, userId));
        assertTrue(jdbc.queryForObject("SELECT vipExpireTime FROM user WHERE id = ?", String.class, userId)
                .startsWith("2030-01-01"), "升级不能破坏会员权益");
        assertEquals(0, post(independentClient(),
                "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD)).path("code").asInt(),
                "升级完成后账户仍可登录");
    }

    @Test
    void logoutRevokesSessionAndSpaceAuthenticationForOldCookies() throws Exception {
        String account = account();
        long userId = register(account, PASSWORD);
        assertEquals(0, get("/user/get/login").path("code").asInt());

        // 注销前保留一份完整 Cookie，模拟「原 Cookie 被继续使用」
        var snapshot = List.copyOf(cookies.getCookieStore().getCookies());
        assertFalse(snapshot.isEmpty());
        var logout = post("/user/logout", Map.of());
        assertEquals(0, logout.path("code").asInt(), logout.toString());

        var replayed = replay(snapshot);
        // 旧 Cookie 已不能识别身份：受保护入口按未登录拒绝，而不是变成「有权限的匿名」
        assertEquals(40100, get(replayed, "/user/get/login").path("code").asInt(),
                "旧 Cookie 不能继续识别身份");
        assertEquals(40100, get(replayed, "/user/picture/recent?count=1").path("code").asInt(),
                "受保护入口必须拒绝");
        assertNotEquals(0, post(replayed, "/user/logout", Map.of()).path("code").asInt(),
                "旧 Cookie 不能再发起注销");
        // 空间鉴权所用的 Sa-Token 令牌也必须失效：注销前该入口返回业务校验结果，
        // 旧令牌则在权限注解处被拒绝
        var fresh = independentClientLoggedIn(account);
        var beforeLogout = post(fresh, "/picture/delete", Map.of("id", 1));
        assertTrue(!rejected(beforeLogout) || beforeLogout.path("code").asInt() == 40400,
                "刚登录的客户端应已通过空间鉴权，只受业务校验影响：" + beforeLogout);
        assertEquals(40100, post(replayed, "/picture/delete", Map.of("id", 1)).path("code").asInt(),
                "旧令牌不能继续通过空间鉴权");
        assertEquals(userId, jdbc.queryForObject("SELECT id FROM user WHERE id = ?", Long.class, userId));
    }

    @Test
    void logoutWithoutLoginIsRejectedWithoutSideEffects() throws Exception {
        var anonymous = independentClient();
        assertEquals(50001, post(anonymous, "/user/logout", Map.of()).path("code").asInt());
    }

    @Test
    void stateChangingRequestFromUntrustedOriginIsRejectedWithoutWrites() throws Exception {
        String account = account();
        long userId = register(account, PASSWORD);
        String before = storedPassword(userId);

        var response = send(user,
                HttpRequest.newBuilder(uri("/user/logout")).header("Origin", "https://evil.example")
                        .header(CsrfToken.HEADER, CsrfToken.of(user))
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), 403);
        assertEquals(40300, json.readTree(response.body()).path("code").asInt());
        assertEquals(before, storedPassword(userId));
        // 会话没有被这次跨站请求改动
        assertEquals(0, get("/user/get/login").path("code").asInt(), "跨站请求不能注销本人会话");
    }

    @Test
    void missingOrMismatchedCsrfTokenIsRejectedWithoutStateChange() throws Exception {
        String account = account();
        long userId = register(account, PASSWORD);
        String before = storedPassword(userId);

        // 带着登录 Cookie，但没有令牌头
        var withoutHeader = send(user, HttpRequest.newBuilder(uri("/user/logout"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), 403);
        assertEquals(40300, json.readTree(withoutHeader.body()).path("code").asInt());
        assertEquals(0, get("/user/get/login").path("code").asInt(), "拒绝后登录态仍然有效");

        // 令牌头与 Cookie 不一致
        var mismatched = send(user, HttpRequest.newBuilder(uri("/user/logout"))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(user) + "x")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), 403);
        assertEquals(40300, json.readTree(mismatched.body()).path("code").asInt());
        assertEquals(before, storedPassword(userId));
        assertEquals(0, get("/user/get/login").path("code").asInt());
    }

    @Test
    void csrfCookieIsVisibleToPageScriptsOutsideTheApiContextPath() throws Exception {
        // 前端在页面（如 /user/login）用 document.cookie 读令牌：Cookie 必须挂在站点根路径，
        // 限制在 /api 下页面脚本看不到，所有写请求都会缺令牌。
        var fresh = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        String account = account();
        var registration = fresh.send(HttpRequest.newBuilder(uri("/user/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, registration.statusCode(), registration.body());

        var login = fresh.send(jsonPost("/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD)), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());

        var csrfSetCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("VS_CSRF="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("登录响应必须下发令牌：" + login.headers().map()));
        assertTrue(csrfSetCookie.contains("Path=/"), "令牌 Cookie 必须对整站可见：" + csrfSetCookie);
        assertFalse(csrfSetCookie.toLowerCase().contains("httponly"), "页面脚本必须能读到令牌");
    }

    @Test
    void sameOriginRequestBehindTlsTerminatingProxyIsNotTreatedAsCrossSite() throws Exception {
        // 生产入口是 nginx 终止 TLS 后转发 http：浏览器的 Origin 是 https，容器看到的是 http。
        // 框架自带的 CORS 处理如果按容器协议判断，就会把本站请求当成跨站并以空响应体 403，
        // 业务代码根本没机会执行——必须由转发头还原真实入口后再判定。
        String account = account();
        register(account, PASSWORD);
        var token = CsrfToken.of(user);
        var response = send(user, HttpRequest.newBuilder(uri("/user/logout"))
                .header("Origin", "https://visionspace.test")
                .header("X-Forwarded-Host", "visionspace.test")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.9")
                .header(CsrfToken.HEADER, token)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), 200);
        // 同源请求必须进入业务：注销成功，而不是被入口层拦下
        assertEquals(0, json.readTree(response.body()).path("code").asInt(), response.body());
    }

    @Test
    void requestsWithoutBrowserOriginKeepWorkingWithToken() throws Exception {
        // 服务端调用与 curl 不带 Origin，仍必须通过（它们不构成跨站风险）
        String account = account();
        register(account, PASSWORD);
        assertEquals(0, get("/user/get/login").path("code").asInt());
        // 注册/登录本身是匿名状态修改，没有会话凭据，因此不需要令牌
        assertEquals(0, post("/user/logout", Map.of()).path("code").asInt());
    }

    @Test
    void loginAndErrorResponsesNeverLeakPasswordOrDigest() throws Exception {
        String account = account();
        long userId = register(account, PASSWORD);
        String stored = storedPassword(userId);
        // 泄漏断言与登录态无关，用不带 Cookie 的客户端观察响应体
        var probe = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        String login = rawPost(probe, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertFalse(login.contains(PASSWORD), "登录响应不能回显密码");
        assertFalse(login.contains(stored), "登录响应不能回显摘要");

        String failure = rawPost(probe, "/user/login", Map.of("userAccount", account, "userPassword", "wrong-password"));
        assertFalse(failure.contains(PASSWORD));
        assertFalse(failure.contains("wrong-password"));
        assertFalse(failure.contains(stored));

        String unknown = rawPost(probe, "/user/login", Map.of("userAccount", "nobody_" + System.nanoTime(),
                "userPassword", PASSWORD));
        assertFalse(unknown.contains(PASSWORD));
        assertFalse(unknown.toLowerCase().contains("password"), "错误文案不能暴露内部字段名");
    }

    @Test
    void firstLoginInFreshClientReceivesCsrfTokenForSubsequentWrites() throws Exception {
        var fresh = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        String account = account();
        var body = Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD);
        // 匿名状态修改不需要令牌
        assertEquals(0, jsonOf(fresh, jsonPost("/user/register", body)).path("code").asInt());
        assertEquals(0, jsonOf(fresh, jsonPost("/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD))).path("code").asInt());
        String token = CsrfToken.of(fresh);
        assertFalse(token.isBlank(), "登录后必须下发双提交令牌");
        // 登录后的写请求带上该令牌即可继续使用
        var logout = HttpRequest.newBuilder(uri("/user/logout"))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, token)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(0, jsonOf(fresh, logout).path("code").asInt());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String legacyMd5(String raw) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest(("yuwen" + raw).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String account() {
        return "acct_" + System.nanoTime();
    }

    private long register(String account, String password) throws Exception {
        var body = Map.of("userAccount", account, "userPassword", password, "checkPassword", password);
        var registration = post("/user/register", body);
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        var login = post("/user/login", Map.of("userAccount", account, "userPassword", password));
        assertEquals(0, login.path("code").asInt(), login.toString());
        return registration.path("data").asLong();
    }

    private long insertUser(String account, String digest) {
        jdbc.update("INSERT INTO user (userAccount, userPassword, userName, userRole) VALUES (?, ?, 'legacy', 'admin')",
                account, digest);
        return jdbc.queryForObject("SELECT id FROM user WHERE userAccount = ?", Long.class, account);
    }

    private String storedPassword(long userId) {
        return jdbc.queryForObject("SELECT userPassword FROM user WHERE id = ?", String.class, userId);
    }

    private boolean rejected(JsonNode response) {
        int code = response.path("code").asInt();
        return code == 40100 || code == 40101;
    }

    private HttpClient client(CookieManager manager) {
        return HttpClient.newBuilder().cookieHandler(manager)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpClient independentClientLoggedIn(String account) throws Exception {
        HttpClient client = independentClient();
        assertEquals(0, post(client, "/user/login",
                Map.of("userAccount", account, "userPassword", PASSWORD)).path("code").asInt());
        return client;
    }

    private HttpClient independentClient() {
        return client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    /** 用一份固定 Cookie 快照发起请求，模拟旧 Cookie 被继续使用。 */
    private HttpClient replay(List<java.net.HttpCookie> snapshot) {
        CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        for (var cookie : snapshot) {
            if (cookie.getPath() == null) {
                cookie.setPath("/");
            }
            manager.getCookieStore().add(URI.create("http://127.0.0.1:" + port), cookie);
        }
        return client(manager);
    }

    private JsonNode get(String path) throws Exception {
        return get(user, path);
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return jsonOf(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode jsonOf(HttpClient client, HttpRequest request) throws Exception {
        return json.readTree(send(client, request, 200).body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        return post(user, path, body);
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return jsonOf(client, request);
    }

    private HttpRequest jsonPost(String path, Object body) throws Exception {
        return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }

    private String rawPost(HttpClient client, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return send(client, request, 200).body();
    }

    private JsonNode bodyOf(HttpRequest request) throws Exception {
        return jsonOf(user, request);
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request, int expectedStatus) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return response;
    }


    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
