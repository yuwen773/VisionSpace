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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #39 VIP 权益独立与原子兑换：真实 HTTP → 事务 → 隔离 MySQL。
 *
 * <p>覆盖：到期时间较晚者加十二个月、管理员角色保留、事务回滚不吞码、
 * 重试不重复延长、已用/无效状态表达。
 */
@IsolatedSpringBootTest
class HttpVipRedemptionTest {
    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private static final String PASSWORD = "acceptance-password";

    @Test
    void firstRedemptionGrantsTwelveMonthsFromNow() throws Exception {
        try (UserSession user = newUser()) {
            String code = insertAvailableCode();
            JsonNode result = user.redeem(code);
            assertEquals("success", result.path("data").path("status").asText(), result.toString());
            assertExpireCloseToNow(user.id, 12);
            // VIP 不经由 userRole 表达：普通用户角色保持不变
            assertEquals("user", roleOf(user.id), "VIP 权益不得改写基础角色");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vip_redemption_record WHERE userId = ?", Integer.class, user.id));
        }
    }

    @Test
    void redemptionExtendsFromTheLaterOfNowAndCurrentExpiry() throws Exception {
        // 未到期用户：从原到期时间累加，不损失已有权益（3 个月 + 12 个月 ≈ 15 个月）
        try (UserSession unexpired = newUser()) {
            jdbc.update("UPDATE user SET vipExpireTime = ? WHERE id = ?",
                    java.sql.Timestamp.valueOf(LocalDate.now().plusMonths(3).atStartOfDay()), unexpired.id);
            String code = insertAvailableCode();
            assertEquals("success", unexpired.redeem(code).path("data").path("status").asText());
            assertExpireCloseToNow(unexpired.id, 15);
        }
        // 已到期用户：从当前时间累加
        try (UserSession expired = newUser()) {
            jdbc.update("UPDATE user SET vipExpireTime = ? WHERE id = ?",
                    java.sql.Timestamp.valueOf(LocalDate.now().minusMonths(2).atStartOfDay()), expired.id);
            String code = insertAvailableCode();
            assertEquals("success", expired.redeem(code).path("data").path("status").asText());
            assertExpireCloseToNow(expired.id, 12);
        }
    }

    @Test
    void adminRedemptionKeepsTheAdminRole() throws Exception {
        try (UserSession admin = newUser()) {
            jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", admin.id);
            String code = insertAvailableCode();
            assertEquals("success", admin.redeem(code).path("data").path("status").asText());
            assertEquals("admin", roleOf(admin.id), "管理员兑换 VIP 不得改变管理角色");
            assertExpireCloseToNow(admin.id, 12);
        }
    }

