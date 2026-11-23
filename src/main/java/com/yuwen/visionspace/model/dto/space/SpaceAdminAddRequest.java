package com.yuwen.visionspace.model.dto.space;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员代用户创建空间请求。
 *
 * <p>与 {@link SpaceAddRequest} 的唯一差别是显式指定空间归属用户：
 * 平台管理员在管理端为某个用户建空间，而不是把空间记在自己名下。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceAdminAddRequest extends SpaceAddRequest {

    /**
     * 空间归属用户 id（必填）
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
