package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.manager.auth.PictureContentAuthManager;
import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.service.PictureRecommendService;
import com.yuwen.visionspace.service.PictureStatsAggregateService;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 公开行为上报（原 20.x），全部经真实 HTTP 与真实表。
 *
 * <p>覆盖：匿名曝光/点击/浏览贯通到持久事件；账户动作要求登录；私有、未审核、
 * 不存在的目标被拒绝；客户端声明的用户 id 被明确拒绝；同一事件重放不重复入账；
 * 超限流量被拒绝且可观察。
 */
@IsolatedSpringBootTest
class HttpPictureActionReportingTest {

    private static final String PASSWORD = "acceptance-password";

    private static final long PUBLIC_PICTURE = 88_601L;
    private static final long PENDING_PICTURE = 88_602L;
    private static final long SPACE_PICTURE = 88_603L;
    private static final long MISSING_PICTURE = 88_604L;
    /** 审核被拒绝的图片：处置过的资源不再接受公开行为 */
    private static final long REJECTED_PICTURE = 88_605L;
    private static final long PRIVATE_SPACE = 88_650L;

    private static final int IMPRESSION = 0;
    private static final int CLICK = 1;
    private static final int VIEW = 2;
    private static final int LIKE = 3;
    private static final int COLLECT = 4;
    private static final int HOME_RECOMMEND = 0;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PictureActionService pictureActionService;

    @Autowired
    private PictureStatsAggregateService aggregateService;

    @Autowired
    private PictureRecommendService recommendService;

    /**
     * 图片内容的统一可见性入口。真实实现里隔离规则落在这里，
     * 用例替换这一层来验证公开行为上报确实走同一条规则。
     */
    @MockitoSpyBean
    private PictureContentAuthManager pictureContentAuthManager;

    private long ownerId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.update("DELETE FROM user_picture_action WHERE pictureId IN (?, ?, ?)",
                PUBLIC_PICTURE, PENDING_PICTURE, SPACE_PICTURE);
        jdbc.update("DELETE FROM user_picture_action WHERE pictureId = ?", REJECTED_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)",
                PUBLIC_PICTURE, PENDING_PICTURE, SPACE_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id = ?", REJECTED_PICTURE);
        jdbc.update("DELETE FROM space WHERE id = ?", PRIVATE_SPACE);

