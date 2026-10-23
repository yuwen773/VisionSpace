package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 授权记录视图。
 *
 * <p>只包含授权审计所需字段；不包含图片内容、存储地址或任何密钥。
 */
@Data
public class ResourceGrantVO implements Serializable {

    private Long id;

    private String resourceType;

    private Long resourceId;

    private String resourceName;

    private Long granteeUserId;

    /** 被授权人的展示名（最小化：仅名称） */
    private String granteeUserName;

    private Long grantedByUserId;

    private String grantedByUserName;

    private String reason;

    private Date expiresTime;

    /** ACTIVE / REVOKED / EXPIRED */
    private String status;

    private Long revokedByUserId;

    private String revokeReason;

    private Date revokeTime;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
