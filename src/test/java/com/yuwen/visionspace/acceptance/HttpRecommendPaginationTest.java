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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐分页与候选有界（原 21.x），全部经真实 HTTP 与真实库。
 *
 * <p>数据集固定：12 张公开已审核图片（其中 3 张大图、9 张小图）、1 张空间图片、
 * 1 张未审核图片。统计行的行键与图片 ID 完全不同，热门的排序必须依赖正确的统计关联。
 */
@IsolatedSpringBootTest
class HttpRecommendPaginationTest {

    /** 公开已审核图片：88901 起共 12 张 */
    private static final long FIRST_PICTURE = 88_901L;
    private static final int PUBLIC_PICTURE_COUNT = 12;
    /** 大图（满足优质门槛）的数量 */
    private static final int LARGE_PICTURE_COUNT = 3;
    /** 互动量最高、创建最早的一张：热门排序应当把它排在第一 */
    private static final long HOT_PICTURE = 88_901L;
    private static final long SPACE_PICTURE = 88_950L;
    private static final long PENDING_PICTURE = 88_951L;
    /** 统计行的行键与图片 ID 完全不重叠，避免按行键查统计的误用被巧合掩盖 */
    private static final long STATS_ID = 9_901L;
    private static final long OWNER = 88_900L;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private com.yuwen.visionspace.service.PictureRecommendService recommendService;

    @BeforeEach
    void fixture() {
        // 其他用例留下的公开图片会影响推荐总数，先清空，保证数据集固定
        jdbc.update("DELETE FROM picture WHERE reviewStatus = 1 AND spaceId IS NULL");
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?)", HOT_PICTURE, SPACE_PICTURE, PENDING_PICTURE);
        jdbc.update("DELETE FROM picture_stats WHERE pictureId >= ?", FIRST_PICTURE);

