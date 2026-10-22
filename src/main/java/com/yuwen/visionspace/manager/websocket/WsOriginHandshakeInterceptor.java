package com.yuwen.visionspace.manager.websocket;

import com.yuwen.visionspace.config.WebSecurityProperties;
import com.yuwen.visionspace.manager.auth.RequestOriginPolicy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手来源校验。
 *
 * <p>WebSocket 不受同源策略保护：任意站点都能带着用户 Cookie 发起握手，
 * 因此必须与 REST 用同一套判定（{@link RequestOriginPolicy}）。
 * 静态白名单无法表达「本站同源」——生产域名一换就得改代码，
 * 所以这里按请求自身的 Host 与代理透传信息做同源判定，
 * 配置里的可信来源只用于额外的开发期跨域入口。
 */
@Slf4j
@Component
public class WsOriginHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private WebSecurityProperties properties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }
        HttpServletRequest http = servletRequest.getServletRequest();
        boolean trusted = RequestOriginPolicy.isTrusted(
                http.getHeader("Origin"),
                http.getHeader("Host"),
                RequestOriginPolicy.requestHost(http.getHeader("X-Forwarded-Host"),
                        http.getServerName(), http.getServerPort()),
                scheme(http),
                properties.getTrustedOrigins());
        if (!trusted) {
            log.warn("拒绝来源不受信任的 WebSocket 握手: origin={}", http.getHeader("Origin"));
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String scheme(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getScheme();
    }
}
