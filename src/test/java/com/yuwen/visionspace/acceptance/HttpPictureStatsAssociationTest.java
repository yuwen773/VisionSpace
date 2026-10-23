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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计记录按图片关联键读写（原 18.1 / 18.4）。
 *
 * <p>数据集故意让 {@code picture_stats.id} 与 {@code pictureId} 不同，并且两者互相交叉，
 * 以便把"按主键批量查询"误用暴露成可见的错值：按主键取会把别人的统计贴到本图，
 * 取不到则显示为 0。
 */
@IsolatedSpringBootTest
class HttpPictureStatsAssociationTest {

    private static final long FIRST_PICTURE = 88_001L;
    private static final long SECOND_PICTURE = 88_002L;
    private static final long THIRD_PICTURE = 88_003L;

    /** 统计行的行键与图片 ID 完全无关，且两两交叉，避免"碰巧相等"掩盖错误。 */
    private static final long FIRST_STATS_ID = 9_101L;
    private static final long SECOND_STATS_ID = 9_102L;
    private static final long THIRD_STATS_ID = 9_103L;

    private static final long OWNER = 88_100L;
    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture_stats WHERE id IN (?, ?, ?)",
                FIRST_STATS_ID, SECOND_STATS_ID, THIRD_STATS_ID);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)",
                FIRST_PICTURE, SECOND_PICTURE, THIRD_PICTURE);
        seedPicture(FIRST_PICTURE, "first");
        seedPicture(SECOND_PICTURE, "second");
        seedPicture(THIRD_PICTURE, "third");
        seedStats(FIRST_STATS_ID, FIRST_PICTURE, 11, 3, 7, 5, 2, 41, 9);
        seedStats(SECOND_STATS_ID, SECOND_PICTURE, 23, 6, 13, 8, 4, 64, 15);
        seedStats(THIRD_STATS_ID, THIRD_PICTURE, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void detailShowsTheStatsThatBelongToThePicture() throws Exception {
        try (var anonymous = client()) {
            var first = get(anonymous, "/picture/get/vo?id=" + FIRST_PICTURE);
            assertEquals(0, first.path("code").asInt(), first.toString());
            assertEquals(11L, first.path("data").path("viewCount").asLong(), first.toString());
            assertEquals(3L, first.path("data").path("likeCount").asLong(), first.toString());
            assertEquals(9L, first.path("data").path("clickCount").asLong(), first.toString());

            var second = get(anonymous, "/picture/get/vo?id=" + SECOND_PICTURE);
            assertEquals(0, second.path("code").asInt(), second.toString());
            assertEquals(23L, second.path("data").path("viewCount").asLong(), second.toString());
            assertEquals(15L, second.path("data").path("clickCount").asLong(), second.toString());
        }
    }

    @Test
    void listingAttachesEachPictureItsOwnStats() throws Exception {
        try (var anonymous = client()) {
            var listing = post(anonymous, "/picture/list/page/vo",
                    body(Map.of("current", 1, "pageSize", 10, "sortField", "id", "sortOrder", "ascend")));
            assertEquals(0, listing.path("code").asInt(), listing.toString());

            Map<Long, JsonNode> byId = new HashMap<>();
            for (JsonNode record : listing.path("data").path("records")) {
                byId.put(record.path("id").asLong(), record);
            }
            assertTrue(byId.containsKey(FIRST_PICTURE), listing.toString());
            assertTrue(byId.containsKey(SECOND_PICTURE), listing.toString());
            assertTrue(byId.containsKey(THIRD_PICTURE), listing.toString());

            assertEquals(11L, byId.get(FIRST_PICTURE).path("viewCount").asLong(),
                    "first picture must show its own view count: " + byId.get(FIRST_PICTURE));
            assertEquals(23L, byId.get(SECOND_PICTURE).path("viewCount").asLong(),
                    "second picture must not borrow another row's stats: " + byId.get(SECOND_PICTURE));
            assertEquals(0L, byId.get(THIRD_PICTURE).path("viewCount").asLong(),
                    "picture without activity must show zero: " + byId.get(THIRD_PICTURE));
            assertEquals(4L, byId.get(SECOND_PICTURE).path("shareCount").asLong(),
                    byId.get(SECOND_PICTURE).toString());
        }
    }

    @Test
    void pictureStatsFollowPictureIdNotTheirOwnRowKey() {
        // 前提断言：数据集本身不能让统计行的行键恰好等于图片 ID，掩盖关联错误
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM picture_stats WHERE pictureId IN (?, ?, ?) ORDER BY id",
                Long.class, FIRST_PICTURE, SECOND_PICTURE, THIRD_PICTURE);
        List<Long> pictureIds = jdbc.queryForList(
                "SELECT pictureId FROM picture_stats WHERE pictureId IN (?, ?, ?) ORDER BY id",
                Long.class, FIRST_PICTURE, SECOND_PICTURE, THIRD_PICTURE);
        assertEquals(List.of(FIRST_STATS_ID, SECOND_STATS_ID, THIRD_STATS_ID), ids);
        assertEquals(List.of(FIRST_PICTURE, SECOND_PICTURE, THIRD_PICTURE), pictureIds);
        for (int i = 0; i < ids.size(); i++) {
            assertNotEquals(ids.get(i), pictureIds.get(i), "fixture must keep stats id and pictureId distinct");
        }
    }

    private void seedPicture(long id, String name) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, picWidth, picHeight, spaceId, reviewStatus, isDelete)
                VALUES (?, ?, ?, ?, 10, 1920, 1080, NULL, 1, 0)
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

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(HttpClient client, String path, Object body) throws Exception {
        String payload = json.writeValueAsString(body);
        return send(client, HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build());
    }

    private Map<String, Object> body(Map<String, Object> values) {
        return new HashMap<>(values);
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
