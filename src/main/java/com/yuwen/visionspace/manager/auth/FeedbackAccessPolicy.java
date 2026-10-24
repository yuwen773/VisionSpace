package com.yuwen.visionspace.manager.auth;

import com.yuwen.visionspace.mapper.UserFeedbackMapper;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Objects;

/**
 * 反馈可见性判定。
 *
 * <p>反馈正文与附件都属于私人内容：只有创建者本人与具有反馈管理职责的平台管理员可读。
 * 判定集中在这里，控制器与附件服务共用同一份规则，避免各处复制后漂移。
 */
@Slf4j
@Component
public class FeedbackAccessPolicy {

    @Resource
    private UserService userService;

    @Resource
    private UserFeedbackMapper userFeedbackMapper;

    /**
     * 反馈可见性：创建者本人，或具有反馈管理职责的平台管理员。
     *
     * @param feedback 反馈记录
     * @param viewer   当前登录用户，匿名请求为 null
     * @return 是否允许读取
     */
    public boolean canReadFeedback(UserFeedback feedback, User viewer) {
        if (feedback == null || viewer == null || viewer.getId() == null) {
            return false;
        }
        if (Objects.equals(feedback.getUserId(), viewer.getId())) {
            return true;
        }
        // 反馈管理职责：平台管理员角色
        return userService.isAdmin(viewer);
    }

    /**
     * 附件可见性：随反馈提交后按该反馈的可见性判定；尚未绑定反馈时只有上传者本人可读。
     * 平台管理员只有在反馈记录仍然存在时才能读到附件。
     *
     * @param feedbackId  附件绑定的反馈标识，未绑定时为 null
     * @param ownerUserId 附件上传者
     * @param viewer      当前登录用户，匿名请求为 null
     * @return 是否允许读取
     */
    public boolean canReadAttachment(Long feedbackId, Long ownerUserId, User viewer) {
        if (viewer == null || viewer.getId() == null) {
            return false;
        }
        if (feedbackId == null) {
            // 尚未随反馈提交：只有上传者本人可以读取
            return Objects.equals(ownerUserId, viewer.getId());
        }
        if (Objects.equals(ownerUserId, viewer.getId())) {
            return true;
        }
        if (!userService.isAdmin(viewer)) {
            return false;
        }
        return userFeedbackMapper.selectById(feedbackId) != null;
    }
}
