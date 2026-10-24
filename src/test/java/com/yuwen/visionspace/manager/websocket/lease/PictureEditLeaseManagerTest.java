package com.yuwen.visionspace.manager.websocket.lease;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编辑租约契约：绑定具体连接、跨实例共享（打真实隔离 Redis）、
 * 有期限与续期，竞争只产生一个有效持有者。
 */
class PictureEditLeaseManagerTest extends PictureEditLeaseTestBase {

    @Autowired
    private PictureEditLeaseManager leaseManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void competitionProducesSingleHolder() {
        long pictureId = uniquePictureId();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-A", 101L, leaseTtl()).acquired());
        var rejected = leaseManager.tryAcquire(pictureId, "session-B", 202L, leaseTtl());
        assertFalse(rejected.acquired());
        assertEquals(101L, leaseManager.holderUserId(pictureId).orElse(-1L));
        // 持有者释放后，竞争者才能拿到
        assertTrue(leaseManager.release(pictureId, "session-A"));
        assertTrue(leaseManager.tryAcquire(pictureId, "session-B", 202L, leaseTtl()).acquired());
    }

    @Test
    void closingAnotherTabDoesNotReleaseLease() {
        // 同一用户的两个标签页是两条连接：session-A1 持有租约，
        // session-A2 关闭时不能误释放。
        long pictureId = uniquePictureId();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-A1", 101L, leaseTtl()).acquired());
        assertFalse(leaseManager.release(pictureId, "session-A2"));
        assertEquals(101L, leaseManager.holderUserId(pictureId).orElse(-1L));
        // 其他用户更不能释放
        assertFalse(leaseManager.release(pictureId, "session-B"));
        assertEquals(101L, leaseManager.holderUserId(pictureId).orElse(-1L));
    }

    @Test
    void expiredLeaseAllowsTakeover() throws InterruptedException {
        long pictureId = uniquePictureId();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-A", 101L, shortTtl()).acquired());
        // 过期后（Redis TTL 自动淘汰），另一实例上的连接可以接管
        awaitLeaseExpiry();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-B", 202L, leaseTtl()).acquired());
        assertEquals(202L, leaseManager.holderUserId(pictureId).orElse(-1L));
        // 旧持有者的迟到释放不能夺回或误释放新租约
        assertFalse(leaseManager.release(pictureId, "session-A"));
        assertEquals(202L, leaseManager.holderUserId(pictureId).orElse(-1L));
    }

    @Test
    void renewalExtendsTtlOnlyForHolder() {
        long pictureId = uniquePictureId();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-A", 101L, shortTtl()).acquired());
        // 持有者续期成功
        assertTrue(leaseManager.renew(pictureId, "session-A", leaseTtl()));
        // 非持有者续期失败，且不破坏现有租约
        assertFalse(leaseManager.renew(pictureId, "session-B", leaseTtl()));
        assertEquals(101L, leaseManager.holderUserId(pictureId).orElse(-1L));
        // 租约 key 仍然存在（续期生效的表现）
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(leaseKey(pictureId))));
    }

    @Test
    void forceRevokeClearsLeaseForRevocation() {
        long pictureId = uniquePictureId();
        assertTrue(leaseManager.tryAcquire(pictureId, "session-A", 101L, leaseTtl()).acquired());
        leaseManager.forceRevoke(pictureId, "session-A");
        assertTrue(leaseManager.holderUserId(pictureId).isEmpty());
        // 撤权后原持有者不能再续期
        assertFalse(leaseManager.renew(pictureId, "session-A", leaseTtl()));
    }
}
