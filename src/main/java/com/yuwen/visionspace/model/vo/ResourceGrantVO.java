package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 授权记录视图。
 *
 * <p>只包含授权审计所需字段；不包含图片内容、存储地址或任何密钥。
 *
 * <p>标识按十进制字符串给出：雪花标识超出 JavaScript 的安全整数范围，
 * 客户端转成 number 会丢精度。
 */
@Data
public class ResourceGrantVO implements Serializable {

    /** 授权记录标识（十进制字符串） */
    private String id;

    private String resourceType;

    /** 资源标识（十进制字符串） */
    private String resourceId;

    private String resourceName;

    /** 被授权查看者标识（十进制字符串） */
    private String granteeUserId;

    /** 被授权人的展示名（最小化：仅名称） */
    private String granteeUserName;

    /** 授权主体标识（十进制字符串） */
    private String grantedByUserId;

    private String grantedByUserName;

    private String reason;

    private Date expiresTime;

    /** ACTIVE / REVOKED / EXPIRED */
    private String status;

    /** 撤销人标识（十进制字符串） */
    private String revokedByUserId;

    private String revokeReason;

    private Date revokeTime;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
