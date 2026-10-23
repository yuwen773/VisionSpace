package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.yuwen.visionspace.mapper.AgentRunStateMapper;
import com.yuwen.visionspace.model.entity.AgentRunState;
import com.yuwen.visionspace.utils.GzipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
     * 把 Checkpoint 保存为完整状态。
     *
     * <p>{@code stateVersion} 必须由调用方按单调顺序给出；写入是 CAS，旧版本影响 0 行。
     *
     * @return true 表示本次写入生效
     */
    public boolean saveFullState(String threadId, Long userId, long stateVersion,
                                 Checkpoint checkpoint, String runStatus,
                                 String pendingConfirmation) {
        if (threadId == null || userId == null || checkpoint == null) {
            return false;
        }
        try {
            // 首次执行时状态行还不存在，下面的 UPDATE 会静默影响 0 行。
            // 先按 (threadId) 唯一键建行；已存在时是空操作，不会改写归属。
            runStateMapper.insertIfAbsent(threadId, userId, AgentStateFormat.V2_FULL.code(),
                    RunStatus.RUNNING);
            byte[] payload = gzipUtils.compress(encodeState(checkpoint.getState()));
            int updated = runStateMapper.updateIfNewer(threadId, userId, stateVersion,
                    AgentStateFormat.V2_FULL.code(), checkpoint.getId(), checkpoint.getNodeId(),
                    checkpoint.getNextNodeId(), payload, runStatus, pendingConfirmation);
            if (updated == 0) {
                // 版本不够新（旧备份晚到）或归属不符：都不是错误，是保护
                log.debug("执行状态未写入（版本较旧或归属不符）, threadId={}, version={}",
                        threadId, stateVersion);
            }
            return updated == 1;
        } catch (Exception e) {
            recordBackupFailure(threadId, userId, e);
            return false;
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

    public void updateStatus(String threadId, Long userId, String runStatus, String pendingConfirmation) {
        if (threadId == null || userId == null) {
            return;
        }
        runStateMapper.updateStatus(threadId, userId, runStatus, pendingConfirmation);
    }

    /**
     * 认领一次续跑：只有当前版本仍等于调用方看到的版本时才能推进。
     *
     * <p>重复或并发的续跑请求带同一个版本，只有一个能成功；失败方拿到
     * {@code false} 并被告知「请求已过期」，不会产生第二次执行。
     */
    public boolean claimForResume(String threadId, Long userId, long expectedVersion) {
        if (threadId == null || userId == null) {
            return false;
        }
        return runStateMapper.claimForResume(threadId, userId, expectedVersion,
                RunStatus.RUNNING) == 1;
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
     */
    public RunnableConfig resumeConfig(String threadId, Long userId, AgentRunState state,
                                       String ownerMetadataKey) {
        return RunnableConfig.builder()
                .threadId(threadId)
                .checkPointId(state.getCheckpointId())
                .nextNode(state.getNextNodeId())
                .addMetadata(ownerMetadataKey, userId)
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
