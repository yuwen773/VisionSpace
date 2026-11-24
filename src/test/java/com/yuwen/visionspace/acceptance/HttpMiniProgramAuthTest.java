package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小程序登录：openid 绑定、令牌鉴权与 CSRF 分流。
 *
 * <p>全部用例经过真实 TCP、过滤器链与 Redis：验证「未绑定可识别」、
 * 「一对一绑定不被覆盖」、「令牌客户端无需双提交且能改状态」，
 * 以及「Web 会话仍然必须带 CSRF 令牌」——最后一条用于证明分流没有放宽浏览器路径。
 */
@IsolatedSpringBootTest
@Import(WeChatStubConfiguration.class)
class HttpMiniProgramAuthTest {

    private static final String PASSWORD = "acceptance-password";
    private static final String TOKEN_HEADER = "satoken";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService userService;

    private HttpClient anonymous;

    @BeforeEach
    void setUp() {
        anonymous = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void unboundWeChatIsReportedDistinctly() throws Exception {
        var response = post("/user/wx/login", Map.of("code", code("openid-unbound")));
        assertEquals(40102, response.path("code").asInt(), response.toString());
    }

    @Test
    void bindThenTokenIdentifiesTheSameAccountAndIsIdempotent() throws Exception {
        String account = account();
        register(account);

        var bind = bind(code("openid-a"), account, PASSWORD);
        assertEquals(0, bind.path("code").asInt(), bind.toString());
        String token = bind.path("data").path("token").asText();
        assertFalse(token.isBlank(), "绑定成功必须下发令牌");
        assertEquals(TOKEN_HEADER, bind.path("data").path("tokenName").asText());
        assertFalse(bind.path("data").has("tokenQueryName"),
                "服务端不再接受 URL 里的令牌，也就不该再下发查询参数名");

        assertEquals("openid-a", boundOpenId(account), "openid 必须写入该账号");

        var me = getWithToken("/user/get/login", token);
        assertEquals(0, me.path("code").asInt(), me.toString());
        assertEquals(account, me.path("data").path("userAccount").asText());

        // 同一微信 + 同一账号重复绑定：幂等成功，仍指向同一账号
        var again = bind(code("openid-a"), account, PASSWORD);
        assertEquals(0, again.path("code").asInt(), again.toString());
        assertEquals(account, again.path("data").path("user").path("userAccount").asText());
    }

    @Test
    void wrongPasswordDoesNotCreateBinding() throws Exception {
        String account = account();
        register(account);

        var response = post("/user/wx/bind", Map.of(
                "code", code("openid-b"),
                "userAccount", account,
                "userPassword", "wrong-password"));
        assertEquals(40000, response.path("code").asInt(), response.toString());
        assertNull(boundOpenId(account), "失败绑定不能写入 openid");
    }

    @Test
    void oneWeChatCannotBindTwoAccounts() throws Exception {
        String first = account();
        String second = account();
        register(first);
        register(second);

        assertEquals(0, bind(code("openid-c"), first, PASSWORD).path("code").asInt());
        var secondBind = bind(code("openid-c"), second, PASSWORD);
        assertEquals(40103, secondBind.path("code").asInt(), secondBind.toString());
        assertEquals("openid-c", boundOpenId(first), "既有绑定不能被覆盖");
        assertNull(boundOpenId(second), "另一个账号不能被顺带绑定");
    }

    @Test
    void oneAccountCannotBindTwoWeChats() throws Exception {
        String account = account();
        register(account);

        assertEquals(0, bind(code("openid-d"), account, PASSWORD).path("code").asInt());
        var override = bind(code("openid-e"), account, PASSWORD);
        assertEquals(40103, override.path("code").asInt(), override.toString());
        assertEquals("openid-d", boundOpenId(account), "账号已有绑定时不能被第二个微信覆盖");
    }

    @Test
    void deletingUserReleasesTheWeChatBinding() throws Exception {
        String account = account();
        String replacement = account();
        register(account);
        register(replacement);
        assertEquals(0, bind(code("openid-h"), account, PASSWORD).path("code").asInt());
        Long userId = jdbc.queryForObject(
                "SELECT id FROM user WHERE userAccount = ?", Long.class, account);

        assertTrue(userService.deleteUser(userId), "删除账号必须成功");

        // 软删行仍然占着唯一索引：openid 必须随删除一起置空。
        // 否则这个微信登录只会拿到 40102，换绑新账号又撞 40103，只能人工改库。
        assertNull(boundOpenId(account), "账号删除后必须释放 openid");
        var rebind = bind(code("openid-h"), replacement, PASSWORD);
        assertEquals(0, rebind.path("code").asInt(), rebind.toString());
        assertEquals("openid-h", boundOpenId(replacement), "openid 必须能绑到新账号");
    }

    @Test
    void tokenClientChangesStateWithoutDoubleSubmitToken() throws Exception {
        String account = account();
        register(account);
        String token = bind(code("openid-f"), account, PASSWORD).path("data").path("token").asText();

        // 只有令牌、没有 CSRF 头：令牌客户端不应被双提交防护拦下
        var logout = postWithToken("/user/logout", token, Map.of());
        assertEquals(0, logout.path("code").asInt(), logout.toString());
        // 注销后令牌失效
        assertEquals(40100, getWithToken("/user/get/login", token).path("code").asInt());
    }

    @Test
    void queryTokenIsRejected() throws Exception {
        String account = account();
        register(account);
        String token = bind(code("openid-g"), account, PASSWORD).path("data").path("token").asText();

        // 令牌只认请求头：完整的登录凭据不能出现在 URL 里（会进日志、Referer 与图片缓存）
        assertEquals(40100, get(anonymous, "/user/get/login?satoken=" + token).path("code").asInt(),
                "查询参数里的令牌不能换到身份");

        // Sa-Token 自身的请求参数读取也必须关闭，否则状态修改请求会绕过这条规则
        var request = HttpRequest.newBuilder(uri("/user/logout?satoken=" + token))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        var response = jsonOf(anonymous, request);
        assertEquals(50001, response.path("code").asInt(), response.toString());
        assertEquals("未登录", response.path("message").asText(), "查询参数令牌必须按未登录处理");

        // 令牌本身没被这次尝试撤销，带请求头仍然可用
        assertEquals(0, getWithToken("/user/get/login", token).path("code").asInt());
    }

    @Test
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        var response = getWithToken("/user/get/login", "not-a-real-token");
        assertEquals(40100, response.path("code").asInt(), response.toString());
    }

