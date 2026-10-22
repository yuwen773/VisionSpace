package com.yuwen.visionspace.manager.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.config.WebSecurityProperties;
import com.yuwen.visionspace.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 状态修改请求的 CSRF 防护。
 *
 * <p>采用「服务端会话绑定 + 双提交」：
 * <ul>
 *   <li>令牌随机生成后同时写入服务端会话与一个可由前端读取的 Cookie；</li>
 *   <li>状态修改请求必须带 {@code X-CSRF-Token}，且与 Cookie、会话中的值三者一致；</li>
 *   <li>同时校验 Origin 与 Host：Origin 存在但不是本站或显式可信来源时直接拒绝。</li>
 * </ul>
 *
 * <p>只靠 Cookie 属性或 CORS 响应头不足以构成防护：攻击者可以让浏览器带上 Cookie。
 * 会话绑定让攻击者即使能往本站写同名 Cookie（例如通过兄弟子域），也无法让
 * 服务端会话里的令牌与之匹配。
 *
 * <p>拒绝发生在进入控制器之前，因此恶意请求不会产生任何业务写入或外部调用。
 */
@Slf4j
@Component
@Order(2)
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String TOKEN_SESSION_KEY = "vs_csrf_token";

    private final WebSecurityProperties properties;

    private final ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();

    public CsrfProtectionFilter(WebSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        boolean stateChanging = !SAFE_METHODS.contains(method);

        if (stateChanging) {
            if (!properties.isCsrfEnabled()) {
                chain.doFilter(request, response);
                return;
            }
            // 先确保本请求的响应能带上令牌：登录等入口会在本次请求内建立会话，
            // 若等到 controller 之后再写，Session Cookie 已随响应提交，新 Cookie 会丢失。
            issueTokenIfNeeded(request, response);
            // 来源判定对所有状态修改都生效：浏览器发起跨站表单/脚本时一定带 Origin
            if (!originAllowed(request)) {
                reject(request, response, "请求来源不受信任");
                return;
            }
            // 令牌只对「已经带上本人登录态」的请求强制：匿名请求没有可被借用的授权，
            // 公开的 POST 查询也不该因为缺少令牌而不可用
            if (isAuthenticated(request) && !tokenMatches(request)) {
                reject(request, response, "缺少或无效的 CSRF 令牌");
                return;
            }
            chain.doFilter(request, response);
            // 登录新建会话后补发令牌；响应已提交（SSE 等）时不影响本次结果
            try {
                issueTokenIfNeeded(request, response);
            } catch (IllegalStateException e) {
                log.debug("CSRF token not issued after committed response: {}", request.getRequestURI());
            }
            return;
        }

        // 安全方法：确保浏览器拿到当前会话的令牌，供后续状态修改使用
        issueTokenIfNeeded(request, response);
        chain.doFilter(request, response);
    }

    /**
     * 请求是否带上了服务端可识别的登录态（HTTP 会话或空间鉴权令牌）。
     *
     * <p>只有这类请求才可能被第三方站点借用身份，因此必须同时证明令牌一致。
     * 匿名请求（含公开的 POST 查询）没有可被借用的授权，不强制令牌。
     */
    private boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UserConstant.USER_LOGIN_STATE) != null) {
            return true;
        }
        try {
            return StpKit.SPACE.isLogin();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean originAllowed(HttpServletRequest request) {
        return RequestOriginPolicy.isTrusted(
                request.getHeader("Origin"),
                request.getHeader("Host"),
                RequestOriginPolicy.requestHost(request.getHeader("X-Forwarded-Host"),
                        request.getServerName(), request.getServerPort()),
                requestScheme(request),
                properties.getTrustedOrigins());
    }

    /**
     * 对外协议：反向代理透传的 X-Forwarded-Proto 优先，其次取容器判定的协议。
     */
    private String requestScheme(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getScheme();
    }

    private boolean tokenMatches(HttpServletRequest request) {
        String submitted = request.getHeader(properties.getCsrfHeaderName());
        String cookieValue = cookieValue(request, properties.getCsrfCookieName());
        HttpSession session = request.getSession(false);
        String sessionToken = session == null ? null : (String) session.getAttribute(TOKEN_SESSION_KEY);
        return constantTimeEquals(submitted, cookieValue) && constantTimeEquals(submitted, sessionToken);
    }

    /**
     * 会话存在时确保服务端与浏览器各持有一份一致的令牌。
     *
     * <p>只处理已经存在的会话：过滤器不在无会话请求上创建新 Session，
     * 避免给匿名访问凭空增加 Redis 存储。
     */
    private void issueTokenIfNeeded(HttpServletRequest request, HttpServletResponse response) {
        // 匿名访问不创建会话；唯一的例外是登录：登录成功后下一次写请求需要令牌，
        // 而那时用户已带 Cookie，不能再靠「无凭据」豁免。
        if (request.getSession(false) == null && !isLoginRequest(request)) {
            return;
        }
        HttpSession session = request.getSession(true);
        String existing = (String) session.getAttribute(TOKEN_SESSION_KEY);
        String cookieValue = cookieValue(request, properties.getCsrfCookieName());
        if (existing == null) {
            existing = newToken();
            session.setAttribute(TOKEN_SESSION_KEY, existing);
        }
        if (cookieValue != null && constantTimeEquals(cookieValue, existing)) {
            return;
        }
        // 令牌本身不是凭据：攻击者拿到它也无法在没有会话的情况下使用
        response.addCookie(buildCookie(request, existing));
    }

    private Cookie buildCookie(HttpServletRequest request, String value) {
        Cookie cookie = new Cookie(properties.getCsrfCookieName(), value);
        cookie.setHttpOnly(false);
        cookie.setSecure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        // 必须是站点根路径：前端在页面（例如 /user/login）用 document.cookie 读取令牌，
        // 若限制在上下文路径 /api 下，页面脚本根本看不到，写请求会永远缺少令牌。
        cookie.setPath("/");
        cookie.setMaxAge(properties.getCsrfTokenMaxAge());
        return cookie;
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/user/login");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        log.warn("CSRF protection rejected request: method={}, uri={}",
                request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ResultUtils.error(ErrorCode.FORBIDDEN_ERROR.getCode(), message));
    }
}
