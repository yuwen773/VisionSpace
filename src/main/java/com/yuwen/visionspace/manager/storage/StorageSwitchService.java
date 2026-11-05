package com.yuwen.visionspace.manager.storage;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.init.StorageSnapshotAssembler;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.mapper.StorageSwitchConfirmMapper;
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
 * <p>语义：候选完整构建验证 → 数据库原子占位（跨实例唯一）→ 本实例应用候选快照
 * → 向确认实例集合登记本实例 → 集合大小达到期望实例数后在同一事务里晋升版本
 * 并迁移持久激活标记。任一步骤失败都保留旧快照：候选失败不占位，占位后失败
 * 保持 pending 可见，不虚报完整成功。进行中的切换可随时安全回滚。
 *
 * <p>确认以「实例集合」记录而非次数：storage_switch_confirm 主键
 * (version, instanceId) + INSERT IGNORE 保证同一实例重复确认（重试、重启）
 * 只计一次，晋升不会被确认数虚增提前触发。发起实例与其他实例走同一条确认路径。
 * 占位事务内由赢家清空同版本号的残留确认行：回滚只清 pending，重新发起时
 * 若不清残留行，流产尝试的确认会被继承而提前晋升。
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
    private final StorageSwitchConfirmMapper switchConfirmMapper;
    private final StorageSnapshotAssembler snapshotAssembler;
    private final StorageSnapshotManager snapshotManager;
    private final StorageInstanceIdProvider instanceIdProvider;
    private final TransactionTemplate transactionTemplate;

    /**
     * 确认只在本实例内串行化；跨实例的确认进度由数据库确认集合的
     * 主键幂等保证，不依赖本锁。
     */
    private final Object confirmLock = new Object();

    /**
     * 发起切换：候选完整构建验证后原子占位，本实例应用候选快照并走统一确认路径。
     * 单实例部署（expectedInstances = 1）在此直接晋升为完整成功。
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

        // 2. 原子占位：两个实例同时切换时只有一个成功。占位赢家在同一事务里
        //    清空同版本号的陈旧确认行——回滚只清 pending 不清 confirm 集合，
        //    若上次流产尝试的残留行不清理，重新发起时同一版本号会继承旧确认，
        //    造成其余实例从未确认就被提前晋升（虚报完整成功）。
        //    清理只由占位赢家执行：落败方（claimSwitch 影响 0 行）不做任何清理，
        //    不会误删并发赢家的新确认。清理覆盖占位时刻已存在的全部残留行；
        //    跨 abort/再占位存活的旧确认线程可能在清理后才落行，由确认插入的
        //    pendingToken 原子条件（insertIfCurrent）封堵——版本号会复用，令牌不会。
        Integer claimed = transactionTemplate.execute(transactionStatus -> {
            int claimedRows = switchStateMapper.claimSwitch(target.getId(), target.getPlatform(), expectedInstances);
            if (claimedRows == 1) {
                // 本事务可见自己的占位结果，版本号取实际占位值而非事前推算，
                // 避免与「占位→晋升→再占位」的完整并发周期产生版本错位
                switchConfirmMapper.deleteByVersion(requireState().getPendingVersion());
            }
            return claimedRows;
        });
        ThrowUtils.throwIf(claimed == null || claimed == 0, ErrorCode.OPERATION_ERROR,
                "已有存储切换进行中，请先完成确认或回滚");

        // 3. 占位与生效之间可能被并发回滚：应用前重读并校验占位仍是本次目标，
        //    不符即放弃应用（fail-closed），不静默运行已被回滚的候选
        StorageSwitchState after = requireState();
        if (after.getPendingVersion() == null
                || !Objects.equals(after.getPendingConfigId(), target.getId())) {
            log.warn("切换占位已被并发回滚，放弃应用候选快照: targetConfigId={}", target.getId());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "切换占位已被并发回滚，本次未生效，请重新发起");
        }
        long version = after.getPendingVersion();
        snapshotManager.apply(candidate.storages(), target.getPlatform(), version);
        recordSelfConfirm(version, after.getPendingToken(), target.getId());
        return maybeComplete(version, target.getId());
    }

    /**
     * 本实例确认候选版本生效；确认集合达到期望实例数后晋升为完整成功。
     * 幂等：同一实例重复确认只计一次（主键幂等），不重复累加。
     */
    public StorageSwitchStatusVO confirmStorageSwitch() {
        synchronized (confirmLock) {
            StorageSwitchState state = requireState();
            if (state.getPendingVersion() == null) {
                selfHealIfNeeded(state);
                return buildStatus(state);
            }
            long version = state.getPendingVersion();
            StorageConfig target = storageConfigMapper.selectById(state.getPendingConfigId());
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                        "待生效配置已被删除，请回滚本次切换");
            }
            ThrowUtils.throwIf(target.getStatus() == null || target.getStatus() != 1,
                    ErrorCode.OPERATION_ERROR, "待生效配置已禁用，请回滚本次切换");
            StorageSnapshotAssembler.Candidate candidate = snapshotAssembler.assemble(target.getPlatform());
            // 构建与占位之间可能被并发回滚：应用前重读校验，不符即放弃
            StorageSwitchState current = requireState();
            if (!Objects.equals(current.getPendingVersion(), version)) {
                return buildStatus(current);
            }
            // 已应用过（重试路径）就不再重复应用；确认集合的插入本身幂等
            if (snapshotManager.currentVersion() != version) {
                snapshotManager.apply(candidate.storages(), target.getPlatform(), version);
            }
            // 令牌取自本次重读的状态行：insertIfCurrent 在单条语句内原子校验
            // pendingVersion + pendingToken，跨 abort/再占位存活的旧线程
            // 持旧令牌无法落行（版本号会被重新发起复用，令牌不会）
            recordSelfConfirm(version, current.getPendingToken(), target.getId());
            return maybeComplete(version, target.getId());
        }
    }

    /**
     * 回滚进行中的切换：清空 pending，activeVersion 不变；
     * 本实例从持久激活配置重建快照。已应用过候选的其他实例在下一次
     * 状态读取/确认时惰性自愈（见 {@link #selfHealIfNeeded}）。
     */
    public StorageSwitchStatusVO rollbackStorageSwitch() {
        StorageSwitchState state = requireState();
        ThrowUtils.throwIf(state.getPendingVersion() == null,
                ErrorCode.OPERATION_ERROR, "当前没有进行中的切换");
        int aborted = switchStateMapper.abortSwitch();
        if (aborted == 0) {
            // 并发下已被其他请求处理
            return buildStatus(requireState());
        }
        log.warn("存储切换已回滚，保留旧快照: pendingVersion={}, platform={}",
                state.getPendingVersion(), safePlatform(state.getPendingPlatform()));
        reloadPersistedSnapshot();
        return buildStatus(requireState());
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

    /**
     * 查询真实生效状态；无切换进行中且本实例版本落后时惰性自愈，
     * 把「其他实例逐台手工刷新」变成自动收敛。自愈失败不阻塞状态读取。
     */
    public StorageSwitchStatusVO getStorageSwitchStatus() {
        StorageSwitchState state = requireState();
        if (state.getPendingVersion() == null) {
            selfHealIfNeeded(state);
            state = requireState();
        }
        return buildStatus(state);
    }

    /**
     * 惰性收敛：没有进行中的切换而本实例快照版本落后于持久生效版本时，
     * 从持久配置重建快照。只处理「回滚后残留漂移」这一种安全情形；
     * 切换进行中（pending 非空）的落后属于待确认状态，必须显式确认，绝不自动应用。
     */
    private void selfHealIfNeeded(StorageSwitchState state) {
        long localVersion = snapshotManager.currentVersion();
        if (localVersion == state.getActiveVersion()) {
            return;
        }
        log.warn("检测到本实例快照漂移，开始从持久配置自愈: local={}, active={}",
                localVersion, state.getActiveVersion());
        try {
            reloadPersistedSnapshot();
            log.info("本实例快照漂移已自愈: version={}", state.getActiveVersion());
        } catch (Exception e) {
            log.error("本实例快照漂移自愈失败，保持当前快照并维持漂移可见", e);
        }
    }

    /**
     * 登记本实例确认：插入与「仍是本次占位尝试」的校验在同一条原子语句内
     * （insertIfCurrent）。返回 0 行可能是重复确认（主键幂等），也可能是
     * 读-写间隙内占位被回滚/重新占位——后者由 maybeComplete 的状态重读兜底，
     * 不会误报完整成功。
     */
    private void recordSelfConfirm(long version, String pendingToken, Long targetConfigId) {
        int inserted = switchConfirmMapper.insertIfCurrent(version, instanceIdProvider.get(),
                targetConfigId, pendingToken);
        if (inserted == 0) {
            log.info("确认未新登记（重复确认或占位已变化）: version={}", version);
        }
    }

    /**
     * 确认集合大小达到期望后晋升：版本晋升与持久激活标记迁移在同一事务内，
     * 保证「运行生效」与「storage_config.isActive」一致提交。
     * 并发晋升由 completeSwitch 的 WHERE pendingVersion 条件裁决唯一赢家。
     */
    private StorageSwitchStatusVO maybeComplete(long version, Long targetConfigId) {
        StorageSwitchState state = requireState();
        if (!Objects.equals(state.getPendingVersion(), version)) {
            // 已被其他实例完成或回滚
            return buildStatus(state);
        }
        long confirmed = switchConfirmMapper.countByVersion(version);
        if (confirmed < state.getExpectedInstances()) {
            return buildStatus(state);
        }
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            int completed = switchStateMapper.completeSwitch(version);
            if (completed == 1) {
                storageConfigMapper.deactivateOtherConfigs(targetConfigId);
                storageConfigMapper.activateConfig(targetConfigId);
            }
        });
        log.info("存储切换晋升为完整生效: version={}, platform={}, confirmedInstances={}",
                version, safePlatform(snapshotManager.currentSnapshot().defaultPlatform()), confirmed);
        return buildStatus(requireState());
    }

    private StorageSwitchStatusVO buildStatus(StorageSwitchState state) {
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
        vo.setConfirmCount(state.getPendingVersion() != null
                ? (int) switchConfirmMapper.countByVersion(state.getPendingVersion())
                : 0);
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
