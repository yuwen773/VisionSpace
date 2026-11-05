package com.yuwen.visionspace.manager.websocket.lease;

import com.yuwen.visionspace.manager.lock.ResourceLockManager;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** 把接管租约与图片数据库写入按同一行锁排序。 */
@Service
public class PictureEditLeaseCoordinator {
    @Resource
    private ResourceLockManager resourceLockManager;
    @Resource
    private PictureEditLeaseManager leaseManager;

    @Transactional(rollbackFor = Exception.class)
    public PictureEditLeaseManager.LeaseAcquireResult acquire(long pictureId, String sessionId, long userId,
                                                               Duration ttl, String token) {
        if (!resourceLockManager.lockResourceRow(ResourceTypeEnum.PICTURE.getValue(), pictureId)) {
            throw new IllegalStateException("图片不存在");
        }
        return leaseManager.tryAcquireWithToken(pictureId, sessionId, userId, ttl, token);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean release(long pictureId, String sessionId) {
        resourceLockManager.lockResourceRow(ResourceTypeEnum.PICTURE.getValue(), pictureId);
        return leaseManager.release(pictureId, sessionId);
    }
}
