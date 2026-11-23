package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序登录/绑定成功后的凭据与用户视图。
 *
 * <p>令牌只在这次响应里下发一次，客户端自行保存；服务端不额外保存明文副本。
 * 请求头名与查询参数名分别下发：{@code <image>} 无法自定义请求头，只能把令牌放进
 * 查询参数，两处名字可独立配置，客户端不应写死。
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
     * 携带令牌的查询参数名（用于 {@code <image>} 等场景）
     */
    private String tokenQueryName;

    /**
     * 脱敏后的当前用户
     */
    private LoginUserVO user;
}
