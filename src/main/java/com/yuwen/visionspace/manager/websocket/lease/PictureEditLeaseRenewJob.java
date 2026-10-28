package com.yuwen.visionspace.manager.websocket.lease;

import com.yuwen.visionspace.manager.websocket.PictureEditHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;

/**
 * 租约续期兜底任务。
 *
 * <p>续期以「连接仍然打开」为前提，按固定节奏把所有仍在线持有者的租约
 * 延长到完整期限。断线、进程崩溃的持有者无法续期，租约按 TTL 自然过期，
 * 其他实例上的连接即可接管——这是跨实例故障转移的核心机制。
 */
@Component
@Slf4j
public class PictureEditLeaseRenewJob {

    @Resource
    private PictureEditLeaseManager leaseManager;

    @Resource
    private PictureEditHandler pictureEditHandler;

    /**
     * 每 10 秒续期一次（期限的 1/3 节奏，Redis TTL 在续期间隔内不会自然到期）。
     */
    @Scheduled(fixedDelayString = "${vision-space.websocket.lease-renew-interval-ms:10000}")
    public void renewActiveLeases() {
        pictureEditHandler.forEachActiveLease((pictureId, sessionId) -> {
            try {
                leaseManager.renew(pictureId, sessionId, pictureEditHandler.leaseTtl());
            } catch (Exception e) {
                log.warn("租约续期失败: pictureId={}", pictureId, e);
            }
        });
    }
}
