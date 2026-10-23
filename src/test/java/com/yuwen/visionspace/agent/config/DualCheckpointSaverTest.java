package com.yuwen.visionspace.agent.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.recovery.RunStatus;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.dto.agent.LightweightCheckpoint;
import com.yuwen.visionspace.model.dto.agent.SimpleMessage;
import com.yuwen.visionspace.model.entity.AgentSession;
import com.yuwen.visionspace.utils.GzipUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双写检查点：恢复点只看 Redis 的完整状态，旧轻量归档只读不可续跑；
 * 完整状态同步写入并带单调版本，写入失败不影响主流程但必须可见。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DualCheckpointSaverTest {

    private static final String THREAD = "thread-1";
    private static final Long OWNER = 99L;

    @Mock
    private RedisSaver redisSaver;
    @Mock
    private AgentSessionMapper sessionMapper;
    @Mock
    private AgentRunStateStore runStateStore;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<Object> bucket;

    private DualCheckpointSaver saver;

    @BeforeEach
    void setUp() throws Exception {
        // 同步执行器：异步备份在本测试里立即完成，便于断言
        Executor directExecutor = Runnable::run;
        saver = new DualCheckpointSaver(redisSaver, sessionMapper, runStateStore,
                new GzipUtils(), new ObjectMapper(), redissonClient);
        ReflectionTestUtils.setField(saver, "asyncExecutor", directExecutor);

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(redisSaver.put(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(runStateStore.saveFullState(anyString(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(true);
        when(sessionMapper.updateCheckpointData(anyString(), anyLong(), any())).thenReturn(1);
    }

    @Test
    void normalStartDoesNotResurrectLegacyArchiveAsResumePoint() {
        // Redis 没有检查点，MySQL 里只有旧的轻量归档
        when(redisSaver.get(any())).thenReturn(Optional.empty());

        Optional<Checkpoint> loaded = saver.get(config());

        assertTrue(loaded.isEmpty(),
                "message-only legacy archive must not be injected into a fresh run");
        verify(sessionMapper, never()).selectBySessionId(anyString());
    }

    @Test
    void putPersistsFullStateSynchronouslyWithIncreasingVersions() throws Exception {
        saver.put(config(), checkpoint());
        saver.put(config(), checkpoint());

        ArgumentCaptor<Long> versions = ArgumentCaptor.forClass(Long.class);
        verify(runStateStore, times(2))
                .saveFullState(eq(THREAD), eq(OWNER), versions.capture(), any(), eq(RunStatus.RUNNING), any());
        List<Long> captured = versions.getAllValues();
        assertTrue(captured.get(1) > captured.get(0),
                "versions must increase so a late older backup cannot win the CAS: " + captured);
    }

    @Test
    void putWithoutOwnerWritesNothingThatCouldLeakIntoAnotherSession() throws Exception {
        RunnableConfig noOwner = RunnableConfig.builder().threadId(THREAD).build();

        saver.put(noOwner, checkpoint());

        verify(runStateStore, never()).saveFullState(anyString(), any(), anyLong(), any(), any(), any());
        verify(sessionMapper, never()).updateCheckpointData(anyString(), any(), any());
    }

    @Test
    void backupFailureDoesNotBreakTheExecution() throws Exception {
        when(runStateStore.saveFullState(anyString(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(false);

        RunnableConfig result = null;
        // 轻量备份每 3 步一次，连写 3 次才会触发
        for (int i = 0; i < 3; i++) {
            result = saver.put(config(), checkpoint());
        }

        assertEquals(THREAD, result.threadId().orElse(null));
        // 完整状态写入返回 false 不抛异常，主流程照常推进
        verify(runStateStore, times(3))
                .saveFullState(eq(THREAD), eq(OWNER), anyLong(), any(), eq(RunStatus.RUNNING), any());
        verify(sessionMapper).updateCheckpointData(eq(THREAD), eq(OWNER), any());
    }

    @Test
    void releaseClearsRunStateAndPerThreadCounters() throws Exception {
        when(redisSaver.release(any())).thenReturn(anyTag());

        saver.put(config(), checkpoint());
        saver.release(config());

        verify(runStateStore).clearForRelease(THREAD, OWNER);
    }

    @Test
    void releaseOfUnknownOwnerStillReleasesFrameworkSide() throws Exception {
        when(redisSaver.release(any())).thenReturn(anyTag());
        RunnableConfig noOwner = RunnableConfig.builder().threadId(THREAD).build();

        saver.release(noOwner);

        verify(runStateStore, never()).clearForRelease(anyString(), any());
        verify(redisSaver).release(noOwner);
    }

    @Test
    void legacyArchiveIsReadableForDisplayOnly() {
        LightweightCheckpoint lw = new LightweightCheckpoint(THREAD,
                List.of(new SimpleMessage("USER", "旧消息")), "summary");
        AgentSession session = new AgentSession();
        session.setCheckpointData(compress(lw));
        when(sessionMapper.selectBySessionId(THREAD)).thenReturn(session);

        Optional<List<SimpleMessage>> messages = saver.loadLegacyArchiveMessages(THREAD);

        assertTrue(messages.isPresent());
        assertEquals("旧消息", messages.get().get(0).content());
    }

    @Test
    void unreadableLegacyArchiveIsIgnoredInsteadOfThrowing() {
        AgentSession session = new AgentSession();
        session.setCheckpointData(new byte[]{9, 9, 9});
        when(sessionMapper.selectBySessionId(THREAD)).thenReturn(session);

        assertTrue(saver.loadLegacyArchiveMessages(THREAD).isEmpty());
    }

    private byte[] compress(LightweightCheckpoint lw) {
        try {
            return new GzipUtils().compress(new ObjectMapper().writeValueAsString(lw));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver.Tag anyTag() {
        return new com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver.Tag(THREAD, List.of());
    }

    private RunnableConfig config() {
        return RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
    }

    private Checkpoint checkpoint() {
        return Checkpoint.builder()
                .id("cp-1")
                .nodeId("model")
                .nextNodeId("tool")
                .state(Map.of("messages", List.of()))
                .build();
    }
}
