package com.yuwen.visionspace.manager.job;

import com.yuwen.visionspace.service.PictureRecommendService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 推荐缓存刷新定时任务
 * 每 5 分钟刷新一次推荐缓存
 */
@Component
public class RecommendCacheRefreshJob {

    @Resource
    private PictureRecommendService pictureRecommendService;

    /**
     * 每 5 分钟刷新推荐缓存
     */
    @Scheduled(fixedRate = 300000) // 5 * 60 * 1000 ms
    public void refreshRecommendCache() {
        pictureRecommendService.refreshRecommendCache();
    }
}
