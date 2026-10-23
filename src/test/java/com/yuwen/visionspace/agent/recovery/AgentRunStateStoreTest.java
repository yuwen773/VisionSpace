package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.mapper.AgentRunStateMapper;
import com.yuwen.visionspace.model.entity.AgentRunState;
import com.yuwen.visionspace.utils.GzipUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 完整执行状态的持久化契约：版本 CAS、归属、格式兼容、旧归档不伪装恢复点。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunStateStoreTest {

    private static final Long OWNER = 11L;

    @Mock
    private AgentRunStateMapper mapper;

    private AgentRunStateStore store;

    @BeforeEach
    void setUp() {
        store = new AgentRunStateStore(mapper,
                com.alibaba.cloud.ai.graph.StateGraph.DEFAULT_JACKSON_SERIALIZER,
                new GzipUtils());
    }

    @Test
    void saveFullStateUsesVersionCasSoLateOldBackupIsIgnored() {
        // 旧备份晚到：CAS 影响 0 行，库里的版本比它新
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(0);
        when(mapper.selectVersion("t1", OWNER)).thenReturn(20L);

        StateSaveResult result = store.saveFullState("t1", OWNER, 5L, checkpoint(),
                RunStatus.RUNNING, null);

        assertEquals(StateSaveResult.SUPERSEDED, result,
                "older version must not overwrite newer state, and must be reported as superseded");
        verify(mapper).updateIfNewer(eq("t1"), eq(OWNER), eq(5L), anyString(), any(), any(),
                any(), any(), eq(RunStatus.RUNNING), any(), any(), any());
        // 被更新版本取代不是故障：不该写 backupError
        verify(mapper, never()).recordBackupFailure(anyString(), any(), anyString());
    }

    @Test
    void saveFullStateReportsSuccessWhenVersionIsNewer() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(1);

        assertEquals(StateSaveResult.SAVED,
                store.saveFullState("t1", OWNER, 9L, checkpoint(), RunStatus.RUNNING, null));
    }

    @Test
    void unwritableRowIsReportedAsFailureNotAsSuperseded() {
        // 影响 0 行但库里版本并没有推进：这是真的写不进去，不能当成「旧备份」放过
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(0);
        when(mapper.selectVersion("t1", OWNER)).thenReturn(2L);

        assertEquals(StateSaveResult.FAILED,
                store.saveFullState("t1", OWNER, 3L, checkpoint(), RunStatus.RUNNING, null));
        verify(mapper).recordBackupFailure(eq("t1"), eq(OWNER), anyString());
    }

    @Test
    void backupFailureIsRecordedSoItIsVisibleAndRetryable() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("disk full"));

        assertEquals(StateSaveResult.FAILED,
                store.saveFullState("t1", OWNER, 3L, checkpoint(), RunStatus.RUNNING, null));
        // 失败必须可见且可重试，不能静默吞掉
        verify(mapper).recordBackupFailure(eq("t1"), eq(OWNER), anyString());
    }

    @Test
    void mcpServerCodesRideTheSameUpdateAsTheCheckpointPayload() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(1);

        store.saveFullState("t1", OWNER, 1L, 0L, "[\"remote-a\",\"remote-b\"]",
                checkpoint(), RunStatus.RUNNING, null);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mapper).updateIfNewer(eq("t1"), eq(OWNER), eq(1L), anyString(), any(), any(),
                any(), any(), eq(RunStatus.RUNNING), any(), any(), captor.capture());
        assertEquals("[\"remote-a\",\"remote-b\"]", captor.getValue(),
                "only server codes are persisted: " + captor.getValue());
        assertFalse(captor.getValue().contains("apiKey"),
                "credentials and addresses must never be part of the recovery config");
    }

    @Test
    void environmentAndPayloadAreWrittenInOneStatementSoTheyStayTheSameVersion() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(1);

        // 环境不单独直写：没有可用的检查点写入，就没有任何环境变更落库——
        // 新请求在首个检查点前崩溃时，既有恢复点的环境原封不动
        store.saveFullState("t1", OWNER, 1L, 0L, "[\"remote-a\"]",
                checkpoint(), RunStatus.RUNNING, null);

        verify(mapper).updateIfNewer(eq("t1"), eq(OWNER), eq(1L), anyString(), any(), any(),
                any(), any(), eq(RunStatus.RUNNING), any(), any(), eq("[\"remote-a\"]"));
    }

    @Test
    void absentEnvironmentLeavesTheRecordedCodesUntouched() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(1);

        // 续跑不声明新环境：null 必须原样传给 SQL（COALESCE 保旧值），
        // 否则恢复点会配上一条空环境
        store.saveFullState("t1", OWNER, 1L, 0L, null,
                checkpoint(), RunStatus.RUNNING, null);

        verify(mapper).updateIfNewer(eq("t1"), eq(OWNER), eq(1L), anyString(), any(), any(),
                any(), any(), eq(RunStatus.RUNNING), any(), any(), (String) isNull());
    }

    @Test
    void epochMismatchIsReportedAsLostOwnershipAndNotRetried() {
        // 世代已更替：库里的世代不是本执行的，0 行必须判成 LOST_OWNERSHIP——
        // 重试同一个旧载荷毫无意义，绝不允许借对齐重试覆盖新进度
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(0);
        when(mapper.selectVersion("t1", OWNER)).thenReturn(20L);
        when(mapper.selectRunEpoch("t1", OWNER)).thenReturn(7L);

        StateSaveResult result = store.saveFullState("t1", OWNER, 21L, 3L, null,
                checkpoint(), RunStatus.RUNNING, null);

        assertEquals(StateSaveResult.LOST_OWNERSHIP, result,
                "a stale execution must not be allowed to retry over newer progress");
        verify(mapper, times(1)).updateIfNewer(anyString(), any(), anyLong(), anyString(),
                any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void epochMatchingVersionStaleIsStillSupersededSoAlignmentRetryStaysPossible() {
        // 世代匹配、版本落后：合法的同执行并发写入，仍按「被取代」报告，
        // 让调用方可以对齐重试
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(0);
        when(mapper.selectVersion("t1", OWNER)).thenReturn(20L);
        when(mapper.selectRunEpoch("t1", OWNER)).thenReturn(3L);

        assertEquals(StateSaveResult.SUPERSEDED,
                store.saveFullState("t1", OWNER, 5L, 3L, null,
                        checkpoint(), RunStatus.RUNNING, null));
    }

    @Test
    void beginExecutionBumpsTheEpochAtomicallyAndReturnsIt() {
        when(mapper.selectRunEpoch("t1", OWNER)).thenReturn(4L);
        when(mapper.bumpRunEpoch("t1", OWNER, 4L)).thenReturn(1);

        assertEquals(5L, store.beginExecution("t1", OWNER),
                "the execution must own the epoch it will write with");
    }

    @Test
    void beginExecutionWithoutARowStartsAtEpochZero() {
        // 首次执行：还没有状态行，insertIfAbsent 建行时世代默认 0，两者自然匹配
        when(mapper.selectRunEpoch("t1", OWNER)).thenReturn(null);

        assertEquals(0L, store.beginExecution("t1", OWNER));
    }

    @Test
    void beginExecutionRetriesWhenTheEpochRacesThenGivesUpWithAnUnwritableEpoch() {
        when(mapper.selectRunEpoch("t1", OWNER)).thenReturn(4L);
        when(mapper.bumpRunEpoch("t1", OWNER, 4L)).thenReturn(0);

        long epoch = store.beginExecution("t1", OWNER);

        assertTrue(epoch < 0,
                "a lost epoch race must return an unwritable epoch, not guess ownership");
        verify(mapper, times(5)).bumpRunEpoch("t1", OWNER, 4L);
    }

    @Test
    void recordedMcpServerCodesRoundTripAndUnparsableValuesAreNotGuessed() {
        AgentRunState state = state(AgentStateFormat.V2_FULL.code(), "next", new byte[]{1});
        state.setMcpServerCodes("[\"remote-a\"]");
        assertEquals(List.of("remote-a"), store.recordedMcpServerCodes(state).orElseThrow());

        state.setMcpServerCodes("[]");
        assertEquals(List.of(), store.recordedMcpServerCodes(state).orElseThrow(),
                "an explicit empty list means the run used no MCP");

        state.setMcpServerCodes("not-json");
        assertTrue(store.recordedMcpServerCodes(state).isEmpty(),
                "an unreadable environment must not be guessed");

        state.setMcpServerCodes(null);
        assertTrue(store.recordedMcpServerCodes(state).isEmpty(),
                "a state written before this column existed has no recorded environment");
    }

    @Test
    void currentVersionComesFromTheSharedStateNotLocalCounters() {
        when(mapper.selectVersion("t1", OWNER)).thenReturn(41L);

        assertEquals(41L, store.currentVersion("t1", OWNER),
                "version ordering must follow the shared persistent state");
    }

    @Test
    void currentVersionFallsBackToZeroWhenTheRowIsMissingOrUnreadable() {
        when(mapper.selectVersion("t1", OWNER)).thenReturn(null);
        assertEquals(0L, store.currentVersion("t1", OWNER));

        when(mapper.selectVersion("t2", OWNER)).thenThrow(new IllegalStateException("db down"));
        assertEquals(0L, store.currentVersion("t2", OWNER),
                "an unreadable version must not break the execution");
    }

    @Test
    void unwritableRowWithUnreadableVersionIsAFailureNotASupersede() {
        // 写入影响 0 行，且版本读不到：无法证明「被更新版本取代」，
        // 只能按真正的写入失败处理，否则这一版进度会静默消失
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any())).thenReturn(0);
        when(mapper.selectVersion("t1", OWNER)).thenThrow(new IllegalStateException("db down"));

        StateSaveResult result = store.saveFullState("t1", OWNER, 5L,
                checkpoint(), RunStatus.RUNNING, null);

        assertEquals(StateSaveResult.FAILED, result,
                "an unreadable version proves nothing, so the write must be reported as failed");
        verify(mapper).recordBackupFailure(eq("t1"), eq(OWNER), anyString());
    }

    @Test
    void legacyLightweightArchiveIsNotResumable() {
        AgentRunState state = state(AgentStateFormat.V1_LIGHTWEIGHT.code(), "next", new byte[]{1, 2, 3});
        when(mapper.selectOwned("t1", OWNER)).thenReturn(state);

        assertTrue(store.loadResumable("t1", OWNER).isEmpty(),
                "message-only archive must never be presented as a resume point");
    }

    @Test
    void unknownStateFormatIsNotResumable() {
        AgentRunState state = state("v9-future", "next", new byte[]{1});
        when(mapper.selectOwned("t1", OWNER)).thenReturn(state);

        assertTrue(store.loadResumable("t1", OWNER).isEmpty());
    }

    @Test
    void legacyArchiveOnlyFlagBlocksResumeEvenWithFullFormat() {
        AgentRunState state = state(AgentStateFormat.V2_FULL.code(), "next", new byte[]{1});
        state.setLegacyArchiveOnly(1);
        when(mapper.selectOwned("t1", OWNER)).thenReturn(state);

        assertTrue(store.loadResumable("t1", OWNER).isEmpty());
    }

    @Test
    void fullStateWithoutRecoveryPointCannotBeTurnedIntoCheckpoint() {
        AgentRunState state = state(AgentStateFormat.V2_FULL.code(), null, new byte[]{1});

        assertTrue(store.toCheckpoint(state).isEmpty(),
                "messages without next node must not be treated as a resume point");
    }

    @Test
    void incompleteResumePointIsRejectedInsteadOfThrowing() {
        AgentRunState noId = state(AgentStateFormat.V2_FULL.code(), "tool_node", new byte[]{1});
        noId.setNodeId("model_node");

        AgentRunState noNode = state(AgentStateFormat.V2_FULL.code(), "tool_node", new byte[]{1});
        noNode.setCheckpointId("cp-1");

        assertTrue(store.toCheckpoint(noId).isEmpty(),
                "a resume point needs a checkpoint id, not only a next node");
        assertTrue(store.toCheckpoint(noNode).isEmpty(),
                "a resume point needs the node it came from");
    }

    @Test
    void fullStateRoundTripsIntoCheckpointWithRecoveryPoint() throws Exception {
        Map<String, Object> data = Map.of(
                "messages", List.of(),
                "phase", "GENERATION",
                "explore_count", 2);
        // 与生产同一条序列化路径：状态必须能经 StateSerializer 往返
        byte[] payload = new GzipUtils().compress(store.encodeState(data));
        AgentRunState state = state(AgentStateFormat.V2_FULL.code(), "tool_node", payload);
        state.setCheckpointId("cp-1");
        state.setNodeId("model_node");

        Optional<Checkpoint> checkpoint = store.toCheckpoint(state);

        assertTrue(checkpoint.isPresent());
        assertEquals("cp-1", checkpoint.get().getId());
        assertEquals("tool_node", checkpoint.get().getNextNodeId());
        assertEquals("GENERATION", checkpoint.get().getState().get("phase"));
        assertEquals(2, checkpoint.get().getState().get("explore_count"));
    }

    @Test
    void statePayloadKeepsMessageTypesAcrossSerialization() throws Exception {
        // 普通 ObjectMapper 会把消息读成 Map；这里证明用的是框架序列化器
        Map<String, Object> data = Map.of("messages",
                List.of(new org.springframework.ai.chat.messages.UserMessage("私人正文")));
        byte[] payload = new GzipUtils().compress(store.encodeState(data));
        AgentRunState state = state(AgentStateFormat.V2_FULL.code(), "tool_node", payload);
        state.setCheckpointId("cp-1");
        state.setNodeId("model_node");

        Checkpoint checkpoint = store.toCheckpoint(state).orElseThrow();
        Object restored = ((List<?>) checkpoint.getState().get("messages")).get(0);

        assertTrue(restored instanceof org.springframework.ai.chat.messages.Message,
                "messages must come back as Spring AI Message, not LinkedHashMap: "
                        + restored.getClass());
    }

    @Test
    void ownershipIsPartOfEveryReadAndWrite() {
        when(mapper.selectOwned("t1", OWNER)).thenReturn(null);

        assertTrue(store.loadForDisplay("t1", OWNER).isEmpty());
        // 读取必须带归属条件，不能只按 threadId 查
        verify(mapper).selectOwned("t1", OWNER);
        verify(mapper, never()).selectById(any());
    }

    @Test
    void saveWithoutOwnerIsRejectedBeforeTouchingStorage() {
        assertEquals(StateSaveResult.FAILED,
                store.saveFullState("t1", null, 1L, checkpoint(), RunStatus.RUNNING, null));
        verify(mapper, never()).updateIfNewer(anyString(), any(), anyLong(), anyString(), any(),
                any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void failureReasonDoesNotCarryRawContent() {
        when(mapper.updateIfNewer(anyString(), any(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("secret-body-内容".repeat(40)));

        store.saveFullState("t1", OWNER, 1L, checkpoint(), RunStatus.RUNNING, null);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mapper).recordBackupFailure(eq("t1"), eq(OWNER), captor.capture());
        assertTrue(captor.getValue().length() <= 160,
                "stored failure reason must be truncated: " + captor.getValue().length());
    }

    private Checkpoint checkpoint() {
        return Checkpoint.builder()
                .id("cp-1")
                .nodeId("model")
                .nextNodeId("tool")
                .state(Map.of("messages", List.of()))
                .build();
    }

    private AgentRunState state(String format, String nextNodeId, byte[] payload) {
        AgentRunState state = new AgentRunState();
        state.setThreadId("t1");
        state.setUserId(OWNER);
        state.setStateFormat(format);
        state.setNextNodeId(nextNodeId);
        state.setStatePayload(payload);
        state.setStateVersion(1L);
        state.setLegacyArchiveOnly(0);
        state.setRunStatus(RunStatus.RUNNING);
        return state;
    }
}
