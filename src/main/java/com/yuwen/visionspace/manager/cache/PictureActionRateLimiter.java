package com.yuwen.visionspace.manager.cache;

import com.yuwen.visionspace.model.vo.PictureActionReportMetricsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;

/**
 * 行为上报的限流与可观测计数。
 *
 * <p>限流按脱敏后的客户端标识计数：登录用户按用户 id，匿名用户按来源地址的
 * 哈希。计数窗口固定一分钟，Redis 不可用时按失败处理（不放行未校验的上报）。
 *
 * <p>计数器只记录数量，不记录用户、地址或事件内容，键名里也不出现这些信息。
 */
@Slf4j
@Component
public class PictureActionRateLimiter {

    private static final String RATE_KEY_PREFIX = "vision-space:picture-action:rate:";
    private static final String METRIC_KEY_PREFIX = "vision-space:picture-action:metric:";

    private static final String METRIC_ACCEPTED = "accepted";
    private static final String METRIC_DUPLICATE = "duplicate";
    private static final String METRIC_REJECTED = "rejected";
    private static final String METRIC_RATE_LIMITED = "rate-limited";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 单个客户端每分钟允许的上报次数 */
    @Value("${vision-space.picture-action.rate-limit-per-minute:120}")
    private long rateLimitPerMinute;

    /**
     * 记录一次上报尝试并判断是否超限。
     *
     * @param clientKey 脱敏后的客户端标识
     * @return true 表示允许继续处理
     */
    public boolean allow(String clientKey) {
        String key = RATE_KEY_PREFIX + clientKey;
        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 窗口在首次写入时设置，避免计数永不过期
                stringRedisTemplate.expire(key, Duration.ofMinutes(1));
            }
        } catch (Exception e) {
            log.error("行为上报限流计数失败，按拒绝处理: clientKey={}", clientKey, e);
            return false;
        }
        return count != null && count <= rateLimitPerMinute;
    }

    /** 计数：成功受理的新事件 */
    public void countAccepted() {
        increment(METRIC_ACCEPTED);
    }

    /** 计数：幂等键命中的重复事件 */
    public void countDuplicate() {
        increment(METRIC_DUPLICATE);
    }

    /** 计数：参数、可见性或越权字段导致的拒绝 */
    public void countRejected() {
        increment(METRIC_REJECTED);
    }

    /** 计数：触发限流被拒绝 */
    public void countRateLimited() {
        increment(METRIC_RATE_LIMITED);
    }

    /**
     * 读取当前可观测计数。Redis 不可用时返回空计数而不是抛出异常。
     */
    public PictureActionReportMetricsVO metrics() {
        PictureActionReportMetricsVO metrics = new PictureActionReportMetricsVO();
        metrics.setAcceptedCount(read(METRIC_ACCEPTED));
        metrics.setDuplicateCount(read(METRIC_DUPLICATE));
        metrics.setRejectedCount(read(METRIC_REJECTED));
        metrics.setRateLimitedCount(read(METRIC_RATE_LIMITED));
        return metrics;
    }

    private void increment(String metric) {
        try {
            stringRedisTemplate.opsForValue().increment(METRIC_KEY_PREFIX + metric);
        } catch (Exception e) {
            log.warn("行为上报计数写入失败: metric={}", metric, e);
        }
    }

    private long read(String metric) {
        try {
            String value = stringRedisTemplate.opsForValue().get(METRIC_KEY_PREFIX + metric);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("行为上报计数读取失败: metric={}", metric, e);
            return 0L;
        }
    }
}
