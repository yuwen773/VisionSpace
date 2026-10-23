package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 动作台账的状态机：认领互斥、已确认完成不重放、结果未知不盲目重试、审计不含正文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    void firstClaimAcquiresTheActionWhenTheReadBackTokenIsOurs() {
        // 认领结果只能靠读回的 token 判定：影响行数在 Connector/J 下不可靠
        when(mapper.claim(eq(THREAD), eq(OWNER), eq(ACTION), anyString(), anyString(),
                anyString(), anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(6);
            when(mapper.selectForClaim(THREAD, OWNER, ACTION))
                    .thenReturn(record(AgentActionRecord.STATUS_EXECUTING, token));
            return 1;
        });

        AgentActionClaim claim = ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.ACQUIRED, claim.outcome());
        assertNotNull(claim.token(), "the winner must get a token to settle with");
    }

    @Test
    void claimLosesWhenAnotherWriterOwnsTheTokenEvenIfRowsWereAffected() {
        // 命中但值没变时 MySQL 仍可能返回非零：token 不是我们的，就是没抢到
        when(mapper.claim(eq(THREAD), eq(OWNER), eq(ACTION), anyString(), anyString(),
                anyString(), anyString())).thenReturn(2);
        when(mapper.selectForClaim(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_EXECUTING, "someone-elses-token"));

        assertEquals(AgentActionClaim.Outcome.IN_FLIGHT,
                ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
    }

    @Test
    void completedActionIsReportedAsCompletedInsteadOfExecutable() {
        when(mapper.claim(eq(THREAD), eq(OWNER), eq(ACTION), anyString(), anyString(),
                anyString(), anyString())).thenReturn(0);
        when(mapper.selectForClaim(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_SUCCEEDED, "old-token"));

        AgentActionClaim claim = ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.ALREADY_SUCCEEDED, claim.outcome(),
                "confirmed-complete action must not be replayed");
        assertNull(claim.token());
    }

    @Test
    void inFlightActionIsReportedAsInFlightNotCompleted() {
        when(mapper.claim(eq(THREAD), eq(OWNER), eq(ACTION), anyString(), anyString(),
                anyString(), anyString())).thenReturn(0);
        when(mapper.selectForClaim(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_EXECUTING, "other-token"));

        assertEquals(AgentActionClaim.Outcome.IN_FLIGHT,
                ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
    }

    @Test
    void actionWaitingForReviewIsReportedAsNeedingReview() {
        when(mapper.claim(eq(THREAD), eq(OWNER), eq(ACTION), anyString(), anyString(),
                anyString(), anyString())).thenReturn(0);
        when(mapper.selectForClaim(THREAD, OWNER, ACTION))
                .thenReturn(record(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, "old-token"));

        assertEquals(AgentActionClaim.Outcome.NEEDS_REVIEW,
                ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
    }

    @Test
    void ledgerFailureIsUnavailableRatherThanCompleted() {
        when(mapper.claim(anyString(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenThrow(new IllegalStateException("db down"));

        AgentActionClaim claim = ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);

        assertEquals(AgentActionClaim.Outcome.UNAVAILABLE, claim.outcome(),
                "a broken ledger proves nothing, so it must never look like a completion");
    }

    @Test
    void claimWithoutAffectedRowAndWithoutRecordIsUnavailable() {
        when(mapper.claim(anyString(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(0);
        when(mapper.selectForClaim(THREAD, OWNER, ACTION)).thenReturn(null);

        assertEquals(AgentActionClaim.Outcome.UNAVAILABLE,
                ledger.claim(THREAD, OWNER, ACTION, "generateImages", "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
    }

    @Test
    void settlingWithoutTheClaimTokenIsRejected() {
        // 没有凭据：连数据库都不碰，直接拒绝
        assertFalse(ledger.markSucceeded(null, "x"));
        assertFalse(ledger.markSucceeded(
                AgentActionClaim.notAcquired(AgentActionClaim.Outcome.IN_FLIGHT,
                        THREAD, OWNER, ACTION), "x"));
        verify(mapper, never()).settleClaimed(anyString(), any(), anyString(), anyString(),
                anyString(), anyString());

        // 凭据不对：交给数据库按 token 条件判定，影响 0 行即失败
        when(mapper.settleClaimed(THREAD, OWNER, ACTION, "forged",
                AgentActionRecord.STATUS_SUCCEEDED, "x")).thenReturn(0);
        assertFalse(ledger.markSucceeded(
                        AgentActionClaim.acquired(THREAD, OWNER, ACTION, "forged"), "x"),
                "a token that does not match the current claim must not settle the action");
    }

    @Test
    void settlingUsesTheIdentityCarriedByTheClaim() {
        when(mapper.settleClaimed(THREAD, OWNER, ACTION, "token",
                AgentActionRecord.STATUS_SUCCEEDED, "ok")).thenReturn(1);

        assertTrue(ledger.markSucceeded(
                AgentActionClaim.acquired(THREAD, OWNER, ACTION, "token"), "ok"));

        // 身份来自认领本身，调用方无法拿 A 的 token 去结算 B
        verify(mapper).settleClaimed(THREAD, OWNER, ACTION, "token",
                AgentActionRecord.STATUS_SUCCEEDED, "ok");
    }

    @Test
    void unknownResultWithoutProvenIdempotencyWaitsForReview() {
        when(mapper.settleClaimed(THREAD, OWNER, ACTION, "token",
                AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, "network reset")).thenReturn(1);

        assertTrue(ledger.markUnknown(
                AgentActionClaim.acquired(THREAD, OWNER, ACTION, "token"), false, "network reset"));
    }

    @Test
    void unknownResultWithProvenIdempotencyFallsBackToSafeRetry() {
        when(mapper.settleClaimed(THREAD, OWNER, ACTION, "token",
                AgentActionRecord.STATUS_FAILED, "network reset")).thenReturn(1);

        assertTrue(ledger.markUnknown(
                AgentActionClaim.acquired(THREAD, OWNER, ACTION, "token"), true, "network reset"));
    }

    @Test
    void staleExecutingIsReconciledInOneStatementWithTheCutoff() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        when(mapper.reconcileStaleExecuting(eq(THREAD), eq(OWNER), any(Date.class), anyString()))
                .thenReturn(2);

        int reconciled = ledger.reconcileStaleExecuting(THREAD, OWNER, cutoff);

        assertEquals(2, reconciled);
        ArgumentCaptor<Date> cutoffCaptor = ArgumentCaptor.forClass(Date.class);
        verify(mapper).reconcileStaleExecuting(eq(THREAD), eq(OWNER), cutoffCaptor.capture(),
                anyString());
        // Date 只有毫秒精度，比较到毫秒
        assertEquals(cutoff.toEpochMilli(), cutoffCaptor.getValue().getTime(),
                "the caller's cutoff must reach the database, not be re-derived from local time");
    }

    @Test
    void reconcileWithoutCutoffIsRejectedInsteadOfGuessing() {
        assertThrows(IllegalArgumentException.class,
                () -> ledger.reconcileStaleExecuting(THREAD, OWNER, null));
        verify(mapper, never()).reconcileStaleExecuting(anyString(), any(), any(Date.class),
                anyString());
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
        return record(status, "stored-token");
    }

    private AgentActionRecord record(String status, String claimToken) {
        AgentActionRecord record = new AgentActionRecord();
        record.setThreadId(THREAD);
        record.setUserId(OWNER);
        record.setActionId(ACTION);
        record.setToolName("tool");
        record.setStatus(status);
        record.setIdempotency(AgentActionRecord.IDEMPOTENCY_NOT_PROVEN);
        record.setClaimToken(claimToken);
        record.setAttempt(1);
        record.setUpdateTime(new Date());
        return record;
    }
}
