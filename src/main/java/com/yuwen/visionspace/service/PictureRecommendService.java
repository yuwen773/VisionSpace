package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.vo.RecommendPageVO;

import java.util.List;

public interface PictureRecommendService {

    /**
     * 获取推荐图片ID列表
     * @param type 推荐类型: hot, latest, quality, random
     * @param page 页码
     * @param size 每页数量
     * @return 图片ID列表
     */
    List<Long> getRecommendPictureIds(String type, int page, int size);

    /**
     * 获取推荐图片分页列表
     * @param type 推荐类型: hot, latest, quality, random
     * @param page 页码
     * @param size 每页数量
     * @return 推荐分页结果
     */
    RecommendPageVO getRecommendPictures(String type, int page, int size);

    /**
     * 刷新推荐缓存
     */
    void refreshRecommendCache();
}