        ownerId = register("action_owner_" + System.nanoTime());

        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'private', ?, 0, 0, 1000, 1000, 0, 1, 0)
                """, PRIVATE_SPACE, ownerId);

        seedPicture(PUBLIC_PICTURE, null, 1, "public");
        seedPicture(PENDING_PICTURE, null, 0, "pending");
        seedPicture(SPACE_PICTURE, PRIVATE_SPACE, 1, "private");
        seedPicture(REJECTED_PICTURE, null, 2, "rejected");
    }

    @Test
    void anonymousImpressionClickAndBrowseBecomePendingEvents() throws Exception {
        try (var anonymous = client()) {
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-impression"));
            assertAccepted(report(anonymous, PUBLIC_PICTURE, CLICK, "evt-click"));
            assertAccepted(report(anonymous, PUBLIC_PICTURE, VIEW, "evt-view"));
        }

        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, PUBLIC_PICTURE);
        assertEquals(3, events, "each accepted report must be persisted as an event");

        // 匿名事件不冒充任何用户，且处于待处理状态
        Integer anonymousEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ? AND userId = 0",
                Integer.class, PUBLIC_PICTURE);
        assertEquals(3, anonymousEvents);
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ? AND isProcessed = 0 AND isAccumulated = 0",
                Integer.class, PUBLIC_PICTURE);
        assertEquals(3, pending, "reported events must stay pending until they are aggregated");
    }

    @Test
    void replayingTheSameEventDoesNotCreateASecondEvent() throws Exception {
        try (var anonymous = client()) {
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-replay"));
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-replay"));
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-replay"));
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, PUBLIC_PICTURE);
        assertEquals(1, events, "the same event replayed must be counted once");
    }

    @Test
    void anonymousAccountActionsAreRejectedWithoutSideEffects() throws Exception {
        try (var anonymous = client()) {
            assertRejected(report(anonymous, PUBLIC_PICTURE, LIKE, "evt-like"), 40101);
            assertRejected(report(anonymous, PUBLIC_PICTURE, COLLECT, "evt-collect"), 40101);
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, PUBLIC_PICTURE);
        assertEquals(0, events, "rejected account actions must not leave events behind");
    }

    @Test
    void privateUnreviewedAndMissingTargetsAreRejected() throws Exception {
        try (var anonymous = client()) {
            assertRejected(report(anonymous, SPACE_PICTURE, IMPRESSION, "evt-private"), 40101);
            assertRejected(report(anonymous, PENDING_PICTURE, IMPRESSION, "evt-pending"), 40101);
            assertRejected(report(anonymous, MISSING_PICTURE, IMPRESSION, "evt-missing"), 40400);
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId IN (?, ?, ?)",
                Integer.class, SPACE_PICTURE, PENDING_PICTURE, MISSING_PICTURE);
        assertEquals(0, events, "rejected targets must not leave events behind");
    }

    @Test
    void rejectedTargetsAreRejectedWithoutSideEffects() throws Exception {
        try (var anonymous = client()) {
            assertRejected(report(anonymous, REJECTED_PICTURE, IMPRESSION, "evt-rejected"), 40101);
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, REJECTED_PICTURE);
        assertEquals(0, events, "a rejected target must not leave events behind");
    }

    @Test
    void clientDeclaredUserIsRejected() throws Exception {
        try (var anonymous = client(); var owner = client()) {
            login(owner, ownerId);
            Map<String, Object> forged = body(PUBLIC_PICTURE, IMPRESSION, "evt-forged");
            forged.put("userId", ownerId);
            assertRejected(post(owner, "/picture/action/report", forged), 40000);
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, PUBLIC_PICTURE);
        assertEquals(0, events, "a client declared user must not produce events");
    }

    @Test
    void invalidActionTypeAndSourceAreRejected() throws Exception {
        try (var anonymous = client()) {
            assertRejected(report(anonymous, PUBLIC_PICTURE, 99, "evt-type"), 40000);
            assertRejected(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-source", 42), 40000);
            assertRejected(report(anonymous, -1L, IMPRESSION, "evt-picture"), 40000);
        }
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId IN (?, ?, ?)",
                Integer.class, PUBLIC_PICTURE, PENDING_PICTURE, SPACE_PICTURE);
        assertEquals(0, events, "invalid input must not leave events behind");
    }

    @Test
    void loggedInUserCanReportAccountActionsOnPublicPictures() throws Exception {
        try (var owner = client()) {
            login(owner, ownerId);
            assertAccepted(report(owner, PUBLIC_PICTURE, LIKE, "evt-owner-like"));
        }
        Long userId = jdbc.queryForObject(
                "SELECT userId FROM user_picture_action WHERE pictureId = ?", Long.class, PUBLIC_PICTURE);
        assertEquals(ownerId, userId, "the event must carry the server resolved user");
    }

    @Test
    void excessiveTrafficIsRejectedAndObservable() throws Exception {
        long accountUser = register("action_burst_" + System.nanoTime());
        long before = pictureActionService.getReportMetrics().getRateLimitedCount();
        try (var client = client()) {
            login(client, accountUser);
            // 隔离验收配置的限额是 50 次/分钟，第 51 次必须被拒绝
            for (int i = 0; i < 51; i++) {
                Map<String, Object> payload = body(PUBLIC_PICTURE, IMPRESSION, "evt-burst-" + i);
                var response = post(client, "/picture/action/report", payload);
                if (i < 50) {
                    assertEquals(0, response.path("code").asInt(), response.toString());
                } else {
                    assertRejected(response, 50001);
                }
            }
        }
        long after = pictureActionService.getReportMetrics().getRateLimitedCount();
        assertTrue(after > before, "rate limited traffic must be observable: " + before + " -> " + after);
    }

    /**
     * 目标被集中可见性入口判定为不可见时，上报必须被明确拒绝，且零副作用。
     *
     * <p>隔离本身的状态与恢复由 #36 提供；这里让集中入口对该目标给出不可见的
     * 判定，等价于隔离生效后的可见性结果，证明公开行为上报走的是同一条
     * 可见性规则，而不是在服务里另写的一套并行判断。
     */
    @Test
    void anInvisibleTargetIsRejectedWithoutSideEffects() throws Exception {
        // 隔离前：目标可见，上报被受理
        try (var anonymous = client()) {
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-before-isolation"));
        }
        assertEquals(1, eventCount(PUBLIC_PICTURE), "a visible target must be accepted");

        // 隔离后：集中入口判定目标不可见，上报被明确拒绝
        hideFromPublic(PUBLIC_PICTURE, true);
        try (var anonymous = client()) {
            assertRejected(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-isolated"), 40101);
        }
        assertEquals(1, eventCount(PUBLIC_PICTURE), "an invisible target must not leave events behind");
        assertEquals(0L, statsCount(PUBLIC_PICTURE), "an invisible target must not accumulate statistics");

        // 缓存状态变化不影响判定：拒绝来自集中入口，不是来自某一份缓存
        recommendService.refreshRecommendCache();
        try (var anonymous = client()) {
            assertRejected(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-isolated-again"), 40101);
        }
        assertEquals(1, eventCount(PUBLIC_PICTURE), "an invisible target must not leave events behind");

        // 恢复后：目标重新可见，上报重新被受理
        hideFromPublic(PUBLIC_PICTURE, false);
        try (var anonymous = client()) {
            assertAccepted(report(anonymous, PUBLIC_PICTURE, IMPRESSION, "evt-after-restore"));
        }
        assertEquals(2, eventCount(PUBLIC_PICTURE), "a restored target must be accepted again");

        aggregateService.aggregateActionStats();
        assertEquals(2L, statsCount(PUBLIC_PICTURE),
                "only the accepted events may reach the statistics");
    }

    // ---- fixtures ----

    /**
     * 让集中可见性入口对某个目标给出「不可见」的判定。
     *
     * <p>真实实现（#36 的隔离）会在同一个入口里按隔离状态拒绝；这里替换这一层，
     * 上报路径本身一行不改。
     */
    private void hideFromPublic(long pictureId, boolean hidden) {
        doAnswer(invocation -> {
            Picture picture = invocation.getArgument(0);
            if (hidden && picture != null && pictureId == picture.getId()) {
                return false;
            }
            return invocation.callRealMethod();
        }).when(pictureContentAuthManager).canReadContent(any(), any());
    }

    private int eventCount(long pictureId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ?", Integer.class, pictureId);
    }

    private long statsCount(long pictureId) {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE((SELECT impressionCount FROM picture_stats WHERE pictureId = ?), 0)",
                Long.class, pictureId);
        return value == null ? 0L : value;
    }

    private void seedPicture(long id, Long spaceId, int reviewStatus, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, ?, ?, 0)
                """, id, "https://origin.invalid/" + name + ".jpg", name, ownerId, spaceId, reviewStatus);
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

    private JsonNode report(HttpClient client, long pictureId, int actionType, String eventId) throws Exception {
        return post(client, "/picture/action/report", body(pictureId, actionType, eventId));
    }

    private JsonNode report(HttpClient client, long pictureId, int actionType, String eventId, int source)
            throws Exception {
        return post(client, "/picture/action/report", body(pictureId, actionType, eventId, source));
    }

    private Map<String, Object> body(long pictureId, int actionType, String eventId) {
        return body(pictureId, actionType, eventId, HOME_RECOMMEND);
    }

    private Map<String, Object> body(long pictureId, int actionType, String eventId, int source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pictureId", pictureId);
        payload.put("actionType", actionType);
        payload.put("source", source);
        payload.put("eventId", eventId);
        return payload;
    }

    private void assertAccepted(JsonNode response) {
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private void assertRejected(JsonNode response, int expectedCode) {
        assertEquals(expectedCode, response.path("code").asInt(), response.toString());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> payload) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