    @Test
    void browserSessionStillRequiresDoubleSubmitToken() throws Exception {
        String account = account();
        Browser browser = browserSession(account);

        // 浏览器会话 + 缺少 CSRF 头：必须仍然被拒绝，证明令牌分流没有放宽 Web 路径
        var withoutCsrf = browser.send("/user/logout", Map.of());
        assertEquals(403, withoutCsrf.statusCode(), withoutCsrf.body());
        assertEquals(40300, json.readTree(withoutCsrf.body()).path("code").asInt());
        // 拒绝后登录态仍然有效
        assertEquals(0, get(browser.client(), "/user/get/login").path("code").asInt());
    }

    @Test
    void forgedTokenHeaderCannotDowngradeCsrfForABrowserSession() throws Exception {
        String account = account();
        Browser browser = browserSession(account);

        // 浏览器会话 + 伪造的令牌头：伪造头解析不出身份，凭据类型仍是 SESSION，
        // 因此不能借它跳过双提交校验
        var forged = browser.send("/user/logout", Map.of(TOKEN_HEADER, "forged-token"));
        assertEquals(403, forged.statusCode(), forged.body());
        assertEquals(0, get(browser.client(), "/user/get/login").path("code").asInt(), "拒绝后会话仍然有效");
    }

    @Test
    void miniProgramLogoutLeavesTheBrowserSessionIntact() throws Exception {
        String account = account();
        Browser browser = browserSession(account);
        String token = bind(code("openid-i"), account, PASSWORD).path("data").path("token").asText();

        // 令牌客户端退出：只撤销本次令牌，不按账号撤销
        assertEquals(0, postWithToken("/user/logout", token, Map.of()).path("code").asInt());
        assertEquals(40100, getWithToken("/user/get/login", token).path("code").asInt(),
                "本次令牌必须失效");
        assertEquals(0, get(browser.client(), "/user/get/login").path("code").asInt(),
                "小程序退出不能把同一账号的 Web 会话一起踢下线");
    }

    @Test
    void browserLogoutRevokesTheMiniProgramToken() throws Exception {
        String account = account();
        Browser browser = browserSession(account);
        String token = bind(code("openid-j"), account, PASSWORD).path("data").path("token").asText();

        // 先读一次令牌，确保 CSRF Cookie 已下发
        assertEquals(0, get(browser.client(), "/user/get/login").path("code").asInt());
        var logout = browser.send("/user/logout", Map.of("X-CSRF-Token", browser.csrfToken()));
        assertEquals(0, json.readTree(logout.body()).path("code").asInt(), logout.body());

        // Web 退出保持「按账号撤销」的既有语义：同一账号的小程序令牌一并失效
        assertEquals(40100, getWithToken("/user/get/login", token).path("code").asInt(),
                "Web 退出必须撤销该账号的令牌");
        assertEquals(40100, get(browser.client(), "/user/get/login").path("code").asInt());
    }

