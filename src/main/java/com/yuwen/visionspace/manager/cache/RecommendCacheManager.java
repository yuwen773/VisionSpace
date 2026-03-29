package com.yuwen.visionspace.manager.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 推荐结果 Redis 缓存管理
 * 键设计: recommend:{type}:{page}:{size}
 * 类型: hot, latest, quality, random
 */
@Component
public class RecommendCacheManager {

    private static final String RECOMMEND_KEY_PREFIX = "recommend:";
    private static final long CACHE_TTL = 5; // 5分钟

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取缓存的推荐结果
     */
    @SuppressWarnings("unchecked")
    public List<Long> getCachedRecommendList(String type, int page, int size) {
        String key = buildKey(type, page, size);
        return (List<Long>) redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置推荐结果缓存
     */
    public void setCachedRecommendList(String type, int page, int size, List<Long> pictureIds) {
        String key = buildKey(type, page, size);
        redisTemplate.opsForValue().set(key, pictureIds, CACHE_TTL, TimeUnit.MINUTES);
    }

    /**
     * 失效推荐缓存
     */
    public void invalidateRecommendCache() {
        // 删除所有推荐相关缓存
        Set<String> keys = redisTemplate.keys(RECOMMEND_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String buildKey(String type, int page, int size) {
        return RECOMMEND_KEY_PREFIX + type + ":" + page + ":" + size;
    }
}