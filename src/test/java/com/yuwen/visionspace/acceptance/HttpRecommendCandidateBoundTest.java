package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐候选有界（原 21.3）。
 *
 * <p>这一组用例用独立的 Spring 上下文把候选池上限压到 4：公开图库里有 12 张图片，
 * 但热门与随机的候选、评分与结果都只覆盖候选池，翻页不会去加载池外的图片。
 */
@IsolatedSpringBootTest
@TestPropertySource(properties = "vision-space.recommend.candidate-limit=4")
class HttpRecommendCandidateBoundTest {

    private static final long FIRST_PICTURE = 88_801L;
    private static final int PUBLIC_PICTURE_COUNT = 12;
    private static final long OWNER = 88_800L;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.yuwen.visionspace.service.PictureRecommendService recommendService;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE reviewStatus = 1 AND spaceId IS NULL");
        jdbc.update("DELETE FROM picture_stats WHERE pictureId >= ?", FIRST_PICTURE);
        for (int i = 0; i < PUBLIC_PICTURE_COUNT; i++) {
            long id = FIRST_PICTURE + i;
            jdbc.update("""
                    INSERT INTO picture (id, url, name, userId, picSize, picWidth, picHeight, spaceId, reviewStatus, isDelete)
                    VALUES (?, ?, ?, ?, 10, 1920, 1080, NULL, 1, 0)
                    """, id, "content-" + id, "bounded-" + i, OWNER + i);
        }
        recommendService.refreshRecommendCache();
    }

    @Test
    void hotOnlyScoresTheBoundedCandidatePool() throws Exception {
        var page = recommend("hot", 1, 10, null);
        assertEquals(0, page.path("code").asInt(), page.toString());
        assertEquals(4, page.path("data").path("records").size(),
                "the candidate pool bounds the result: " + page.toString());
        assertEquals(4, page.path("data").path("total").asInt(),
                "the total must match the reachable candidate pool: " + page.toString());
    }

    @Test
    void randomOnlyReturnsTheBoundedCandidatePool() throws Exception {
        var page = recommend("random", 1, 10, null);
        assertEquals(4, page.path("data").path("records").size(), page.toString());
        assertEquals(4, page.path("data").path("total").asInt(), page.toString());
    }

    @Test
    void pagingBeyondTheCandidatePoolReturnsNoRecordsButKeepsTheTotal() throws Exception {
        var page = recommend("hot", 2, 10, null);
        assertEquals(0, page.path("data").path("records").size(), page.toString());
        assertEquals(4, page.path("data").path("total").asInt(), page.toString());
    }

    @Test
    void thePublicLibraryStillHasMorePicturesThanTheCandidatePool() throws Exception {
        long eligible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE reviewStatus = 1 AND spaceId IS NULL AND isDelete = 0",
                Long.class);
        assertTrue(eligible > 4, "the fixture must have more pictures than the candidate pool: " + eligible);
    }

    private JsonNode recommend(String type, int page, int size, String context) throws Exception {
        var response = client().send(HttpRequest.newBuilder(
                        uri("/picture/recommend/list?type=" + type + "&page=" + page + "&size=" + size)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
