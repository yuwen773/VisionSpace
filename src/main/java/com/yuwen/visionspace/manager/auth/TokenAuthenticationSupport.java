package com.yuwen.visionspace.manager.auth;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.config.AuthTokenProperties;
import com.yuwen.visionspace.constant.UserConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 非浏览器客户端（小程序）的令牌解析。
 *
 * <p>Web 端登录态在 Spring Session 里，凭据是 {@code SESSION} Cookie；
 * 小程序没有可靠的 Cookie 机制，改为持有一个 Sa-Token 令牌，只放在<b>请求头</b>里。
 * 两者共用同一套账号体系与同一个 {@link StpKit#SPACE} 令牌存储。
 *
 * <p><b>只认请求头</b>：不接受查询参数与请求体里的令牌。URL 会进浏览器历史、
 * Referer、图片缓存与反向代理日志，等于把完整登录凭据留在多处副本。
 */
@Component
public class TokenAuthenticationSupport {

    /**
     * 一次请求的凭据类型。CSRF 防护按类型分流：
     * 浏览器会话需要双提交令牌，令牌客户端不需要（令牌不会被浏览器自动附带）。
     */
    public enum CredentialKind {
        SESSION,
        TOKEN,
        ANONYMOUS
    }

    private final AuthTokenProperties properties;

    public TokenAuthenticationSupport(AuthTokenProperties properties) {
        this.properties = properties;
    }

    /**
     * @return 请求携带的令牌原文；没有时返回 {@code null}
     */
    public String resolveTokenValue(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(properties.getHeaderName());
        return StrUtil.isBlank(header) ? null : header.trim();
    }

    /**
     * @return 令牌对应的用户 id；令牌缺失、无效或已过期时返回 {@code null}
     */
    public Long resolveUserId(HttpServletRequest request) {
        String token = resolveTokenValue(request);
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            Object loginId = StpKit.SPACE.getLoginIdByToken(token);
            if (loginId == null) {
                return null;
            }
            return Long.valueOf(loginId.toString());
        } catch (Exception e) {
            // 非法令牌按未登录处理，不把解析异常抛给调用方
            return null;
        }
    }

    /**
     * 判定本次请求凭据类型。
     *
     * <p>顺序有意义：先看浏览器会话，再看显式令牌，最后才看 Sa-Token 自己是否认为已登录
     * （浏览器可能带着 Sa-Token 写的同名 Cookie）。这样 Web 端行为不变，
     * 而带令牌头的请求一定归入 {@link CredentialKind#TOKEN}。
     *
     * <p>Sa-Token 的请求参数读取已关闭（{@code sa-token.is-read-body: false}），
     * 否则 {@code StpKit.SPACE.isLogin()} 也会认 {@code ?satoken=}，把 URL 凭据又放回来。
     */
    public CredentialKind classify(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UserConstant.USER_LOGIN_STATE) != null) {
            return CredentialKind.SESSION;
        }
        if (resolveUserId(request) != null) {
            return CredentialKind.TOKEN;
        }
        try {
            if (StpKit.SPACE.isLogin()) {
                return CredentialKind.SESSION;
            }
        } catch (Exception e) {
            // Sa-Token 在过滤器阶段不可用（无请求上下文等）时按匿名处理
        }
        return CredentialKind.ANONYMOUS;
    }
}
