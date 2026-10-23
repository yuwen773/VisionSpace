package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 创建单资源限时只读授权。
 *
 * <p>查看者、资源与到期时间都必须显式给出；不接受"整个空间"或"无限期"的授权，
 * 也不接受客户端传入的授权主体。
 */
@Data
public class PictureGrantCreateRequest implements Serializable {

    /** 资源（图片）标识 */
    private Long pictureId;

    /** 被授权查看者 */
    private Long granteeUserId;

    /** 到期时间，必填 */
    private Date expiresTime;

    /** 授权理由，可为空 */
    private String reason;

    private static final long serialVersionUID = 1L;
}