        for (int i = 0; i < PUBLIC_PICTURE_COUNT; i++) {
            long id = FIRST_PICTURE + i;
            boolean large = i < LARGE_PICTURE_COUNT;
            // 每个图片属于不同作者，避免作者打散影响评分顺序
            jdbc.update("""
                    INSERT INTO picture (id, url, name, userId, picSize, picWidth, picHeight, spaceId, reviewStatus, isDelete)
                    VALUES (?, ?, ?, ?, 10, ?, ?, NULL, 1, 0)
                    """, id, "content-" + id, "picture-" + i, OWNER + i, large ? 1920 : 800, large ? 1080 : 600);
            // 创建时间：热门图片最早，其余是刚创建的
            jdbc.update("UPDATE picture SET createTime = ? WHERE id = ?",
                    id == HOT_PICTURE
                            ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(30))
                            : java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()),
                    id);
        }
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, picWidth, picHeight, spaceId, reviewStatus, isDelete)
                VALUES (?, 'content-space', 'space', ?, 10, 1920, 1080, 88999, 1, 0)
                """, SPACE_PICTURE, OWNER);
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, picWidth, picHeight, spaceId, reviewStatus, isDelete)
                VALUES (?, 'content-pending', 'pending', ?, 10, 1920, 1080, NULL, 0, 0)
                """, PENDING_PICTURE, OWNER);

        // 热门图片：互动量远高于其他图片，统计行的行键与图片 ID 无关
        jdbc.update("""
                INSERT INTO picture_stats (id, pictureId, viewCount, likeCount, collectCount, downloadCount,
                                           shareCount, impressionCount, clickCount, ctr)
                VALUES (?, ?, 1000000, 1000000, 1000000, 1000000, 1000000, 1000000, 1000000, 1.000000)
                """, STATS_ID, HOT_PICTURE);

        // 推荐缓存按「类型 + 上下文」缓存，用例之间先失效一次
        recommendService.refreshRecommendCache();
    }

    @Test
    void homePageReturnsTheFirstSliceWithARealTotal() throws Exception {
        var page = recommend("hot", 1, 5, null);
        assertEquals(0, page.path("code").asInt(), page.toString());
        JsonNode data = page.path("data");
        assertEquals(5, data.path("records").size(), page.toString());
        assertEquals(PUBLIC_PICTURE_COUNT, data.path("total").asInt(), page.toString());
    }

    @Test
    void lastPageReturnsTheRemainderAndTheTotalStaysReal() throws Exception {
        var page = recommend("hot", 3, 5, null);
        assertEquals(2, page.path("data").path("records").size(), page.toString());
        assertEquals(PUBLIC_PICTURE_COUNT, page.path("data").path("total").asInt(), page.toString());
    }

    @Test
    void outOfRangePageKeepsTheTotalOfTheNonEmptySet() throws Exception {
        var page = recommend("hot", 99, 5, null);
        assertEquals(0, page.path("data").path("records").size(), page.toString());
        assertEquals(PUBLIC_PICTURE_COUNT, page.path("data").path("total").asInt(),
                "an out of range page must not zero the total of a non empty set");
    }

    @Test
    void hotOrderingUsesTheStatisticsOfThePictureItself() throws Exception {
        var page = recommend("hot", 1, 5, null);
        long firstId = page.path("data").path("records").get(0).path("id").asLong();
        assertEquals(HOT_PICTURE, firstId,
                "the picture with the highest engagement must be first: " + page.path("data").path("records"));
    }

    @Test
    void pagingThroughTheWholeSetNeitherRepeatsNorSkips() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var response = recommend("hot", page, 5, null);
            for (JsonNode record : response.path("data").path("records")) {
                ids.add(record.path("id").asLong());
            }
        }
        assertEquals(PUBLIC_PICTURE_COUNT, ids.size());
        assertEquals(PUBLIC_PICTURE_COUNT, new HashSet<>(ids).size(), "pages must not repeat pictures: " + ids);
    }

    @Test
    void randomTotalIsNotAFixedConstant() throws Exception {
        var page = recommend("random", 1, 5, null);
        assertEquals(PUBLIC_PICTURE_COUNT, page.path("data").path("total").asInt(),
                "the random total must be the real number of pictures: " + page.toString());
        assertEquals(5, page.path("data").path("records").size(), page.toString());
    }

    @Test
    void randomWithinTheSameContextIsStableAndComplete() throws Exception {
        var first = recommend("random", 1, 5, null);
        String context = first.path("data").path("context").asText();
        assertNotNull(context);
        assertFalse(context.isBlank(), "the response must carry the paging context");

        List<Long> ids = new ArrayList<>();
        for (JsonNode record : first.path("data").path("records")) {
            ids.add(record.path("id").asLong());
        }
        for (int page = 2; page <= 3; page++) {
            var response = recommend("random", page, 5, context);
            assertEquals(context, response.path("data").path("context").asText(),
                    "the same context must stay the same context");
            for (JsonNode record : response.path("data").path("records")) {
                ids.add(record.path("id").asLong());
            }
        }
        assertEquals(PUBLIC_PICTURE_COUNT, ids.size());
        assertEquals(PUBLIC_PICTURE_COUNT, new HashSet<>(ids).size(),
                "one context must neither repeat nor skip pictures: " + ids);

        // 同一上下文重复请求给出同一个顺序
        var repeated = recommend("random", 1, 5, context);
        long repeatedFirst = repeated.path("data").path("records").get(0).path("id").asLong();
        assertEquals(ids.get(0), repeatedFirst, "the same context must keep the same order");
    }

    @Test
    void latestAndQualityKeepTheirOwnTypes() throws Exception {
        var latest = recommend("latest", 1, 20, null);
        assertEquals(PUBLIC_PICTURE_COUNT, latest.path("data").path("total").asInt(), latest.toString());

        var quality = recommend("quality", 1, 20, null);
        assertEquals(LARGE_PICTURE_COUNT, quality.path("data").path("total").asInt(),
                "the quality type keeps its own filter: " + quality.toString());
        for (JsonNode record : quality.path("data").path("records")) {
            long id = record.path("id").asLong();
            assertTrue(id - FIRST_PICTURE < LARGE_PICTURE_COUNT, "unexpected picture in quality: " + id);
        }
    }

    @Test
    void spaceAndUnreviewedPicturesAreNeverRecommended() throws Exception {
        Set<Long> ids = new HashSet<>();
        for (int page = 1; page <= 3; page++) {
            var response = recommend("hot", page, 5, null);
            for (JsonNode record : response.path("data").path("records")) {
                ids.add(record.path("id").asLong());
            }
        }
        assertFalse(ids.contains(SPACE_PICTURE), "space pictures must not be recommended");
        assertFalse(ids.contains(PENDING_PICTURE), "unreviewed pictures must not be recommended");
    }

    @Test
    void theCacheKeepsOneCandidateListPerContextInsteadOfOneFullResultPerPage() throws Exception {
        recommend("hot", 1, 5, null);
        recommend("hot", 2, 5, null);
        recommend("hot", 3, 5, null);

        Set<String> keys = redis.keys("recommend:hot:*");
        assertTrue(keys != null && keys.contains("recommend:hot:list"),
                "the candidate list must be cached once per context: " + keys);
        for (String key : keys) {
            assertFalse(key.matches("recommend:hot:\\d+:\\d+"),
                    "the full result must not be cached per page key: " + key);
        }
    }

    @Test
    void pagingThroughTheHotListReusesTheSameCandidateList() throws Exception {
        var first = recommend("hot", 1, 5, null);
        String context = first.path("data").path("context").asText();

        var second = recommend("hot", 2, 5, context);
        var third = recommend("hot", 3, 5, context);

        // 翻页带着上一页的上下文回来，命中的正是当初存下的那份候选
        Set<String> candidateKeys = new HashSet<>();
        for (String key : redis.keys("recommend:hot:*")) {
            if (!key.endsWith(":total")) {
                candidateKeys.add(key);
            }
        }
        assertEquals(1, candidateKeys.size(),
                "paging must not build a new candidate list per page: " + candidateKeys);

        // 同一份候选切出来的三页既不重复也不遗漏
        List<Long> ids = new ArrayList<>();
        for (var response : List.of(first, second, third)) {
            for (JsonNode record : response.path("data").path("records")) {
                ids.add(record.path("id").asLong());
            }
        }
        assertEquals(PUBLIC_PICTURE_COUNT, ids.size());
        assertEquals(PUBLIC_PICTURE_COUNT, new HashSet<>(ids).size(), "pages must not repeat pictures: " + ids);
    }

    @Test
    void anEmptyLibraryReturnsNoRecordsAndAZeroTotal() throws Exception {
        jdbc.update("DELETE FROM picture WHERE reviewStatus = 1 AND spaceId IS NULL");
        recommendService.refreshRecommendCache();

        var page = recommend("hot", 1, 5, null);
        assertEquals(0, page.path("code").asInt(), page.toString());
        assertEquals(0, page.path("data").path("records").size(), page.toString());
        assertEquals(0, page.path("data").path("total").asInt(), page.toString());

        var random = recommend("random", 1, 5, null);
        assertEquals(0, random.path("data").path("records").size(), random.toString());
        assertEquals(0, random.path("data").path("total").asInt(), random.toString());
    }

    /**
     * 候选被刷新之后，旧上下文必须被明确结束，而不是被绑定到新的排序上。
     *
     * <p>否则客户端会拿旧页码继续往后翻，同一份结果里既有重复也有遗漏。
     */
    @Test
    void aContextIsEndedWhenTheCandidatesAreRefreshed() throws Exception {
        var first = recommend("hot", 1, 5, null);
        String context = first.path("data").path("context").asText();
        assertFalse(context.isBlank(), "the response must carry the paging context");

        // 统计变化让热门排序换了一份，再刷新缓存
        jdbc.update("UPDATE picture_stats SET impressionCount = impressionCount + 100000 "
                + "WHERE pictureId = ?", FIRST_PICTURE + 1);
        recommendService.refreshRecommendCache();

        var second = recommend("hot", 2, 5, context);
        assertEquals(0, second.path("code").asInt(), second.toString());
        assertFalse(second.path("data").path("contextValid").asBoolean(true),
                "a context that no longer matches the candidates must be ended explicitly: " + second);
        assertNotEquals(context, second.path("data").path("context").asText(),
                "the ended context must not be reused for the new ordering: " + second);
        assertEquals(PUBLIC_PICTURE_COUNT, second.path("data").path("total").asInt(), second.toString());
        // 明确结束之后给的是新候选的第一页，客户端从新的一份结果重新开始
        assertEquals(5, second.path("data").path("records").size(), second.toString());

        // 新的一份结果里翻页仍然不重复、不遗漏
        String restarted = second.path("data").path("context").asText();
        List<Long> ids = new ArrayList<>();
        for (var response : List.of(second, recommend("hot", 2, 5, restarted))) {
            for (JsonNode record : response.path("data").path("records")) {
                ids.add(record.path("id").asLong());
            }
        }
        assertEquals(10, ids.size());
        assertEquals(10, new HashSet<>(ids).size(), "the restarted result must not repeat pictures: " + ids);

        // 候选仍然只有一份：按上下文分槽会让缓存份数随客户端增长
        Set<String> candidateKeys = new HashSet<>();
        for (String key : redis.keys("recommend:hot:*")) {
            if (!key.endsWith(":total")) {
                candidateKeys.add(key);
            }
        }
        assertEquals(1, candidateKeys.size(),
                "the candidate list must stay bounded to one per type: " + candidateKeys);
    }

    /**
     * 候选缓存到期之后，旧上下文同样必须被明确结束。
     */
    @Test
    void aContextIsEndedWhenTheCachedCandidatesExpire() throws Exception {
        var first = recommend("hot", 1, 5, null);
        String context = first.path("data").path("context").asText();

        // 模拟缓存到期：候选列表不在缓存里了
        redis.delete("recommend:hot:list");

        var second = recommend("hot", 2, 5, context);
        assertEquals(0, second.path("code").asInt(), second.toString());
        assertFalse(second.path("data").path("contextValid").asBoolean(true),
                "a context whose candidates expired must be ended explicitly: " + second);
        assertNotEquals(context, second.path("data").path("context").asText(), second.toString());
    }

    /**
     * 越界到极大的页码时返回空记录与真实总数，而不是服务器错误或退回第一页。
     */
    @Test
    void aHugePageNumberReturnsAnEmptyPageWithTheRealTotal() throws Exception {
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("hot", PUBLIC_PICTURE_COUNT);
        totals.put("random", PUBLIC_PICTURE_COUNT);
        totals.put("latest", PUBLIC_PICTURE_COUNT);
        totals.put("quality", LARGE_PICTURE_COUNT);

        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            var page = recommend(entry.getKey(), Integer.MAX_VALUE, 100, null);
            assertEquals(0, page.path("code").asInt(),
                    entry.getKey() + " must not fail on a huge page: " + page);
            assertEquals(0, page.path("data").path("records").size(),
                    entry.getKey() + " must return an empty page: " + page);
            assertEquals(entry.getValue(), page.path("data").path("total").asInt(),
                    entry.getKey() + " must keep the real total: " + page);
        }
    }

    // ---- http helpers ----

    private JsonNode recommend(String type, int page, int size, String context) throws Exception {
        StringBuilder path = new StringBuilder("/picture/recommend/list?type=" + type
                + "&page=" + page + "&size=" + size);
        if (context != null && !context.isBlank()) {
            path.append("&context=").append(java.net.URLEncoder.encode(context, java.nio.charset.StandardCharsets.UTF_8));
        }
        return json.readTree(send(HttpRequest.newBuilder(uri(path.toString())).GET().build()).body());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        var response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
