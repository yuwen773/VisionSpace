package com.yuwen.visionspace.manager.websocket.lease;

import com.yuwen.visionspace.acceptance.IsolatedSpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 租约测试公共基类：要求隔离验收环境（真实隔离 Redis），基座与其它验收测试一致。
 */
@IsolatedSpringBootTest
abstract class PictureEditLeaseTestBase {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    protected static final Duration LEASE_TTL = Duration.ofSeconds(30);

    /** 短期限：验证过期接管，避免测试等待过久。 */
    protected static final Duration SHORT_TTL = Duration.ofMillis(800);

    protected Duration leaseTtl() {
        return LEASE_TTL;
    }

    protected Duration shortTtl() {
        return SHORT_TTL;
    }

    protected void awaitLeaseExpiry() throws InterruptedException {
        Thread.sleep(SHORT_TTL.toMillis() + 300);
    }

    /** 每条用例独立 pictureId，避免用例间互相看到对方租约。 */
    protected long uniquePictureId() {
        return 440000_000L + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    protected String leaseKey(long pictureId) {
        return "picture:edit:lease:" + pictureId;
    }
}
