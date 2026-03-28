package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PictureStatsAggregateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片行为聚合服务实现
 */
@Slf4j
@Service
public class PictureStatsAggregateServiceImpl implements PictureStatsAggregateService {

    @Resource
    private UserPictureActionMapper actionMapper;

    @Resource
    private PictureMapper pictureMapper;

    private static final int BATCH_SIZE = 100;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateActionStats() {
        log.info("开始执行图片行为聚合任务");

        // 1. 获取所有有行为记录的图片ID
        List<Long> pictureIds = actionMapper.selectPictureIdsWithActions();
        if (pictureIds == null || pictureIds.isEmpty()) {
            log.info("没有发现待聚合的行为数据");
            return;
        }

        log.info("发现 {} 张图片有待聚合的行为数据", pictureIds.size());

        // 2. 分批处理，避免一次查询太多
        int totalUpdated = 0;
        for (int i = 0; i < pictureIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, pictureIds.size());
            List<Long> batchIds = pictureIds.subList(i, end);

            int updated = processBatch(batchIds);
            totalUpdated += updated;
        }

        log.info("图片行为聚合任务完成，共更新 {} 张图片", totalUpdated);
    }

    /**
     * 处理一批图片ID
     */
    private int processBatch(List<Long> pictureIds) {
        // 查询这批图片的聚合数据（只查当前批次）
        List<PictureActionStatsDTO> statsList = actionMapper.aggregateByPictureId(pictureIds);

        if (statsList == null || statsList.isEmpty()) {
            return 0;
        }

        // 转换为 Map，key 为 pictureId
        Map<Long, PictureActionStatsDTO> statsMap = new HashMap<>();
        for (PictureActionStatsDTO stats : statsList) {
            statsMap.put(stats.getPictureId(), stats);
        }

        // 查询图片并更新
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", pictureIds);
        List<Picture> pictures = pictureMapper.selectList(queryWrapper);

        int updateCount = 0;
        for (Picture picture : pictures) {
            PictureActionStatsDTO stats = statsMap.get(picture.getId());
            if (stats == null) {
                continue;
            }

            // 更新计数
            picture.setViewCount(stats.getViewCount() != null ? stats.getViewCount() : 0L);
            picture.setLikeCount(stats.getLikeCount() != null ? stats.getLikeCount() : 0L);
            picture.setCollectCount(stats.getCollectCount() != null ? stats.getCollectCount() : 0L);
            picture.setDownloadCount(stats.getDownloadCount() != null ? stats.getDownloadCount() : 0L);
            picture.setShareCount(stats.getShareCount() != null ? stats.getShareCount() : 0L);
            picture.setImpressionCount(stats.getImpressionCount() != null ? stats.getImpressionCount() : 0L);

            // 计算 CTR = clickCount / impressionCount
            if (stats.getImpressionCount() != null && stats.getImpressionCount() > 0
                    && stats.getClickCount() != null && stats.getClickCount() > 0) {
                BigDecimal ctr = BigDecimal.valueOf(stats.getClickCount())
                        .divide(BigDecimal.valueOf(stats.getImpressionCount()), 6, RoundingMode.HALF_UP);
                picture.setCtr(ctr);
            } else {
                picture.setCtr(BigDecimal.ZERO);
            }

            pictureMapper.updateById(picture);
            updateCount++;
        }

        log.debug("批次更新了 {} 张图片的统计数据", updateCount);
        return updateCount;
    }
}
