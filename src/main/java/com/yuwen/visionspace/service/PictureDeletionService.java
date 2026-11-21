package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.User;

/**
 * 图片删除生命周期：行锁、隔离复核、额度释放与对象清理。
 *
 * <p>删除的事务边界与补偿顺序留在这个模块内部；调用方只提供目标图片
 * 与当前用户。
 */
public interface PictureDeletionService {

    /**
     * 删除图片。
     *
     * @param pictureId 图片标识
     * @param loginUser 当前登录用户
     */
    void deletePicture(long pictureId, User loginUser);
}
