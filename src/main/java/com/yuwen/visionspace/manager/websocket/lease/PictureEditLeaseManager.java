package com.yuwen.visionspace.manager.websocket.lease;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 图片编辑租约管理器（跨实例共享）。
 *
 * <p>租约绑定到具体的 WebSocket 连接（sessionId），而不是用户：
 * 同一用户的多个标签页是多条连接，只有真正持有租约的那条连接关闭才释放。
 * 状态存 Redis 并带 TTL，两个后端实例看到同一份租约，
 * 竞争通过 Lua 脚本原子判定，任何时刻只有一个有效持有者。
 */
@Component
@Slf4j
public class PictureEditLeaseManager {

    /** value 编码：sessionId|userId，释放与续期都要做归属验证。 */
    private static final String KEY_PREFIX = "picture:edit:lease:";

    private static final DefaultRedisScript<String> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
                return '-1'
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return '1'
            """, String.class);

    private static final DefaultRedisScript<String> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return '0'
            end
            local sep = string.find(current, '|', 1, true)
            local sessionId = string.sub(current, 1, sep - 1)
            if sessionId ~= ARGV[1] then
                return '0'
            end
            redis.call('DEL', KEYS[1])
            return '1'
            """, String.class);

    private static final DefaultRedisScript<String> RENEW_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return '0'
            end
            local sep = string.find(current, '|', 1, true)
            local sessionId = string.sub(current, 1, sep - 1)
            if sessionId ~= ARGV[1] then
                return '0'
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return '1'
            """, String.class);

    private static final DefaultRedisScript<String> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return '0'
            end
            if ARGV[1] ~= '' then
                local sep = string.find(current, '|', 1, true)
                local sessionId = string.sub(current, 1, sep - 1)
                if sessionId ~= ARGV[1] then
                    return '0'
                end
            end
            redis.call('DEL', KEYS[1])
            return '1'
            """, String.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取租约。
     *
     * @param pictureId 图片
     * @param sessionId 连接标识（绑定具体连接）
     * @param userId    用户
     * @param ttl       期限
     * @return 获取结果，失败时携带当前持有者信息
     */
    public LeaseAcquireResult tryAcquire(long pictureId, String sessionId, long userId, Duration ttl) {
        String value = sessionId + "|" + userId;
        String result = stringRedisTemplate.execute(ACQUIRE_SCRIPT,
                List.of(key(pictureId)), value, String.valueOf(ttl.toMillis()));
        if ("1".equals(result)) {
            return LeaseAcquireResult.acquired(userId);
        }
        return LeaseAcquireResult.rejected(holderUserId(pictureId).orElse(null));
    }

    /**
     * 释放租约：只有持有租约的那条连接能释放。
     */
    public boolean release(long pictureId, String sessionId) {
        String result = stringRedisTemplate.execute(RELEASE_SCRIPT,
                List.of(key(pictureId)), sessionId);
        return "1".equals(result);
    }

    /**
     * 续期：只有当前持有者能续期，过期后不能再续。
     */
    public boolean renew(long pictureId, String sessionId, Duration ttl) {
        String result = stringRedisTemplate.execute(RENEW_SCRIPT,
                List.of(key(pictureId)), sessionId, String.valueOf(ttl.toMillis()));
        return "1".equals(result);
    }

    /**
     * 强制撤销：撤权/注销时按持有者（或连同具体连接）收回租约。
     *
     * @param sessionId 连接标识，为空表示撤销该图片租约（不管当前持有者是谁）
     */
    public void forceRevoke(long pictureId, String sessionId) {
        stringRedisTemplate.execute(REVOKE_SCRIPT, List.of(key(pictureId)),
                sessionId == null ? "" : sessionId);
    }

    /**
     * 当前持有者用户。
     */
    public Optional<Long> holderUserId(long pictureId) {
        String value = stringRedisTemplate.opsForValue().get(key(pictureId));
        if (value == null) {
            return Optional.empty();
        }
        int sep = value.indexOf('|');
        if (sep < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.substring(sep + 1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * 当前持有者的连接标识。
     */
    public Optional<String> holderSessionId(long pictureId) {
        String value = stringRedisTemplate.opsForValue().get(key(pictureId));
        if (value == null) {
            return Optional.empty();
        }
        int sep = value.indexOf('|');
        return sep < 0 ? Optional.empty() : Optional.of(value.substring(0, sep));
    }

    private String key(long pictureId) {
        return KEY_PREFIX + pictureId;
    }

    /**
     * 获取结果：成功时携带持有者，失败时携带当前租约归属，供 UI 反馈。
     */
    public record LeaseAcquireResult(boolean acquired, Long holderUserId) {
        public static LeaseAcquireResult acquired(long userId) {
            return new LeaseAcquireResult(true, userId);
        }

        public static LeaseAcquireResult rejected(Long currentHolderUserId) {
            return new LeaseAcquireResult(false, currentHolderUserId);
        }
    }
}
