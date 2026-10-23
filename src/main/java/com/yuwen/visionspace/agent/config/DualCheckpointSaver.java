package com.yuwen.visionspace.agent.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;

import com.yuwen.visionspace.agent.recovery.AgentRunStateStore;
import com.yuwen.visionspace.agent.recovery.RunStatus;
import com.yuwen.visionspace.agent.recovery.StateSaveResult;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.dto.agent.LightweightCheckpoint;
import com.yuwen.visionspace.model.dto.agent.SimpleMessage;
import com.yuwen.visionspace.model.entity.AgentSession;
import com.yuwen.visionspace.utils.GzipUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DualCheckpointSaver implements BaseCheckpointSaver {

    /** 会话归属由调用方放进 RunnableConfig 的 metadata，备份写入按它做条件更新 */
    public static final String OWNER_USER_ID_METADATA_KEY = "visionspace.ownerUserId";

    private final RedisSaver redisSaver;
    private final AgentSessionMapper sessionMapper;
    private final AgentRunStateStore runStateStore;
    private final GzipUtils gzipUtils;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    @Autowired
    @Qualifier("agentAsyncExecutor")
    private Executor asyncExecutor;

    /** 备份频率控制：每 N 步才执行一次 MySQL 备份 */
    private static final int BACKUP_EVERY_N_STEPS = 3;
    /** 按 threadId 分别的步骤计数器，支持多会话并发；释放会话时清理 */
    private final ConcurrentHashMap<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();
    /** 按 threadId 分别的状态版本号，只作为本地加速，真正的顺序由数据库决定 */
    private final ConcurrentHashMap<String, AtomicLong> versionCounters = new ConcurrentHashMap<>();

    /**
     * 取下一个状态版本。
     *
     * <p><b>版本顺序必须与共享持久状态一致。</b>只用进程本地计数器时，一个启动更早的
     * 实例接管另一个实例的会话后，本地计数一直小于库里的版本，写入会被 CAS 全部拒绝，
     * 而执行照常继续——有效进度被静默丢弃。
     *
     * <p>因此每次都以库里读到的 {@code storedVersion} 为下界：本地计数器只用来在
     * 同一毫秒内快速递增，永远不允许落后于持久状态。
     */
    private long nextVersion(String threadId, Long ownerUserId) {
        long stored = runStateStore.currentVersion(threadId, ownerUserId);
        AtomicLong counter = versionCounters.computeIfAbsent(threadId, key -> new AtomicLong(stored));
        // updateAndGet 保证结果一定大于 stored：本地计数器只会领先，不会落后
        return counter.updateAndGet(current -> Math.max(current, stored) + 1);
    }

    // ==================== 必须实现的 4 个方法 ====================

    /**
     * 加载检查点。
     *
     * <p>只在 Redis 有数据时返回。旧实现会在 Redis 缺失时用 MySQL 的轻量归档
     * 「重建」一个只含 messages 的 Checkpoint，但框架的 {@code getInitialState}
     * 每次正常开始都会调用本方法——于是仅含消息的归档会被当成完整恢复点混进
     * 新一次执行，正是「不能证明安全续跑却伪装恢复」的缺陷。
     *
     * <p>现在 Redis 缺失一律返回空：新一次执行从干净状态开始，历史消息由
     * {@code agent_message} 负责展示，完整续跑只走 {@link AgentRunStateStore}
     * 的显式恢复入口。旧轻量归档仍然可查看，只是不再参与执行。
     */
    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        Optional<String> threadIdOpt = config.threadId();
        if (threadIdOpt.isEmpty()) {
            log.warn("RunnableConfig 缺少 threadId，无法加载 Checkpoint");
            return Optional.empty();
        }
        return redisSaver.get(config);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);

        // 1. 主写 Redis
        RunnableConfig result = redisSaver.put(config, checkpoint);

        // 2. 设置 TTL
        setCheckpointTtl(threadId);

        Long ownerUserId = ownerUserId(config);

        // 3. 完整状态：同步、版本化写入。恢复点必须可靠，不能等异步任务，
        //    否则「进程在 put 之后崩溃」就没有可续跑的完整状态。
        if (ownerUserId != null) {
            saveWithVersionAlignment(threadId, ownerUserId, checkpoint);
        }

        // 4. 异步轻量备份到 MySQL（历史展示用，直接提交到线程池，绕过 Spring 代理问题）
        asyncExecutor.execute(() -> doBackupLightweight(threadId, ownerUserId, checkpoint));

        return result;
    }

    /**
     * 写入完整状态，并在版本被别的实例抢先时对齐重试一次。
     *
     * <p>「被更新版本取代」与「备份失败」必须分开处理：前者说明本实例的版本号
     * 落后于共享持久状态（跨实例接管后必然发生），对齐后重试就能把有效进度写进去；
     * 后者才是真的写不进去，交给 {@link AgentRunStateStore} 记录 backupError。
     */
    private void saveWithVersionAlignment(String threadId, Long ownerUserId, Checkpoint checkpoint) {
        StateSaveResult result = runStateStore.saveFullState(threadId, ownerUserId,
                nextVersion(threadId, ownerUserId), checkpoint, RunStatus.RUNNING, null);
        if (result == StateSaveResult.SUPERSEDED) {
            // 对齐到共享版本后重试一次：不重试就等于静默丢掉这一步的进度
            result = runStateStore.saveFullState(threadId, ownerUserId,
                    nextVersion(threadId, ownerUserId), checkpoint, RunStatus.RUNNING, null);
        }
        if (result == StateSaveResult.FAILED) {
            // 真写不进去不能只留 debug：备份失败已经记进 backupError，这里同步告警
            log.warn("完整状态写入失败，已记录备份失败, threadId={}", threadId);
        } else if (result == StateSaveResult.SUPERSEDED) {
            // 对齐后仍被取代：说明这一版本来得更旧，进度确实被更新的一版覆盖
            log.info("完整状态被更新版本取代，未覆盖较新的进度, threadId={}", threadId);
        }
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return redisSaver.list(config);
    }

    /**
     * 释放会话。
     *
     * <p>除了框架侧的释放，还要清掉本会话的进程内运行计数与持久化状态载荷：
     * 否则每开一个新会话就永久留下一个计数器，长期运行会无界增长。
     *
     * <p>清掉本地版本计数器不会让版本回退：{@link #nextVersion} 每次都以数据库里的
     * 版本为下界，重新播种时直接对齐持久状态。
     */
    @Override
    public BaseCheckpointSaver.Tag release(RunnableConfig config) throws Exception {
        config.threadId().ifPresent(threadId -> {
            stepCounters.remove(threadId);
            versionCounters.remove(threadId);
            Long owner = ownerUserId(config);
            if (owner != null) {
                try {
                    runStateStore.clearForRelease(threadId, owner);
                } catch (Exception e) {
                    log.warn("释放会话时清理执行状态失败, threadId={}", threadId);
                }
            }
        });
        return redisSaver.release(config);
    }

    // ==================== TTL 管理 ====================

    /**
     * 设置 Checkpoint 的过期时间（支持按用户等级动态调整）
     */
    private void setCheckpointTtl(String threadId) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket("checkpoint:" + threadId);
            Duration ttl = Duration.ofDays(7);
            bucket.expire(ttl);
            log.debug("Checkpoint TTL 已设置: {} → {} 天", threadId, ttl.toDays());
        } catch (Exception e) {
            log.warn("设置 Redis TTL 失败（不影响主流程）", e);
        }
    }

    // ==================== 轻量备份核心逻辑（保持不变） ====================

    /**
     * 旧轻量归档的读取入口。
     *
     * <p><b>只用于查看历史，不用于续跑。</b>返回的消息列表来自
     * {@code agent_session.checkpointData}，没有恢复点、迭代状态与待确认信息，
     * 因此不能被包装成 Checkpoint 交给框架。显式恢复走
     * {@link AgentRunStateStore#loadResumable}。
     */
    Optional<List<SimpleMessage>> loadLegacyArchiveMessages(String threadId) {
        AgentSession session = sessionMapper.selectBySessionId(threadId);
        if (session == null || session.getCheckpointData() == null) {
            return Optional.empty();
        }
        try {
            String json = gzipUtils.decompress(session.getCheckpointData());
            LightweightCheckpoint lw = objectMapper.readValue(json, LightweightCheckpoint.class);
            return Optional.of(lw.messages() == null ? List.of() : lw.messages());
        } catch (Exception e) {
            log.warn("旧轻量归档无法解析, threadId={}", threadId);
            return Optional.empty();
        }
    }

    /**
     * 异步备份（由 asyncExecutor 线程池执行）
     *
     * <p>归属未知时不写库：没有 owner 就无法证明这次执行属于该会话，
     * 宁可不备份，也不能把状态写进别人的会话。
     */
    private void doBackupLightweight(String threadId, Long ownerUserId, Checkpoint checkpoint) {
        if (ownerUserId == null) {
            log.warn("Checkpoint 缺少归属信息，跳过 MySQL 备份, threadId={}", threadId);
            return;
        }
        // 每 N 步才备份一次，减少 MySQL 写入压力
        int step = stepCounters.computeIfAbsent(threadId, k -> new AtomicInteger(0)).incrementAndGet();
        if (step % BACKUP_EVERY_N_STEPS != 0) {
            return;
        }

        try {
            LightweightCheckpoint lw = extractLightweight(threadId, checkpoint);
            String json = objectMapper.writeValueAsString(lw);
            byte[] compressed = gzipUtils.compress(json);

            int updated = sessionMapper.updateCheckpointData(threadId, ownerUserId, compressed);
            if (updated == 0) {
                log.warn("拒绝写入他人会话的 Checkpoint, threadId={}, userId={}", threadId, ownerUserId);
                return;
            }
            log.debug("轻量备份成功, threadId={}", threadId);
        } catch (Exception e) {
            log.warn("备份 Checkpoint 到 MySQL 失败（不影响主流程）, threadId={}", threadId, e);
        }
    }

    /**
     * 从 RunnableConfig 读取调用方声明的会话归属。
     */
    private Long ownerUserId(RunnableConfig config) {
        return config.metadata(OWNER_USER_ID_METADATA_KEY)
                .map(value -> value instanceof Number number ? number.longValue()
                        : Long.valueOf(String.valueOf(value)))
                .orElse(null);
    }

    /**
     * 从 Checkpoint 提取轻量信息
     */
    private LightweightCheckpoint extractLightweight(String threadId, Checkpoint cp) {
        List<SimpleMessage> simpleMsgs = List.of();
        String summary = null;

        try {
            if (cp.getState() != null && cp.getState().containsKey("messages")) {
                @SuppressWarnings("unchecked")
                List<Object> msgs = (List<Object>) cp.getState().get("messages");
                if (msgs != null) {
                    simpleMsgs = msgs.stream()
                            .map(this::extractSimpleMessage)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.warn("提取 messages 失败", e);
        }

        try {
            if (cp.getState() != null && cp.getState().containsKey("summary")) {
                summary = String.valueOf(cp.getState().get("summary"));
            }
        } catch (Exception e) {
            log.warn("提取 summary 失败，使用 null", e);
        }

        return new LightweightCheckpoint(threadId, simpleMsgs, summary);
    }

    /**
     * 从 Spring AI Message 提取 SimpleMessage
     */
    private SimpleMessage extractSimpleMessage(Object msg) {
        try {
            if (msg instanceof org.springframework.ai.chat.messages.UserMessage um) {
                return new SimpleMessage("USER", um.getText());
            } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                return new SimpleMessage("ASSISTANT", am.getText());
            } else if (msg instanceof org.springframework.ai.chat.messages.SystemMessage sm) {
                return new SimpleMessage("SYSTEM", sm.getText());
            }else {
                // 兜底：尝试调用 toString
                return new SimpleMessage("UNKNOWN", msg.toString());
            }
        } catch (Exception e) {
            return new SimpleMessage("ERROR", "消息提取失败");
        }
    }

}