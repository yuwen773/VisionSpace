package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 动作台账的状态机：已确认完成不重放、结果未知不盲目重试、审计不含正文。
 */
@ExtendWith(MockitoExtension.class)
class AgentActionLedgerTest {

    private static final Long OWNER = 7L;
    private static final String THREAD = "t-1";
    private static final String ACTION = "a-1";

    @Mock
    private AgentActionRecordMapper mapper;

    private AgentActionLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new AgentActionLedger(mapper);
    }

    @Test
    void completedActionIsNotExecutedAgain() {
        when(mapper.selectOne(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_SUCCEEDED));

        assertFalse(ledger.begin(THREAD, OWNER, ACTION, "generateImages", "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN),
                "confirmed-complete action must not be replayed");
    }

    @Test
    void executingActionMayContinue() {
        when(mapper.selectOne(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_EXECUTING));

        assertTrue(ledger.begin(THREAD, OWNER, ACTION, "searchContentImages", "{}",
                AgentActionRecord.IDEMPOTENCY_PROVEN));
    }

    @Test
    void unknownResultWithoutProvenIdempotencyWaitsForReview() {
        ledger.markUnknown(THREAD, OWNER, ACTION, false, "network reset");

        verify(mapper).settle(THREAD, OWNER, ACTION,
                AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, "network reset");
    }

    @Test
    void unknownResultWithProvenIdempotencyFallsBackToSafeRetry() {
        ledger.markUnknown(THREAD, OWNER, ACTION, true, "network reset");

        verify(mapper).settle(THREAD, OWNER, ACTION,
                AgentActionRecord.STATUS_FAILED, "network reset");
    }

    @Test
    void staleExecutingIsReconciledAsUnknownRatherThanReplayed() {
        Instant now = Instant.now();
        when(mapper.selectByThread(THREAD, OWNER)).thenReturn(List.of(
                recordWithTime(AgentActionRecord.STATUS_EXECUTING, ACTION,
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN, hoursAgo(2)),
                recordWithTime(AgentActionRecord.STATUS_EXECUTING, "a-2",
                        AgentActionRecord.IDEMPOTENCY_PROVEN, hoursAgo(2)),
                recordWithTime(AgentActionRecord.STATUS_SUCCEEDED, "a-3",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN, hoursAgo(2))));
        when(mapper.settle(eq(THREAD), eq(OWNER), anyString(), anyString(), anyString()))
                .thenReturn(1);

        int reconciled = ledger.reconcileStaleExecuting(THREAD, OWNER, now.minus(Duration.ofMinutes(10)));

        assertEquals(2, reconciled, "only stale EXECUTING actions are reconciled");
        verify(mapper).settle(THREAD, OWNER, ACTION,
                AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, "上次执行中断，结果未确认");
        verify(mapper).settle(THREAD, OWNER, "a-2",
                AgentActionRecord.STATUS_FAILED, "上次执行中断，结果未确认");
        // 已完成的动作不参与收敛
        verify(mapper, never()).settle(eq(THREAD), eq(OWNER), eq("a-3"), anyString(), anyString());
    }

    @Test
    void actionStillInFlightIsNotTreatedAsInterrupted() {
        // 另一个实例正在执行：刚更新过的 EXECUTING 不能被判成遗留而放行重放
        when(mapper.selectByThread(THREAD, OWNER)).thenReturn(List.of(
                recordWithTime(AgentActionRecord.STATUS_EXECUTING, ACTION,
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN, new Date())));

        int reconciled = ledger.reconcileStaleExecuting(THREAD, OWNER,
                Instant.now().minus(Duration.ofMinutes(10)));

        assertEquals(0, reconciled);
        verify(mapper, never()).settle(eq(THREAD), eq(OWNER), anyString(), anyString(), anyString());
    }

    @Test
    void actionIdIsStableAndDistinctPerCall() {
        assertEquals(AgentActionLedger.actionId(THREAD, "call-1"),
                AgentActionLedger.actionId(THREAD, "call-1"),
                "same tool call must map to the same action id");
        assertNotEquals(AgentActionLedger.actionId(THREAD, "call-1"),
                AgentActionLedger.actionId(THREAD, "call-2"));
        assertNotEquals(AgentActionLedger.actionId(THREAD, "call-1"),
                AgentActionLedger.actionId("t-2", "call-1"));
    }

    @Test
    void argumentsAreStoredAsDigestNotAsRawText() {
        assertEquals(AgentActionLedger.argumentsDigest("{\"prompt\":\"private\"}"),
                AgentActionLedger.argumentsDigest("{\"prompt\":\"private\"}"));
        assertEquals(64, AgentActionLedger.argumentsDigest("anything").length(),
                "digest must be a fixed-length hash");
        assertNotEquals(AgentActionLedger.argumentsDigest("a"),
                AgentActionLedger.argumentsDigest("b"));
    }

    @Test
    void confirmAndDismissOnlyMoveFromReviewableStates() {
        when(mapper.confirmCompleted(THREAD, OWNER, ACTION, "ok")).thenReturn(1);
        when(mapper.dismissUnknown(THREAD, OWNER, ACTION, "no")).thenReturn(0);

        assertTrue(ledger.confirmCompleted(THREAD, OWNER, ACTION, "ok"));
        assertFalse(ledger.dismissUnknown(THREAD, OWNER, ACTION, "no"),
                "already handled action must not be re-handled");
    }

    private AgentActionRecord record(String status) {
        return recordWithId(status, ACTION, AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
    }

    private AgentActionRecord recordWithId(String status, String id, String idempotency) {
        return recordWithTime(status, id, idempotency, hoursAgo(2));
    }

    private AgentActionRecord recordWithTime(String status, String id, String idempotency,
                                             Date updateTime) {
        AgentActionRecord record = new AgentActionRecord();
        record.setThreadId(THREAD);
        record.setUserId(OWNER);
        record.setActionId(id);
        record.setToolName("tool");
        record.setStatus(status);
        record.setIdempotency(idempotency);
        record.setAttempt(1);
        record.setUpdateTime(updateTime);
        return record;
    }

    private Date hoursAgo(int hours) {
        return new Date(System.currentTimeMillis() - hours * 3600_000L);
    }
}
