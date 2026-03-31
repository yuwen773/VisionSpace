package com.yuwen.visionspace.agent.config;

import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Store 配置
 */
@Configuration
public class AgentStoreConfig {

    @Bean
    public MemoryStore memoryStore() {
        return new MemoryStore();
    }
}
