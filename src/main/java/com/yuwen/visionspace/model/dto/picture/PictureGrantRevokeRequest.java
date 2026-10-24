package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 撤销单资源授权。
 *
 * <p>标识按十进制字符串传输，理由与创建授权一致。
 */
@Data
public class PictureGrantRevokeRequest implements Serializable {

    /** 授权记录标识 */
    private String grantId;

    /** 撤销理由 */
    private String reason;

    private static final long serialVersionUID = 1L;
}
