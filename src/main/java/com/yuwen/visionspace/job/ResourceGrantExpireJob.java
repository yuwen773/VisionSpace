package com.yuwen.visionspace.job;

import com.yuwen.visionspace.service.ResourceAccessGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 授权到期迁移定时任务
 *
 * <p>每 5 分钟把已到期的授权落库为 EXPIRED 并释放唯一键：
 * 只改展示状态的话，同一资源与被授权人无法再次授权。
 */
@Slf4j
@Component
public class ResourceGrantExpireJob {

    @Resource
    private ResourceAccessGrantService resourceAccessGrantService;

    /**
     * 每 5 分钟执行一次到期迁移
     * fixedRate = 300000ms = 5 分钟
     */
    @Scheduled(fixedRate = 300000)
    public void expire() {
        try {
            resourceAccessGrantService.expireOutdatedGrants();
        } catch (RuntimeException e) {
            // 到期迁移失败不影响其他定时任务，下一轮继续
            log.warn("授权到期迁移失败: {}", e.getMessage());
        }
    }
}
