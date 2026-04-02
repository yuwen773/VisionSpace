package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("picture_stats")
public class PictureStats {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long pictureId;

    private Long viewCount;

    private Long likeCount;

    private Long collectCount;

    private Long downloadCount;

    private Long shareCount;

    private Long impressionCount;

    private Long clickCount;

    private BigDecimal ctr;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
