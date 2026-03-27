package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_picture_action")
public class UserPictureAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long pictureId;

    private String actionType;

    private Integer actionValue;

    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
