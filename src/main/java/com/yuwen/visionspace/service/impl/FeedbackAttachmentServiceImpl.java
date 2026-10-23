package com.yuwen.visionspace.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.FeedbackAccessPolicy;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.FeedbackAttachmentMapper;
import com.yuwen.visionspace.model.entity.FeedbackAttachment;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.FeedbackAttachmentStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.FeedbackAttachmentService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class FeedbackAttachmentServiceImpl extends ServiceImpl<FeedbackAttachmentMapper, FeedbackAttachment>
        implements FeedbackAttachmentService {

    static final String CONTENT_PATH = "/feedback/attachment/";

    @Resource
    private FeedbackAccessPolicy feedbackAccessPolicy;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Override
    public FeedbackAttachment registerUpload(User uploader, String platform, String objectKey, String url, Long size) {
        ThrowUtils.throwIf(uploader == null || uploader.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(platform) || StrUtil.isBlank(objectKey),
                ErrorCode.SYSTEM_ERROR, "上传结果缺少资源定位信息");
        FeedbackAttachment attachment = new FeedbackAttachment();
        attachment.setOwnerUserId(uploader.getId());
        attachment.setPlatform(platform);
        attachment.setObjectKey(objectKey);
        attachment.setUrl(url);
        attachment.setFileSize(size);
        attachment.setStatus(FeedbackAttachmentStatusEnum.UPLOADED.getValue());
        ThrowUtils.throwIf(!this.save(attachment), ErrorCode.OPERATION_ERROR, "附件记录保存失败");
        return attachment;
    }

    @Override
    public List<FeedbackAttachment> bindToFeedback(Long feedbackId, Long ownerUserId, Collection<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        ThrowUtils.throwIf(feedbackId == null || ownerUserId == null, ErrorCode.PARAMS_ERROR);
        List<FeedbackAttachment> attachments = this.listByIds(attachmentIds);
        ThrowUtils.throwIf(attachments.size() != attachmentIds.size(), ErrorCode.PARAMS_ERROR, "附件不存在");
        for (FeedbackAttachment attachment : attachments) {
            // 只接受本人上传、尚未绑定、归属可确认的附件；旧附件不能被认领
            ThrowUtils.throwIf(!Objects.equals(ownerUserId, attachment.getOwnerUserId()),
                    ErrorCode.NO_AUTH_ERROR, "附件不属于当前用户");
            ThrowUtils.throwIf(!Objects.equals(attachment.getStatus(), FeedbackAttachmentStatusEnum.UPLOADED.getValue()),
                    ErrorCode.OPERATION_ERROR, "附件已绑定或不可用");
            ThrowUtils.throwIf(attachment.getFeedbackId() != null, ErrorCode.OPERATION_ERROR, "附件已绑定");
        }
        for (FeedbackAttachment attachment : attachments) {
            FeedbackAttachment update = new FeedbackAttachment();
            update.setId(attachment.getId());
            update.setFeedbackId(feedbackId);
            update.setStatus(FeedbackAttachmentStatusEnum.BOUND.getValue());
            ThrowUtils.throwIf(!this.updateById(update), ErrorCode.OPERATION_ERROR, "附件绑定失败");
            attachment.setFeedbackId(feedbackId);
            attachment.setStatus(FeedbackAttachmentStatusEnum.BOUND.getValue());
        }
        return attachments;
    }

    @Override
    public List<FeedbackAttachment> listByFeedback(Long feedbackId) {
        if (feedbackId == null) {
            return List.of();
        }
        return this.lambdaQuery()
                .eq(FeedbackAttachment::getFeedbackId, feedbackId)
                .orderByAsc(FeedbackAttachment::getId)
                .list();
    }

    @Override
    public Map<Long, List<FeedbackAttachment>> listByFeedbacks(Collection<Long> feedbackIds) {
        if (feedbackIds == null || feedbackIds.isEmpty()) {
            return Map.of();
        }
        List<FeedbackAttachment> attachments = this.lambdaQuery()
                .in(FeedbackAttachment::getFeedbackId, feedbackIds)
                .orderByAsc(FeedbackAttachment::getId)
                .list();
        Map<Long, List<FeedbackAttachment>> result = new HashMap<>();
        for (FeedbackAttachment attachment : attachments) {
            if (attachment.getFeedbackId() == null) {
                continue;
            }
            result.computeIfAbsent(attachment.getFeedbackId(), key -> new java.util.ArrayList<>()).add(attachment);
        }
        return result;
    }

    @Override
    public AttachmentContent loadContent(Long attachmentId, User viewer) {
        ThrowUtils.throwIf(attachmentId == null || attachmentId <= 0, ErrorCode.PARAMS_ERROR);
        FeedbackAttachment attachment = this.getById(attachmentId);
        ThrowUtils.throwIf(attachment == null, ErrorCode.NOT_FOUND_ERROR, "附件不存在");
        // 每次新请求都按当前反馈权限与隔离状态判定
        ThrowUtils.throwIf(!canRead(attachment, viewer), ErrorCode.NO_AUTH_ERROR, "无权读取该附件");
        byte[] bytes = pictureStorageService.readObject(attachment.getPlatform(), attachment.getObjectKey());
        return new AttachmentContent(bytes,
                PictureStorageService.contentTypeOf(attachment.getObjectKey()),
                downloadName(attachment));
    }

    @Override
    public boolean canRead(FeedbackAttachment attachment, User viewer) {
        if (attachment == null || viewer == null || viewer.getId() == null) {
            return false;
        }
        Integer status = attachment.getStatus();
        // 归属未确认的旧附件不提供读取入口
        if (!Objects.equals(status, FeedbackAttachmentStatusEnum.UPLOADED.getValue())
                && !Objects.equals(status, FeedbackAttachmentStatusEnum.BOUND.getValue())
                && !Objects.equals(status, FeedbackAttachmentStatusEnum.LEGACY_VERIFIED.getValue())) {
            return false;
        }
        if (resourceIsolationService.isQuarantined(
                ResourceTypeEnum.FEEDBACK_ATTACHMENT.getValue(), attachment.getId())) {
            return false;
        }
        if (attachment.getFeedbackId() == null) {
            // 尚未随反馈提交：只有上传者本人可以读取
            return canReadFeedback(null, attachment.getOwnerUserId(), viewer);
        }
        return canReadFeedback(attachment.getFeedbackId(), attachment.getOwnerUserId(), viewer);
    }

    @Override
    public boolean canReadFeedback(Long feedbackId, Long ownerUserId, User viewer) {
        return feedbackAccessPolicy.canReadAttachment(feedbackId, ownerUserId, viewer);
    }

    @Override
    public String contentUrl(FeedbackAttachment attachment, User viewer) {
        if (!canRead(attachment, viewer)) {
            return null;
        }
        return CONTENT_PATH + attachment.getId();
    }

    /**
     * 附件的原始记录只用于服务端读取；对外一律使用受控入口。
     */
    private String downloadName(FeedbackAttachment attachment) {
        String name = FileUtil.getName(attachment.getObjectKey());
        return StrUtil.isBlank(name) ? "attachment" : name;
    }
}
