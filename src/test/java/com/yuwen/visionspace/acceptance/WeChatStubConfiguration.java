package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.wechat.WeChatSessionProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 用本机替身替换真实微信 {@code jscode2session}。
 *
 * <p>验收测试不依赖外网、AppSecret 或真实微信用户：约定 {@code vs-code-<openid>}
 * 这样的 code 直接映射为对应的 openid，其它 code 一律按无效处理。
 */
@TestConfiguration
public class WeChatStubConfiguration {

    public static final String CODE_PREFIX = "vs-code-";

    @Bean
    @Primary
    public WeChatSessionProvider weChatSessionProvider() {
        return code -> {
            if (code == null || !code.startsWith(CODE_PREFIX) || code.length() == CODE_PREFIX.length()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "stub: 无效的微信 code");
            }
            return code.substring(CODE_PREFIX.length());
        };
    }
}
