package com.yuwen.visionspace.manager.wechat;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuwen.visionspace.config.WeChatMiniProgramProperties;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 真实微信接口的 {@code jscode2session} 客户端。
 *
 * <p>只把 openid 交给调用方。错误信息里不回显 AppSecret、code 或微信原始 errmsg：
 * 前者是凭据，后两者会进入日志并可能被用来关联用户。
 */
@Slf4j
@Component
public class HttpWeChatSessionProvider implements WeChatSessionProvider {

    private final WeChatMiniProgramProperties properties;

    private final RestClient restClient;

    public HttpWeChatSessionProvider(WeChatMiniProgramProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @PostConstruct
    void warnWhenNotConfigured() {
        if (!properties.isConfigured()) {
            log.warn("微信小程序未配置 appid/secret，/user/wx/* 端点将返回失败");
        }
    }

    @Override
    public String exchangeOpenId(String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少微信登录凭证");
        }
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "微信登录未配置");
        }
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppid())
                            .queryParam("secret", properties.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            // 只记录异常类型：异常消息可能带上含 secret 的请求地址
            log.warn("jscode2session 调用失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "微信登录失败，请稍后重试");
        }
        if (body == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "微信登录失败，请稍后重试");
        }
        int errcode = body.path("errcode").asInt(0);
        String openId = body.path("openid").asText("");
        if (errcode != 0 || StrUtil.isBlank(openId)) {
            // errcode 可观测、errmsg 不入库：40029=code 无效，40163=code 已被使用
            log.warn("jscode2session 返回错误: errcode={}", errcode);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "微信登录失败，请重新进入小程序");
        }
        return openId;
    }
}
