package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 非浏览器客户端的令牌读取配置。
 *
 * <p>Web 端仍用 {@code SESSION} Cookie 承载登录态；小程序没有可靠的 Cookie 机制，
 * 改为在请求头里携带 Sa-Token 令牌。头名默认与 Sa-Token 自身的默认令牌名一致，
 * 避免维护两套叫法。
 *
 * <p><b>不支持把令牌放进查询参数</b>：URL 会进浏览器地址栏、Referer、图片缓存与
 * 反向代理访问日志，等于把完整登录凭据散到多个地方。无法自定义请求头的加载场景
 * （如私有图片）由客户端先用鉴权头下载到本地再展示。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.auth.token")
public class AuthTokenProperties {

    /**
     * 携带令牌的请求头名。
     */
    private String headerName = "satoken";
}
