package com.yuwen.visionspace.job;

import com.yuwen.visionspace.service.PictureStatsAggregateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 图片行为聚合定时任务
 * 每 5 分钟执行一次，将 user_picture_action 聚合到 picture 表
 * 异步执行，不阻塞其他定时任务
 */
@Component
public class PictureStatsAggregateJob {

    @Resource
    private PictureStatsAggregateService pictureStatsAggregateService;

    /**
     * 周期性执行聚合任务。间隔由配置决定，隔离验收里把间隔调长，
     * 避免后台任务与验收用例争抢同一批事件。
     */
    @Scheduled(fixedRateString = "${vision-space.picture-stats.aggregate.interval-ms:300000}")
    public void aggregate() {
        pictureStatsAggregateService.aggregateActionStats();
    }
}
