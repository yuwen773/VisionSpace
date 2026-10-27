package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 单资源限时只读授权。
 *
 * <p>授权绑定指定查看者与指定资源，到期时间必填，可随时撤销。
 * 每次内容请求都重新查库判定，不签发可绕过撤销的独立直链。
 */
@Data
@TableName("resource_access_grant")
public class ResourceAccessGrant implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** {@link com.yuwen.visionspace.model.enums.ResourceTypeEnum} */
    private String resourceType;

    private Long resourceId;

    /** 被授权查看者 */
    private Long granteeUserId;

    /** 授权主体：个人资源所有者或团队管理员 */
    private Long grantedByUserId;

    private String reason;

    /** 到期时间，必填 */
    private Date expiresTime;

    /** {@link com.yuwen.visionspace.model.enums.ResourceGrantStatusEnum} */
    private Integer status;

    /** 有效授权唯一键；撤销或到期后置空 */
    private String activeKey;

    private Long revokedByUserId;

    private String revokeReason;

    private Date revokeTime;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
