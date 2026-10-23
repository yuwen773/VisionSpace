package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 撤销单资源授权。
 */
@Data
public class PictureGrantRevokeRequest implements Serializable {

    /** 授权记录标识 */
    private Long grantId;

    /** 撤销理由 */
    private String reason;

    private static final long serialVersionUID = 1L;
}
