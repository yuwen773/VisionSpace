package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.storage.init.StorageSnapshotAssembler;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.mapper.StorageSwitchConfirmMapper;
import com.yuwen.visionspace.mapper.StorageSwitchStateMapper;
import com.yuwen.visionspace.model.dto.storage.StorageSwitchRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.entity.StorageSwitchState;
import com.yuwen.visionspace.model.vo.StorageSwitchStatusVO;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储切换编排（#40）：候选失败保留旧快照、原子占位唯一赢家、
 * 占位被并发回滚时放弃应用、确认集合幂等（不虚增）、晋升原子性、
 * 回滚保留 activeVersion、状态读取惰性自愈漂移。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageSwitchServiceTest {

    private static final long TARGET_ID = 2L;
    private static final String INSTANCE_ID = "instance-a";

    @Mock
    private StorageConfigMapper configMapper;
    @Mock
    private StorageSwitchStateMapper stateMapper;
    @Mock
    private StorageSwitchConfirmMapper confirmMapper;
    @Mock
    private StorageSnapshotAssembler assembler;
    @Mock
    private StorageSnapshotManager snapshotManager;
    @Mock
    private StorageInstanceIdProvider instanceIdProvider;
    @Mock
    private FileStorage targetStorage;

    /**
     * 可状态的快照替身：apply 会真实推进版本，与运行时语义一致。
     */
    private final AtomicReference<StorageSnapshotManager.Snapshot> snapshotRef =
            new AtomicReference<>(new StorageSnapshotManager.Snapshot(5, "minio", List.of("minio")));

    private StorageSwitchService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new org.springframework.transaction.support.SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
        service = new StorageSwitchService(configMapper, stateMapper, confirmMapper, assembler,
                snapshotManager, instanceIdProvider, txTemplate);
        when(targetStorage.getPlatform()).thenReturn("oss");
        when(assembler.assemble(anyString())).thenReturn(
                new StorageSnapshotAssembler.Candidate(List.of(targetStorage), "oss"));
        when(assembler.assemble((String) isNull())).thenReturn(
                new StorageSnapshotAssembler.Candidate(List.of(targetStorage), "minio"));
        when(snapshotManager.currentSnapshot()).thenAnswer(invocation -> snapshotRef.get());
        when(snapshotManager.currentVersion()).thenAnswer(invocation -> snapshotRef.get().version());
        doAnswer(invocation -> {
            List<String> platforms = ((List<?>) invocation.getArgument(0)).stream()
                    .map(storage -> ((FileStorage) storage).getPlatform())
                    .toList();
            snapshotRef.set(new StorageSnapshotManager.Snapshot(
                    invocation.getArgument(2, Long.class), invocation.getArgument(1, String.class), platforms));
            return null;
        }).when(snapshotManager).apply(any(), anyString(), anyLong());
        when(instanceIdProvider.get()).thenReturn(INSTANCE_ID);
        when(confirmMapper.insertIgnore(anyLong(), anyString(), any(Long.class))).thenReturn(1);
    }

    @Test
    void candidateVerificationFailureKeepsOldSnapshotAndState() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(idleState());
        when(assembler.assemble(anyString())).thenThrow(
                new BusinessException(50000, "候选存储平台客户端初始化失败: oss"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.switchToStoragePlatform(request(TARGET_ID, 1)));

        assertEquals(50000, exception.getCode());
        verify(stateMapper, never()).claimSwitch(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
    }

    @Test
    void alreadyActivePlatformIsRejectedWithoutClaiming() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(1));
        when(stateMapper.selectById(1L)).thenReturn(idleState());

        assertThrows(BusinessException.class, () -> service.switchToStoragePlatform(request(TARGET_ID, 1)));
        verify(stateMapper, never()).claimSwitch(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void concurrentSwitchLosesWithExplicitError() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(idleState());
        when(stateMapper.claimSwitch(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.switchToStoragePlatform(request(TARGET_ID, 2)));

        assertEquals(50001, exception.getCode());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
    }

    /**
     * P1-2 回归：占位成功后、应用前被另一实例回滚，本实例必须放弃应用，
     * 不静默运行已被回滚的候选快照。
     */
    @Test
    void switchAbortsWhenClaimIsConcurrentlyRolledBack() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(idleState(), idleState());
        when(stateMapper.claimSwitch(TARGET_ID, "oss", 1)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.switchToStoragePlatform(request(TARGET_ID, 1)));

        assertEquals(50001, exception.getCode());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
        verify(confirmMapper, never()).insertIgnore(anyLong(), anyString(), any(Long.class));
    }

    /**
     * P1-1 回归：发起实例不再预置确认数，走统一确认路径；
     * 单实例（expected=1）占位并确认后立即晋升。
     */
    @Test
    void singleInstanceSwitchCompletesImmediatelyAndMovesActivation() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        // 读序：校验激活状态 → 占位后重读 → maybeComplete 读 → 晋升后 buildStatus 读
        when(stateMapper.selectById(1L)).thenReturn(
                idleState(),
                state(5L, 6L, TARGET_ID, "oss", 1),
                state(5L, 6L, TARGET_ID, "oss", 1),
                completedState(6L));
        when(stateMapper.claimSwitch(TARGET_ID, "oss", 1)).thenReturn(1);
        when(confirmMapper.countByVersion(6L)).thenReturn(1L);
        when(stateMapper.completeSwitch(6L)).thenReturn(1);

        StorageSwitchStatusVO status = service.switchToStoragePlatform(request(TARGET_ID, 1));

        assertEquals("completed", status.getPhase());
        verify(snapshotManager).apply(List.of(targetStorage), "oss", 6L);
        verify(confirmMapper).insertIgnore(6L, INSTANCE_ID, TARGET_ID);
        verify(stateMapper).completeSwitch(6L);
        verify(configMapper).deactivateOtherConfigs(TARGET_ID);
        verify(configMapper).activateConfig(TARGET_ID);
    }

    /**
     * P1-1 回归：确认数来自确认实例集合，重复确认（INSERT IGNORE 命中 0 行）
     * 不产生第二次记录，晋升只看集合大小。
     */
    @Test
    void pendingSwitchWaitsUntilConfirmSetReachesExpectedInstances() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(
                idleState(),
                state(5L, 6L, TARGET_ID, "oss", 2),
                state(5L, 6L, TARGET_ID, "oss", 2));
        when(stateMapper.claimSwitch(TARGET_ID, "oss", 2)).thenReturn(1);
        when(confirmMapper.countByVersion(6L)).thenReturn(1L);

        StorageSwitchStatusVO status = service.switchToStoragePlatform(request(TARGET_ID, 2));

        assertEquals("pending", status.getPhase());
        assertEquals(1, status.getConfirmCount());
        assertEquals(2, status.getExpectedInstances());
        assertEquals(6L, status.getLocalEffectiveVersion());
        verify(snapshotManager).apply(List.of(targetStorage), "oss", 6L);
        verify(stateMapper, never()).completeSwitch(anyLong());
        verify(configMapper, never()).deactivateOtherConfigs(anyLong());
    }

    @Test
    void confirmIsIdempotentForSameInstanceAndNeverReapplies() {
        // 本实例快照已是候选版本：跳过应用，确认集合幂等（重复插入 0 行）
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 2),
                state(5L, 6L, TARGET_ID, "oss", 2));
        when(snapshotManager.currentVersion()).thenReturn(6L);
        when(confirmMapper.insertIgnore(6L, INSTANCE_ID, TARGET_ID)).thenReturn(0);
        when(confirmMapper.countByVersion(6L)).thenReturn(1L);

        StorageSwitchStatusVO status = service.confirmStorageSwitch();

        assertEquals("pending", status.getPhase());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
        verify(confirmMapper).insertIgnore(6L, INSTANCE_ID, TARGET_ID);
        verify(stateMapper, never()).completeSwitch(anyLong());
    }

    @Test
    void reachingExpectedInstancesPromotesAndMovesActivationAtomically() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 2),
                state(5L, 6L, TARGET_ID, "oss", 2),
                state(5L, 6L, TARGET_ID, "oss", 2),
                completedState(6L));
        when(snapshotManager.currentVersion()).thenReturn(5L);
        when(confirmMapper.countByVersion(6L)).thenReturn(2L);
        when(stateMapper.completeSwitch(6L)).thenReturn(1);

        StorageSwitchStatusVO status = service.confirmStorageSwitch();

        assertEquals("completed", status.getPhase());
        verify(snapshotManager).apply(List.of(targetStorage), "oss", 6L);
        verify(stateMapper).completeSwitch(6L);
        verify(configMapper).deactivateOtherConfigs(TARGET_ID);
        verify(configMapper).activateConfig(TARGET_ID);
    }

    @Test
    void rollbackClearsPendingWithoutTouchingActiveVersion() {
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 1),
                completedState(5L),
                completedState(5L));
        when(stateMapper.abortSwitch()).thenReturn(1);
        when(configMapper.selectActiveConfig()).thenReturn(enabledTarget(1));

        StorageSwitchStatusVO status = service.rollbackStorageSwitch();

        assertEquals("completed", status.getPhase());
        assertEquals(5L, status.getActiveVersion());
        assertTrue(status.getLocalSynced());
        verify(stateMapper).abortSwitch();
        verify(assembler).assemble(isNull());
        verify(snapshotManager).apply(any(), anyString(), eq(5L));
        verify(stateMapper, never()).completeSwitch(anyLong());
    }

    @Test
    void rollbackWithoutPendingSwitchIsRejected() {
        when(stateMapper.selectById(1L)).thenReturn(idleState());
        assertThrows(BusinessException.class, () -> service.rollbackStorageSwitch());
        verify(assembler, never()).assemble(anyString());
    }

    /**
     * P2-7 回归：无切换进行中而本实例版本落后（回滚后残留漂移），
     * 状态读取触发惰性自愈，从持久配置重建快照。
     */
    @Test
    void statusReadSelfHealsResidualDriftAfterRollback() {
        when(stateMapper.selectById(1L)).thenReturn(completedState(5L), completedState(5L));
        when(configMapper.selectActiveConfig()).thenReturn(enabledTarget(1));
        snapshotRef.set(new StorageSnapshotManager.Snapshot(6, "oss", List.of("oss")));

        StorageSwitchStatusVO status = service.getStorageSwitchStatus();

        assertEquals("completed", status.getPhase());
        assertTrue(status.getLocalSynced());
        assertEquals(5L, status.getLocalEffectiveVersion());
        assertEquals("minio", status.getLocalDefaultPlatform());
        verify(assembler).assemble(isNull());
        verify(snapshotManager).apply(any(), anyString(), eq(5L));
    }

    /**
     * 切换进行中（pending 非空）的版本落后是待确认状态，绝不自动应用候选。
     */
    @Test
    void pendingSwitchIsNeverAutoAppliedByStatusRead() {
        when(stateMapper.selectById(1L)).thenReturn(state(5L, 6L, TARGET_ID, "oss", 1));
        when(configMapper.selectActiveConfig()).thenReturn(enabledTarget(1));
        snapshotRef.set(new StorageSnapshotManager.Snapshot(5, "minio", List.of("minio")));

        StorageSwitchStatusVO status = service.getStorageSwitchStatus();

        assertEquals("pending", status.getPhase());
        assertFalse(status.getLocalSynced());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
    }

    @Test
    void statusExposesVersionsWithoutDriftWhenSynced() {
        when(stateMapper.selectById(1L)).thenReturn(completedState(5L));
        when(configMapper.selectActiveConfig()).thenReturn(enabledTarget(1));
        snapshotRef.set(new StorageSnapshotManager.Snapshot(5, "oss", List.of("oss")));

        StorageSwitchStatusVO status = service.getStorageSwitchStatus();

        assertEquals("completed", status.getPhase());
        assertEquals(5L, status.getActiveVersion());
        assertTrue(status.getLocalSynced());
        assertEquals("oss", status.getActivePlatform());
        assertFalse(status.getRegisteredPlatforms().isEmpty());
    }

    private StorageSwitchRequest request(long targetId, int expectedInstances) {
        StorageSwitchRequest request = new StorageSwitchRequest();
        request.setTargetConfigId(targetId);
        request.setExpectedInstances(expectedInstances);
        return request;
    }

    private StorageConfig enabledTarget(int isActive) {
        StorageConfig config = new StorageConfig();
        config.setId(TARGET_ID);
        config.setPlatform("oss");
        config.setPlatformName("acceptance oss");
        config.setStatus(1);
        config.setIsActive(isActive);
        return config;
    }

    private StorageSwitchState idleState() {
        return completedState(5L);
    }

    private StorageSwitchState completedState(long activeVersion) {
        return state(activeVersion, null, null, null);
    }

    private StorageSwitchState state(long activeVersion, Long pendingVersion, Long pendingConfigId,
                                     String pendingPlatform) {
        return state(activeVersion, pendingVersion, pendingConfigId, pendingPlatform, 1);
    }

    private StorageSwitchState state(long activeVersion, Long pendingVersion, Long pendingConfigId,
                                     String pendingPlatform, int expectedInstances) {
        StorageSwitchState state = new StorageSwitchState();
        state.setId(1L);
        state.setActiveVersion(activeVersion);
        state.setPendingVersion(pendingVersion);
        state.setPendingConfigId(pendingConfigId);
        state.setPendingPlatform(pendingPlatform);
        state.setExpectedInstances(expectedInstances);
        return state;
    }
}
