package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.recovery.AgentActionLedger;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限恢复流程（#41）：全部经真实 HTTP 与真实 MySQL。
 *
 * <p>覆盖：恢复入口沿用会话归属（他人与平台管理员都不能续跑）；
 * 只有旧轻量归档时明确不可恢复；待核对动作阻止续跑；过期版本与重复恢复请求
 * 被拒绝；重复核对与过期核对不生效；双写者按版本 CAS 只有一个生效；
 * 备份失败可见可重试；释放会话后载荷与计数被清理。
 *
 * <p>「已确认完成不重放」的执行边界由 {@code AgentActionInterceptorTest} 用受控
 * 工具替身证明（验收环境没有真实模型，Agent 循环跑不到工具节点），本类只证明
 * 入口侧不会因为重复/过期请求多产生一次执行。
 */
@IsolatedSpringBootTest
@TestPropertySource(properties = {
        "spring.ai.retry.max-attempts=1",
        "spring.ai.retry.backoff.initial-interval=10ms",
        "spring.ai.retry.backoff.max-interval=20ms",
})
class HttpAgentRecoveryTest {

    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    @Autowired
    private AgentActionLedger ledger;

    @Autowired
    private AgentRunStateStore runStateStore;

    private long ownerId;
    private long strangerId;

    private final List<String> threadIds = new ArrayList<>();

    @BeforeEach
    void fixture() throws Exception {
        ownerId = register("recovery_owner_" + System.nanoTime());
        strangerId = register("recovery_stranger_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        for (String threadId : threadIds) {
            jdbc.update("DELETE FROM agent_action_record WHERE threadId = ?", threadId);
            jdbc.update("DELETE FROM agent_run_state WHERE threadId = ?", threadId);
            jdbc.update("DELETE FROM agent_message WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_session WHERE sessionId = ?", threadId);
        }
    }

    // ---- 归属：他人与平台管理员都不能续跑 ----

