package com.yuwen.visionspace.service;

/**
 * 图片行为聚合服务
 * 负责将 user_picture_action 明细数据聚合到 picture 表
 */
public interface PictureStatsAggregateService {

    /**
     * 执行聚合任务
     * 扫描 user_picture_action，按 pictureId 分组统计，更新 picture 表
     */
    void aggregateActionStats();
}
