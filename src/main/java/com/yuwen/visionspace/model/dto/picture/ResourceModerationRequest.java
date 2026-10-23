package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 违规资源隔离 / 恢复请求。
 *
 * <p>目标、操作者与理由都必须可核对：操作者来自服务端会话，理由必填。
 */
@Data
public class ResourceModerationRequest implements Serializable {

    /** 资源类型，目前支持 picture / feedback-attachment */
    private String resourceType;

    /** 资源标识 */
    private Long resourceId;

    /** 处置理由，必填 */
    private String reason;

    private static final long serialVersionUID = 1L;
}
