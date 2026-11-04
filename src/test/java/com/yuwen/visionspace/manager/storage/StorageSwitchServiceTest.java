package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.storage.init.StorageSnapshotAssembler;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储切换编排（#40）：候选失败保留旧快照、原子占位唯一赢家、
 * 确认幂等与晋升原子性、回滚安全。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageSwitchServiceTest {

    private static final long TARGET_ID = 2L;

    @Mock
    private StorageConfigMapper configMapper;
    @Mock
    private StorageSwitchStateMapper stateMapper;
    @Mock
    private StorageSnapshotAssembler assembler;
    @Mock
    private StorageSnapshotManager snapshotManager;
    @Mock
    private FileStorage targetStorage;

    private StorageSwitchService service;

    /**
     * 可状态的快照替身：apply 会真实推进版本，与运行时语义一致。
     */
    private final java.util.concurrent.atomic.AtomicReference<StorageSnapshotManager.Snapshot> snapshotRef =
            new java.util.concurrent.atomic.AtomicReference<>(
                    new StorageSnapshotManager.Snapshot(5, "minio", List.of("minio")));

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
        service = new StorageSwitchService(configMapper, stateMapper, assembler, snapshotManager, txTemplate);
        when(targetStorage.getPlatform()).thenReturn("oss");
        when(assembler.assemble(anyString())).thenReturn(
                new StorageSnapshotAssembler.Candidate(List.of(targetStorage), "oss"));
        when(assembler.assemble((String) isNull())).thenReturn(
                new StorageSnapshotAssembler.Candidate(List.of(targetStorage), "minio"));
        when(snapshotManager.currentSnapshot()).thenAnswer(invocation -> snapshotRef.get());
        when(snapshotManager.currentVersion()).thenAnswer(invocation -> snapshotRef.get().version());
        org.mockito.Mockito.doAnswer(invocation -> {
            List<String> platforms = ((List<?>) invocation.getArgument(0)).stream()
                    .map(storage -> ((FileStorage) storage).getPlatform())
                    .toList();
            snapshotRef.set(new StorageSnapshotManager.Snapshot(
                    invocation.getArgument(2, Long.class), invocation.getArgument(1, String.class), platforms));
            return null;
        }).when(snapshotManager).apply(any(), anyString(), anyLong());
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
        verify(stateMapper, never()).claimSwitch(anyLong(), anyString(), anyInt());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
        assertEquals("completed", service.getStorageSwitchStatus().getPhase());
    }

    @Test
    void alreadyActivePlatformIsRejectedWithoutClaiming() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(1));
        when(stateMapper.selectById(1L)).thenReturn(idleState());

        assertThrows(BusinessException.class, () -> service.switchToStoragePlatform(request(TARGET_ID, 1)));
        verify(stateMapper, never()).claimSwitch(anyLong(), anyString(), anyInt());
    }

    @Test
    void concurrentSwitchLosesWithExplicitError() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(idleState());
        when(stateMapper.claimSwitch(anyLong(), anyString(), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.switchToStoragePlatform(request(TARGET_ID, 2)));

        assertEquals(50001, exception.getCode());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
    }

    @Test
    void claimedSwitchAppliesLocallyAndReportsPending() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(idleState(),
                state(5L, 6L, TARGET_ID, "oss", 1, 2));
        when(stateMapper.claimSwitch(TARGET_ID, "oss", 2)).thenReturn(1);

        StorageSwitchStatusVO status = service.switchToStoragePlatform(request(TARGET_ID, 2));

        assertEquals("pending", status.getPhase());
        assertEquals(1, status.getConfirmCount());
        assertEquals(2, status.getExpectedInstances());
        assertEquals(6L, status.getLocalEffectiveVersion());
        verify(snapshotManager).apply(List.of(targetStorage), "oss", 6L);
    }

    @Test
    void confirmIsIdempotentWhenInstanceAlreadyEffective() {
        when(stateMapper.selectById(1L)).thenReturn(state(5L, 6L, TARGET_ID, "oss", 1, 2));
        when(snapshotManager.currentVersion()).thenReturn(6L);

        service.confirmStorageSwitch();

        verify(stateMapper, never()).incrementConfirm(anyLong());
        verify(snapshotManager, never()).apply(any(), anyString(), anyLong());
    }

    @Test
    void reachingExpectedInstancesPromotesAndMovesActivationAtomically() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        // 第一次读：pending 进行中（本实例旧版本）；确认后读：额度达标；晋升后读：完成
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 1, 2),
                state(5L, 6L, TARGET_ID, "oss", 2, 2),
                completedState(6L));
        when(stateMapper.incrementConfirm(6L)).thenReturn(1);
        when(stateMapper.completeSwitch(6L)).thenReturn(1);

        StorageSwitchStatusVO status = service.confirmStorageSwitch();

        assertEquals("completed", status.getPhase());
        verify(stateMapper).incrementConfirm(6L);
        verify(stateMapper).completeSwitch(6L);
        verify(configMapper).deactivateOtherConfigs(TARGET_ID);
        verify(configMapper).activateConfig(TARGET_ID);
    }

    @Test
    void promotionIsHeldUntilEveryExpectedInstanceConfirms() {
        when(configMapper.selectById(TARGET_ID)).thenReturn(enabledTarget(0));
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 1, 3),
                state(5L, 6L, TARGET_ID, "oss", 2, 3),
                state(5L, 6L, TARGET_ID, "oss", 2, 3));
        when(stateMapper.incrementConfirm(6L)).thenReturn(1);

        StorageSwitchStatusVO status = service.confirmStorageSwitch();

        assertEquals("pending", status.getPhase());
        verify(stateMapper, never()).completeSwitch(anyLong());
        verify(configMapper, never()).deactivateOtherConfigs(anyLong());
    }

    @Test
    void rollbackClearsPendingWithoutTouchingActiveVersion() {
        when(stateMapper.selectById(1L)).thenReturn(
                state(5L, 6L, TARGET_ID, "oss", 1, 2),
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
        verify(snapshotManager).apply(any(), anyString(), org.mockito.ArgumentMatchers.eq(5L));
        verify(stateMapper, never()).completeSwitch(anyLong());
    }

    @Test
    void rollbackWithoutPendingSwitchIsRejected() {
        when(stateMapper.selectById(1L)).thenReturn(idleState());
        assertThrows(BusinessException.class, () -> service.rollbackStorageSwitch());
        verify(assembler, never()).assemble(anyString());
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
        return state(activeVersion, null, null, null, 0, 1);
    }

    private StorageSwitchState state(long activeVersion, Long pendingVersion, Long pendingConfigId,
                                     String pendingPlatform, int confirmCount, int expectedInstances) {
        StorageSwitchState state = new StorageSwitchState();
        state.setId(1L);
        state.setActiveVersion(activeVersion);
        state.setPendingVersion(pendingVersion);
        state.setPendingConfigId(pendingConfigId);
        state.setPendingPlatform(pendingPlatform);
        state.setConfirmCount(confirmCount);
        state.setExpectedInstances(expectedInstances);
        return state;
    }

    private static String isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
