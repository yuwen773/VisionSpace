package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 仅第二验收 JVM 的显式故障开关；不打入产品 jar，不触发任何真实提供方。 */
@TestConfiguration(proxyBeanMethods = false)
public class ProposalCrashConfiguration {
    @Bean
    @ConditionalOnProperty(name = "vs.acceptance.proposal-crash-after-commit", havingValue = "true")
    FilterRegistrationBean<Filter> proposalCrashFilter() {
        Filter filter = (request, response, chain) -> {
            var http = (HttpServletRequest) request;
            String nonce = System.getenv("VS_ACCEPTANCE_NONCE");
            if (nonce == null || !nonce.equals(http.getHeader("X-VS-Acceptance-Crash"))
                    || !"POST".equals(http.getMethod()) || !http.getRequestURI().startsWith("/api/agent/board-proposals")) {
                chain.doFilter(request, response);
                return;
            }
            var buffered = new ContentCachingResponseWrapper((HttpServletResponse) response);
            chain.doFilter(request, buffered);
            var result = new ObjectMapper().readTree(buffered.getContentAsByteArray());
            String status = result.path("data").path("status").asText();
            // Controller 已返回，业务代理的数据库事务已提交；缓冲字节尚未发给客户端。
            // halt 不执行 shutdown hook，区别于已有优雅停止/响应丢失测试。
            if (result.path("code").asInt(-1) == 0
                    && ("AWAITING_CONFIRMATION".equals(status) || "COMMITTED".equals(status))) {
                Runtime.getRuntime().halt(56);
            }
            buffered.copyBodyToResponse();
        };
        return new FilterRegistrationBean<>(filter);
    }
}
