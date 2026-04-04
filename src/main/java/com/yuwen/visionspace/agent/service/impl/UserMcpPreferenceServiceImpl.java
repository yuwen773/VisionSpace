package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.mapper.UserMcpPreferenceMapper;
import com.yuwen.visionspace.model.entity.UserMcpPreferenceEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMcpPreferenceServiceImpl implements UserMcpPreferenceService {

    private static final String REDIS_KEY_PREFIX = "user:mcp:preference:";
    private static final long CACHE_TTL_DAYS = 7;

    private final UserMcpPreferenceMapper preferenceMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getDefaultEnabledServers(Long userId) {
        String cacheKey = REDIS_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return JsonUtils.fromJson(cached, new TypeReference<List<String>>() {});
        }

        UserMcpPreferenceEntity entity = preferenceMapper.selectOne(
            new LambdaQueryWrapper<UserMcpPreferenceEntity>()
                .eq(UserMcpPreferenceEntity::getUserId, userId)
        );

        List<String> servers = null;
        if (entity != null && entity.getDefaultEnabledServers() != null) {
            servers = JsonUtils.fromJson(entity.getDefaultEnabledServers(),
                new TypeReference<List<String>>() {});
        }

        // 缓存结果
        String toCache = servers != null ? JsonUtils.toJson(servers) : "[]";
        redisTemplate.opsForValue().set(cacheKey, toCache, CACHE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);

        return servers;
    }

    @Override
    public void setDefaultEnabledServers(Long userId, List<String> mcpServerCodes) {
        UserMcpPreferenceEntity entity = preferenceMapper.selectOne(
            new LambdaQueryWrapper<UserMcpPreferenceEntity>()
                .eq(UserMcpPreferenceEntity::getUserId, userId)
        );

        if (entity == null) {
            entity = new UserMcpPreferenceEntity();
            entity.setUserId(userId);
            entity.setDefaultEnabledServers(JsonUtils.toJson(mcpServerCodes));
            preferenceMapper.insert(entity);
        } else {
            entity.setDefaultEnabledServers(JsonUtils.toJson(mcpServerCodes));
            preferenceMapper.updateById(entity);
        }

        // 刷新缓存
        String cacheKey = REDIS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(mcpServerCodes), CACHE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }
}
