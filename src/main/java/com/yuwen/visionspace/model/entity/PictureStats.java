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

    // 项目没有注册 MetaObjectHandler：fill 注解只会把未赋值的时间字段写成 NULL，
    // 绕过库里的默认值与更新时间触发器。时间列交给数据库维护。
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
