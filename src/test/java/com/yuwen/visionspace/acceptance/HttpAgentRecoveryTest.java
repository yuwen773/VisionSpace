package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.recovery.AgentActionClaim;
import com.yuwen.visionspace.agent.recovery.AgentActionLedger;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.recovery.StateSaveResult;
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
import java.time.Instant;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限恢复流程（#41）：全部经真实 HTTP 与真实 MySQL。
 *
 * <p>覆盖：恢复入口沿用会话归属（他人与平台管理员都不能续跑）；
 * 只有旧轻量归档时明确不可恢复；待核对动作阻止续跑；过期版本与重复恢复请求
 * 被拒绝；重复核对与过期核对不生效；双写者按版本 CAS 只有一个生效；
 * 备份失败可见可重试；释放会话后载荷与计数被清理；
 * 执行世代拒绝迟到旧载荷覆盖新进度（含真实 MySQL 时序交错）；
 * RUNNING 失活判定区分活跃执行与硬崩溃遗留；工具环境与检查点同版本原子落库。
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
    void duplicateResumeRequestsCannotBothClaimTheSameVersion() {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 9L, "tool_node");

        // 两个请求带同一个版本：只有一个能认领，另一个落败，不会执行第二次
        assertTrue(runStateStore.claimForResume(threadId, ownerId, 9L).isPresent(),
                "the first claim must win");
        assertFalse(runStateStore.claimForResume(threadId, ownerId, 9L).isPresent(),
                "a duplicate resume must lose the version race");
        assertEquals(10L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId),
                "the claim advances the version so old requests expire");
    }

    /**
     * 刷新版本也不能绕过互斥：第一个续跑还没结束时，第二个请求即便拿到新版本也必须落败。
     *
     * <p>这是评审 P1 的第二个缺陷：旧实现只比版本，第二个请求读一次最新版本就能再次认领。
     */
    @Test
    void refreshedVersionCannotBypassARunningResume() {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 9L, "tool_node");

        assertTrue(runStateStore.claimForResume(threadId, ownerId, 9L).isPresent());
        long refreshed = jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId);

        assertFalse(runStateStore.claimForResume(threadId, ownerId, refreshed).isPresent(),
                "a second resume must not run while the first still holds the lease");
    }

    @Test
    void releasingTheLeaseLetsTheUserResumeAgain() {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 9L, "tool_node");

        String lease = runStateStore.claimForResume(threadId, ownerId, 9L).orElseThrow();
        runStateStore.releaseResumeLease(threadId, ownerId, lease);

        long current = jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId);
        assertTrue(runStateStore.claimForResume(threadId, ownerId, current).isPresent(),
                "after the lease is released the user must be able to resume again");
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

    // ---- 动作认领：真实 MySQL 的原子性 ----

    /**
     * 同一动作的并发认领在真实 MySQL 上只有一个赢家。
     *
     * <p>这条必须打真库：{@code INSERT ... ON DUPLICATE KEY UPDATE} 的赋值顺序语义
     * （后续表达式看到已更新的值）在内存替身里复现不出来。
     */
    @Test
    void concurrentClaimsOnRealMySqlLetOnlyOneOwnerIn() throws Exception {
        String threadId = seedSession(ownerId);
        String actionId = "action-" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<AgentActionClaim>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                            AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
                }));
            }
            start.countDown();

            int acquired = 0;
            for (Future<AgentActionClaim> future : futures) {
                AgentActionClaim claim = future.get(30, TimeUnit.SECONDS);
                if (claim.acquired()) {
                    acquired++;
                } else {
                    assertEquals(AgentActionClaim.Outcome.IN_FLIGHT, claim.outcome(),
                            "a losing claimant must be told the action is in flight");
                }
            }
            assertEquals(1, acquired, "exactly one claimant may own the action");
            assertEquals(AgentActionRecord.STATUS_EXECUTING, statusOf(threadId, actionId));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 明确失败的动作可以被重新认领，并且拿到**新的** token。
     *
     * <p>这正是 {@code ON DUPLICATE KEY UPDATE} 赋值顺序的回归点：如果 status 先被改写，
     * 后面的条件全部失效，claimToken 会停留在旧值，原认领者仍能落结果。
     */
    @Test
    void failedActionCanBeReclaimedWithANewToken() {
        String threadId = seedSession(ownerId);
        String actionId = "action-" + UUID.randomUUID();

        AgentActionClaim first = ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        assertTrue(first.acquired());
        assertTrue(ledger.markFailed(first, "明确失败"));

        AgentActionClaim second = ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        assertTrue(second.acquired(), "an explicitly failed action may be retried");
        assertNotEquals(first.token(), second.token(),
                "the retry must get its own token, otherwise the old owner could settle it");

        // 旧 token 已经失效，不能再落结果
        assertFalse(ledger.markSucceeded(first, "伪造"),
                "the previous claim must not be able to settle the new attempt");
        assertTrue(ledger.markSucceeded(second, "ok"));
    }

    @Test
    void succeededActionCannotBeReclaimed() {
        String threadId = seedSession(ownerId);
        String actionId = "action-" + UUID.randomUUID();

        AgentActionClaim claim = ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        ledger.markSucceeded(claim, "ok");

        AgentActionClaim again = ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.ALREADY_SUCCEEDED, again.outcome());
        assertEquals(AgentActionRecord.STATUS_SUCCEEDED, statusOf(threadId, actionId));
    }

    @Test
    void dismissedActionIsTerminalAndNotSilentlyReExecuted() {
        String threadId = seedSession(ownerId);
        String actionId = seedUnknownAction(threadId, ownerId);

        assertTrue(ledger.dismissUnknown(threadId, ownerId, actionId, "确认未执行"));

        AgentActionClaim again = ledger.claim(threadId, ownerId, actionId, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        assertEquals(AgentActionClaim.Outcome.DISMISSED, again.outcome(),
                "a reviewed-as-not-executed action must not become executable again");
        assertEquals(AgentActionRecord.STATUS_DISMISSED, statusOf(threadId, actionId));
    }

    // ---- 双写者与备份失败 ----

    @Test
    void lateOlderBackupLosesTheVersionRace() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 20L, "newer_node");

        // 旧异步备份晚到：带更小的版本
        StateSaveResult applied = runStateStore.saveFullState(threadId, ownerId, 7L,
                checkpoint("stale_node"), "RUNNING", null);
        assertEquals(StateSaveResult.SUPERSEDED, applied,
                "an older backup must not overwrite newer state, and must be reported as superseded");
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
            Future<StateSaveResult> a = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return runStateStore.saveFullState(threadId, ownerId, 100L,
                        checkpoint("instance_a"), "RUNNING", null);
            });
            Future<StateSaveResult> b = pool.submit(() -> {
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

    /**
     * 启动更早的实例接管会话后，版本必须从共享状态续上，而不是被 CAS 一直拒绝。
     *
     * <p>这是评审 P1 的回归点：旧实现用「进程启动时间 + 本地计数器」生成版本，
     * 接管方每次写入都落在库里的版本之下，影响 0 行——进度被静默丢弃。
     */
    @Test
    void takingOverInstanceWritesProgressAboveTheStoredVersion() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 500L, "tool_node");

        // 接管方不持有任何本地计数器：版本只能从共享状态推算
        long next = runStateStore.currentVersion(threadId, ownerId) + 1;
        StateSaveResult result = runStateStore.saveFullState(threadId, ownerId, next,
                checkpoint("after_takeover"), "RUNNING", null);

        assertEquals(StateSaveResult.SAVED, result,
                "a taking-over instance must be able to persist progress");
        assertEquals(501L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId));
        assertEquals("after_takeover", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
    }

    // ---- 执行世代：迟到旧载荷不能借重试覆盖新进度 ----

    /**
     * 评审 F1 的探针场景：新执行已提交 v2，旧执行的迟到载荷被版本 CAS 拒绝后
     * 重新编号重试成 v3，最终恢复点倒退。世代约束下，旧载荷无论编号多高都写不进。
     */
    @Test
    void staleEpochWriteCannotOverwriteAfterTakeover() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "start_node");

        // 执行 A 接管并推进一个检查点
        long epochA = runStateStore.beginExecution(threadId, ownerId);
        assertTrue(epochA > 0, "an existing row must hand out a bumped epoch");
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 2L,
                epochA, null, checkpoint("a1_node"), "RUNNING", null));

        // 执行 B 开始：世代更替，A 从此失去写入资格
        long epochB = runStateStore.beginExecution(threadId, ownerId);
        assertTrue(epochB > epochA, "each execution start must bump the epoch");
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 3L,
                epochB, null, checkpoint("b1_node"), "RUNNING", null));

        // A 的迟到载荷：版本更高也必须被拒，且不允许对齐重试
        StateSaveResult late = runStateStore.saveFullState(threadId, ownerId, 4L,
                epochA, null, checkpoint("a2_node"), "RUNNING", null);
        assertEquals(StateSaveResult.LOST_OWNERSHIP, late,
                "a superseded execution must not renumber its stale payload over newer progress");

        // 最终恢复点仍是执行 B 的内容：版本与载荷一起核对，不只看版本递增
        assertEquals(3L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId));
        assertEquals("b1_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
    }

    @Test
    void takingOverExecutionKeepsPersistingWithItsOwnEpoch() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "start_node");

        long epochA = runStateStore.beginExecution(threadId, ownerId);
        runStateStore.saveFullState(threadId, ownerId, 2L,
                epochA, null, checkpoint("a1_node"), "RUNNING", null);

        // 较晚开始的执行合法接管：推进自己的检查点是正常能力，不能被误伤
        long epochB = runStateStore.beginExecution(threadId, ownerId);
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 3L,
                epochB, null, checkpoint("b2_node"), "RUNNING", null));
        assertEquals("b2_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));

        // 并发时序交错：两个执行同时发起写入，世代不一致的只会输掉归属，不是故障
        assertEquals(StateSaveResult.LOST_OWNERSHIP, runStateStore.saveFullState(threadId,
                ownerId, 4L, epochA, null, checkpoint("a3_node"), "RUNNING", null));
    }

    // ---- RUNNING 失活判定：硬崩溃遗留状态必须露出恢复入口 ----

    @Test
    void freshRunningStateReportsActiveExecution() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 5L, "tool_node");

        try (var owner = client()) {
            login(owner, ownerId);
            var state = get(owner, "/agent/recovery/" + threadId);
            assertEquals(0, state.path("code").asInt(), state.toString());
            assertTrue(state.path("data").path("activeExecution").asBoolean(),
                    "a just-started run must be reported as active: " + state);
            assertTrue(state.path("data").path("resumable").asBoolean());
        }
    }

    @Test
    void staleRunningStateExposesTheResumeEntryAfterAHardCrash() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 5L, "tool_node");
        // 模拟进程失活：硬崩溃不经过 catch，行永远停在 RUNNING 且不再推进。
        // 夹具用固定远古时间戳，避免 MySQL 与 JVM 的时区差影响判定
        jdbc.update("UPDATE agent_run_state SET updateTime = '2020-01-01 00:00:00' "
                + "WHERE threadId = ?", threadId);

        try (var owner = client()) {
            login(owner, ownerId);
            var state = get(owner, "/agent/recovery/" + threadId);
            assertFalse(state.path("data").path("activeExecution").asBoolean(),
                    "an orphaned RUNNING row must not be reported as active: " + state);
            assertTrue(state.path("data").path("resumable").asBoolean(),
                    "an orphaned run with a full checkpoint must offer resume: " + state);
            assertEquals("RUNNING", state.path("data").path("runStatus").asText());
        }
    }

    @Test
    void unexpiredLeaseKeepsTheRunActiveEvenWithoutRecentUpdates() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 5L, "tool_node");
        jdbc.update("UPDATE agent_run_state SET resumeLease = 'lease-tok', "
                + "resumeLeaseUntil = '2099-01-01 00:00:00', "
                + "updateTime = '2020-01-01 00:00:00' WHERE threadId = ?", threadId);

        try (var owner = client()) {
            login(owner, ownerId);
            var state = get(owner, "/agent/recovery/" + threadId);
            assertTrue(state.path("data").path("activeExecution").asBoolean(),
                    "a valid lease proves another execution is still running: " + state);
        }
    }

    // ---- 工具环境与检查点同版本原子落库 ----

    @Test
    void checkpointWriteCarriesItsExecutionEnvironment() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "start_node");
        long epoch = runStateStore.beginExecution(threadId, ownerId);

        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 2L,
                epoch, "[\"remote-a\"]", checkpoint("tool_node"), "RUNNING", null));

        // 环境与载荷在同一条 UPDATE 里：同一版本、同一次写入
        assertEquals(2L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId));
        assertEquals("[\"remote-a\"]", jdbc.queryForObject(
                "SELECT mcpServerCodes FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        assertEquals("tool_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
    }

    @Test
    void newRequestBeforeItsFirstCheckpointLeavesTheOldEnvironmentIntact() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "mcp_node");
        jdbc.update("UPDATE agent_run_state SET mcpServerCodes = '[\"legacy-a\"]' "
                + "WHERE threadId = ?", threadId);

        // 新请求（内置工具）开始：只声明世代与环境，尚未提交任何新检查点就崩溃。
        // 旧实现会在这里提前直写 []，留下「旧载荷 + 内置环境」的错配。
        long epoch = runStateStore.beginExecution(threadId, ownerId);
        assertTrue(epoch > 0);

        // 崩溃后：既有恢复点连同它的环境必须原样保留
        assertEquals(1L, jdbc.queryForObject(
                "SELECT stateVersion FROM agent_run_state WHERE threadId = ?", Long.class, threadId));
        assertEquals("[\"legacy-a\"]", jdbc.queryForObject(
                "SELECT mcpServerCodes FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        assertEquals("mcp_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
    }

    @Test
    void resumeExecutionKeepsTheRecordedEnvironmentPairedWithThePayload() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "mcp_node");
        jdbc.update("UPDATE agent_run_state SET mcpServerCodes = '[\"legacy-a\"]' "
                + "WHERE threadId = ?", threadId);
        long epoch = runStateStore.beginExecution(threadId, ownerId);

        // 续跑不声明新环境（null）：载荷推进到新版本，环境保持与恢复点配对
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 2L,
                epoch, null, checkpoint("resumed_node"), "RUNNING", null));

        assertEquals("[\"legacy-a\"]", jdbc.queryForObject(
                "SELECT mcpServerCodes FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        assertEquals("resumed_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
    }

    @Test
    void interleavedExecutionsKeepEnvironmentAndPayloadPaired() throws Exception {
        String threadId = seedSession(ownerId);
        seedRunState(threadId, ownerId, 1L, "start_node");

        // 执行 A（MCP-A）先写入带环境的检查点
        long epochA = runStateStore.beginExecution(threadId, ownerId);
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 2L,
                epochA, "[\"remote-a\"]", checkpoint("a_node"), "RUNNING", null));

        // 执行 B（内置）接管：A 的迟到写入（连环境一起）必须整体被拒
        long epochB = runStateStore.beginExecution(threadId, ownerId);
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 3L,
                epochB, "[]", checkpoint("b_node"), "RUNNING", null));
        assertEquals(StateSaveResult.LOST_OWNERSHIP, runStateStore.saveFullState(threadId,
                ownerId, 4L, epochA, "[\"remote-a\"]", checkpoint("a_late_node"), "RUNNING", null));

        // 最终状态必须是 B 的配对：内置载荷 + 显式空环境，而不是 A 的环境
        assertEquals("[]", jdbc.queryForObject(
                "SELECT mcpServerCodes FROM agent_run_state WHERE threadId = ?", String.class, threadId));
        assertEquals("b_node", jdbc.queryForObject(
                "SELECT nextNodeId FROM agent_run_state WHERE threadId = ?", String.class, threadId));
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
        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 3L,
                checkpoint("tool_node"), "RUNNING", null));
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
        // 夹具用固定的远古时间戳，避免 MySQL 与 JVM 的时区/时钟差影响判定
        return ledger.reconcileStaleExecuting(threadId, ownerId,
                Instant.now().minus(Duration.ofMinutes(10)));
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

    /**
     * 走真实保存路径写入完整状态。
     *
     * <p>这里<b>不预先插行</b>：状态行必须由 {@code saveFullState} 自己创建，
     * 否则生产里第一次执行会因为「行不存在、UPDATE 影响 0 行」而静默丢失状态。
     */
    private void seedRunState(String threadId, long userId, long version, String nextNodeId) {
        StateSaveResult saved = runStateStore.saveFullState(threadId, userId, version,
                checkpoint(nextNodeId), "RUNNING", null);
        assertEquals(StateSaveResult.SAVED, saved,
                "saveFullState must create the row on first use and persist the state");
    }

    private void seedLegacyArchiveOnly(String threadId, long userId) {
        // 模拟历史数据：只有轻量归档，没有完整载荷与恢复点
        jdbc.update("""
                INSERT INTO agent_run_state (threadId, userId, stateVersion, stateFormat, statePayload,
                                             runStatus, legacyArchiveOnly, backupAttempts, createTime, updateTime)
                VALUES (?, ?, 1, 'v1-lightweight', NULL, 'PAUSED_UNRECOVERABLE', 1, 0, NOW(), NOW())
                """, threadId, userId);
    }

    @Test
    void firstRunCreatesTheStateRowSoStatusIsPersisted() {
        // 生产里第一次执行必须先建行，否则所有 UPDATE 都是静默的空操作
        String threadId = seedSession(ownerId);

        assertEquals(StateSaveResult.SAVED, runStateStore.saveFullState(threadId, ownerId, 1L,
                checkpoint("tool_node"), "RUNNING", null));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_state WHERE threadId = ? AND userId = ?",
                Integer.class, threadId, ownerId));
        assertNotNull(jdbc.queryForObject(
                "SELECT statePayload FROM agent_run_state WHERE threadId = ?", byte[].class, threadId),
                "the full payload must actually be stored, not silently dropped");
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
                                                 status, idempotency, claimToken, attempt, createTime, updateTime)
                VALUES (?, ?, ?, ?, 'digest', ?, ?, 'seed-token', 1, '2020-01-01 00:00:00', '2020-01-01 00:00:00')
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
