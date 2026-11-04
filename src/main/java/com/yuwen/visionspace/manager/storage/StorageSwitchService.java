package com.yuwen.visionspace.manager.storage;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.init.StorageSnapshotAssembler;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.mapper.StorageSwitchStateMapper;
import com.yuwen.visionspace.model.dto.storage.StorageSwitchRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.entity.StorageSwitchState;
import com.yuwen.visionspace.model.vo.StorageSwitchStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * 存储切换编排（D20 / #40）。
 *
 * <p>语义：候选完整构建验证 → 数据库原子占位（跨实例唯一）→ 本实例生效
 * → 各实例确认 → 达到期望实例数后在同一事务里晋升版本并迁移持久激活标记。
 * 任一步骤失败都保留旧快照：候选失败不占位，占位后失败保持 pending 可见，
 * 不虚报完整成功。进行中的切换可随时安全回滚。
 *
 * <p>切换本身不搬迁、不删除任何旧平台对象；旧资源的读取、删除与补偿
 * 始终按资源记录里的原平台定位，与默认平台无关。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StorageSwitchService {

    private final StorageConfigMapper storageConfigMapper;
    private final StorageSwitchStateMapper switchStateMapper;
    private final StorageSnapshotAssembler snapshotAssembler;
    private final StorageSnapshotManager snapshotManager;
    private final TransactionTemplate transactionTemplate;

    /**
     * 确认只在本实例内串行化；跨实例的确认进度由数据库原子累加保证。
     */
    private final Object confirmLock = new Object();

    /**
     * 本实例已为哪个版本领取过确认额度（防止应用失败重试时重复累加）。
     */
    private long localConfirmedVersion = 0;

    /**
     * 发起切换：候选完整构建验证后原子占位，并在本实例立即生效。
     * 占位成功即计为本实例已确认（confirmCount = 1）。
     */
    public StorageSwitchStatusVO switchToStoragePlatform(StorageSwitchRequest request) {
        ThrowUtils.throwIf(request == null || request.getTargetConfigId() == null, ErrorCode.PARAMS_ERROR);
        int expectedInstances = request.getExpectedInstances() == null ? 1 : request.getExpectedInstances();
        ThrowUtils.throwIf(expectedInstances < 1 || expectedInstances > 1000,
                ErrorCode.PARAMS_ERROR, "expectedInstances 必须在 1 到 1000 之间");
        StorageConfig target = storageConfigMapper.selectById(request.getTargetConfigId());
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目标存储配置不存在");
        }
        ThrowUtils.throwIf(target.getStatus() == null || target.getStatus() != 1,
                ErrorCode.OPERATION_ERROR, "目标平台配置未启用，请先启用");
        StorageSwitchState state = requireState();
        ThrowUtils.throwIf(state.getPendingVersion() == null
                        && Objects.equals(target.getIsActive(), 1),
                ErrorCode.OPERATION_ERROR, "该平台已是当前生效平台");

        // 1. 候选完整构建验证：任一配置失败即整体失败，运行时与持久状态都不触碰
        StorageSnapshotAssembler.Candidate candidate;
        try {
            candidate = snapshotAssembler.assemble(target.getPlatform());
        } catch (BusinessException e) {
            log.error("存储切换候选验证失败，已保留当前生效快照: targetConfigId={}", request.getTargetConfigId(), e);
            throw e;
        }

        // 2. 原子占位：两个实例同时切换时只有一个成功
        int claimed = switchStateMapper.claimSwitch(target.getId(), target.getPlatform(), expectedInstances);
        ThrowUtils.throwIf(claimed == 0, ErrorCode.OPERATION_ERROR,
                "已有存储切换进行中，请先完成确认或回滚");

        // 3. 本实例立即生效（计为第一个确认实例）
        StorageSwitchState after = requireState();
        snapshotManager.apply(candidate.storages(), target.getPlatform(), after.getPendingVersion());
        localConfirmedVersion = after.getPendingVersion();
        log.info("存储切换已占位并在本实例生效: version={}, platform={}, expectedInstances={}",
                after.getPendingVersion(), target.getPlatform(), expectedInstances);
        return buildStatus();
    }

    /**
     * 本实例确认候选版本生效；确认数达到期望后晋升为完整成功。
     * 幂等：重复确认不重复累加。
     */
    public StorageSwitchStatusVO confirmStorageSwitch() {
        synchronized (confirmLock) {
            StorageSwitchState state = requireState();
            if (state.getPendingVersion() == null) {
                return buildStatus();
            }
            long version = state.getPendingVersion();
            if (snapshotManager.currentVersion() == version) {
                // 本实例已生效：不再重复领取确认额度
                return buildStatus();
            }
            StorageConfig target = storageConfigMapper.selectById(state.getPendingConfigId());
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                        "待生效配置已被删除，请回滚本次切换");
            }
            ThrowUtils.throwIf(target.getStatus() == null || target.getStatus() != 1,
                    ErrorCode.OPERATION_ERROR, "待生效配置已禁用，请回滚本次切换");
            StorageSnapshotAssembler.Candidate candidate = snapshotAssembler.assemble(target.getPlatform());
            // 先领取确认额度再应用：应用失败可重试且不重复累加
            if (localConfirmedVersion < version) {
                int credited = switchStateMapper.incrementConfirm(version);
                ThrowUtils.throwIf(credited == 0, ErrorCode.OPERATION_ERROR,
                        "切换状态已变化，请重新查询后再确认");
                localConfirmedVersion = version;
            }
            snapshotManager.apply(candidate.storages(), target.getPlatform(), version);
            localConfirmedVersion = version;
            return maybeComplete(version, target.getId());
        }
    }

    /**
     * 回滚进行中的切换：清空 pending，activeVersion 不变；
     * 本实例从持久激活配置重建快照。已应用过候选的其他实例会呈现版本漂移，
     * 由管理员对其执行刷新使其回到持久生效快照。
     */
    public StorageSwitchStatusVO rollbackStorageSwitch() {
        StorageSwitchState state = requireState();
        ThrowUtils.throwIf(state.getPendingVersion() == null,
                ErrorCode.OPERATION_ERROR, "当前没有进行中的切换");
        int aborted = switchStateMapper.abortSwitch();
        if (aborted == 0) {
            // 并发下已被其他请求处理
            return buildStatus();
        }
        log.warn("存储切换已回滚，保留旧快照: pendingVersion={}, platform={}",
                state.getPendingVersion(), state.getPendingPlatform());
        reloadPersistedSnapshot();
        return buildStatus();
    }

    /**
     * 从持久配置重建本实例快照（版本对齐 activeVersion）。
     * 构建失败时抛出，运行时保持原样。
     */
    public void reloadPersistedSnapshot() {
        StorageSnapshotAssembler.Candidate candidate = snapshotAssembler.assemble(null);
        snapshotManager.apply(candidate.storages(), candidate.defaultPlatform(),
                requireState().getActiveVersion());
    }

    public StorageSwitchStatusVO getStorageSwitchStatus() {
        return buildStatus();
    }

    /**
     * 确认数达到期望后晋升：版本晋升与持久激活标记迁移在同一事务内，
     * 保证「运行生效」与「storage_config.isActive」一致提交。
     */
    private StorageSwitchStatusVO maybeComplete(long version, Long targetConfigId) {
        StorageSwitchState state = requireState();
        if (!Objects.equals(state.getPendingVersion(), version)) {
            // 已被其他实例完成或回滚
            return buildStatus();
        }
        if (state.getConfirmCount() < state.getExpectedInstances()) {
            return buildStatus();
        }
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            int completed = switchStateMapper.completeSwitch(version);
            if (completed == 1) {
                storageConfigMapper.deactivateOtherConfigs(targetConfigId);
                storageConfigMapper.activateConfig(targetConfigId);
            }
        });
        localConfirmedVersion = version;
        log.info("存储切换晋升为完整生效: version={}, platform={}", version,
                safePlatform(snapshotManager.currentSnapshot().defaultPlatform()));
        return buildStatus();
    }

    private StorageSwitchStatusVO buildStatus() {
        StorageSwitchState state = requireState();
        StorageConfig active = storageConfigMapper.selectActiveConfig();
        StorageSnapshotManager.Snapshot snapshot = snapshotManager.currentSnapshot();
        long expectedCurrent = state.getPendingVersion() != null
                ? state.getPendingVersion()
                : state.getActiveVersion();
        StorageSwitchStatusVO vo = new StorageSwitchStatusVO();
        vo.setPhase(state.getPendingVersion() != null ? "pending" : "completed");
        vo.setActiveVersion(state.getActiveVersion());
        vo.setPendingVersion(state.getPendingVersion());
        vo.setPendingConfigId(state.getPendingConfigId());
        vo.setPendingPlatform(state.getPendingPlatform());
        vo.setConfirmCount(state.getConfirmCount());
        vo.setExpectedInstances(state.getExpectedInstances());
        vo.setLocalEffectiveVersion(snapshot.version());
        vo.setLocalSynced(snapshot.version() == expectedCurrent);
        vo.setLocalDefaultPlatform(snapshot.defaultPlatform());
        vo.setRegisteredPlatforms(snapshot.platforms());
        vo.setActivePlatform(active != null ? active.getPlatform() : null);
        vo.setActiveConfigId(active != null ? active.getId() : null);
        return vo;
    }

    /**
     * 读取单行状态；行缺失时（迁移未执行的环境）兜底创建，避免读后写竞态。
     */
    private StorageSwitchState requireState() {
        StorageSwitchState state = switchStateMapper.selectById(1L);
        if (state == null) {
            switchStateMapper.ensureRow();
            state = switchStateMapper.selectById(1L);
        }
        if (state == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "存储切换状态不可用");
        }
        return state;
    }

    /**
     * 仅供日志与诊断使用：确认无凭据参与。
     */
    private static String safePlatform(String platform) {
        return StrUtil.isBlank(platform) ? "(none)" : platform;
    }
}
