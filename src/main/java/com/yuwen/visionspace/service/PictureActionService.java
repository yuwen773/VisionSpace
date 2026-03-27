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
     * @param actionType 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share
     * @param source 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER
     */
    void reportAction(Long userId, Long pictureId, Integer actionType, Integer source);
}
