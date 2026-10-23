package com.yuwen.visionspace.manager.lock;

import com.yuwen.visionspace.mapper.FeedbackAttachmentMapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 资源行锁：让隔离、替换、删除对同一资源按同一顺序串行化。
 *
 * <p>锁来自数据库（SELECT ... FOR UPDATE），跨应用实例有效，不依赖进程内状态：
 * 多个实例同时操作同一资源时，先取到行锁的一方先完成破坏性动作并提交，
 * 后取锁的一方在拿到锁后按最新状态重新判定，不会出现
 * "隔离已经生效，但原内容仍被替换或删除"的窗口。
 *
 * <p>调用方必须在事务里使用：锁只在当前事务内有效，事务结束即释放。
 */
@Component
public class ResourceLockManager {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FeedbackAttachmentMapper feedbackAttachmentMapper;

    /**
     * 锁定资源行。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @return 资源是否存在（存在时行锁在当前事务内一直持有）
     */
    public boolean lockResourceRow(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null || resourceId <= 0) {
            return false;
        }
        if (ResourceTypeEnum.PICTURE.getValue().equals(resourceType)) {
            return pictureMapper.selectIdForUpdate(resourceId) != null;
        }
        if (ResourceTypeEnum.FEEDBACK_ATTACHMENT.getValue().equals(resourceType)) {
            return feedbackAttachmentMapper.selectIdForUpdate(resourceId) != null;
        }
        return false;
    }
}
