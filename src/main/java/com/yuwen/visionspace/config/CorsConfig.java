package com.yuwen.visionspace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置。
 *
 * <p>浏览器入口优先走 HTTPS 同源反向代理，正常部署下不产生跨域请求。
 * 开发期只放行显式配置的可信来源（默认本机 Vite 开发服务器）；
 * 不再使用 {@code *} 通配，避免任意站点带着 Cookie 调用本站。
 *
 * <p>CORS 控制的是「响应能不能被跨站页面读取」，不能阻止请求本身被执行，
 * 所以状态修改的防护由 {@code CsrfProtectionFilter} 独立完成。
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final WebSecurityProperties properties;

    public CorsConfig(WebSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 列表为空表示只信同源：不注册任何跨站来源，而不是退回开发期默认值。
        // 开发期的 localhost:5173 由 application.yml 的默认值提供，生产覆盖为空即关闭。
        if (properties.getTrustedOrigins().isEmpty()) {
            log.info("CORS allowed origins: none (same-origin only)");
            return;
        }
        String[] origins = properties.getTrustedOrigins().toArray(new String[0]);
        log.info("CORS allowed origins: {}", java.util.Arrays.toString(origins));
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 只放行显式可信来源
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
