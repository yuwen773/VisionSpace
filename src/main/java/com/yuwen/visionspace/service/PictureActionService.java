package com.yuwen.visionspace.service;

public interface PictureActionService {

    /**
     * 上报用户行为
     */
    void reportAction(Long userId, Long pictureId, String actionType, String source);
}
