package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 反馈附件记录。
 *
 * <p>平台与对象键来自服务端上传结果，是读取附件的唯一定位依据；
 * {@link #url} 只作记录，不返回浏览器。旧附件无法确认真实归属，
 * 只登记为 BLOCKED 等待人工核对，不自动认领也不公开。
 */
@Data
@TableName("feedback_attachment")
public class FeedbackAttachment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 上传者用户 id，来自服务端会话 */
    private Long ownerUserId;

    /** 绑定后的反馈 id；尚未随反馈提交时为空 */
    private Long feedbackId;

    /** 存储平台标识；旧附件为 legacy */
    private String platform;

    /** 存储对象键；旧附件保留地址原文 */
    private String objectKey;

    /** 上传时返回的地址，仅作记录，不对外返回 */
    private String url;

    private Long fileSize;

    /** {@link com.yuwen.visionspace.model.enums.FeedbackAttachmentStatusEnum} */
    private Integer status;

    /** 旧附件地址摘要，仅用于幂等登记 */
    private String legacyUrlHash;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
