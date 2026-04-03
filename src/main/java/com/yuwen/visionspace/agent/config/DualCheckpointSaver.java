package com.yuwen.visionspace.agent.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;

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
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DualCheckpointSaver implements BaseCheckpointSaver {

    private final RedisSaver redisSaver;
    private final AgentSessionMapper sessionMapper;
    private final GzipUtils gzipUtils;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    @Autowired
    @Qualifier("agentAsyncExecutor")
    private Executor asyncExecutor;

    /** 备份频率控制：每 N 步才执行一次 MySQL 备份 */
    private static final int BACKUP_EVERY_N_STEPS = 3;
    /** 按 threadId 分别的步骤计数器，支持多会话并发 */
    private final ConcurrentHashMap<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    // ==================== 必须实现的 4 个方法 ====================

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        Optional<String> threadIdOpt = config.threadId();
        if (threadIdOpt.isEmpty()) {
            log.warn("RunnableConfig 缺少 threadId，无法加载 Checkpoint");
            return Optional.empty();
        }
        String threadId = threadIdOpt.get();

        // 1. 优先 Redis
        Optional<Checkpoint> redisCp = redisSaver.get(config);
        if (redisCp.isPresent()) {
            return redisCp;
        }

        log.info("Redis Checkpoint 不存在 → 触发 MySQL 预热: {}", threadId);
        return loadLightweightFromMySQL(threadId);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);

        // 1. 主写 Redis
        RunnableConfig result = redisSaver.put(config, checkpoint);

        // 2. 设置 TTL
        setCheckpointTtl(threadId);

        // 3. 异步轻量备份到 MySQL（直接提交到线程池，绕过 Spring 代理问题）
        asyncExecutor.execute(() -> doBackupLightweight(threadId, checkpoint));

        return result;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return redisSaver.list(config);
    }

    @Override
    public BaseCheckpointSaver.Tag release(RunnableConfig config) throws Exception {
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

    private Optional<Checkpoint> loadLightweightFromMySQL(String threadId) {
        AgentSession session = sessionMapper.selectBySessionId(threadId);
        if (session == null || session.getCheckpointData() == null) {
            return Optional.empty();
        }

        try {
            String json = gzipUtils.decompress(session.getCheckpointData());
            LightweightCheckpoint lw = objectMapper.readValue(json, LightweightCheckpoint.class);

            Checkpoint rebuilt = Checkpoint.builder()
                    .state(Map.of("messages", convertToSpringAIMessages(lw.messages())))
                    .build();

            return Optional.of(rebuilt);
        } catch (Exception e) {
            log.error("MySQL 预热失败, threadId={}", threadId, e);
            return Optional.empty();
        }
    }

    /**
     * 异步备份（由 asyncExecutor 线程池执行）
     */
    private void doBackupLightweight(String threadId, Checkpoint checkpoint) {
        // 每 N 步才备份一次，减少 MySQL 写入压力
        int step = stepCounters.computeIfAbsent(threadId, k -> new AtomicInteger(0)).incrementAndGet();
        if (step % BACKUP_EVERY_N_STEPS != 0) {
            return;
        }

        try {
            LightweightCheckpoint lw = extractLightweight(threadId, checkpoint);
            String json = objectMapper.writeValueAsString(lw);
            byte[] compressed = gzipUtils.compress(json);

            sessionMapper.updateCheckpointData(threadId, compressed);
            log.debug("轻量备份成功, threadId={}", threadId);
        } catch (Exception e) {
            log.warn("备份 Checkpoint 到 MySQL 失败（不影响主流程）, threadId={}", threadId, e);
        }
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

    /**
     * 将 SimpleMessage 列表转换回 Spring AI Message
     */
    private List<org.springframework.ai.chat.messages.Message> convertToSpringAIMessages(
            List<SimpleMessage> simpleMsgs) {
        if (simpleMsgs == null) return List.of();
        return simpleMsgs.stream()
                .map(m -> switch (m.role()) {
                    case "USER" -> new org.springframework.ai.chat.messages.UserMessage(m.content());
                    case "ASSISTANT" -> new org.springframework.ai.chat.messages.AssistantMessage(m.content());
                    case "SYSTEM" -> new org.springframework.ai.chat.messages.SystemMessage(m.content());
                    default -> new org.springframework.ai.chat.messages.AssistantMessage(m.content());
                })
                .collect(Collectors.toList());
    }
}