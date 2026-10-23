package com.yuwen.visionspace.manager.cache;

import com.yuwen.visionspace.utils.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 推荐结果 Redis 缓存管理
 * 键设计: recommend:{type}:list[:{context}] 缓存有界候选列表
 *         recommend:{type}:total         缓存真实总数
 * 类型: hot, latest, quality, random
 *
 * <p>候选列表按「类型 + 分页上下文」缓存一份，翻页只做内存切片；
 * 不再按分页键缓存重复的全量结果。键是普通字符串，失效时可以按前缀批量删除。
 */
@Slf4j
@Component
public class RecommendCacheManager {

    private static final String RECOMMEND_KEY_PREFIX = "recommend:";
    private static final long CACHE_TTL = 5; // 5分钟

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 获取缓存的有界候选列表。
     *
     * @param type 推荐类型
     * @param context 分页上下文；为空表示默认上下文
     * @return 候选列表，未命中返回 null
     */
    public RecommendCandidateList getCandidateList(String type, String context) {
        String cached = stringRedisTemplate.opsForValue().get(candidateKey(type, context));
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, RecommendCandidateList.class);
        } catch (Exception e) {
            log.warn("推荐候选缓存解析失败，按未命中处理: type={}", type, e);
            return null;
        }
    }

    /**
     * 缓存有界候选列表。
     *
     * @param type 推荐类型
     * @param context 请求里的分页上下文；为空表示写入默认上下文槽位
     * @param list 候选列表
     */
    public void setCandidateList(String type, String context, RecommendCandidateList list) {
        try {
            stringRedisTemplate.opsForValue().set(candidateKey(type, context),
                    objectMapper.writeValueAsString(list), CACHE_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("推荐候选缓存写入失败: type={}", type, e);
        }
    }

    /**
     * 失效推荐缓存
     */
    public void invalidateRecommendCache() {
        // 删除所有推荐相关缓存
        Set<String> keys = stringRedisTemplate.keys(RECOMMEND_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private String buildTotalKey(String type) {
        return RECOMMEND_KEY_PREFIX + type + ":total";
    }

    /**
     * 候选列表的缓存键：默认上下文一个槽位，带上下文的按上下文哈希分槽。
     */
    private String candidateKey(String type, String context) {
        if (context == null || context.isBlank()) {
            return RECOMMEND_KEY_PREFIX + type + ":list";
        }
        return RECOMMEND_KEY_PREFIX + type + ":list:" + HashUtils.shortHash(context.trim());
    }

    /**
     * 获取缓存的推荐总数
     */
    public Long getCachedTotal(String type) {
        String total = stringRedisTemplate.opsForValue().get(buildTotalKey(type));
        if (total == null) {
            return null;
        }
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 设置推荐总数缓存
     */
    public void setCachedTotal(String type, long total) {
        stringRedisTemplate.opsForValue().set(buildTotalKey(type),
                String.valueOf(total), CACHE_TTL, TimeUnit.MINUTES);
    }
}
