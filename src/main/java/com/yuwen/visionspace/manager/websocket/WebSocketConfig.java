package com.yuwen.visionspace.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import jakarta.annotation.Resource;

/**
 * WebSocket 配置（定义连接）。
 *
 * <p>来源判定交给 {@link WsOriginHandshakeInterceptor}，与 REST 共用
 * {@link com.yuwen.visionspace.manager.auth.RequestOriginPolicy}：
 * 同源请求（含反向代理透传的 Host 与协议）直接放行，配置里的可信来源
 * 只额外放行开发期跨域入口。{@code setAllowedOrigins("*")} 会让任意站点
 * 借用户 Cookie 建立编辑连接，所以这里不做静态白名单——
 * 静态列表无法表达「本站同源」，换域名就要改代码。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Resource
    private WsOriginHandshakeInterceptor wsOriginHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsOriginHandshakeInterceptor, wsHandshakeInterceptor)
                // 来源由上面的拦截器按同源规则判定；容器层不再做一遍静态白名单
                .setAllowedOriginPatterns("*");
    }
}
