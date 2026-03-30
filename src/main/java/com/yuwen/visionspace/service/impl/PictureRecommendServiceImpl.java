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
import cn.hutool.json.JSONUtil;
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
        List<Picture> pictures = calculateRecommendPictures(type);

        // 3. 分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, pictures.size());
        if (start >= pictures.size()) {
            return Collections.emptyList();
        }
        List<Long> result = pictures.subList(start, end).stream()
                .map(Picture::getId)
                .collect(Collectors.toList());

        // 4. 写入缓存
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

        // 转换为 VO
        List<RecommendPictureVO> records = pictures.stream()
                .map(this::toRecommendPictureVO)
                .collect(Collectors.toList());

        // 计算总数
        Long total = getRecommendTotal(type);

        return new RecommendPageVO(records, total);
    }

    /**
     * 转换 Picture 为 RecommendPictureVO
     */
    private RecommendPictureVO toRecommendPictureVO(Picture picture) {
        RecommendPictureVO vo = new RecommendPictureVO();
        vo.setId(picture.getId());
        vo.setThumbnailUrl(picture.getThumbnailUrl());
        vo.setName(picture.getName());
        vo.setPicWidth(picture.getPicWidth());
        vo.setPicHeight(picture.getPicHeight());
        vo.setPicScale(picture.getPicScale());
        vo.setPicColor(picture.getPicColor());
        vo.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return vo;
    }

    /**
     * 获取推荐总数
     */
    private Long getRecommendTotal(String type) {
        List<Picture> pictures = calculateRecommendPictures(type);
        return (long) pictures.size();
    }

    @Override
    public void refreshRecommendCache() {
        cacheManager.invalidateRecommendCache();
    }

    /**
     * 根据类型计算推荐图片列表
     */
    private List<Picture> calculateRecommendPictures(String type) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Picture::getReviewStatus, 1); // 仅返回已审核图片

        List<Picture> pictures;

        switch (type) {
            case "hot":
                // 热门: 按评分排序
                pictures = pictureMapper.selectList(queryWrapper);
                pictures = sortByScore(pictures);
                break;

            case "latest":
                // 最新: 按创建时间倒序
                queryWrapper.orderByDesc("createTime");
                pictures = pictureMapper.selectList(queryWrapper);
                break;

            case "quality":
                // 优质: 筛选高质量图片 (宽高乘积 > 200万)
                queryWrapper.apply("picWidth * picHeight > 2000000");
                queryWrapper.orderByDesc("picWidth", "picHeight");
                pictures = pictureMapper.selectList(queryWrapper);
                break;

            case "random":
                // 随机探索: 随机抽取
                queryWrapper.last("ORDER BY RAND() LIMIT 100");
                pictures = pictureMapper.selectList(queryWrapper);
                break;

            default:
                pictures = Collections.emptyList();
        }

        // 重排打散
        pictures = PictureReorderUtils.reorderByAuthor(
            pictures,
            reorderConfig.getAuthorWindowSize(),
            reorderConfig.getAuthorMaxCount()
        );
        pictures = PictureReorderUtils.reorderByCategory(
            pictures,
            reorderConfig.getCategoryMaxConsecutive()
        );

        return pictures;
    }

    /**
     * 按推荐评分排序
     */
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
}