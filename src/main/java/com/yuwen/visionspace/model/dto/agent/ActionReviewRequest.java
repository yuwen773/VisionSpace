package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 人工核对请求。
 *
 * <p>身份只取服务端登录态；{@code actionId} 由会话状态接口返回，
 * 客户端不能凭它推断别人的动作。
 */
@Data
public class ActionReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String threadId;

    /** 待核对的动作标识 */
    private String actionId;

    /** 核对说明（可选，会截断保存，不应包含正文或秘密） */
    private String note;
}
