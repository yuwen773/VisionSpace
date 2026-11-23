package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.vo.WxLoginVO;

/**
 * 微信小程序登录与账号绑定。
 *
 * <p>与 Web 端共用同一套账号与 Sa-Token 空间令牌；小程序额外持有一个 openid，
 * 与账号一对一绑定。绑定必须由用户输入现有账号密码完成——个人主体小程序拿不到手机号，
 * 账号密码是唯一能证明「这个微信就是这位用户」的凭据。
 */
public interface MiniProgramAuthService {

    /**
     * 静默登录：用 code 换 openid，找到已绑定账号并签发令牌。
     *
     * @throws com.yuwen.visionspace.exception.BusinessException
     *         {@code 40102} 该微信尚未绑定账号，客户端应转到绑定页
     */
    WxLoginVO login(String code);

    /**
     * 首次绑定并登录：校验账号密码，把 openid 绑定到该账号，再签发令牌。
     *
     * <p>同一微信重复绑定同一账号是幂等的（直接登录）；绑定到不同账号、
     * 或该账号已绑定其它微信时拒绝，不覆盖既有绑定。
     */
    WxLoginVO bind(String code, String userAccount, String userPassword);
}
