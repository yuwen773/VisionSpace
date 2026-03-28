package com.yuwen.visionspace.manager.job;

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
     * 每 5 分钟执行一次聚合任务
     * fixedRate = 300000ms = 5 分钟
     */
    @Scheduled(fixedRate = 300000)
    public void aggregate() {
        pictureStatsAggregateService.aggregateActionStats();
    }
}
