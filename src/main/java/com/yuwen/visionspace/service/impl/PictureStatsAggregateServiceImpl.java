package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.mapper.PictureStatsMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.service.PictureStatsAggregateService;
import com.yuwen.visionspace.service.PictureStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PictureStatsAggregateServiceImpl implements PictureStatsAggregateService {

    @Resource
    private UserPictureActionMapper actionMapper;

    @Resource
    private PictureStatsMapper pictureStatsMapper;

    @Resource
    private PictureStatsService pictureStatsService;

    @Value("${picture-stats.aggregate.batch-size:500}")
    private int batchSize;

    private LocalDateTime lastRunTime;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateActionStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = (lastRunTime != null) ? lastRunTime : now.minusMinutes(5);

        log.info("开始执行图片行为聚合任务，查询时间范围: {} ~ {}", startTime, now);

        // 1. 增量查询待聚合的 action 记录
        List<PictureActionStatsDTO> statsList = actionMapper.selectStatsByTimeRange(startTime, now);
        if (statsList == null || statsList.isEmpty()) {
            log.info("未发现待聚合的行为数据");
            lastRunTime = now;
            return;
        }

        log.info("发现 {} 条待聚合的行为数据", statsList.size());

        // 2. 分批 UPSERT 到 picture_stats
        int totalUpdated = 0;
        for (int i = 0; i < statsList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, statsList.size());
            List<PictureActionStatsDTO> batch = statsList.subList(i, end);
            int updated = upsertBatch(batch);
            totalUpdated += updated;
        }

        // 3. 标记已处理
        actionMapper.markAsProcessed(startTime, now);

        // 4. 清理7天前数据
        cleanOldActions();

        lastRunTime = now;
        log.info("图片行为聚合任务完成，共更新 {} 张图片", totalUpdated);
    }

    private int upsertBatch(List<PictureActionStatsDTO> statsList) {
        List<PictureStats> toSave = new ArrayList<>();
        for (PictureActionStatsDTO stats : statsList) {
            PictureStats pictureStats = pictureStatsService.getByPictureId(stats.getPictureId());
            if (pictureStats == null) {
                pictureStats = new PictureStats();
                pictureStats.setPictureId(stats.getPictureId());
            }

            // 更新统计字段
            pictureStats.setViewCount(stats.getViewCount() != null ? stats.getViewCount() : 0L);
            pictureStats.setLikeCount(stats.getLikeCount() != null ? stats.getLikeCount() : 0L);
            pictureStats.setCollectCount(stats.getCollectCount() != null ? stats.getCollectCount() : 0L);
            pictureStats.setDownloadCount(stats.getDownloadCount() != null ? stats.getDownloadCount() : 0L);
            pictureStats.setShareCount(stats.getShareCount() != null ? stats.getShareCount() : 0L);
            pictureStats.setImpressionCount(stats.getImpressionCount() != null ? stats.getImpressionCount() : 0L);
            pictureStats.setClickCount(stats.getClickCount() != null ? stats.getClickCount() : 0L);

            // 计算 CTR
            if (stats.getImpressionCount() != null && stats.getImpressionCount() > 0
                    && stats.getClickCount() != null && stats.getClickCount() > 0) {
                BigDecimal ctr = BigDecimal.valueOf(stats.getClickCount())
                        .divide(BigDecimal.valueOf(stats.getImpressionCount()), 6, RoundingMode.HALF_UP);
                pictureStats.setCtr(ctr);
            } else {
                pictureStats.setCtr(BigDecimal.ZERO);
            }

            toSave.add(pictureStats);
        }

        pictureStatsService.saveOrUpdateBatch(toSave);
        return toSave.size();
    }

    private void cleanOldActions() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = actionMapper.deleteOldActions(threshold);
        if (deleted > 0) {
            log.info("清理了 {} 条7天前的 action 数据", deleted);
        }
    }
}
