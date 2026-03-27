package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户图片行为明细表
 * 用于记录用户对图片的各种行为，为推荐系统提供基础数据
 *
 * <p>支持的的行为类型(actionType)：
 * <ul>
 *   <li>impression - 曝光（图片在用户视野中展示）</li>
 *   <li>click - 点击（用户点击查看图片）</li>
 *   <li>view - 浏览（用户在详情页浏览）</li>
 *   <li>like - 点赞</li>
 *   <li>collect - 收藏</li>
 *   <li>download - 下载</li>
 *   <li>share - 分享</li>
 * </ul>
 *
 * @see com.yuwen.visionspace.mapper.UserPictureActionMapper
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
     * 行为类型：impression/click/view/like/collect/download/share
     */
    private String actionType;

    /**
     * 行为值，默认为1
     */
    private Integer actionValue;

    /**
     * 来源标识：HOME_RECOMMEND/DETAIL/SEARCH/OTHER
     */
    private String source;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
