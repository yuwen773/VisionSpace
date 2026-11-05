package com.yuwen.visionspace.manager.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Redis 跨实例广播。来源实例本地直发一次，订阅回放按实例标识去重。
 * 广播队列最多 1024 条；超载丢弃并计数，不在 Redis 订阅线程等待慢接收者。
 * 发布失败记录失败，本地连接仍可协作；总线不承诺持久化或补发。
 */
@Component
@Slf4j
public class PictureEditBroadcastBus {

    public record BusMessage(String originInstanceId, Long pictureId, String senderSessionId, String payload) { }

    private static final String CHANNEL_PREFIX = "picture:edit:bus:";
    private static final DefaultRedisScript<Long> EDIT_SCRIPT = new DefaultRedisScript<>("""
            local lease = redis.call('GET', KEYS[1])
            if not lease then return 0 end
            local sep = string.find(lease, '|', 1, true)
            if not sep or string.sub(lease, 1, sep - 1) ~= ARGV[1] then return 0 end
            redis.call('PUBLISH', ARGV[2], ARGV[3])
            return 1
            """, Long.class);
    private final String instanceId = UUID.randomUUID().toString();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new SimpleModule()
            .addSerializer(Long.class, ToStringSerializer.instance)
            .addSerializer(Long.TYPE, ToStringSerializer.instance));

    @Resource
    private PictureEditHandler pictureEditHandler;
    @Resource
    private PictureEditMonitor monitor;
    @Resource
    private RedisConnectionFactory redisConnectionFactory;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private RedisMessageListenerContainer container;
    private ThreadPoolExecutor executor;

    @PostConstruct
    public void subscribe() {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1024), r -> {
                    Thread thread = new Thread(r, "picture-edit-bus");
                    thread.setDaemon(true);
                    return thread;
                }, (task, pool) -> {
                    monitor.onBroadcastRejected();
                    log.warn("协作广播队列已满或关闭，丢弃本次回放");
                });
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(executor);
        container.addMessageListener((message, pattern) -> {
            try {
                BusMessage bus = objectMapper.readValue(message.getBody(), BusMessage.class);
                if (!instanceId.equals(bus.originInstanceId())) {
                    pictureEditHandler.relayBusMessage(bus.pictureId(), bus);
                }
            } catch (Exception e) {
                monitor.onBroadcastFailure();
                log.warn("协作广播转发失败", e);
            }
        }, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.afterPropertiesSet();
        container.start();
    }

    public void publish(long pictureId, String senderSessionId, PictureEditResponseMessage message) {
        try {
            BusMessage bus = new BusMessage(instanceId, pictureId, senderSessionId,
                    objectMapper.writeValueAsString(message));
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + pictureId, objectMapper.writeValueAsString(bus));
        } catch (Exception e) {
            monitor.onBroadcastFailure();
            log.warn("协作广播跨实例发布失败: pictureId={}", pictureId, e);
        }
    }

    public int remainingCapacity() {
        return executor == null ? -1 : executor.getQueue().remainingCapacity();
    }

    /** 归属判断与操作发布在 Redis 中原子执行，过期/接管不能插入二者之间。 */
    public boolean publishEditIfHolder(long pictureId, String sessionId, PictureEditResponseMessage message) {
        try {
            BusMessage bus = new BusMessage(instanceId, pictureId, sessionId,
                    objectMapper.writeValueAsString(message));
            Long accepted = stringRedisTemplate.execute(EDIT_SCRIPT,
                    List.of("picture:edit:lease:" + pictureId), sessionId,
                    CHANNEL_PREFIX + pictureId, objectMapper.writeValueAsString(bus));
            return Long.valueOf(1).equals(accepted);
        } catch (Exception e) {
            monitor.onBroadcastFailure();
            log.warn("协作编辑发布失败: pictureId={}", pictureId, e);
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (container != null) container.destroy();
        } catch (Exception e) {
            log.warn("协作广播容器停机异常", e);
        } finally {
            if (executor != null) executor.shutdownNow();
        }
    }
}
