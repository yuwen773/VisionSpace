package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.service.PictureActionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class PictureActionServiceImpl implements PictureActionService {

    @Resource
    private UserPictureActionMapper actionMapper;

    @Override
    public void reportAction(Long userId, Long pictureId, String actionType, String source) {
        UserPictureAction action = new UserPictureAction();
        action.setUserId(userId);
        action.setPictureId(pictureId);
        action.setActionType(actionType);
        action.setActionValue(1);
        action.setSource(source);
        actionMapper.insert(action);
    }
}
