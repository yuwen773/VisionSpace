package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器入口安全配置。
 *
 * <p>生产与开发都通过同一组属性表达「谁可以带着 Cookie 调用本站」，
 * 部署入口（域名、反向代理、端口）变化时只改配置，不改业务代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.web")
public class WebSecurityProperties {

    /**
     * 允许携带 Cookie 跨站访问的可信 Origin（含协议与端口），例如 https://visionspace.space。
     *
     * <p>同源请求不受此列表限制；留空表示不额外信任任何跨站来源。
     */
    private List<String> trustedOrigins = new ArrayList<>();

    /**
     * 访问入口（协议 + 主机 + 端口），用于拼接 Cookie 与跨站判定。
     *
     * <p>只用于日志与文档，不参与鉴权：鉴权只看请求自身的 Origin / Host / 代理头。
     */
    private String publicOrigin = "";

    /**
     * 状态修改请求的 CSRF 防护开关。生产与开发都必须保持开启；
     * 仅在隔离验收里显式关闭以证明防护确实生效过。
     */
    private boolean csrfEnabled = true;

    /**
     * CSRF 令牌 Cookie 名。令牌以双提交方式工作：Cookie 与请求头必须一致。
     */
    private String csrfCookieName = "VS_CSRF";

    /**
     * CSRF 令牌请求头名。
     */
    private String csrfHeaderName = "X-CSRF-Token";

    /**
     * CSRF 令牌 Cookie 的存活秒数。
     */
    private int csrfTokenMaxAge = 7200;
}
