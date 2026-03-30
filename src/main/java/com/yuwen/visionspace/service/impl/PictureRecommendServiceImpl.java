package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.config.ReorderConfig;
import com.yuwen.visionspace.manager.cache.RecommendCacheManager;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.vo.RecommendPageVO;
import com.yuwen.visionspace.model.vo.RecommendPictureVO;
import com.yuwen.visionspace.service.PictureRecommendService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.utils.HomeRecommendScoreCalculator;
import com.yuwen.visionspace.utils.PictureReorderUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PictureRecommendServiceImpl implements PictureRecommendService {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private HomeRecommendScoreCalculator scoreCalculator;

    @Resource
    private RecommendCacheManager cacheManager;

    @Resource
    private ReorderConfig reorderConfig;

    @Resource
    private PictureStatsService pictureStatsService;

    @Override
    public List<Long> getRecommendPictureIds(String type, int page, int size) {
        // 1. 尝试从缓存获取
        List<Long> cached = cacheManager.getCachedRecommendList(type, page, size);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 2. 缓存未命中, 计算推荐结果
        List<Picture> pictures = calculateRecommendPictures(type, page, size);

        // 3. 缓存总数（仅第一页时计算并缓存）
        if (page == 1) {
            Long cachedTotal = cacheManager.getCachedTotal(type);
            if (cachedTotal == null) {
                long total = countRecommendPictures(type);
                cacheManager.setCachedTotal(type, total);
            }
        }

        // 4. 提取 ID
        List<Long> result = pictures.stream()
                .map(Picture::getId)
                .collect(Collectors.toList());

        // 5. 写入缓存
        cacheManager.setCachedRecommendList(type, page, size, result);

        return result;
    }

    @Override
    public RecommendPageVO getRecommendPictures(String type, int page, int size) {
        List<Long> ids = getRecommendPictureIds(type, page, size);
        if (ids.isEmpty()) {
            return new RecommendPageVO(Collections.emptyList(), 0L);
        }

        // 查询图片详情
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().in(Picture::getId, ids);
        List<Picture> pictures = pictureMapper.selectList(queryWrapper);

        // 按ID顺序排序
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            orderMap.put(ids.get(i), i);
        }
        pictures.sort(Comparator.comparingInt(p -> orderMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));

        List<RecommendPictureVO> records = pictures.stream()
                .map(RecommendPictureVO::objToVo)
                .collect(Collectors.toList());

        Long total = getRecommendTotal(type);

        return new RecommendPageVO(records, total);
    }

    private Long getRecommendTotal(String type) {
        Long cachedTotal = cacheManager.getCachedTotal(type);
        if (cachedTotal != null) {
            return cachedTotal;
        }
        long total = countRecommendPictures(type);
        cacheManager.setCachedTotal(type, total);
        return total;
    }

    private long countRecommendPictures(String type) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Picture::getReviewStatus, 1);

        switch (type) {
            case "latest":
                return pictureMapper.selectCount(queryWrapper);

            case "quality":
                queryWrapper.apply("picWidth * picHeight > 2000000");
                return pictureMapper.selectCount(queryWrapper);

            case "hot":
                // 热门需要全量计算才能排序，返回实际图片数
                List<Picture> pictures = pictureMapper.selectList(queryWrapper);
                return (long) pictures.size();

            case "random":
                // 随机固定返回 100
                return 100;

            default:
                return 0;
        }
    }

    /**
     * 根据类型计算推荐图片列表
     * - latest/quality: 数据库分页，无重排
     * - hot/random: 全量计算+重排，内存分页
     */
    private List<Picture> calculateRecommendPictures(String type, int page, int size) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Picture::getReviewStatus, 1);

        List<Picture> pictures;
        boolean useDbPagination = false;

        switch (type) {
            case "latest":
                // 最新: 直接数据库分页
                queryWrapper.orderByDesc("createTime");
                useDbPagination = true;
                break;

            case "quality":
                // 优质: 数据库筛选 + 分页
                queryWrapper.apply("picWidth * picHeight > 2000000");
                queryWrapper.orderByDesc("picWidth", "picHeight");
                useDbPagination = true;
                break;

            case "hot":
                // 热门: 全量评分排序
                pictures = pictureMapper.selectList(queryWrapper);
                pictures = sortByScore(pictures);
                pictures = reorderPictures(pictures);
                return pictures;

            case "random":
                // 随机: 已限制数量
                queryWrapper.last("ORDER BY RAND() LIMIT 100");
                pictures = pictureMapper.selectList(queryWrapper);
                pictures = reorderPictures(pictures);
                return pictures;

            default:
                return Collections.emptyList();
        }

        if (useDbPagination) {
            // 数据库分页
            int offset = (page - 1) * size;
            queryWrapper.last("LIMIT " + offset + ", " + size);
            pictures = pictureMapper.selectList(queryWrapper);
        } else {
            pictures = Collections.emptyList();
        }

        return pictures;
    }

    private List<Picture> reorderPictures(List<Picture> pictures) {
        pictures = PictureReorderUtils.reorderByAuthor(
            pictures,
            reorderConfig.getAuthorWindowSize(),
            reorderConfig.getAuthorMaxCount()
        );
        return PictureReorderUtils.reorderByCategory(
            pictures,
            reorderConfig.getCategoryMaxConsecutive()
        );
    }

    private List<Picture> sortByScore(List<Picture> pictures) {
        if (pictures == null || pictures.isEmpty()) {
            return pictures;
        }

        // 批量获取统计数据
        List<Long> pictureIds = pictures.stream().map(Picture::getId).collect(Collectors.toList());
        List<PictureStats> statsList = pictureStatsService.listByIds(pictureIds);
        Map<Long, PictureStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(PictureStats::getPictureId, s -> s));

        // 按评分排序
        pictures.sort((a, b) -> {
            PictureStats statsA = statsMap.get(a.getId());
            PictureStats statsB = statsMap.get(b.getId());
            return Double.compare(scoreCalculator.calculateScore(b, statsB), scoreCalculator.calculateScore(a, statsA));
        });

        return pictures;
    }

    @Override
    public void refreshRecommendCache() {
        cacheManager.invalidateRecommendCache();
    }
}