package com.yuwen.visionspace.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序首次绑定请求：用账号密码证明账号归属，再把当前微信 openid 绑定到该账号。
 */
@Data
public class WxBindRequest implements Serializable {

    private static final long serialVersionUID = 6130974812956372840L;

    /**
     * wx.login 返回的一次性 code
     */
    private String code;

    /**
     * 现有 VisionSpace 账号
     */
    private String userAccount;

    /**
     * 账号密码
     */
    private String userPassword;
}
