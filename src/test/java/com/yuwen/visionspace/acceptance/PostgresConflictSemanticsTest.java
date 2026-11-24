package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;
import com.yuwen.visionspace.mapper.PictureActionEventDedupeMapper;
import com.yuwen.visionspace.mapper.PictureIndexTaskMapper;
import com.yuwen.visionspace.mapper.PictureStatsMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@code ON CONFLICT} 的冲突目标与影响行数语义，跑在真实 PostgreSQL 上。
 *
 * <p>MySQL 的 {@code ON DUPLICATE KEY UPDATE} 与 PostgreSQL 的 {@code ON CONFLICT}
 * 有两处不能靠阅读代码发现的差别，这里逐条钉住：
 * <ul>
 *   <li>影响行数：MySQL 更新返回 2、插入返回 1；PostgreSQL 插入与更新都返回 1，
 *       只有 {@code DO NOTHING} 才是 1/0。任何靠 {@code == 2} 区分插入/更新的写法都必须改写。</li>
 *   <li>赋值顺序：MySQL 的 SET 从左到右、后一项能看到前一项的新值；PostgreSQL 的
 *       SET 右侧全部看到<strong>旧行</strong>。点击率这种「用计数重算」的列必须显式
 *       用「旧值 + EXCLUDED」表达，否则会算出错的结果。</li>
 * </ul>
 */
@IsolatedSpringBootTest
class PostgresConflictSemanticsTest {

    private static final long PICTURE_ID = 9_141_001L;
    private static final long INDEX_TASK_PICTURE_ID = 9_141_002L;
    private static final long USER_ID = 9_141_003L;
    private static final String THREAD_ID = "acceptance-conflict-thread";
    private static final String ACTION_ID = "acceptance-conflict-action";
    private static final String EVENT_ID = "acceptance-conflict-event";

    @Autowired
    private PictureStatsMapper statsMapper;

    @Autowired
    private PictureIndexTaskMapper indexTaskMapper;

    @Autowired
    private PictureActionEventDedupeMapper dedupeMapper;

    @Autowired
    private AgentActionRecordMapper actionRecordMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM picture_stats WHERE picture_id = ?", PICTURE_ID);
        jdbc.update("DELETE FROM picture_index_task WHERE picture_id = ?", INDEX_TASK_PICTURE_ID);
        jdbc.update("DELETE FROM picture_action_event_dedupe WHERE event_id = ?", EVENT_ID);
        jdbc.update("DELETE FROM agent_action_record WHERE thread_id = ?", THREAD_ID);
    }

    @Test
    void accumulateRecomputesClickThroughRateFromTheOldRow() {
        statsMapper.upsertIncrement(increment(4, 1));
        statsMapper.upsertIncrement(increment(6, 3));

        var row = jdbc.queryForMap(
                "SELECT impression_count, click_count, ctr FROM picture_stats WHERE picture_id = ?", PICTURE_ID);
        assertEquals(10L, ((Number) row.get("impression_count")).longValue());
        assertEquals(4L, ((Number) row.get("click_count")).longValue());
        BigDecimal ctr = (BigDecimal) row.get("ctr");
        assertEquals(0, new BigDecimal("0.4").compareTo(ctr),
                "ctr 必须按累加后的点击/曝光重算：SET 右侧只看到旧行，写错会得到 0.1 或 0.3");
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM picture_stats WHERE picture_id = ?", Long.class, PICTURE_ID),
                "同一张图只允许一行统计；更新不能变成插入第二行");
    }

    @Test
    void indexTaskFailuresCollapseToOneRowAndInsertAndUpdateBothReportOne() {
        assertEquals(1, indexTaskMapper.recordFailure(INDEX_TASK_PICTURE_ID, "UPSERT",
                PictureIndexTask.STATUS_FAILED, 1, "第一次", new Date()));
        assertEquals(1, indexTaskMapper.recordFailure(INDEX_TASK_PICTURE_ID, "DELETE",
                PictureIndexTask.STATUS_FAILED, 2, "第二次", new Date()),
                "PostgreSQL 的 ON CONFLICT DO UPDATE 在更新时也返回 1：调用方不能靠 2 判断插入");
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM picture_index_task WHERE picture_id = ?",
                Long.class, INDEX_TASK_PICTURE_ID));
        assertEquals(2, jdbc.queryForObject(
                "SELECT attempts FROM picture_index_task WHERE picture_id = ?",
                Integer.class, INDEX_TASK_PICTURE_ID));
    }

    @Test
    void dedupeEvidenceReportsFirstAndDuplicateAsOneAndZero() {
        assertEquals(1, dedupeMapper.insertEvidence(EVENT_ID), "首次受理：写入一行");
        assertEquals(0, dedupeMapper.insertEvidence(EVENT_ID),
                "重复受理：DO NOTHING 不写行，影响行数 0；不能靠捕获唯一约束异常判定");
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM picture_action_event_dedupe WHERE event_id = ?", Long.class, EVENT_ID));
    }

    @Test
    void onlyFailedAgentActionsCanBeReclaimedAndTheTokenProvesIt() {
        assertEquals(1, actionRecordMapper.claim(THREAD_ID, USER_ID, ACTION_ID, "tool",
                "digest", "NOT_PROVEN", "token-1"));
        AgentActionRecord executing = actionRecordMapper.selectForClaim(THREAD_ID, USER_ID, ACTION_ID);
        assertEquals("token-1", executing.getClaimToken());

        // 执行中的动作不能抢：影响行数仍是 1，但 claimToken 保持原值。
        actionRecordMapper.claim(THREAD_ID, USER_ID, ACTION_ID, "tool", "digest", "NOT_PROVEN", "token-2");
        assertEquals("token-1", actionRecordMapper.selectForClaim(THREAD_ID, USER_ID, ACTION_ID).getClaimToken(),
                "执行中的动作被再次认领会让两个执行者都以为自己抢到了");

        jdbc.update("UPDATE agent_action_record SET status = 'FAILED' WHERE thread_id = ?", THREAD_ID);
        actionRecordMapper.claim(THREAD_ID, USER_ID, ACTION_ID, "tool", "digest", "NOT_PROVEN", "token-3");
        AgentActionRecord reclaimed = actionRecordMapper.selectForClaim(THREAD_ID, USER_ID, ACTION_ID);
        assertEquals("token-3", reclaimed.getClaimToken(), "已明确失败的动作可以被重新认领");
        assertEquals("EXECUTING", reclaimed.getStatus());
        assertEquals(2, reclaimed.getAttempt(), "重新认领要计入尝试次数，供恢复策略判断");
        assertNotEquals(executing.getClaimToken(), reclaimed.getClaimToken());
    }

    private PictureActionStatsDTO increment(long impressions, long clicks) {
        PictureActionStatsDTO stats = new PictureActionStatsDTO();
        stats.setPictureId(PICTURE_ID);
        stats.setImpressionCount(impressions);
        stats.setClickCount(clicks);
        return stats;
    }
}
