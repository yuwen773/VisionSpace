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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
                new GzipUtils(), new ObjectMapper());
    }

    @Test
    void saveFullStateUsesVersionCasSoLateOldBackupIsIgnored() {
        // 旧备份晚到：CAS 影响 0 行
        when(mapper.updateIfNewer(anyString(), anyLong(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any())).thenReturn(0);

        boolean saved = store.saveFullState("t1", OWNER, 5L, checkpoint(), RunStatus.RUNNING, null);

        assertFalse(saved, "older version must not overwrite newer state");
        verify(mapper).updateIfNewer(eq("t1"), eq(OWNER), eq(5L), anyString(), any(), any(),
                any(), any(), eq(RunStatus.RUNNING), any());
    }

    @Test
    void saveFullStateReportsSuccessWhenVersionIsNewer() {
        when(mapper.updateIfNewer(anyString(), anyLong(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any())).thenReturn(1);

        assertTrue(store.saveFullState("t1", OWNER, 9L, checkpoint(), RunStatus.RUNNING, null));
    }

    @Test
    void backupFailureIsRecordedSoItIsVisibleAndRetryable() {
        when(mapper.updateIfNewer(anyString(), anyLong(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("disk full"));

        boolean saved = store.saveFullState("t1", OWNER, 3L, checkpoint(), RunStatus.RUNNING, null);

        assertFalse(saved);
        // 失败必须可见且可重试，不能静默吞掉
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
        assertFalse(store.saveFullState("t1", null, 1L, checkpoint(), RunStatus.RUNNING, null));
        verify(mapper, never()).updateIfNewer(anyString(), any(), anyLong(), anyString(), any(),
                any(), any(), any(), anyString(), any());
    }

    @Test
    void failureReasonDoesNotCarryRawContent() {
        when(mapper.updateIfNewer(anyString(), anyLong(), anyLong(), anyString(), any(), any(),
                any(), any(), anyString(), any()))
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