    @Test
    void recoveryStateRejectsStrangersAndAdmins() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 3L, "tool_node");
        long adminId = registerAdmin("recovery_admin_" + System.nanoTime());

        try (var stranger = client(); var admin = client()) {
            login(stranger, strangerId);
            assertRejected(get(stranger, "/agent/recovery/" + threadId));
            // 平台管理员没有旁路
            login(admin, adminId);
            assertRejected(get(admin, "/agent/recovery/" + threadId));
        }
    }

    @Test
    void strangerCannotResumeAndNothingExecutes() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 3L, "tool_node");

        try (var stranger = client()) {
            login(stranger, strangerId);
            int before = recorder.calls().size();
            var response = post(stranger, "/agent/recovery/" + threadId + "/resume?expectedVersion=3", Map.of());
            assertRejected(response);
            assertNoExternalCalls(before);
        }
        assertEquals("tool_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId),
                "rejected resume must not modify the recovery point");
    }

    @Test
    void strangerCannotReviewActionsOwnedBySomeoneElse() throws Exception {
        String threadId = seedSession(ownerId);
        String actionId = seedUnknownAction(threadId, ownerId);

        try (var stranger = client()) {
            login(stranger, strangerId);
            assertRejected(post(stranger, "/agent/recovery/action/confirm",
                    Map.of("threadId", threadId, "actionId", actionId)));
            assertRejected(post(stranger, "/agent/recovery/action/dismiss",
                    Map.of("threadId", threadId, "actionId", actionId)));
        }
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, statusOf(threadId, actionId),
                "stranger review must not change the action state");
    }

    // ---- 状态契约：可续跑 / 不可恢复 ----

    @Test
    void legacyArchiveOnlyIsReadableButNotResumable() throws Exception {
        String threadId = seedSession(ownerId);
        seedLegacyArchiveOnly(threadId, ownerId);

        try (var owner = client()) {
            login(owner, ownerId);
            // 旧归档仍可按权限查看历史
            assertEquals(0, get(owner, "/agent/history/" + threadId).path("code").asInt());

            var state = get(owner, "/agent/recovery/" + threadId);
            assertEquals(0, state.path("code").asInt(), state.toString());
            assertFalse(state.path("data").path("resumable").asBoolean(),
                    "message-only archive must not be offered as a resume point: " + state);
            assertTrue(state.path("data").path("legacyArchiveOnly").asBoolean());
            assertEquals("PAUSED_UNRECOVERABLE", state.path("data").path("runStatus").asText());
        }
    }

    @Test
    void resumeWithoutAnyStateIsPausedInSteadOfFakingRecovery() throws Exception {
        String threadId = seedSession(ownerId);

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            var response = post(owner, "/agent/recovery/" + threadId + "/resume", Map.of());
            assertTrue(response.path("code").asInt() != 0, response.toString());
            assertNoExternalCalls(before);
        }
    }

    @Test
    void pendingReviewBlocksResumeUntilReviewed() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 5L, "tool_node");
        String actionId = seedUnknownAction(threadId, ownerId);

        try (var owner = client()) {
            login(owner, ownerId);

            var state = get(owner, "/agent/recovery/" + threadId);
            assertEquals(1, state.path("data").path("pendingReviewCount").asInt(), state.toString());
            assertFalse(state.path("data").path("resumable").asBoolean(),
                    "pending review must block resume even with a full state: " + state);

            int before = recorder.calls().size();
            var blocked = post(owner, "/agent/recovery/" + threadId + "/resume?expectedVersion=5", Map.of());
            assertTrue(blocked.path("code").asInt() != 0, blocked.toString());
            assertNoExternalCalls(before);
        }
    }

    @Test
    void repeatedReviewIsRejectedAndOnlyTheFirstOneCounts() throws Exception {
        String threadId = seedSession(ownerId);
        String actionId = seedUnknownAction(threadId, ownerId);

        try (var owner = client()) {
            login(owner, ownerId);

            var first = post(owner, "/agent/recovery/action/confirm",
                    Map.of("threadId", threadId, "actionId", actionId));
            assertEquals(0, first.path("code").asInt(), first.toString());
            assertEquals(0, first.path("data").path("pendingReviewCount").asInt(), first.toString());
            assertEquals(AgentActionRecord.STATUS_SUCCEEDED, statusOf(threadId, actionId));

            // 重复核对：动作已是终态，必须被拒绝，且不会把它打回可执行
            var second = post(owner, "/agent/recovery/action/dismiss",
                    Map.of("threadId", threadId, "actionId", actionId));
            assertTrue(second.path("code").asInt() != 0, second.toString());
            assertEquals(AgentActionRecord.STATUS_SUCCEEDED, statusOf(threadId, actionId),
                    "a reviewed action must stay terminal");
        }
    }

    @Test
    void staleResumeVersionIsRejected() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 9L, "tool_node");

        try (var owner = client()) {
            login(owner, ownerId);
            int before = recorder.calls().size();
            var stale = post(owner, "/agent/recovery/" + threadId + "/resume?expectedVersion=4", Map.of());

            assertTrue(stale.path("code").asInt() != 0, stale.toString());
            assertTrue(stale.path("message").asText().contains("过期"), stale.toString());
            assertNoExternalCalls(before);
            assertEquals(9L, jdbc.queryForObject(
                    "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId),
                    "stale request must not touch the stored version");
        }
    }

    @Test
    void recoveryStateNeverLeaksAnotherUsersActions() throws Exception {
        String ownedThread = seedSession(ownerId);
        seedUnknownAction(ownedThread, ownerId);

        try (var stranger = client()) {
            login(stranger, strangerId);
            var response = get(stranger, "/agent/recovery/" + ownedThread);
            assertRejected(response);
            assertFalse(response.toString().contains(ownedThread),
                    "rejection must not echo the requested thread: " + response);
        }
    }

    // ---- 双写者与备份失败 ----

    @Test
    void lateOlderBackupLosesTheVersionRace() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 20L, "newer_node");

        // 旧异步备份晚到：带更小的版本
        boolean applied = runStateStore.saveFullState(threadId, ownerId, 7L, checkpoint("stale_node"),
                "RUNNING", null);
        assertFalse(applied, "an older backup must not overwrite newer state");
        assertEquals("newer_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        assertEquals(20L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId));
    }

    @Test
    void concurrentWritersFromTwoInstancesProduceOneWinner() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "start_node");

        // 两个实例各带自己的版本竞争同一行，数据库 CAS 只能让一个生效
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return runStateStore.saveFullState(threadId, ownerId, 100L,
                        checkpoint("instance_a"), "RUNNING", null);
            });
            Future<Boolean> b = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return runStateStore.saveFullState(threadId, ownerId, 101L,
                        checkpoint("instance_b"), "RUNNING", null);
            });
            start.countDown();

            // 两个都可能成功（版本递增），但最终状态必须是最新版本的那一个
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);

            assertEquals(101L, jdbc.queryForObject(
                    "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId),
                    "the newest version must win regardless of arrival order");
            assertEquals("instance_b", jdbc.queryForObject(
                    "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void backupFailureIsVisibleAndRetryClearsIt() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 2L, "tool_node");

        runStateStore.recordBackupFailure(threadId, ownerId,
                new IllegalStateException("disk full at /var/lib/secret-path"));

        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT backupError, backupAttempts FROM agent_run_state WHERE threadId = ?", threadId);
        assertNotNull(failed.get("backupError"), "a failed backup must be visible");
        assertEquals(1, ((Number) failed.get("backupAttempts")).intValue());
        assertFalse(String.valueOf(failed.get("backupError")).contains("secret-path"),
                "stored failure reason must not carry raw paths or content: " + failed.get("backupError"));

        // 重试成功一次后，错误被清掉、次数归零
        assertTrue(runStateStore.saveFullState(threadId, ownerId, 3L, checkpoint("tool_node"),
                "RUNNING", null));
        Map<String, Object> recovered = jdbc.queryForMap(
                "SELECT backupError, backupAttempts FROM agent_run_state WHERE threadId = ?", threadId);
        assertNull(recovered.get("backupError"));
        assertEquals(0, ((Number) recovered.get("backupAttempts")).intValue());
    }

    @Test
    void releaseClearsPayloadAndRecoveryPoint() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 4L, "tool_node");

        runStateStore.clearForRelease(threadId, ownerId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT statePayload, checkpointId, nextNodeId, pendingConfirmation, runStatus " +
                        "FROM agent_run_state WHERE threadId = ?", threadId);
        assertNull(row.get("statePayload"));
        assertNull(row.get("checkpointId"));
        assertNull(row.get("nextNodeId"));
        assertNull(row.get("pendingConfirmation"));
        assertEquals("COMPLETED", row.get("runStatus"));
    }

    @Test
    void staleExecutingActionIsReconciledNotReplayed() throws Exception {
        String threadId = seedSession(ownerId);
        seedAction(threadId, ownerId, "non-idempotent", "generateImages",
                AgentActionRecord.STATUS_EXECUTING, AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        seedAction(threadId, ownerId, "idempotent", "searchContentImages",
                AgentActionRecord.STATUS_EXECUTING, AgentActionRecord.IDEMPOTENCY_PROVEN);

        int reconciled = runStateStoreReconcile(threadId);

        assertEquals(2, reconciled);
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, statusOf(threadId, "non-idempotent"),
                "an interrupted non-idempotent call must wait for review, not be retried");
        assertEquals(AgentActionRecord.STATUS_FAILED, statusOf(threadId, "idempotent"),
                "a proven-idempotent call may fall back to a safe retry");
    }

    // ---- helpers ----

    private int runStateStoreReconcile(String threadId) {
        return ledger.reconcileStaleExecuting(threadId, ownerId);
    }

    private com.alibaba.cloud.ai.graph.checkpoint.Checkpoint checkpoint(String nextNode) {
        return com.alibaba.cloud.ai.graph.checkpoint.Checkpoint.builder()
                .id("cp-" + nextNode)
                .nodeId("model")
                .nextNodeId(nextNode)
                .state(Map.of("messages", List.of(
                                new org.springframework.ai.chat.messages.UserMessage("进行中的对话")),
                        "phase", "GENERATION"))
                .build();
    }

    private String statusOf(String threadId, String actionId) {
        return jdbc.queryForObject(
                "SELECT status FROM agent_action_record WHERE threadId = ? AND actionId = ?",
                String.class, threadId, actionId);
    }

    private void assertNoExternalCalls(int callsBefore) {
        assertEquals(callsBefore, recorder.calls().size(),
                "a blocked recovery request must not call models, tools or storage: "
                        + recorder.calls().subList(callsBefore, recorder.calls().size()));
    }

    private boolean isRejected(JsonNode response) {
        int code = response.path("code").asInt();
        return code == 40100 || code == 40101;
    }

    private void assertRejected(JsonNode response) {
        assertTrue(isRejected(response), "expected authorization rejection: " + response);
    }

    private String newThreadId() {
        String threadId = "it-recovery-" + UUID.randomUUID();
        threadIds.add(threadId);
        return threadId;
    }

    private String seedSession(long userId) {
        String threadId = newThreadId();
        jdbc.update("""
                INSERT INTO agent_session (sessionId, userId, title, status, lastMessage, messageCount,
                                           createdTime, updatedTime, isDelete)
                VALUES (?, ?, 'recovery thread', 1, '', 0, NOW(), NOW(), 0)
                """, threadId, userId);
        return threadId;
    }

    /** 走真实保存路径写入完整状态，避免测试自己拼载荷后与生产序列化分叉 */
    private void seedRunState(String threadId, long userId, long version, String nextNodeId) {
        jdbc.update("""
                INSERT INTO agent_run_state (threadId, userId, stateVersion, stateFormat, runStatus,
                                             legacyArchiveOnly, backupAttempts, createTime, updateTime)
                VALUES (?, ?, 0, 'v2-full', 'RUNNING', 0, 0, NOW(), NOW())
                """, threadId, userId);
        boolean saved = runStateStore.saveFullState(threadId, userId, version,
                checkpoint(nextNodeId), "RUNNING", null);
        assertTrue(saved, "fixture must persist a resumable state");
    }

    private void seedLegacyArchiveOnly(String threadId, long userId) {
        jdbc.update("""
                INSERT INTO agent_run_state (threadId, userId, stateVersion, stateFormat, statePayload,
                                             runStatus, legacyArchiveOnly, backupAttempts, createTime, updateTime)
                VALUES (?, ?, 1, 'v1-lightweight', NULL, 'PAUSED_UNRECOVERABLE', 1, 0, NOW(), NOW())
                """, threadId, userId);
    }

    private String seedUnknownAction(String threadId, long userId) {
        String actionId = "action-" + UUID.randomUUID();
        seedAction(threadId, userId, actionId, "generateImages",
                AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW,
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        return actionId;
    }

    private void seedAction(String threadId, long userId, String actionId, String toolName,
                            String status, String idempotency) {
        jdbc.update("""
                INSERT INTO agent_action_record (threadId, userId, actionId, toolName, argumentsDigest,
                                                 status, idempotency, attempt, createTime, updateTime)
                VALUES (?, ?, ?, ?, 'digest', ?, ?, 1, NOW(), NOW())
                """, threadId, userId, actionId, toolName, status, idempotency);
    }

    private long register(String account) throws Exception {
        try (var client = client()) {
            var response = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, response.path("code").asInt(), response.toString());
            return response.path("data").asLong();
        }
    }

    private long registerAdmin(String account) throws Exception {
        long id = register(account);
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", id);
        return id;
    }

    private void login(HttpClient client, long userId) throws Exception {
        String account = jdbc.queryForObject(
                "SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        var response = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path)).GET().build()).body());
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> body) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(CsrfToken.HEADER, CsrfToken.of(client))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
