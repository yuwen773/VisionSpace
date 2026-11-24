package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序登录/绑定成功后的凭据与用户视图。
 *
 * <p>令牌只在这次响应里下发一次，客户端自行保存；服务端不额外保存明文副本。
 * 只下发请求头名：服务端不接受查询参数或其他 URL 形式的令牌，
 * 私有图片由客户端带鉴权头下载到本地再展示。
 */
@Data
public class WxLoginVO implements Serializable {

    private static final long serialVersionUID = 7809215536420891327L;

    /**
     * Sa-Token 令牌
     */
    private String token;

    /**
     * 携带令牌的请求头名
     */
    private String tokenName;

    /**
     * 脱敏后的当前用户
     */
    private LoginUserVO user;
}
