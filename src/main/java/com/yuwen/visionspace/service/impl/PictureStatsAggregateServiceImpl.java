package com.yuwen.visionspace.service.impl;

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

    private static final int CTR_PRECISION = 6;

    @Resource
    private UserPictureActionMapper actionMapper;

    @Resource
    private PictureStatsService pictureStatsService;

    @Value("${vision-space.picture-stats.aggregate.batch-size:500}")
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
        // 1. 批量加载所有已存在的 PictureStats，避免 N+1 查询
        List<Long> pictureIds = statsList.stream()
                .map(PictureActionStatsDTO::getPictureId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        List<PictureStats> existingStats = pictureStatsService.listByIds(pictureIds);

        // 2. 构建 pictureId -> PictureStats 映射
        java.util.Map<Long, PictureStats> statsMap = existingStats.stream()
                .collect(java.util.stream.Collectors.toMap(PictureStats::getPictureId, s -> s));

        List<PictureStats> toSave = new ArrayList<>();
        for (PictureActionStatsDTO stats : statsList) {
            PictureStats pictureStats = statsMap.get(stats.getPictureId());
            if (pictureStats == null) {
                pictureStats = new PictureStats();
                pictureStats.setPictureId(stats.getPictureId());
                // 新增时设置初始值
                pictureStats.setViewCount(stats.getViewCount() != null ? stats.getViewCount() : 0L);
                pictureStats.setLikeCount(stats.getLikeCount() != null ? stats.getLikeCount() : 0L);
                pictureStats.setCollectCount(stats.getCollectCount() != null ? stats.getCollectCount() : 0L);
                pictureStats.setDownloadCount(stats.getDownloadCount() != null ? stats.getDownloadCount() : 0L);
                pictureStats.setShareCount(stats.getShareCount() != null ? stats.getShareCount() : 0L);
                pictureStats.setImpressionCount(stats.getImpressionCount() != null ? stats.getImpressionCount() : 0L);
                pictureStats.setClickCount(stats.getClickCount() != null ? stats.getClickCount() : 0L);
                statsMap.put(stats.getPictureId(), pictureStats);
            } else {
                // 累加到已有值
                pictureStats.setViewCount(pictureStats.getViewCount() + (stats.getViewCount() != null ? stats.getViewCount() : 0L));
                pictureStats.setLikeCount(pictureStats.getLikeCount() + (stats.getLikeCount() != null ? stats.getLikeCount() : 0L));
                pictureStats.setCollectCount(pictureStats.getCollectCount() + (stats.getCollectCount() != null ? stats.getCollectCount() : 0L));
                pictureStats.setDownloadCount(pictureStats.getDownloadCount() + (stats.getDownloadCount() != null ? stats.getDownloadCount() : 0L));
                pictureStats.setShareCount(pictureStats.getShareCount() + (stats.getShareCount() != null ? stats.getShareCount() : 0L));
                pictureStats.setImpressionCount(pictureStats.getImpressionCount() + (stats.getImpressionCount() != null ? stats.getImpressionCount() : 0L));
                pictureStats.setClickCount(pictureStats.getClickCount() + (stats.getClickCount() != null ? stats.getClickCount() : 0L));
            }

            // 计算 CTR
            Long impressionCount = pictureStats.getImpressionCount();
            Long clickCount = pictureStats.getClickCount();
            if (impressionCount != null && impressionCount > 0 && clickCount != null && clickCount > 0) {
                BigDecimal ctr = BigDecimal.valueOf(clickCount)
                        .divide(BigDecimal.valueOf(impressionCount), CTR_PRECISION, RoundingMode.HALF_UP);
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
