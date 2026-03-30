package com.yuwen.visionspace.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 反馈视图对象
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
     * 反馈用户信息
     */
    private UserVO userVO;

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
     * 附件图片URLs
     */
    private List<String> pictureUrls;

    /**
     * 处理状态：0=待处理 1=处理中 2=已解决
     */
    private Integer status;

    /**
     * 处理人管理员ID
     */
    private Long handlerId;

    /**
     * 处理人信息
     */
    private UserVO handlerVO;

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