    @Test
    void usedCodeReportsAlreadyUsedAndDoesNotExtendAgain() throws Exception {
        String code = insertAvailableCode();
        try (UserSession first = newUser(); UserSession second = newUser()) {
            assertEquals("success", first.redeem(code).path("data").path("status").asText());
            java.sql.Timestamp firstExpiry = expiryOf(first.id);

            JsonNode retry = second.redeem(code);
            assertEquals("already_used", retry.path("data").path("status").asText(), "同码第二次兑换必须明确报已用");
            assertEquals(firstExpiry, expiryOf(first.id), "重试不得重复延长");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vip_redemption_record WHERE codeId = "
                            + "(SELECT id FROM vip_redemption_code WHERE codeHash = ?)",
                    Integer.class, VipTestHashes.sha256(code)));
        }
    }

    @Test
    void sameUserRetryingTheSameCodeDoesNotExtendTwice() throws Exception {
        try (UserSession user = newUser()) {
            String code = insertAvailableCode();
            assertEquals("success", user.redeem(code).path("data").path("status").asText());
            java.sql.Timestamp firstExpiry = expiryOf(user.id);
            assertEquals("already_used", user.redeem(code).path("data").path("status").asText());
            assertEquals(firstExpiry, expiryOf(user.id), "同人重试同码不得再次加时");
        }
    }

    @Test
    void voidedAndUnknownCodesAreReportedAsInvalidWithoutSideEffects() throws Exception {
        try (UserSession user = newUser()) {
            String voided = insertCodeWithStatus(2);
            assertEquals("invalid", user.redeem(voided).path("data").path("status").asText());
            assertEquals("invalid", user.redeem("no-such-code-anything").path("data").path("status").asText());
            assertEquals(40000, user.redeem("").path("code").asInt());
            // 无副作用
            assertEquals(null, jdbc.queryForObject("SELECT vipExpireTime FROM user WHERE id = ?",
                    java.sql.Timestamp.class, user.id));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vip_redemption_record WHERE userId = ?", Integer.class, user.id));
        }
    }

    @Test
    void userUpdateFailureRollsBackRedemptionAndDoesNotConsumeTheCode() throws Exception {
        try (UserSession user = newUser()) {
            String code = insertAvailableCode();
            // 预插入一条指向该码的流水，使本次兑换写流水时撞 uk_codeId，
            // 模拟「核销成功后、事务内后续步骤失败」；核销必须随之回滚。
            long codeId = jdbc.queryForObject("SELECT id FROM vip_redemption_code WHERE codeHash = ?",
                    Long.class, VipTestHashes.sha256(code));
            jdbc.update("INSERT INTO vip_redemption_record (userId, codeId, codeHash, previousExpireTime, expireTime) "
                    + "VALUES (?, ?, ?, NOW(), NOW())", user.id + 1, codeId, VipTestHashes.sha256(code));

            JsonNode result = user.redeem(code);
            assertNotEquals("success", result.path("data").path("status").asText(), "事务内失败必须向调用方报错");
            assertEquals(0, jdbc.queryForObject(
                    "SELECT status FROM vip_redemption_code WHERE id = ?", Integer.class, codeId),
                    "失败回滚后码必须未被吞");
            // 清掉障碍后重试成功
            jdbc.update("DELETE FROM vip_redemption_record WHERE userId = ?", user.id + 1);
            assertEquals("success", user.redeem(code).path("data").path("status").asText(), "失败不吞码，重试必须能成功");
        }
    }

    @Test
    void loginStateCarriesVipExpireTimeIndependentlyOfRole() throws Exception {
        try (UserSession user = newUser()) {
            String code = insertAvailableCode();
            assertEquals("success", user.redeem(code).path("data").path("status").asText());
            JsonNode loginState = user.get("/user/get/login");
            assertTrue(loginState.path("data").has("vipExpireTime"), "登录态必须携带 VIP 到期时间");
            assertFalse(loginState.path("data").has("vipCode"), "登录态不得回显兑换码字段");
        }
    }

    // ---- helpers ----

    private final class UserSession implements AutoCloseable {
        final HttpClient http;
        final long id;

        UserSession() throws Exception {
            this.http = HttpVipRedemptionTest.this.client();
            this.id = registerAndLogin(http, "vip_" + System.nanoTime() + "_" + http.hashCode());
        }

        JsonNode redeem(String code) throws Exception {
            return post(http, "/user/exchange/vip", json.writeValueAsString(Map.of("vipCode", code)));
        }

        JsonNode get(String path) throws Exception {
            return HttpVipRedemptionTest.this.get(http, path);
        }

        @Override
        public void close() {
            http.close();
        }
    }

    private UserSession newUser() throws Exception {
        return new UserSession();
    }

    private String insertAvailableCode() {
        return insertCodeWithStatus(0);
    }

    private String insertCodeWithStatus(int status) {
        String code = "TEST" + Long.toHexString(System.nanoTime()) + "CODE";
        jdbc.update("INSERT INTO vip_redemption_code (codeHash, status) VALUES (?, ?)",
                VipTestHashes.sha256(code), status);
        return code;
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT userRole FROM user WHERE id = ?", String.class, userId);
    }

    private java.sql.Timestamp expiryOf(long userId) {
        return jdbc.queryForObject("SELECT vipExpireTime FROM user WHERE id = ?", java.sql.Timestamp.class, userId);
    }

    private void assertExpireCloseToNow(long userId, int monthsFromNow) {
        java.sql.Timestamp expiry = expiryOf(userId);
        LocalDate expectedLow = LocalDate.now().plusMonths(monthsFromNow).minusDays(2);
        LocalDate expectedHigh = LocalDate.now().plusMonths(monthsFromNow).plusDays(2);
        LocalDate actual = expiry.toLocalDateTime().toLocalDate();
        assertTrue(!actual.isBefore(expectedLow) && !actual.isAfter(expectedHigh),
                "到期时间应约为 " + monthsFromNow + " 个月后，实际 " + actual);
    }

    private long registerAndLogin(HttpClient client, String account) throws Exception {
        String body = json.writeValueAsString(
                Map.of("userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        var registration = post(client, "/user/register", body);
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        var login = post(client, "/user/login", body);
        assertEquals(0, login.path("code").asInt(), login.toString());
        return registration.path("data").asLong();
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, String body) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
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
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
