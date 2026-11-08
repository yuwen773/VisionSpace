package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 快照只存检索依据，文件及原图片不变。 */
@Data
@TableName("asset_search_snapshot")
public class AssetSearchSnapshot {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long ownerId;
    private Long spaceId;
    private String threadId;
    private String payload;
}
