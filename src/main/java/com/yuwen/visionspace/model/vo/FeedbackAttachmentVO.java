package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 反馈附件视图。
 *
 * <p>只暴露受控内容入口与状态；不返回源站地址、存储平台或对象键。
 * 归属未确认的旧附件没有入口，状态为 BLOCKED。
 */
@Data
public class FeedbackAttachmentVO implements Serializable {

    private Long id;

    /** 受控内容入口；归属未确认或已隔离时为空 */
    private String url;

    /** UPLOADED / BOUND / LEGACY_VERIFIED / BLOCKED */
    private String status;

    private static final long serialVersionUID = 1L;
}
