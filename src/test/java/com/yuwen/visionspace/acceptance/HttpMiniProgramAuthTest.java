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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

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
    private static final String TOKEN_QUERY = "satoken";

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
        assertEquals(TOKEN_QUERY, bind.path("data").path("tokenQueryName").asText(),
                "<image> 用的查询参数名必须一并下发");

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
    void queryTokenAlsoAuthenticatesForHeaderlessClients() throws Exception {
        String account = account();
        register(account);
        String token = bind(code("openid-g"), account, PASSWORD).path("data").path("token").asText();

        // <image> / <img> 不能带请求头，只能把令牌放进查询参数。
        // 这里用一个状态修改请求同时证明「过滤器读到了查询令牌」且不需要 CSRF 头。
        var request = HttpRequest.newBuilder(uri("/user/logout?satoken=" + token))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        var response = send(anonymous, request, 200);
        assertEquals(0, json.readTree(response.body()).path("code").asInt(), response.body());
    }

    @Test
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        var response = getWithToken("/user/get/login", "not-a-real-token");
        assertEquals(40100, response.path("code").asInt(), response.toString());
    }

    @Test
    void browserSessionStillRequiresDoubleSubmitToken() throws Exception {
        String account = account();
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var browser = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();

        var registration = post(browser, "/user/register", Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        assertEquals(0, post(browser, "/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD)).path("code").asInt());

        // 浏览器会话 + 缺少 CSRF 头：必须仍然被拒绝，证明令牌分流没有放宽 Web 路径
        var withoutCsrf = browser.send(HttpRequest.newBuilder(uri("/user/logout"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, withoutCsrf.statusCode(), withoutCsrf.body());
        assertEquals(40300, json.readTree(withoutCsrf.body()).path("code").asInt());
        // 拒绝后登录态仍然有效
        assertEquals(0, get(browser, "/user/get/login").path("code").asInt());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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
