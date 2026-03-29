package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.service.PictureActionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 图片行为服务实现类
 * 负责将用户行为数据写入数据库，用于后续的推荐系统计算
 *
 * @see PictureActionService 接口定义
 */
@Slf4j
@Service
public class PictureActionServiceImpl implements PictureActionService {

    @Resource
    private UserPictureActionMapper actionMapper;

    /**
     * 上报用户行为
     * 将行为数据写入 user_picture_action 表
     *
     * @param userId 用户ID，0表示匿名用户
     * @param pictureId 图片ID
     * @param actionType 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share
     * @param source 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER
     */
    @Override
    public void reportAction(Long userId, Long pictureId, Integer actionType, Integer source) {
        UserPictureAction action = new UserPictureAction();
        action.setUserId(userId);
        action.setPictureId(pictureId);
        action.setActionType(actionType);
        action.setActionValue(1);
        action.setSource(source);
        actionMapper.insert(action);
        log.debug("记录用户行为: userId={}, pictureId={}, actionType={}, source={}",
                userId, pictureId, actionType, source);
    }
}
