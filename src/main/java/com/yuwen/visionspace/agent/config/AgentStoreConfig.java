package com.yuwen.visionspace.agent.config;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Store 配置
 */
@Configuration
public class AgentStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = String.format("redis://%s:%d", redisHost, redisPort);

        var serverConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isBlank()) {
            serverConfig.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }

    @Bean
    public RedisSaver redisSaver(RedissonClient redissonClient) {
        return RedisSaver.builder()
                .redisson(redissonClient)
                .build();
    }
    @Bean
    public MemoryStore memoryStore() {
        return new MemoryStore();
    }

    /**
     * 与 RedisSaver 默认一致的序列化器。
     *
     * <p>完整执行状态的持久化必须用同一份序列化语义，否则「Redis 里能读、
     * MySQL 里读不回」。这里显式建 Bean，避免两边各自 new 一个 ObjectMapper
     * 后悄悄分叉。
     */
    @Bean
    public StateSerializer stateSerializer() {
        return StateGraph.DEFAULT_JACKSON_SERIALIZER;
    }
}