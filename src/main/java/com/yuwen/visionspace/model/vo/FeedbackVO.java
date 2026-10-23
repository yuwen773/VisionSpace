package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 反馈视图对象。
 *
 * <p>响应最小化：提交者只返回标识与名称，附件只返回受控入口与状态。
 * 不返回完整用户资料、源站附件地址或存储定位信息。
 */
@Data
public class FeedbackVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 反馈用户ID
     */
    private Long userId;

    /**
     * 反馈用户名称（最小化，不含账号、头像、角色等资料）
     */
    private String userName;

    /**
     * 反馈类型：1=产品建议 2=内容举报 3=工单支持
     */
    private Integer type;

    /**
     * 反馈标题
     */
    private String title;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 受控附件列表
     */
    private List<FeedbackAttachmentVO> attachments;

    /**
     * 处理状态：0=待处理 1=处理中 2=已解决
     */
    private Integer status;

    /**
     * 处理人管理员ID
     */
    private Long handlerId;

    /**
     * 处理人名称（最小化）
     */
    private String handlerName;

    /**
     * 管理员处理备注
     */
    private String handlerNote;

    /**
     * 处理时间
     */
    private Date handleTime;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
