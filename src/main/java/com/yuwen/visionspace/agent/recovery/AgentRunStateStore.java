package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.yuwen.visionspace.mapper.AgentRunStateMapper;
import com.yuwen.visionspace.model.entity.AgentRunState;
import com.yuwen.visionspace.utils.GzipUtils;
import com.yuwen.visionspace.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 完整执行状态的持久化与恢复。
 *
 * <p>三条不变式：
 * <ol>
 *   <li>写入按 {@code stateVersion} 做 CAS，迟到的旧备份影响 0 行；</li>
 *   <li>读取与恢复都带 {@code userId} 条件，跨用户既读不到也写不进；</li>
 *   <li>只有 {@link AgentStateFormat#V2_FULL} 且带完整载荷的状态才算可续跑，
 *       旧轻量归档只用于查看，绝不重建为恢复点。</li>
 * </ol>
 *
 * <p>恢复后不重放已确认完成的动作由 {@link AgentActionLedger} 负责；本类只保证
 * 「恢复到哪个点」是可信的，不宣称外部动作恰好执行一次。
 */
@Slf4j
@Component
public class AgentRunStateStore {

    private final AgentRunStateMapper runStateMapper;
    private final StateSerializer stateSerializer;
    private final GzipUtils gzipUtils;

    public AgentRunStateStore(AgentRunStateMapper runStateMapper,
                              StateSerializer stateSerializer,
                              GzipUtils gzipUtils) {
        this.runStateMapper = runStateMapper;
        this.stateSerializer = stateSerializer;
        this.gzipUtils = gzipUtils;
    }

    /**
     * 读取可续跑状态。归属不成立、格式不可识别或载荷缺失时返回空，
     * 调用方必须按「不可恢复」处理，不能退化成用消息历史续跑。
     */
    public Optional<AgentRunState> loadResumable(String threadId, Long userId) {
        AgentRunState state = runStateMapper.selectOwned(threadId, userId);
        if (state == null) {
            return Optional.empty();
        }
        if (!Integer.valueOf(1).equals(state.getLegacyArchiveOnly()) && isResumable(state)) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    /** 只读读取（含旧轻量归档），用于展示与状态查询 */
    public Optional<AgentRunState> loadForDisplay(String threadId, Long userId) {
        return Optional.ofNullable(runStateMapper.selectOwned(threadId, userId));
    }

    public boolean isResumable(AgentRunState state) {
        AgentStateFormat format = AgentStateFormat.fromCode(state.getStateFormat());
        return format != null && format.resumable()
                && state.getStatePayload() != null
                && state.getStatePayload().length > 0;
    }

    /**
     * 读取共享持久状态里的当前版本号。
     *
     * <p>版本顺序必须以它为准：只靠进程本地计数器时，一个启动更早的实例接管
     * 另一个实例的会话后，本地计数一直小于库里的版本，写入被 CAS 全部拒绝，
     * 而执行却继续跑——进度被静默丢弃。
     *
     * @return 当前版本；没有状态行时返回 0
     */
    public long currentVersion(String threadId, Long userId) {
        Long version = readVersion(threadId, userId);
        return version == null ? 0L : version;
    }

    /**
     * 读版本号，区分「没有行」（0）与「读不到」（null）。
     *
     * <p>两者不能混为一谈：读失败时把版本当成 0，就可能把一个落后的版本号
     * 当成「被更新版本取代」而安静跳过，有效进度被静默丢弃。调用方必须
     * 在 {@code null} 时按「写不进去」处理，而不是按「被取代」处理。
     */
    private Long readVersion(String threadId, Long userId) {
        if (threadId == null || userId == null) {
            return 0L;
        }
        try {
            Long version = runStateMapper.selectVersion(threadId, userId);
            return version == null ? 0L : version;
        } catch (Exception e) {
            log.warn("读取执行状态版本失败, threadId={}", threadId);
            return null;
        }
    }

    /**
     * 把 Checkpoint 保存为完整状态。
     *
     * <p>{@code stateVersion} 必须由调用方按共享持久状态给出（见
     * {@link #currentVersion}）；写入是 CAS，旧版本影响 0 行。
     *
     * <p><b>不带执行世代与工具环境的入口只留给旧调用方与测试种子。</b>生产执行
     * 必须走带 {@code runEpoch} 与 {@code mcpServerCodes} 的重载：世代拒绝被取代
     * 执行的迟到写入，环境与载荷在同一条 UPDATE 里保持同版本配对。
     *
     * <p>返回三态加一而不是布尔：只有「版本被更新状态取代」可以安静跳过并对齐重试，
     * 「世代更替」连重试都不允许，真正的写入失败必须留痕。
     */
    public StateSaveResult saveFullState(String threadId, Long userId, long stateVersion,
                                         Checkpoint checkpoint, String runStatus,
                                         String pendingConfirmation) {
        return saveFullState(threadId, userId, stateVersion, null, null,
                checkpoint, runStatus, pendingConfirmation);
    }

    /**
     * 带执行世代与工具环境的完整状态写入。
     *
     * <p>{@code runEpoch} 必须来自 {@link #beginExecution}（或续跑认领）返回的世代；
     * 写入条件是「版本更新 <b>且</b> 世代仍是当前执行」，两个条件任一不满足都影响
     * 0 行，但含义不同：版本不够新可以对齐重试，世代更替说明本执行已被取代，
     * 迟到的载荷绝不允许借重试覆盖新进度。
     *
     * <p>{@code mcpServerCodes} 与载荷、版本在同一条 UPDATE 里落库：null 表示本次
     * 执行没有声明环境（续跑沿用已记录的环境），列保持不变。环境记录失败或缺失
     * 都不会把另一轮执行的环境拼到本载荷上——它们本来就是同一次写入。
     */
    public StateSaveResult saveFullState(String threadId, Long userId, long stateVersion,
                                         Long runEpoch, String mcpServerCodes,
                                         Checkpoint checkpoint, String runStatus,
                                         String pendingConfirmation) {
        if (threadId == null || userId == null || checkpoint == null) {
            return StateSaveResult.FAILED;
        }
        try {
            // 首次执行时状态行还不存在，下面的 UPDATE 会静默影响 0 行。
            // 先按 (threadId) 唯一键建行；已存在时是空操作，不会改写归属。
            // 建行时世代默认 0：首个执行在 beginExecution 里读不到行，同样以 0 开始。
            runStateMapper.insertIfAbsent(threadId, userId, AgentStateFormat.V2_FULL.code(),
                    RunStatus.RUNNING);
            byte[] payload = gzipUtils.compress(encodeState(checkpoint.getState()));
            int updated = runStateMapper.updateIfNewer(threadId, userId, stateVersion,
                    AgentStateFormat.V2_FULL.code(), checkpoint.getId(), checkpoint.getNodeId(),
                    checkpoint.getNextNodeId(), payload, runStatus, pendingConfirmation,
                    runEpoch, mcpServerCodes);
            if (updated == 1) {
                return StateSaveResult.SAVED;
            }
            // 0 行有三种原因，必须分清：版本被推进、世代已更替，还是根本写不进去。
            // 读不到版本时无法判断是不是被取代，一律按「写不进去」留痕，
            // 否则一次读失败就会被当成「被更新版本取代」，进度静默消失。
            Long stored = readVersion(threadId, userId);
            if (stored != null) {
                // 世代对不上本身就是所有权更替的证据，与版本新旧无关：
                // 版本条件没挡住的 0 行只可能是世代条件挡的。
                // 此时重试毫无意义——重试的仍是旧执行的载荷，重新编号只会把
                // 「迟到」伪装成「最新」，必须原样拒绝且不允许重试。
                if (runEpoch != null) {
                    Long storedEpoch = readRunEpoch(threadId, userId);
                    if (storedEpoch != null && storedEpoch != runEpoch) {
                        log.info("执行世代已更替，拒绝旧执行的迟到写入, threadId={}, offeredEpoch={}, storedEpoch={}",
                                threadId, runEpoch, storedEpoch);
                        return StateSaveResult.LOST_OWNERSHIP;
                    }
                }
                if (stored >= stateVersion) {
                    log.info("执行状态被更新版本取代, threadId={}, offered={}, stored={}",
                            threadId, stateVersion, stored);
                    return StateSaveResult.SUPERSEDED;
                }
            }
            log.warn("执行状态未写入且版本未推进，按备份失败处理, threadId={}, version={}",
                    threadId, stateVersion);
            recordBackupFailure(threadId, userId,
                    new IllegalStateException("state row not writable"));
            return StateSaveResult.FAILED;
        } catch (Exception e) {
            recordBackupFailure(threadId, userId, e);
            return StateSaveResult.FAILED;
        }
    }

    /**
     * 备份失败必须可见且可重试：记录原因与次数，不吞掉。
     * 原因只保留异常类型与简短消息，避免把私人正文写进审计列。
     */
    public void recordBackupFailure(String threadId, Long userId, Exception error) {
        if (threadId == null || userId == null) {
            return;
        }
        String reason = sanitizeFailure(error);
        try {
            runStateMapper.recordBackupFailure(threadId, userId, reason);
        } catch (Exception e) {
            log.warn("记录备份失败状态时出错, threadId={}", threadId);
        }
    }

    /**
     * 开始一次新执行：原子推进执行世代并返回它。
     *
     * <p>世代是跨实例有效的所有权凭据：返回值必须放进本次执行的
     * {@code RunnableConfig} metadata，{@code DualCheckpointSaver} 写完整状态时
     * 按它匹配。上一个执行（另一实例上的续跑、或用户刷新前的旧流）从此刻起
     * 失去写入资格——它迟到的检查点不再能借「对齐重试」覆盖新进度。
     *
     * <p>状态行还不存在时返回 0：首次建行（第一个检查点）的世代默认就是 0，
     * 两者自然匹配。CAS 竞争失败时重读重试；极端竞争下放弃并返回 -1
     * （永不匹配任何世代，宁可不写恢复点也不写错归属）。
     */
    public long beginExecution(String threadId, Long userId) {
        if (threadId == null || userId == null) {
            return 0L;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            Long current = readRunEpoch(threadId, userId);
            if (current == null) {
                // 还没有状态行：首个检查点建行时的世代默认 0
                return 0L;
            }
            if (runStateMapper.bumpRunEpoch(threadId, userId, current) == 1) {
                return current + 1;
            }
        }
        log.warn("执行世代推进持续冲突，本次执行放弃持久化资格, threadId={}", threadId);
        return -1L;
    }

    private Long readRunEpoch(String threadId, Long userId) {
        try {
            return runStateMapper.selectRunEpoch(threadId, userId);
        } catch (Exception e) {
            log.warn("读取执行世代失败, threadId={}", threadId);
            return null;
        }
    }

    /**
     * 读取上次执行用到的 MCP 服务 code。
     *
     * <p>返回空 {@link Optional} 表示<b>没有记录</b>（列为 NULL 或无法解析），
     * 与「记录了但没有用 MCP」（空列表）是两回事：前者无法确认上次的工具环境，
     * 续跑时必须拒绝；后者可以安全地用内置工具继续。
     */
    public Optional<List<String>> recordedMcpServerCodes(AgentRunState state) {
        if (state == null || state.getMcpServerCodes() == null
                || state.getMcpServerCodes().isBlank()) {
            return Optional.empty();
        }
        try {
            List<String> codes = JsonUtils.fromJson(state.getMcpServerCodes(),
                    new TypeReference<List<String>>() {
                    });
            return Optional.of(codes == null ? List.of() : codes);
        } catch (Exception e) {
            log.warn("MCP 服务列表无法解析, threadId={}", state.getThreadId());
            return Optional.empty();
        }
    }

    public void updateStatus(String threadId, Long userId, String runStatus, String pendingConfirmation) {
        if (threadId == null || userId == null) {
            return;
        }
        runStateMapper.updateStatus(threadId, userId, runStatus, pendingConfirmation);
    }

    /**
     * 续跑租约时长。只用于回收崩溃遗留的租约，不用于证明动作安全。
     */
    private static final int RESUME_LEASE_SECONDS = 300;

    /**
     * 认领一次续跑。
     *
     * <p>两条约束缺一不可：
     * <ol>
     *   <li>版本仍是调用方看到的那个——挡住过期请求；</li>
     *   <li>没有别的请求持有有效租约——挡住「刷新版本后再次认领」。</li>
     * </ol>
     * 只看版本是不够的：第一个请求推进版本后，第二个请求刷新一下就能拿到新版本，
     * 于是同一次续跑被执行两次。
     *
     * @return 认领成功时返回租约凭据，失败返回空
     */
    public Optional<String> claimForResume(String threadId, Long userId, long expectedVersion) {
        if (threadId == null || userId == null) {
            return Optional.empty();
        }
        String leaseToken = UUID.randomUUID().toString();
        boolean claimed = runStateMapper.claimForResume(threadId, userId, expectedVersion,
                RunStatus.RUNNING, leaseToken, RESUME_LEASE_SECONDS) == 1;
        return claimed ? Optional.of(leaseToken) : Optional.empty();
    }

    /** 释放续跑租约；只释放自己持有的那一份 */
    public void releaseResumeLease(String threadId, Long userId, String leaseToken) {
        if (threadId == null || userId == null || leaseToken == null) {
            return;
        }
        try {
            runStateMapper.releaseResumeLease(threadId, userId, leaseToken);
        } catch (Exception e) {
            // 释放失败只是让租约到期后自然失效，不影响本次结果
            log.warn("释放续跑租约失败, threadId={}", threadId);
        }
    }

    /** 释放会话：清理载荷与待确认信息 */
    public void clearForRelease(String threadId, Long userId) {
        runStateMapper.clearForRelease(threadId, userId, RunStatus.COMPLETED);
    }

    /**
     * 把完整状态还原为可交给框架的 Checkpoint。
     *
     * <p>只还原完整状态里有的字段；缺少恢复点（{@code nextNodeId}）时返回空，
     * 避免把"有消息"误当成"能续跑"。
     */
    public Optional<Checkpoint> toCheckpoint(AgentRunState state) {
        // 恢复点必须完整：Checkpoint 要求 id / nodeId / nextNodeId 都不为空，
        // 缺任何一个都还原不出可续跑的点，这里显式拒绝而不是等构造器抛 NPE
        if (state == null || !isResumable(state)
                || state.getCheckpointId() == null
                || state.getNodeId() == null
                || state.getNextNodeId() == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> data = decodeState(
                    gzipUtils.decompress(state.getStatePayload()).getBytes(StandardCharsets.UTF_8));
            Checkpoint checkpoint = Checkpoint.builder()
                    .id(state.getCheckpointId())
                    .nodeId(state.getNodeId())
                    .nextNodeId(state.getNextNodeId())
                    .state(data)
                    .build();
            return Optional.of(checkpoint);
        } catch (Exception e) {
            // 还原失败必须留痕：否则「续跑不了」只剩一句面向用户的提示，无法定位
            log.warn("完整执行状态无法还原, threadId={}", state.getThreadId(), e);
            return Optional.empty();
        }
    }

    /**
     * 构造带恢复点的执行配置：{@code checkPointId} 让框架从该检查点续跑，
     * {@code nextNode} 保证恢复点确实指向下一步。
     *
     * <p>执行世代来自认领后的状态行（{@code claimForResume} 在认领时已递增）：
     * 续跑是一次新执行，它写下的检查点必须以自己的世代为凭，旧执行从此写不进去。
     * 续跑不声明新的工具环境——沿用已与恢复点配对的记录。
     */
    public RunnableConfig resumeConfig(String threadId, Long userId, AgentRunState state,
                                       String ownerMetadataKey, String epochMetadataKey) {
        return RunnableConfig.builder()
                .threadId(threadId)
                .checkPointId(state.getCheckpointId())
                .nextNode(state.getNextNodeId())
                .addMetadata(ownerMetadataKey, userId)
                .addMetadata(epochMetadataKey, state.getRunEpoch() == null ? 0L : state.getRunEpoch())
                .build();
    }

    // ==================== 序列化 ====================

    /**
     * 序列化完整状态。
     *
     * <p>必须走与框架同一份 {@link StateSerializer}：状态里的 {@code messages} 是
     * Spring AI 的 {@link org.springframework.ai.chat.messages.Message}，用普通
     * ObjectMapper 写出去、再读回来会退化成 {@code LinkedHashMap}，续跑时框架
     * 拿到一堆 Map 而不是消息对象，恢复就会在节点执行处失败。
     */
    String encodeState(Map<String, Object> data) throws Exception {
        Map<String, Object> serializable = new LinkedHashMap<>();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                serializable.put(entry.getKey(), entry.getValue());
            }
        }
        // dataToBytes 内部是 ObjectOutputStream + writeData，带类型信息
        return Base64.getEncoder().encodeToString(stateSerializer.dataToBytes(serializable));
    }

    Map<String, Object> decodeState(byte[] json) throws Exception {
        if (json == null || json.length == 0) {
            return Map.of();
        }
        return stateSerializer.dataFromBytes(Base64.getDecoder().decode(json));
    }

    /**
     * 只保留异常类型，不写异常消息。
     *
     * <p>异常消息可能携带 SQL 参数、文件路径或对话正文，落到审计列就是泄露；
     * 类型足以让运维辨认「写库失败」还是「序列化失败」，细节看服务端日志。
     */
    private String sanitizeFailure(Exception error) {
        String type = error.getClass().getSimpleName();
        return type.length() > 120 ? type.substring(0, 120) : type;
    }
}
