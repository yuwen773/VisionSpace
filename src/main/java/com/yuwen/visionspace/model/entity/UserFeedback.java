package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户反馈表
 */
@Data
@TableName("user_feedback")
public class UserFeedback implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 反馈用户ID
     */
    private Long userId;

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
     * 附件图片URLs（JSON数组）
     */
    private String pictureUrls;

    /**
     * 处理状态：0=待处理 1=处理中 2=已解决
     */
    private Integer status;

    /**
     * 处理人管理员ID
     */
    private Long handlerId;

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
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}