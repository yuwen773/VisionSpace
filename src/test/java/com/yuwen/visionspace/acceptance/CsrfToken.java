package com.yuwen.visionspace.acceptance;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.http.HttpClient;

/**
 * 从测试客户端自己的 Cookie 存储里取双提交令牌。
 *
 * <p>与浏览器的行为一致：令牌 Cookie 由服务端在会话建立后下发，
 * 状态修改请求把它原样放进请求头。测试不绕过过滤器，也不关闭防护。
 */
final class CsrfToken {

    static final String COOKIE = "VS_CSRF";
    static final String HEADER = "X-CSRF-Token";

    private CsrfToken() {
    }

    /**
     * @return 当前客户端持有的令牌；尚未下发时返回空串（首次注册/登录）
     */
    static String of(HttpClient client) {
        CookieManager manager = client.cookieHandler()
                .filter(CookieManager.class::isInstance)
                .map(CookieManager.class::cast)
                .orElse(null);
        if (manager == null) {
            return "";
        }
        return manager.getCookieStore().getCookies().stream()
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse("");
    }
}
