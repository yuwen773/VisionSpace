package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 非浏览器客户端的令牌读取配置。
 *
 * <p>Web 端仍用 {@code SESSION} Cookie 承载登录态；小程序没有可靠的 Cookie 机制，
 * 改为在请求头或查询参数里携带 Sa-Token 令牌。两个名字默认都是 {@code satoken}，
 * 与 Sa-Token 自身的默认令牌名一致，避免维护两套叫法。
 *
 * <p>查询参数只用于 {@code <image>} 这类无法自定义请求头的加载场景；它会被返回给
 * 浏览器地址栏与反向代理日志，因此反向代理必须对该参数脱敏（见 nginx 配置）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.auth.token")
public class AuthTokenProperties {

    /**
     * 携带令牌的请求头名。
     */
    private String headerName = "satoken";

    /**
     * 携带令牌的查询参数名。
     */
    private String queryName = "satoken";
}
