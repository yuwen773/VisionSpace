package com.yuwen.visionspace.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录配置。
 *
 * <p>小程序端只用 {@code wx.login} 的 code 换取 openid，不直接持有 AppSecret。
 * AppSecret 只在服务端用于调用 {@code jscode2session}，任何响应、日志与异常文案都不得回显它。
 *
 * <p>未配置时（appid / secret 为空）应用照常启动，只有小程序登录端点会明确失败，
 * 不影响 Web 端与其它部署形态。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.wechat.miniprogram")
public class WeChatMiniProgramProperties {

    /**
     * 小程序 AppID（可公开）。
     */
    private String appid = "";

    /**
     * 小程序 AppSecret（敏感）。只能来自环境变量 / 密钥管理，不写进仓库。
     */
    private String secret = "";

    /**
     * 微信接口基地址。默认官方域名；隔离验收里指向本机替身。
     */
    private String baseUrl = "https://api.weixin.qq.com";

    /**
     * 连接超时（毫秒）。
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读取超时（毫秒）。微信接口正常在数百毫秒内返回，超时不重试，交给客户端重试。
     */
    private int readTimeoutMs = 5000;

    public boolean isConfigured() {
        return StrUtil.isNotBlank(appid) && StrUtil.isNotBlank(secret);
    }
}
