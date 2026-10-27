package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.FeedbackAttachment;
import com.yuwen.visionspace.model.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 反馈附件的可信记录与受控读取。
 *
 * <p>附件不再以原始地址交给浏览器：上传返回附件标识，读取时按当前反馈权限判定，
 * 再由服务端按记录中的平台与对象键读取内容。
 */
public interface FeedbackAttachmentService extends IService<FeedbackAttachment> {

    /**
     * 登记一次反馈附件上传。上传者与存储定位来自服务端。
     *
     * @param uploader    上传者
     * @param platform    存储平台
     * @param objectKey   对象键
     * @param url         上传返回地址，仅作记录
     * @param size        字节数
     * @return 新建的附件记录
     */
    FeedbackAttachment registerUpload(User uploader, String platform, String objectKey, String url, Long size);

    /**
     * 读取附件内容。每次调用都重新判定权限与隔离状态。
     * 归属未确认（BLOCKED）或已隔离的附件一律拒绝。
     *
     * @param attachmentId 附件标识
     * @param viewer       当前登录用户
     * @return 内容
     */
    AttachmentContent loadContent(Long attachmentId, User viewer);

    /**
     * 把附件绑定到反馈。只接受本人上传且尚未绑定的附件。
     *
     * @return 已绑定的附件
     */
    List<FeedbackAttachment> bindToFeedback(Long feedbackId, Long ownerUserId, Collection<Long> attachmentIds);

    /**
     * 列出反馈的附件。
     */
    List<FeedbackAttachment> listByFeedback(Long feedbackId);

    /**
     * 批量列出多条反馈的附件，按反馈 id 分组。
     */
    Map<Long, List<FeedbackAttachment>> listByFeedbacks(Collection<Long> feedbackIds);

    /**
     * 附件当前是否可被查看者读取。
     */
    boolean canRead(FeedbackAttachment attachment, User viewer);

    /**
     * 判断附件关联的反馈是否对查看者可读：创建者，或具有反馈管理职责的平台管理员。
     */
    boolean canReadFeedback(Long feedbackId, Long ownerUserId, User viewer);

    /**
     * 受控内容入口。无入口时返回 null。
     */
    String contentUrl(FeedbackAttachment attachment, User viewer);

    /**
     * 附件内容。
     */
    record AttachmentContent(byte[] bytes, String contentType, String downloadName) {
    }
}
