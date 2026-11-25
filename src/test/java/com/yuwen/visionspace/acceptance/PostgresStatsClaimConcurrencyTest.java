package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.service.PictureStatsAggregateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计事件「认领 + 租约」的并发语义，跑在真实 PostgreSQL 上。
 *
 * <p>MySQL 的 {@code UPDATE ... ORDER BY ... LIMIT} 在 PostgreSQL 里要写成
 * CTE + {@code FOR UPDATE SKIP LOCKED}。这改的是并发正确性本身，所以必须用多个
 * 真实连接同时抢同一批事件来验证：
 * <ul>
 *   <li>每条事件只会被一个 worker 认领（不重复累计）；</li>
 *   <li>认领不是永久占用：租约到期的行可以被别的 worker 重新认领（崩溃可恢复）；</li>
 *   <li>并发下不丢事件：所有事件最终都被认领，且每条只属于一个认领令牌。</li>
 * </ul>
 */
@IsolatedSpringBootTest
class PostgresStatsClaimConcurrencyTest {

    private static final long PICTURE_ID = 9_142_001L;
    private static final long FIRST_EVENT_ID = 9_142_100L;
    private static final int EVENT_COUNT = 30;

    @Autowired
    private UserPictureActionMapper actionMapper;

    @Autowired
    private PictureStatsAggregateService aggregateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanUp();
        jdbc.update("INSERT INTO picture (id, url, name, user_id, review_status) VALUES (?, ?, ?, ?, 1)",
                PICTURE_ID, "https://acceptance.invalid/claim.png", "claim", 9_142_000L);
        for (int i = 0; i < EVENT_COUNT; i++) {
            jdbc.update("""
                    INSERT INTO user_picture_action
                        (id, user_id, picture_id, action_type, action_value, source, is_processed, is_accumulated)
                    VALUES (?, 0, ?, 0, 1, 0, 0, 0)
                    """, FIRST_EVENT_ID + i, PICTURE_ID);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM picture_stats WHERE picture_id = ?", PICTURE_ID);
        jdbc.update("DELETE FROM user_picture_action WHERE picture_id = ?", PICTURE_ID);
        jdbc.update("DELETE FROM picture WHERE id = ?", PICTURE_ID);
    }

    @Test
    void concurrentWorkersPartitionTheBatchWithoutOverlapOrLoss() throws Exception {
        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                String token = "acceptance-claim-" + worker;
                Callable<Integer> task = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    int claimed = 0;
                    int idleRounds = 0;
                    // 每轮最多领 10 条；连续空转才退出——SKIP LOCKED 下别的 worker 恰好
                    // 持有行锁时本轮可能领到 0，立刻退出会漏事件。
                    while (idleRounds < 50) {
                        int current = actionMapper.claimPendingEvents(token, 30, 10);
                        claimed += current;
                        if (current == 0) {
                            idleRounds++;
                            Thread.sleep(20);
                        } else {
                            idleRounds = 0;
                        }
                    }
                    return claimed;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(60, TimeUnit.SECONDS);
            }
            assertEquals(EVENT_COUNT, total, "并发认领不能多领也不能丢：总数必须等于事件数");
        } finally {
            pool.shutdownNow();
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, claim_token FROM user_picture_action WHERE picture_id = ? ORDER BY id
                """, PICTURE_ID);
        assertEquals(EVENT_COUNT, rows.size());
        Set<Object> ids = new HashSet<>();
        Set<Object> tokens = new HashSet<>();
        for (Map<String, Object> row : rows) {
            ids.add(row.get("id"));
            tokens.add(row.get("claim_token"));
            assertTrue(row.get("claim_token") != null, "每条被领走的事件都必须带认领令牌: " + row.get("id"));
        }
        assertEquals(EVENT_COUNT, ids.size(), "同一事件不能被两个 worker 同时认领");
        assertEquals(EVENT_COUNT, jdbc.queryForObject(
                "SELECT count(*) FROM user_picture_action WHERE picture_id = ? AND is_accumulated = 0",
                Long.class, PICTURE_ID));
        assertTrue(tokens.size() <= 4, "认领令牌只来自本轮四个 worker: " + tokens);
    }

    @Test
    void expiredLeaseCanBeReclaimedByAnotherWorker() {
        assertEquals(EVENT_COUNT, actionMapper.claimPendingEvents("acceptance-claim-owner", 30, EVENT_COUNT),
                "先由一个 worker 把整批事件认领走");
        jdbc.update("""
                UPDATE user_picture_action
                SET claim_expire_at = statement_timestamp() - interval '1 second'
                WHERE picture_id = ?
                """, PICTURE_ID);

        assertEquals(1, actionMapper.claimPendingEvents("acceptance-claim-taker", 30, 1),
                "租约过期的行必须能被重新认领：否则认领进程崩溃后事件永久卡住");
        assertEquals("acceptance-claim-taker", jdbc.queryForObject(
                "SELECT claim_token FROM user_picture_action WHERE id = ?", String.class, FIRST_EVENT_ID));
        assertEquals(2, jdbc.queryForObject(
                "SELECT attempt_count FROM user_picture_action WHERE id = ?", Integer.class, FIRST_EVENT_ID),
                "重新认领要计入尝试次数，积压看板才能识别一直失败的事件");
    }

    @Test
    void unexpiredLeaseIsNotStolen() {
        assertEquals(EVENT_COUNT, actionMapper.claimPendingEvents("acceptance-claim-owner", 300, EVENT_COUNT));
        assertEquals(0, actionMapper.claimPendingEvents("acceptance-claim-thief", 300, EVENT_COUNT),
                "租约未到期时不能抢：否则同一个 worker 正在累计的事件会被重复累计");
        assertEquals("acceptance-claim-owner", jdbc.queryForObject(
                "SELECT claim_token FROM user_picture_action WHERE id = ?", String.class, FIRST_EVENT_ID));
    }

    /**
     * 并发累计的最终值必须恰好等于事件数：小于说明丢更新，大于说明重复累计。
     * 认领不重不漏只是前半程，统计表的累加语义要单独钉住。
     */
    @Test
    void concurrentAggregatorsAccumulateEveryEventExactlyOnce() throws Exception {
        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    aggregateService.aggregateActionStats();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(EVENT_COUNT, pictureStats("impression_count"),
                "并发累计的最终值必须等于事件数");
        assertEquals(0L, unfinishedEvents(), "所有事件都必须被累计并完成");
    }

    /**
     * 失败注入：整轮聚合在外层事务里回滚后，事件必须退回待处理，统计不留下增量。
     */
    @Test
    void rolledBackAggregationLeavesEventsPendingWithoutDoubleCounting() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            aggregateService.aggregateActionStats();
            assertEquals(0L, unfinishedEvents(), "回滚前事件应当已被累计并标记完成");
            status.setRollbackOnly();
        });

        assertEquals(0L, pictureStats("impression_count"), "整体回滚后统计不能留下增量");
        assertEquals(EVENT_COUNT, unfinishedEvents(), "整体回滚后事件必须全部回到待处理：不丢也不重复累计");
    }

    private long pictureStats(String column) {
        List<Long> values = jdbc.queryForList(
                "SELECT " + column + " FROM picture_stats WHERE picture_id = ?", Long.class, PICTURE_ID);
        return values.isEmpty() ? 0L : values.get(0);
    }

    private long unfinishedEvents() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM user_picture_action
                WHERE picture_id = ? AND (is_accumulated = 0 OR is_processed = 0)
                """, Long.class, PICTURE_ID);
    }
}