    @Test
    void explicitTokenWinsOverTheBrowserSession() throws Exception {
        String webAccount = account();
        Browser browser = browserSession(webAccount);
        String phoneAccount = account();
        register(phoneAccount);
        String token = bind(code("openid-k"), phoneAccount, PASSWORD).path("data").path("token").asText();

        // 同一请求同时带浏览器会话与令牌：显式令牌优先（客户端明确指定了身份）
        var request = HttpRequest.newBuilder(uri("/user/get/login"))
                .timeout(Duration.ofSeconds(10))
                .header(TOKEN_HEADER, token)
                .GET().build();
        var response = jsonOf(browser.client(), request);
        assertEquals(0, response.path("code").asInt(), response.toString());
        assertEquals(phoneAccount, response.path("data").path("userAccount").asText(),
                "显式令牌必须优先于浏览器会话");
    }

    @Test
    void concurrentBindOfTheSameOpenIdBindsExactlyOneAccount() throws Exception {
        String first = account();
        String second = account();
        register(first);
        register(second);

        // 两个账号同时抢同一个 openid：唯一索引兜底，只能有一个成功
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firstBind = pool.submit(() -> bind(code("openid-l"), first, PASSWORD).path("code").asInt());
            var secondBind = pool.submit(() -> bind(code("openid-l"), second, PASSWORD).path("code").asInt());
            var codes = List.of(firstBind.get(20, TimeUnit.SECONDS), secondBind.get(20, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertEquals(List.of(0, 40103), codes, "并发绑定必须恰好一胜一败");
        } finally {
            pool.shutdownNow();
        }
        String boundFirst = boundOpenId(first);
        String boundSecond = boundOpenId(second);
        assertTrue("openid-l".equals(boundFirst) ^ "openid-l".equals(boundSecond),
                "openid 必须恰好落在其中一个账号上");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * 带 Cookie 容器的浏览器客户端。
     *
     * <p>CSRF 令牌从 Cookie 读、放进请求头，和真实前端的双提交方式一致；
     * 不带 CSRF 头也不伪造，用来证明防护确实生效。
     */
    private final class Browser {

        private final HttpClient client;

        private final CookieManager cookies;

        private Browser(HttpClient client, CookieManager cookies) {
            this.client = client;
            this.cookies = cookies;
        }

        HttpClient client() {
            return client;
        }

        String csrfToken() {
            for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
                if ("VS_CSRF".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
            throw new AssertionError("缺少 CSRF Cookie");
        }

        HttpResponse<String> send(String path, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            headers.forEach(builder::header);
            var request = builder.POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /** 注册并登录一个浏览器会话（真实 Cookie + CSRF 双提交）。 */
    private Browser browserSession(String account) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();
        assertEquals(0, post(client, "/user/register", Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD)).path("code").asInt());
        assertEquals(0, post(client, "/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD)).path("code").asInt());
        return new Browser(client, cookies);
    }

    private String code(String openId) {
        return WeChatStubConfiguration.CODE_PREFIX + openId;
    }

    private String account() {
        return "mp_" + System.nanoTime();
    }

    private void register(String account) throws Exception {
        var body = Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD);
        assertEquals(0, post(anonymous, "/user/register", body).path("code").asInt());
    }

    private JsonNode bind(String code, String account, String password) throws Exception {
        return post(anonymous, "/user/wx/bind", Map.of(
                "code", code, "userAccount", account, "userPassword", password));
    }

    private String boundOpenId(String account) {
        // 列本身允许 NULL：未绑定时必须原样返回 null，而不是伪造空串
        return jdbc.queryForObject("SELECT wxOpenId FROM user WHERE userAccount = ?", String.class, account);
    }

    private JsonNode getWithToken(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header(TOKEN_HEADER, token)
                .GET().build();
        return jsonOf(anonymous, request);
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return jsonOf(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(String path, Object body) throws Exception {
        return post(anonymous, path, body);
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return jsonOf(client, request);
    }

    private JsonNode postWithToken(String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(TOKEN_HEADER, token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return jsonOf(anonymous, request);
    }

    private JsonNode jsonOf(HttpClient client, HttpRequest request) throws Exception {
        return json.readTree(send(client, request, 200).body());
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
