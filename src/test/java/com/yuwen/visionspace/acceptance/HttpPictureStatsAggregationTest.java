package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行为事件的持久领取与幂等聚合（原 19.x），全部经真实 Mapper 与真实库。
 *
 * <p>故障窗口用「崩溃后真实留在库里的状态」复现：
 * <ul>
 *   <li>领取之后崩溃：行上留有未过期的租约，其他实例不能重复领取，租约到期后可重新领取；</li>
 *   <li>累计之后崩溃：行上已标记「已计入」但没标记完成，下一轮只补完成标记，不重新入账。</li>
 * </ul>
 */
@IsolatedSpringBootTest
class HttpPictureStatsAggregationTest {

    private static final long PICTURE = 88_701L;
    private static final long OTHER_PICTURE = 88_702L;
    private static final long MISSING_PICTURE = 88_703L;
    private static final long OWNER = 88_700L;
    /** 统计主键与图片 ID 故意不同，且与另一张图的统计主键交叉 */
    private static final long STATS_ID = 9_701L;
    private static final long OTHER_STATS_ID = 9_702L;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.yuwen.visionspace.service.PictureStatsAggregateService aggregateService;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM user_picture_action WHERE pictureId IN (?, ?, ?)",
                PICTURE, OTHER_PICTURE, MISSING_PICTURE);
        jdbc.update("DELETE FROM picture_stats WHERE pictureId IN (?, ?)", PICTURE, OTHER_PICTURE);
        jdbc.update("DELETE FROM picture_stats WHERE id IN (?, ?)", STATS_ID, OTHER_STATS_ID);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?)", PICTURE, OTHER_PICTURE);

        seedPicture(PICTURE, "aggregated");
        seedPicture(OTHER_PICTURE, "other");
        // 另一张图的统计行，主键与本图的统计主键相邻但不交叉
        seedStats(OTHER_STATS_ID, OTHER_PICTURE, 100, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void oldPendingEventsAreAggregatedRegardlessOfTheirAge() {
        // 三天前的事件：旧的「最近五分钟」时间窗会直接跳过它
        insertEvent(PICTURE, 2, 1, "old-event", LocalDateTime.now().minusDays(3));

        aggregateService.aggregateActionStats();

        assertEquals(1L, viewCount(PICTURE), "old pending events must not be skipped by a time window");
        assertEquals(1, processedCount(PICTURE), "the event must be marked processed after it is counted");
    }

    @Test
    void aggregationUpdatesTheExistingStatsRowInsteadOfCreatingAnother() {
        seedStats(STATS_ID, PICTURE, 5, 0, 0, 0, 0, 0, 0);
        insertEvent(PICTURE, 2, 1, "view-event");

        aggregateService.aggregateActionStats();

        assertEquals(6L, viewCount(PICTURE), "existing stats must be updated, not treated as a new row");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_stats WHERE pictureId = ?", Integer.class, PICTURE);
        assertEquals(1, rows, "the stats row must be unique per picture");
        // 另一张图的统计不受影响
        assertEquals(100L, viewCount(OTHER_PICTURE), "aggregation must not touch another picture's stats");
    }

    @Test
    void repeatedRunsDoNotCountTheSameEventTwice() {
        insertEvent(PICTURE, 2, 1, "repeat-event");

        aggregateService.aggregateActionStats();
        aggregateService.aggregateActionStats();
        aggregateService.aggregateActionStats();

        assertEquals(1L, viewCount(PICTURE), "a repeated batch must not count the same event twice");
    }

    @Test
    void eventHeldByAnotherInstanceIsNotTouchedUntilItsLeaseExpires() {
        // 领取之后崩溃留下的状态：租约仍然有效
        insertEvent(PICTURE, 2, 1, "claimed-event");
        jdbc.update("""
                UPDATE user_picture_action
                SET claimToken = 'other-instance', claimedAt = NOW(), claimExpireAt = DATE_ADD(NOW(), INTERVAL 60 SECOND)
                WHERE eventId LIKE ?
                """, suffix("claimed-event"));

        aggregateService.aggregateActionStats();
        assertEquals(0L, viewCount(PICTURE), "an event with a live lease belongs to the other instance");
        assertEquals(0, processedCount(PICTURE), "the other instance's event must not be marked processed");

        // 租约到期后可以被重新领取，且只入账一次
        jdbc.update("""
                UPDATE user_picture_action SET claimExpireAt = DATE_SUB(NOW(), INTERVAL 1 SECOND)
                WHERE eventId LIKE ?
                """, suffix("claimed-event"));

        aggregateService.aggregateActionStats();
        assertEquals(1L, viewCount(PICTURE), "the expired lease must let the event be counted once");
        assertEquals(1, processedCount(PICTURE));
    }

    @Test
    void eventAccumulatedButNotCompletedIsCompletedWithoutDoubleCounting() {
        // 累计之后、完成之前崩溃留下的状态：已计入统计但没有完成标记
        seedStats(STATS_ID, PICTURE, 1, 0, 0, 0, 0, 0, 0);
        insertEvent(PICTURE, 2, 1, "accumulated-event");
        jdbc.update("UPDATE user_picture_action SET isAccumulated = 1 WHERE eventId LIKE ?",
                suffix("accumulated-event"));

        aggregateService.aggregateActionStats();

        assertEquals(1L, viewCount(PICTURE), "an already counted event must not be counted again");
        assertEquals(1, processedCount(PICTURE), "the event must be completed after recovery");
    }

    @Test
    void concurrentAggregationsCountEachEventExactlyOnce() throws Exception {
        for (int i = 0; i < 20; i++) {
            insertEvent(PICTURE, 2, 1, "race-" + i);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        aggregateService.aggregateActionStats();
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "aggregation must finish");
        } finally {
            pool.shutdownNow();
        }
        assertNull(failure.get(), "concurrent aggregation must not fail: " + failure.get());

        assertEquals(20L, viewCount(PICTURE), "competing instances must not lose or duplicate events");
        assertEquals(20, processedCount(PICTURE), "every event must end up processed");
    }

    @Test
    void cleanupKeepsPendingEventsAndOnlyRemovesProcessedOnes() {
        LocalDateTime longAgo = LocalDateTime.now().minusDays(30);
        // 目标图片不存在的事件不会被聚合消费，因此仍是待处理状态
        insertEvent(MISSING_PICTURE, 2, 1, "pending-old", longAgo);
        insertEvent(PICTURE, 2, 1, "processed-old", longAgo);
        jdbc.update("UPDATE user_picture_action SET isProcessed = 1, isAccumulated = 1 WHERE eventId LIKE ?",
                suffix("processed-old"));

        aggregateService.aggregateActionStats();

        Integer pendingRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE eventId LIKE ?",
                Integer.class, suffix("pending-old"));
        assertEquals(1, pendingRows, "pending events must not be dropped by the cleanup");
        Integer processedRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE eventId LIKE ?",
                Integer.class, suffix("processed-old"));
        assertEquals(0, processedRows, "processed events beyond the retention window are cleaned up");
    }

    @Test
    void eventsWithoutAnExistingPictureStayVisibleAsConflicts() {
        insertEvent(MISSING_PICTURE, 2, 1, "orphan-event");

        aggregateService.aggregateActionStats();

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE eventId LIKE ?",
                Integer.class, suffix("orphan-event"));
        assertEquals(1, rows, "conflict events must not be silently dropped");
        Integer processed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE eventId LIKE ? AND isProcessed = 1",
                Integer.class, suffix("orphan-event"));
        assertEquals(0, processed, "an event without a target must not be marked as counted");
        assertTrue(aggregateService.getBacklog().getOrphanCount() >= 1,
                "conflict events must be observable: " + aggregateService.getBacklog().getOrphanCount());
    }

    @Test
    void backlogCountsPendingWork() {
        insertEvent(PICTURE, 2, 1, "backlog-event");

        var backlog = aggregateService.getBacklog();
        assertTrue(backlog.getPendingCount() >= 1,
                "pending events must be observable: " + backlog.getPendingCount());

        aggregateService.aggregateActionStats();

        var after = aggregateService.getBacklog();
        assertEquals(0L, after.getPendingCount(), "the backlog must shrink after a successful aggregation");
        assertEquals(0L, after.getAccumulatedPendingCount(),
                "no event may stay in the accumulated-but-not-completed state");
    }

    @Test
    void reportedEventReachesTheFinalStatistics() throws Exception {
        try (var anonymous = client()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("pictureId", PICTURE);
            payload.put("actionType", 2);
            payload.put("source", 1);
            payload.put("eventId", "http-to-stats");
            var response = post(anonymous, "/picture/action/report", payload);
            assertEquals(0, response.path("code").asInt(), response.toString());
        }

        aggregateService.aggregateActionStats();

        assertEquals(1L, viewCount(PICTURE), "a reported event must reach the final statistics");
        var detail = get(client(), "/picture/get/vo?id=" + PICTURE);
        assertEquals(1L, detail.path("data").path("viewCount").asLong(), detail.toString());
    }

    // ---- fixtures ----

    private void seedPicture(long id, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, NULL, 1, 0)
                """, id, "content-" + id, name, OWNER);
    }

    private void seedStats(long id, long pictureId, long view, long like, long collect,
                           long download, long share, long impression, long click) {
        jdbc.update("""
                INSERT INTO picture_stats (id, pictureId, viewCount, likeCount, collectCount,
                                           downloadCount, shareCount, impressionCount, clickCount, ctr)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.000000)
                """, id, pictureId, view, like, collect, download, share, impression, click);
    }

    private void insertEvent(long pictureId, int actionType, int actionValue, String eventId) {
        insertEvent(pictureId, actionType, actionValue, eventId, LocalDateTime.now());
    }

    private void insertEvent(long pictureId, int actionType, int actionValue, String eventId,
                              LocalDateTime createdTime) {
        jdbc.update("""
                INSERT INTO user_picture_action (userId, pictureId, actionType, actionValue, source,
                                                 eventId, createdTime, isProcessed, isAccumulated, attemptCount)
                VALUES (0, ?, ?, ?, 0, ?, ?, 0, 0, 0)
                """, pictureId, actionType, actionValue, scopedEventId(eventId), createdTime);
    }

    /**
     * 事件幂等键在库里带客户端范围前缀，测试按后缀定位事件。
     */
    private String scopedEventId(String eventId) {
        return "test:" + eventId;
    }

    private String suffix(String eventId) {
        return "%:" + eventId;
    }

    private long viewCount(long pictureId) {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE((SELECT viewCount FROM picture_stats WHERE pictureId = ?), 0)",
                Long.class, pictureId);
        return value == null ? 0L : value;
    }

    private int processedCount(long pictureId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_picture_action WHERE pictureId = ? AND isProcessed = 1",
                Integer.class, pictureId);
    }

    // ---- http helpers ----

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path)).GET().build()).body());
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
