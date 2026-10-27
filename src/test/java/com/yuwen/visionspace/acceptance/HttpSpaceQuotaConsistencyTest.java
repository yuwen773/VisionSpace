package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空间唯一性、配额与文件一致性（原 17.x / 22.x），全部经真实 HTTP。
 *
 * <p>覆盖：新增只加一次、重复替换十次数量不变且按差额调整、公开删除不扣空空间；
 * 并发不突破额度也不出现负数；存储写失败不产生成功图片；存储成功而数据库失败时
 * 新对象可追踪补偿、旧图在真正替换成功前可用；删除再创建至少两轮。
 */
@IsolatedSpringBootTest
class HttpSpaceQuotaConsistencyTest {

    private static final String PASSWORD = "acceptance-password";

    /** 夹具空间 id 从高位段开始，避免与其它用例的固定 id 撞上 */
    private static final long SPACE_SEQ_START = 89200;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    private long ownerId;
    private final List<Long> createdPictureIds = new ArrayList<>();
    private final List<Long> createdSpaceIds = new ArrayList<>();

    @BeforeEach
    void fixture() throws Exception {
        RecordingStorageConfiguration.failUploads(false);
        RecordingStorageConfiguration.failDeletes(false);
        RecordingStorageConfiguration.resetUploadSize();
        createdPictureIds.clear();
        createdSpaceIds.clear();
        ownerId = register("quota_owner_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        RecordingStorageConfiguration.failUploads(false);
        RecordingStorageConfiguration.failDeletes(false);
        RecordingStorageConfiguration.resetUploadSize();
        jdbc.update("DELETE FROM storage_cleanup_task WHERE reason IN "
                + "('上传失败回收原图未确认', '上传失败回收缩略图未确认')");
        jdbc.update("DELETE FROM storage_cleanup_task WHERE reason = 'test'");
        // 本用例登记的清理任务按空间对象键定位清理：不按全局计数清理，
        // 否则会顺手删掉别的用例留下的任务
        for (Long spaceId : createdSpaceIds) {
            jdbc.update("DELETE FROM storage_cleanup_task WHERE objectKey LIKE ?",
                    "%/space/" + spaceId + "/%");
        }
        for (Long pictureId : createdPictureIds) {
            jdbc.update("DELETE FROM picture_file WHERE pictureId = ?", pictureId);
            jdbc.update("DELETE FROM picture WHERE id = ?", pictureId);
        }
        // 本用例注册的用户不共享空间，按 userId 清理即可
        jdbc.update("DELETE FROM space_user WHERE userId = ?", ownerId);
        jdbc.update("DELETE FROM space WHERE userId = ?", ownerId);
    }

    // ==================== 17.1 新增 / 替换 / 删除的计数 ----------

    @Test
    void addingCountsOnceAndReplacingTenTimesKeepsTheCount() throws Exception {
        long spaceId = seedSpace(ownerId, "count-space", 0, 100, 1_000_000);
        long pictureId;
        try (var client = client()) {
            login(client, ownerId);
            var first = upload(client, "/picture/upload", "first.png", "spaceId", String.valueOf(spaceId));
            assertEquals(0, first.path("code").asInt(), first.toString());
            pictureId = first.path("data").path("id").asLong();
            createdPictureIds.add(pictureId);

            // 新增：数量只加一次
            assertEquals(1L, totalCount(spaceId), "one upload must count exactly once");
            long sizeAfterFirst = totalSize(spaceId);
            assertTrue(sizeAfterFirst > 0, "the uploaded bytes must be counted");

            // 同一大小连续替换十次：数量不变，大小按差额调整后收敛
            for (int i = 0; i < 10; i++) {
                var replaced = upload(client, "/picture/upload", "replacement-" + i + ".png",
                        "id", String.valueOf(pictureId), "spaceId", String.valueOf(spaceId));
                assertEquals(0, replaced.path("code").asInt(), "replace #" + i + " -> " + replaced);
            }
            assertEquals(1L, totalCount(spaceId), "replacing must not change the picture count");
            assertEquals(sizeAfterFirst, totalSize(spaceId),
                    "replacing with the same size must leave the used size unchanged");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM picture WHERE id = ? AND isDelete = 0", Integer.class, pictureId));
        }
    }

    @Test
    void publicDeletionDoesNotTouchAnySpaceQuota() throws Exception {
        // 公共图库图片（spaceId 为空）删除不能扣任何空间
        long spaceId = seedSpace(ownerId, "untouched", 0, 100, 1_000_000);
        long publicPictureId;
        try (var client = client()) {
            login(client, ownerId);
            var uploaded = upload(client, "/picture/upload", "public-only.png");
            assertEquals(0, uploaded.path("code").asInt(), uploaded.toString());
            publicPictureId = uploaded.path("data").path("id").asLong();
            createdPictureIds.add(publicPictureId);
            // 空间里有自己的计数，删除公开图片不能动它
            jdbc.update("UPDATE space SET totalCount = 5, totalSize = 500 WHERE id = ?", spaceId);

            var deleted = post(client, "/picture/delete", Map.of("id", publicPictureId));
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
        }
        assertEquals(5L, totalCount(spaceId), "public deletion must not change a space count");
        assertEquals(500L, totalSize(spaceId), "public deletion must not change a space size");
    }

    @Test
    void deletionNeverDrivesTheQuotaNegative() throws Exception {
        // 存量偏差：计数已经被历史错误改成 0，但还有图片可删
        long spaceId = seedSpace(ownerId, "drifted", 0, 100, 1_000_000);
        long pictureId;
        try (var client = client()) {
            login(client, ownerId);
            var uploaded = upload(client, "/picture/upload", "drift.png", "spaceId", String.valueOf(spaceId));
            assertEquals(0, uploaded.path("code").asInt(), uploaded.toString());
            pictureId = uploaded.path("data").path("id").asLong();
            createdPictureIds.add(pictureId);
            jdbc.update("UPDATE space SET totalCount = 0, totalSize = 0 WHERE id = ?", spaceId);

            var deleted = post(client, "/picture/delete", Map.of("id", pictureId));
            assertEquals(0, deleted.path("code").asInt(), deleted.toString());
        }
        assertEquals(0L, totalCount(spaceId), "a drifted count must not go negative");
        assertEquals(0L, totalSize(spaceId), "a drifted size must not go negative");
    }

    // ==================== 17.2 并发额度 ----------

    @Test
    void concurrentUploadsCannotExceedTheSpaceQuota() throws Exception {
        // 只留一个名额：两个请求并发上传，恰好一个成功
        long spaceId = seedSpace(ownerId, "single-slot", 0, 1, 1_000_000);
        List<JsonNode> results = uploadConcurrently(spaceId, 2);
        long successes = results.stream().filter(r -> r.path("code").asInt() == 0).count();
        assertEquals(1, successes, "only one upload may take the single slot: " + results);
        assertEquals(1L, totalCount(spaceId), "the count must not exceed the limit");
        assertTrue(totalSize(spaceId) <= 1_000_000L);
        // 被拒绝的请求不能留下图片记录
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE spaceId = ? AND isDelete = 0", Integer.class, spaceId));
    }

    @Test
    void concurrentUploadsCannotExceedTheSizeLimit() throws Exception {
        // 夹具文件 22 字节；只留够一个的位置
        long spaceId = seedSpace(ownerId, "size-slot", 0, 100, 30);
        List<JsonNode> results = uploadConcurrently(spaceId, 3);
        long successes = results.stream().filter(r -> r.path("code").asInt() == 0).count();
        assertEquals(1, successes, "only one upload fits the remaining size: " + results);
        assertEquals(1L, totalCount(spaceId));
        assertTrue(totalSize(spaceId) <= 30L, "used size must stay within the limit");
    }

    @Test
    void repeatedReplacementsDoNotDoubleCountTheSize() throws Exception {
        long spaceId = seedSpace(ownerId, "replace-race", 0, 100, 1_000_000);
        long pictureId;
        try (var client = client()) {
            login(client, ownerId);
            var uploaded = upload(client, "/picture/upload", "base.png", "spaceId", String.valueOf(spaceId));
            pictureId = uploaded.path("data").path("id").asLong();
            createdPictureIds.add(pictureId);
        }
        long sizeAfterFirst = totalSize(spaceId);

        // 同一张图并发替换五次：数量始终为 1，大小不能累加
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try (var client = client()) {
                        login(client, ownerId);
                        start.await();
                        return upload(client, "/picture/upload", "race-" + index + ".png",
                                "id", String.valueOf(pictureId), "spaceId", String.valueOf(spaceId));
                    }
                }));
            }
            start.countDown();
            for (Future<JsonNode> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1L, totalCount(spaceId), "concurrent replacements must keep one picture");
        assertEquals(sizeAfterFirst, totalSize(spaceId),
                "same-size replacements must not accumulate the used size");
        // 一次上传登记原图与缩略图两条记录：替换后仍各有一条有效记录，不能累加
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND kind = 'original' AND status = 1",
                Integer.class, pictureId), "exactly one original resource record may stay active");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND kind = 'thumbnail' AND status = 1",
                Integer.class, pictureId), "exactly one thumbnail resource record may stay active");
    }

    // ==================== 17.3 存储 / 数据库失败 ----------

    @Test
    void storageWriteFailureProducesNoPicture() throws Exception {
        long spaceId = seedSpace(ownerId, "storage-fail", 0, 100, 1_000_000);
        long picturesBefore = countPicturesIn(spaceId);
        RecordingStorageConfiguration.failUploads(true);
        try (var client = client()) {
            login(client, ownerId);
            var response = upload(client, "/picture/upload", "fail.png", "spaceId", String.valueOf(spaceId));
            assertNotEquals(0, response.path("code").asInt(), "a failed storage write must fail the upload");
        } finally {
            RecordingStorageConfiguration.failUploads(false);
        }
        assertEquals(picturesBefore, countPicturesIn(spaceId), "a failed storage write must not create a picture");
        assertEquals(0L, totalCount(spaceId), "a failed storage write must not consume quota");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE spaceId = ?", Integer.class, spaceId),
                "a failed storage write must not register resource records");
    }

    @Test
    void databaseFailureAfterStorageLeavesATraceableCompensationTask() throws Exception {
        // 存储写入成功之后数据库失败（空间大小上限为 0，条件更新影响 0 行），
        // 事务回滚：不能留下成功图片，新对象必须可追踪地回收
        long spaceId = seedSpace(ownerId, "db-fail", 0, 100, 0);
        try (var client = client()) {
            login(client, ownerId);
            var response = upload(client, "/picture/upload", "db-fail.png", "spaceId", String.valueOf(spaceId));
            assertNotEquals(0, response.path("code").asInt(), "the failed transaction must reject the upload");
        }
        // 图片与资源记录都不存在：回收路径把记录收敛成已删除，不能留下仍占用存储的记录
        assertEquals(0, countPicturesIn(spaceId), "a rolled back transaction must not leave a picture");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE spaceId = ? AND status IN (0, 1)",
                Integer.class, spaceId),
                "a rolled back transaction must not leave live resource records");
        // 已写入的对象被回收：存储里有一次原图删除调用；回收成功则没有残留任务
        assertTrue(recorder.calls().stream().anyMatch(call -> "DELETE".equals(call.method())),
                "the object written before the database failure must be reclaimed: " + recorder.calls());
        assertEquals(0, unresolvedTasksFor(spaceId),
                "a successful compensation must not leave an unresolved task");
    }

    @Test
    void failedCompensationOfAnUnboundUploadKeepsATraceableTask() throws Exception {
        // 存储写入成功、数据库失败，且回收也失败：对象在存储里，数据库里没有任何记录。
        // 这时必须留下可重试的清理任务，否则这个对象再也不会被找到。
        long spaceId = seedSpace(ownerId, "db-fail-unbound", 0, 100, 0);
        RecordingStorageConfiguration.failDeletes(true);
        try (var client = client()) {
            login(client, ownerId);
            var response = upload(client, "/picture/upload", "db-fail-unbound.png",
                    "spaceId", String.valueOf(spaceId));
            assertNotEquals(0, response.path("code").asInt(), response.toString());
        } finally {
            RecordingStorageConfiguration.failDeletes(false);
        }
        List<Map<String, Object>> tasks = tasksFor(spaceId);
        assertFalse(tasks.isEmpty(), "an unreclaimable object must leave a traceable task");
        for (var task : tasks) {
            assertTrue(String.valueOf(task.get("objectKey")).contains("/space/" + spaceId + "/"),
                    "the task must locate the object written for this space: " + task);
            assertTrue(task.get("nextRetryTime") != null, "the task must be retryable: " + task);
            assertTrue(task.get("platform") != null, "the task must carry the storage platform: " + task);
        }
    }

    @Test
    void failedCompensationKeepsARetryableTaskAndTheOldPictureStaysUsable() throws Exception {
        long spaceId = seedSpace(ownerId, "comp-fail", 0, 100, 1_000_000);
        long pictureId;
        try (var client = client()) {
            login(client, ownerId);
            var uploaded = upload(client, "/picture/upload", "keep.png", "spaceId", String.valueOf(spaceId));
            assertEquals(0, uploaded.path("code").asInt(), uploaded.toString());
            pictureId = uploaded.path("data").path("id").asLong();
            createdPictureIds.add(pictureId);
        }
        String originalUrl = jdbc.queryForObject(
                "SELECT url FROM picture WHERE id = ?", String.class, pictureId);

        // 替换时数据库失败 + 存储删除也失败：新对象无法回收，旧图必须仍然可用。
        // 新图必须比旧图大，差额为正才会被空间大小上限拒绝；同尺寸的替换差额为 0，
        // 本来就不该失败。
        RecordingStorageConfiguration.failDeletes(true);
        RecordingStorageConfiguration.uploadSize(1_000_000L);
        jdbc.update("UPDATE space SET maxSize = 0 WHERE id = ?", spaceId);
        try (var client = client()) {
            login(client, ownerId);
            var replaced = upload(client, "/picture/upload", "replace-fail.png",
                    "id", String.valueOf(pictureId), "spaceId", String.valueOf(spaceId));
            assertNotEquals(0, replaced.path("code").asInt(), "the failed replacement must be rejected");
        } finally {
            RecordingStorageConfiguration.failDeletes(false);
            RecordingStorageConfiguration.resetUploadSize();
        }

        // 旧图与旧资源记录仍在，内容地址不变
        assertEquals(originalUrl, jdbc.queryForObject(
                "SELECT url FROM picture WHERE id = ?", String.class, pictureId),
                "the old picture must stay usable until the replacement really succeeds");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture_file WHERE pictureId = ? AND kind = 'original' AND status = 1",
                Integer.class, pictureId), "the old resource record must stay active");

        // 新对象删除未确认：留下可观察、可重试的任务
        List<Map<String, Object>> tasks = tasksFor(spaceId);
        assertFalse(tasks.isEmpty(), "a failed compensation must leave a retryable task");
        assertTrue(tasks.stream().allMatch(t -> t.get("nextRetryTime") != null),
                "a retryable task must carry a next retry time: " + tasks);
        assertTrue(tasks.stream().allMatch(t -> t.get("platform") != null && t.get("objectKey") != null),
                "a retryable task must carry the platform and object key: " + tasks);
    }

    @Test
    void cleanupTaskIsRetriedAndNeverDeletesAReReferencedObject() throws Exception {
        // 任务指向一个仍被有效记录引用的对象：重试必须阻断，不能删对象
        long spaceId = seedSpace(ownerId, "task-guard", 0, 100, 1_000_000);
        long pictureId;
        try (var client = client()) {
            login(client, ownerId);
            var uploaded = upload(client, "/picture/upload", "guard.png", "spaceId", String.valueOf(spaceId));
            pictureId = uploaded.path("data").path("id").asLong();
            createdPictureIds.add(pictureId);
        }
        String platform = jdbc.queryForObject(
                "SELECT platform FROM picture_file WHERE pictureId = ? AND kind = 'original'",
                String.class, pictureId);
        String objectKey = jdbc.queryForObject(
                "SELECT objectKey FROM picture_file WHERE pictureId = ? AND kind = 'original'",
                String.class, pictureId);
        jdbc.update("""
                INSERT INTO storage_cleanup_task (pictureFileId, pictureId, platform, objectKey, kind,
                                                  reason, status, attempts, nextRetryTime, pendingKey)
                VALUES (NULL, ?, ?, ?, 'original', 'test', 0, 0, DATE_SUB(NOW(), INTERVAL 1 MINUTE),
                        CONCAT('guard|', ?))
                """, pictureId, platform, objectKey, objectKey);

        int deletesBefore = deleteCallsFor(objectKey);
        var taskId = jdbc.queryForObject(
                "SELECT id FROM storage_cleanup_task WHERE pendingKey = ?", Long.class, "guard|" + objectKey);
        boolean confirmed = storageCleanupService.retryOne(taskId);
        assertFalse(confirmed, "an object still referenced by a live record must not be deleted");
        assertEquals(deletesBefore, deleteCallsFor(objectKey),
                "a blocked task must not touch the object");
        assertEquals(2, jdbc.queryForObject(
                "SELECT status FROM storage_cleanup_task WHERE id = ?", Integer.class, taskId),
                "a blocked task must be visible for manual review");
        jdbc.update("DELETE FROM storage_cleanup_task WHERE id = ?", taskId);
    }

    // ==================== 22.1-22.3 空间唯一性与重建 ----------

    @Test
    void onlyOneConcurrentSpaceCreationSucceeds() throws Exception {
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try (var client = client()) {
                        login(client, ownerId);
                        start.await();
                        return post(client, "/space/add",
                                Map.of("spaceName", "concurrent-" + index, "spaceType", 1));
                    }
                }));
            }
            start.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            long successes = results.stream().filter(r -> r.path("code").asInt() == 0).count();
            assertEquals(1, successes, "concurrent creation of the same space type must succeed once: " + results);
            // 失败方得到明确的业务结果，而不是重复数据或系统错误
            for (JsonNode result : results) {
                if (result.path("code").asInt() != 0) {
                    assertEquals(50001, result.path("code").asInt(),
                            "the losing side must get a business result: " + result);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                Integer.class, ownerId), "only one valid team space may exist");
        // 团队空间与创建者的管理员成员记录一致提交
        Long spaceId = jdbc.queryForObject(
                "SELECT id FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                Long.class, ownerId);
        assertEquals("admin", jdbc.queryForObject(
                "SELECT spaceRole FROM space_user WHERE spaceId = ? AND userId = ?",
                String.class, spaceId, ownerId), "the creator must be a team admin");
    }

    @Test
    void deletingAndRecreatingTheSameSpaceWorksTwice() throws Exception {
        for (int round = 0; round < 2; round++) {
            long spaceId;
            try (var client = client()) {
                login(client, ownerId);
                var created = post(client, "/space/add",
                        Map.of("spaceName", "rebuild-" + round, "spaceType", 1));
                assertEquals(0, created.path("code").asInt(), "round " + round + " -> " + created);
                spaceId = created.path("data").asLong();
                assertTrue(spaceId > 0);

                // 同类空间再次创建必须被拒绝
                var duplicate = post(client, "/space/add",
                        Map.of("spaceName", "duplicate-" + round, "spaceType", 1));
                assertNotEquals(0, duplicate.path("code").asInt(),
                        "a second space of the same type must be rejected in round " + round);

                var deleted = post(client, "/space/delete", Map.of("id", spaceId));
                assertEquals(0, deleted.path("code").asInt(), "round " + round + " -> " + deleted);
            }
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space WHERE id = ? AND isDelete = 0",
                    Integer.class, spaceId), "the space must be logically deleted");
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space WHERE userId = ? AND spaceType = 1 AND isDelete = 0",
                    Integer.class, ownerId), "no valid team space may remain after deletion");
        }
        // 至少两轮都成功，且没有残留的无主空间
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM space s WHERE s.userId = ? AND s.isDelete = 0 "
                        + "AND NOT EXISTS (SELECT 1 FROM space_user su WHERE su.spaceId = s.id)",
                Integer.class, ownerId), "no orphan space may be left behind");
    }

    @Test
    void teamSpaceAndCreatorMembershipCommitTogether() throws Exception {
        try (var client = client()) {
            login(client, ownerId);
            var created = post(client, "/space/add", Map.of("spaceName", "atomic", "spaceType", 1));
            assertEquals(0, created.path("code").asInt(), created.toString());
            long spaceId = created.path("data").asLong();
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space_user WHERE spaceId = ? AND userId = ? AND spaceRole = 'admin'",
                    Integer.class, spaceId, ownerId),
                    "the creator membership must commit with the space");
        }
    }

    // ==================== helpers ====================

    @Autowired
    private com.yuwen.visionspace.service.StorageCleanupService storageCleanupService;

    private long seedSpace(long userId, String name, int type, long maxCount, long maxSize) {
        long id = SPACE_SEQ_START + Math.abs(System.nanoTime() % 1000);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM space WHERE id = ?", Integer.class, id) > 0) {
            id++;
        }
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete, activeKey)
                VALUES (?, ?, ?, 0, ?, ?, ?, 0, 0, 0, ?)
                """, id, name, userId, type, maxSize, maxCount, userId + ":" + type);
        createdSpaceIds.add(id);
        return id;
    }

    private long totalCount(long spaceId) {
        Long value = jdbc.queryForObject("SELECT totalCount FROM space WHERE id = ?", Long.class, spaceId);
        return value == null ? 0L : value;
    }

    private long totalSize(long spaceId) {
        Long value = jdbc.queryForObject("SELECT totalSize FROM space WHERE id = ?", Long.class, spaceId);
        return value == null ? 0L : value;
    }

    private long countPicturesIn(long spaceId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE spaceId = ? AND isDelete = 0", Integer.class, spaceId);
        return value == null ? 0L : value;
    }

    /**
     * 本用例空间下尚未收敛的清理任务。
     *
     * <p>按空间对象键过滤：清理任务表是全局的，别的用例（例如 Agent 附件的删除
     * 未确认）也会留下任务，按全局计数断言会互相污染。
     */
    private List<Map<String, Object>> tasksFor(long spaceId) {
        return jdbc.queryForList(
                "SELECT platform, objectKey, status, attempts, nextRetryTime FROM storage_cleanup_task "
                        + "WHERE status IN (0, 2) AND objectKey LIKE ?",
                "%/space/" + spaceId + "/%");
    }

    private int unresolvedTasksFor(long spaceId) {
        return tasksFor(spaceId).size();
    }

    private int deleteCallsFor(String objectKey) {
        return (int) recorder.calls().stream()
                .filter(call -> "DELETE".equals(call.method()))
                .filter(call -> objectKey.equals(call.body()))
                .count();
    }

    private List<JsonNode> uploadConcurrently(long spaceId, int count) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try (var client = client()) {
                        login(client, ownerId);
                        start.await();
                        return upload(client, "/picture/upload", "concurrent-" + index + ".png",
                                "spaceId", String.valueOf(spaceId));
                    }
                }));
            }
            start.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
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

    private JsonNode upload(HttpClient client, String path, String filename, String... formFields)
            throws Exception {
        String boundary = "----acceptance" + System.nanoTime();
        byte[] content = "acceptance-image-bytes".getBytes(StandardCharsets.UTF_8);
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        for (int i = 0; i + 1 < formFields.length; i += 2) {
            body.write(("\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + formFields[i] + "\"\r\n\r\n"
                    + formFields[i + 1]).getBytes(StandardCharsets.UTF_8));
        }
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var response = client.send(HttpRequest.newBuilder(uri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> body) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
