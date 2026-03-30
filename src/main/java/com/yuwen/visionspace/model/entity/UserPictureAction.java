package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户图片行为明细表
 * 用于记录用户对图片的各种行为，为推荐系统提供基础数据
 *
 * @see ActionTypeEnum 行为类型枚举
 * @see ActionSourceEnum 来源枚举
 */
@Data
@TableName("user_picture_action")
public class UserPictureAction {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，0表示匿名用户（未登录）
     */
    private Long userId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share
     */
    private Integer actionType;

    /**
     * 行为值，默认为1
     */
    private Integer actionValue;

    /**
     * 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER
     */
    private Integer source;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 是否已聚合：0-未处理 1-已处理
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer isProcessed;
}
