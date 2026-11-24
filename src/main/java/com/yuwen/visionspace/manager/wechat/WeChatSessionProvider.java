package com.yuwen.visionspace.manager.wechat;

/**
 * 用 {@code wx.login} 的临时 code 换取小程序 openid。
 *
 * <p>抽成接口是为了让验收测试能用本机替身替换真实微信接口：
 * 测试不应该依赖外网、AppSecret 或真实用户。
 */
public interface WeChatSessionProvider {

    /**
     * @param code 小程序 {@code wx.login} 返回的一次性 code
     * @return 该用户在本小程序下的 openid
     * @throws com.yuwen.visionspace.exception.BusinessException code 无效或微信接口不可用
     */
    String exchangeOpenId(String code);
}
