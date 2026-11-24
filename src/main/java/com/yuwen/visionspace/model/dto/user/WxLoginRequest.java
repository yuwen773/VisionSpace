package com.yuwen.visionspace.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序静默登录请求：用 {@code wx.login} 的 code 换 openid 并识别已绑定账号。
 */
@Data
public class WxLoginRequest implements Serializable {

    private static final long serialVersionUID = 4421985309283741021L;

    /**
     * wx.login 返回的一次性 code
     */
    private String code;
}
