package com.yuwen.visionspace.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 缓存配置
 */
@Configuration
public class CacheConfig {

    /**
     * 图片列表本地缓存
     * 用于缓存分页查询结果，防止缓存雪崩
     */
    @Bean
    public Cache<String, String> pictureListCache() {
        return Caffeine.newBuilder()
                .initialCapacity(1024)
                .maximumSize(10_000L)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }
}
