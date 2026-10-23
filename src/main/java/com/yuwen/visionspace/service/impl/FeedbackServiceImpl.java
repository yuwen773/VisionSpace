package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.mapper.UserFeedbackMapper;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.FeedbackAttachment;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.enums.FeedbackAttachmentStatusEnum;
import com.yuwen.visionspace.model.vo.FeedbackAttachmentVO;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import com.yuwen.visionspace.service.FeedbackAttachmentService;
import com.yuwen.visionspace.service.FeedbackService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    /** 单条反馈最多允许的附件数量，与前端提示一致 */
    static final int MAX_ATTACHMENTS = 5;

    @Resource
    private UserFeedbackMapper feedbackMapper;

    @Resource
    private UserService userService;

    @Resource
    private FeedbackAttachmentService feedbackAttachmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addFeedback(FeedbackAddRequest request, Long userId) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getType() == null || request.getTitle() == null || request.getContent() == null,
                ErrorCode.PARAMS_ERROR, "参数不完整");
        ThrowUtils.throwIf(request.getTitle().length() > 50, ErrorCode.PARAMS_ERROR, "标题过长");
        ThrowUtils.throwIf(request.getContent().length() > 2000, ErrorCode.PARAMS_ERROR, "内容过长");
        List<Long> attachmentIds = request.getAttachmentIds() == null
                ? List.of() : request.getAttachmentIds();
        ThrowUtils.throwIf(attachmentIds.size() > MAX_ATTACHMENTS, ErrorCode.PARAMS_ERROR, "附件数量超出限制");
        // 客户端提交的地址不再作为附件依据：只接受服务端登记过的附件标识
        ThrowUtils.throwIf(request.getPictureUrls() != null && !request.getPictureUrls().isEmpty(),
                ErrorCode.PARAMS_ERROR, "附件必须通过上传接口登记");

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setType(request.getType());
        feedback.setTitle(request.getTitle());
        feedback.setContent(request.getContent());
        feedback.setStatus(0); // 默认待处理
        feedback.setCreateTime(new Date());

        boolean result = feedbackMapper.insert(feedback) > 0;
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        List<FeedbackAttachment> bound = feedbackAttachmentService.bindToFeedback(
                feedback.getId(), userId, attachmentIds);
        if (!bound.isEmpty()) {
            // 兼容既有列：只保留已确认归属的附件标识，不再保存可直连的地址
            UserFeedback update = new UserFeedback();
            update.setId(feedback.getId());
            update.setPictureUrls(attachmentRefs(bound));
            feedbackMapper.updateById(update);
        }
        return feedback.getId();
    }

    @Override
    public FeedbackVO getFeedbackById(Long id) {
        return getFeedbackVO(getRequiredFeedback(id), null);
    }

    @Override
    public UserFeedback getRequiredFeedback(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        UserFeedback feedback = feedbackMapper.selectById(id);
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);
        return feedback;
    }

    @Override
    public Page<FeedbackVO> listFeedbackByPage(FeedbackQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long pageSize = queryRequest.getPageSize();
        Page<UserFeedback> page = new Page<>(current, pageSize);

        QueryWrapper<UserFeedback> queryWrapper = new QueryWrapper<>();
        if (queryRequest.getType() != null) {
            queryWrapper.eq("type", queryRequest.getType());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq("status", queryRequest.getStatus());
        }
        queryWrapper.orderByDesc("createTime");

        Page<UserFeedback> feedbackPage = feedbackMapper.selectPage(page, queryWrapper);
        Page<FeedbackVO> voPage = new Page<>(current, pageSize, feedbackPage.getTotal());

        List<Long> feedbackIds = feedbackPage.getRecords().stream().map(UserFeedback::getId).toList();
        Map<Long, List<FeedbackAttachment>> attachments =
                feedbackAttachmentService.listByFeedbacks(feedbackIds);
        // 列表只返回附件数量信息所需的最小字段，不返回受控入口
        List<FeedbackVO> records = new ArrayList<>();
        for (UserFeedback feedback : feedbackPage.getRecords()) {
            records.add(toVO(feedback, attachments.getOrDefault(feedback.getId(), List.of()), null, false));
        }
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public boolean updateFeedbackStatus(FeedbackUpdateStatusRequest request, Long handlerId) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        UserFeedback feedback = feedbackMapper.selectById(request.getId());
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);

        feedback.setStatus(request.getStatus());
        feedback.setHandlerId(handlerId);
        feedback.setHandlerNote(request.getHandlerNote());
        if (request.getStatus() != null && request.getStatus() == 2) {
            feedback.setHandleTime(new Date());
        }
        return feedbackMapper.updateById(feedback) > 0;
    }

    @Override
    public FeedbackVO getFeedbackVO(UserFeedback feedback, User viewer) {
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);
        return toVO(feedback, feedbackAttachmentService.listByFeedback(feedback.getId()), viewer, true);
    }

    private FeedbackVO toVO(UserFeedback feedback, List<FeedbackAttachment> attachments,
                            User viewer, boolean includeContentUrls) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(feedback.getId());
        vo.setUserId(feedback.getUserId());
        vo.setType(feedback.getType());
        vo.setTitle(feedback.getTitle());
        vo.setContent(feedback.getContent());
        vo.setStatus(feedback.getStatus());
        vo.setHandlerId(feedback.getHandlerId());
        vo.setHandlerNote(feedback.getHandlerNote());
        vo.setHandleTime(feedback.getHandleTime());
        vo.setCreateTime(feedback.getCreateTime());

        // 最小化用户信息：仅名称，不返回账号、头像、角色等资料
        vo.setUserName(displayName(feedback.getUserId()));
        vo.setHandlerName(displayName(feedback.getHandlerId()));

        List<FeedbackAttachmentVO> attachmentVOs = new ArrayList<>();
        for (FeedbackAttachment attachment : attachments) {
            FeedbackAttachmentVO attachmentVO = new FeedbackAttachmentVO();
            attachmentVO.setId(attachment.getId());
            attachmentVO.setStatus(attachmentStatus(attachment));
            if (includeContentUrls) {
                attachmentVO.setUrl(feedbackAttachmentService.contentUrl(attachment, viewer));
            }
            attachmentVOs.add(attachmentVO);
        }
        vo.setAttachments(attachmentVOs);
        return vo;
    }

    private String attachmentStatus(FeedbackAttachment attachment) {
        FeedbackAttachmentStatusEnum status =
                FeedbackAttachmentStatusEnum.getEnumByValue(attachment.getStatus());
        return status == null ? "UNKNOWN" : status.name();
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userService.getById(userId);
        return user == null ? null : user.getUserName();
    }

    /**
     * 兼容列的新写法：只保存附件标识，避免历史列继续携带可直连的地址。
     */
    private String attachmentRefs(List<FeedbackAttachment> attachments) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < attachments.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("\"attachment:").append(attachments.get(i).getId()).append('"');
        }
        return builder.append(']').toString();
    }

    /**
     * 判断查看者对反馈是否可读：创建者本人，或具有反馈管理职责的平台管理员。
     */
    public static boolean canReadFeedback(UserFeedback feedback, User viewer, UserService userService) {
        if (feedback == null || viewer == null || viewer.getId() == null) {
            return false;
        }
        if (Objects.equals(feedback.getUserId(), viewer.getId())) {
            return true;
        }
        return userService.isAdmin(viewer);
    }
}
