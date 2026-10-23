package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 违规资源隔离与恢复记录。
 *
 * <p>隔离保留内容与引用，只改变可访问状态；恢复记录操作者、理由与时间，
 * 解除后资源仍按原有归属与成员关系判定，不扩大任何人的访问范围。
 */
@Data
@TableName("resource_isolation")
public class ResourceIsolation implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** {@link com.yuwen.visionspace.model.enums.ResourceTypeEnum} */
    private String resourceType;

    private Long resourceId;

    /** {@link com.yuwen.visionspace.model.enums.ResourceIsolationStatusEnum} */
    private Integer status;

    /** 隔离中唯一键；解除后置空 */
    private String activeKey;

    /** 隔离操作者 */
    private Long operatorId;

    /** 隔离理由，必填 */
    private String reason;

    private Long releaseOperatorId;

    private String releaseReason;

    private Date releaseTime;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
