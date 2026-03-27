package com.yuwen.visionspace.service;

/**
 * 图片行为服务接口
 * 定义用户对图片行为的上报和处理能力
 *
 * @see PictureActionServiceImpl 实现类
 */
public interface PictureActionService {

    /**
     * 上报用户行为
     *
     * @param userId 用户ID，0表示匿名用户
     * @param pictureId 图片ID
     * @param actionType 行为类型：impression/click/view/like/collect/download/share
     * @param source 来源标识：HOME_RECOMMEND/DETAIL/SEARCH/OTHER
     */
    void reportAction(Long userId, Long pictureId, String actionType, String source);
}
